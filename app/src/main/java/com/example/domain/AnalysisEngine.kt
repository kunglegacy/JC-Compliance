package com.example.domain

import com.example.data.model.AnalysisSummary
import com.example.data.model.CategoryBreakdown
import com.example.data.model.ComplianceDivision
import com.example.data.model.ComplianceReport
import com.example.data.model.DivisionSummary
import com.example.data.model.OfficerMetric
import com.example.data.model.OfficerSortOption
import com.example.data.model.ReportResolutionStatus
import com.example.data.model.ReportScope
import com.example.data.model.SectionBreakdown
import com.example.data.model.SeverityLevel
import com.example.data.model.StaffAggregateMetric
import com.example.data.model.StaffMember
import kotlin.math.round

object AnalysisEngine {

    private val primaryStudentHeadcount = 135
    private val secondaryStudentHeadcount = 165

    fun resolveDivision(report: ComplianceReport, staff: StaffMember? = null): ComplianceDivision {
        ComplianceDivision.fromAny(report.division)?.let { return it }
        ComplianceDivision.fromAny(report.department)?.let { return it }
        ComplianceDivision.fromAny(report.category)?.let { return it }
        ComplianceDivision.fromAny(staff?.department)?.let { return it }
        if (report.targetType.equals("division", ignoreCase = true) &&
            report.complianceCheck.lowercase().let {
                it.contains("cbt") || it.contains("camera") || it.contains("computer") ||
                    it.contains("projector") || it.contains("biometric") || it.contains("portal")
            }
        ) {
            return ComplianceDivision.CDS
        }
        return ComplianceDivision.ACADEMIC
    }

    fun resolveAcademicSection(report: ComplianceReport, staff: StaffMember? = null): String {
        val text = buildString {
            append(report.className.orEmpty()).append(' ')
            append(staff?.assignedLocation.orEmpty()).append(' ')
            append(staff?.staffGroup.orEmpty()).append(' ')
            append(report.department.orEmpty()).append(' ')
            append(report.staffGroup.orEmpty())
        }.lowercase()

        return when {
            text.contains("creche") || text.contains("nursery") || text.contains("kg") -> "Nursery"
            text.contains("basic 1") || text.contains("basic 2") || text.contains("basic 3") ||
                text.contains("junior primary") || text.contains("pry 1") || text.contains("pry 2") || text.contains("pry 3") -> "Junior Primary"
            text.contains("basic 4") || text.contains("basic 5") || text.contains("basic 6") ||
                text.contains("senior primary") || text.contains("pry 4") || text.contains("pry 5") || text.contains("pry 6") -> "Senior Primary"
            text.contains("jss") || text.contains("junior secondary") -> "Junior Secondary"
            text.contains("ss 1") || text.contains("ss 2") || text.contains("ss 3") ||
                text.contains("sss") || text.contains("senior secondary") -> "Senior Secondary"
            else -> "Junior Secondary"
        }
    }

    fun resolveOperationScope(report: ComplianceReport, staff: StaffMember? = null): String {
        val text = buildString {
            append(report.category).append(' ')
            append(report.complianceCheck).append(' ')
            append(report.staffGroup.orEmpty()).append(' ')
            append(staff?.staffGroup.orEmpty()).append(' ')
            append(staff?.role.orEmpty()).append(' ')
            append(report.exceptionText)
        }.lowercase()

        return when {
            text.contains("secur") -> "Security"
            text.contains("minder") || text.contains("nanny") -> "Minders"
            text.contains("hostel") || text.contains("kitchen") || text.contains("siesta") || text.contains("dining") -> "Hostel/Kitchen"
            text.contains("transport") || text.contains("driver") || text.contains("bus") || text.contains("tyre") -> "Transport"
            else -> "Maintenance"
        }
    }

    fun getRecordScope(report: ComplianceReport, staffMap: Map<String, StaffMember>): String {
        val staff = findStaff(report, staffMap)
        val combined = "${report.category} ${report.complianceCheck} ${report.staffGroup.orEmpty()} ${staff?.role.orEmpty()}".lowercase()
        if (combined.contains("mentor")) return "Mentorship"

        return when (resolveDivision(report, staff)) {
            ComplianceDivision.CDS -> "CDS"
            ComplianceDivision.DISCIPLINE -> "Discipline"
            ComplianceDivision.OPERATIONS -> resolveOperationScope(report, staff)
            ComplianceDivision.ACADEMIC -> resolveAcademicSection(report, staff)
        }
    }

    fun recordMatchesScope(
        report: ComplianceReport,
        scope: ReportScope,
        staffMap: Map<String, StaffMember>
    ): Boolean {
        if (scope == ReportScope.ALL) return true
        val resolved = getRecordScope(report, staffMap)
        if (resolved == scope.key) return true
        if (scope == ReportScope.HOSTEL_KITCHEN && (resolved == "Hostel/Kitchen" || resolved == "Hostel / Kitchen")) return true
        if (scope == ReportScope.ACADEMIC) {
            return resolved in listOf("Nursery", "Junior Primary", "Senior Primary", "Junior Secondary", "Senior Secondary", "Academic")
        }
        return false
    }

    fun filterReports(
        reports: List<ComplianceReport>,
        term: String = "ALL",
        startDate: String = "",
        endDate: String = ""
    ): List<ComplianceReport> {
        return reports.filter { r ->
            if (term != "ALL" && !r.term.equals(term, ignoreCase = true)) return@filter false
            val d = r.incidentDate.take(10)
            if (d.isNotBlank()) {
                if (startDate.isNotBlank() && d < startDate) return@filter false
                if (endDate.isNotBlank() && d > endDate) return@filter false
            }
            true
        }
    }

    fun buildStaffLookup(staff: List<StaffMember>): Map<String, StaffMember> {
        val map = mutableMapOf<String, StaffMember>()
        staff.forEach { s ->
            if (s.id.isNotBlank()) map[s.id.lowercase()] = s
            if (s.staffCode.isNotBlank()) map["code:${s.staffCode.lowercase()}"] = s
            if (s.name.isNotBlank()) map["name:${s.name.lowercase()}"] = s
        }
        return map
    }

    private fun findStaff(r: ComplianceReport, staffMap: Map<String, StaffMember>): StaffMember? {
        r.staffId?.lowercase()?.let { staffMap[it] }?.let { return it }
        r.staffCode?.lowercase()?.let { staffMap["code:$it"] }?.let { return it }
        r.staffName?.lowercase()?.let { staffMap["name:$it"] }?.let { return it }
        return null
    }

    fun calculateStaffAggregates(
        reports: List<ComplianceReport>,
        staffRoster: List<StaffMember>
    ): List<StaffAggregateMetric> {
        val nonCdsReports = reports.filter { resolveDivision(it) != ComplianceDivision.CDS && !it.staffName.isNullOrBlank() }
        val grouped = nonCdsReports.groupBy { r ->
            val key = r.staffId?.takeIf { it.isNotBlank() }
                ?: r.staffCode?.takeIf { it.isNotBlank() }?.lowercase()
                ?: r.staffName.orEmpty().trim().lowercase()
            key
        }

        return grouped.map { (key, list) ->
            val first = list.first()
            val matchedRoster = staffRoster.firstOrNull {
                it.id == first.staffId ||
                    (first.staffCode != null && it.staffCode.equals(first.staffCode, true)) ||
                    (first.staffName != null && it.name.equals(first.staffName, true))
            }
            val count = list.size
            val seen = list.count { it.isSeen }
            StaffAggregateMetric(
                staffKey = key,
                staffId = first.staffId ?: matchedRoster?.id,
                staffName = matchedRoster?.name ?: first.staffName ?: "Staff Member",
                staffCode = matchedRoster?.staffCode ?: first.staffCode.orEmpty(),
                department = matchedRoster?.department ?: first.department ?: first.division,
                exceptionCount = count,
                seenCount = seen,
                unseenCount = count - seen,
                severity = SeverityLevel.fromCount(count),
                latestIncidentDate = list.maxOfOrNull { it.incidentDate } ?: "",
                reports = list.sortedByDescending { it.incidentDate }
            )
        }.sortedByDescending { it.exceptionCount }
    }

    fun calculateOfficerMetrics(
        reports: List<ComplianceReport>,
        startDate: String = "",
        endDate: String = "",
        searchQuery: String = "",
        sortOption: OfficerSortOption = OfficerSortOption.HIGHEST_TOTAL
    ): List<OfficerMetric> {
        val filtered = filterReports(reports, term = "ALL", startDate = startDate, endDate = endDate)
        val grouped = filtered.groupBy { r ->
            r.reportedBy.ifBlank { r.reporterName.lowercase().trim() }.ifBlank { "unknown" }
        }

        val q = searchQuery.trim().lowercase()
        val list = grouped.map { (key, items) ->
            val name = items.firstNotNullOfOrNull { it.reporterName.takeIf { n -> n.isNotBlank() } }
                ?: "Reporting Officer"
            val seen = items.count { it.isSeen }
            val rejected = items.count { it.status == ReportResolutionStatus.REJECTED }
            OfficerMetric(
                officerKey = key,
                officerName = name,
                officerEmail = items.firstOrNull()?.reporterRole.orEmpty(),
                totalReports = items.size,
                seenCount = seen,
                unseenCount = items.count { !it.isSeen },
                rejectedCount = rejected
            )
        }.filter { m ->
            if (q.isBlank()) true
            else m.officerName.lowercase().contains(q) || m.officerEmail.lowercase().contains(q) || m.officerKey.lowercase().contains(q)
        }

        return when (sortOption) {
            OfficerSortOption.HIGHEST_TOTAL -> list.sortedByDescending { it.totalReports }
            OfficerSortOption.MOST_UNSEEN -> list.sortedByDescending { it.unseenCount }
            OfficerSortOption.MOST_REJECTED -> list.sortedByDescending { it.rejectedCount }
        }
    }

    fun calculateAnalysisSummary(
        reports: List<ComplianceReport>,
        staffRoster: List<StaffMember>,
        term: String,
        startDate: String,
        endDate: String
    ): AnalysisSummary {
        val filtered = filterReports(reports, term, startDate, endDate)
        val staffMap = buildStaffLookup(staffRoster)

        val seenCount = filtered.count { it.isSeen }
        val resolvedCount = filtered.count { it.status == ReportResolutionStatus.ACCEPTED }
        val activeCount = filtered.size - resolvedCount

        val divisions = ComplianceDivision.entries.map { div ->
            val items = filtered.filter { resolveDivision(it, findStaff(it, staffMap)) == div }
            DivisionSummary(
                division = div,
                totalReports = items.size,
                seenCount = items.count { it.isSeen },
                unseenCount = items.count { !it.isSeen }
            )
        }

        val acadReports = filtered.filter { resolveDivision(it, findStaff(it, staffMap)) == ComplianceDivision.ACADEMIC }
        val sectionNames = listOf("Nursery", "Junior Primary", "Senior Primary", "Junior Secondary", "Senior Secondary")
        val sections = sectionNames.map { sec ->
            val count = acadReports.count { resolveAcademicSection(it, findStaff(it, staffMap)) == sec }
            val headcount = when (sec) {
                "Nursery", "Junior Primary", "Senior Primary" -> 50
                else -> 65
            }
            val ratio = round((count.toFloat() / headcount.toFloat()) * 100f) / 100f
            SectionBreakdown(sec, count, headcount, ratio)
        }

        val discReports = filtered.filter { resolveDivision(it, findStaff(it, staffMap)) == ComplianceDivision.DISCIPLINE }
        var discPrimary = 0
        var discSecondary = 0
        discReports.forEach { r ->
            val sec = resolveAcademicSection(r, findStaff(r, staffMap))
            if (sec.contains("Primary") || sec == "Nursery") discPrimary++ else discSecondary++
        }

        val catMap = filtered.groupBy { it.complianceCheck.ifBlank { it.category } }
            .map { (cat, list) -> CategoryBreakdown(cat, list.size) }
            .sortedByDescending { it.count }
            .take(8)

        val activeStaff = staffRoster.filter { it.isActive }
        val sevCounts = mutableMapOf(
            SeverityLevel.NONE to 0,
            SeverityLevel.LOW to 0,
            SeverityLevel.MEDIUM to 0,
            SeverityLevel.HIGH to 0,
            SeverityLevel.CRITICAL to 0
        )
        val nonCds = filtered.filter { resolveDivision(it, findStaff(it, staffMap)) != ComplianceDivision.CDS }
        activeStaff.forEach { person ->
            val c = nonCds.count { r ->
                (person.id.isNotBlank() && r.staffId == person.id) ||
                    (person.staffCode.isNotBlank() && r.staffCode.equals(person.staffCode, true)) ||
                    (r.staffName.equals(person.name, true))
            }
            val sev = SeverityLevel.fromCount(c)
            sevCounts[sev] = (sevCounts[sev] ?: 0) + 1
        }

        return AnalysisSummary(
            totalExceptions = filtered.size,
            activeCases = activeCount,
            resolvedCases = resolvedCount,
            seenCount = seenCount,
            unseenCount = filtered.size - seenCount,
            divisionBreakdown = divisions,
            sectionBreakdowns = sections,
            disciplinePrimaryCount = discPrimary,
            disciplinePrimaryRatio = round((discPrimary.toFloat() / primaryStudentHeadcount) * 1000f) / 1000f,
            disciplineSecondaryCount = discSecondary,
            disciplineSecondaryRatio = round((discSecondary.toFloat() / secondaryStudentHeadcount) * 1000f) / 1000f,
            categoryBreakdowns = catMap,
            staffSeverityCounts = sevCounts
        )
    }
}
