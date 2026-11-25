package com.example.domain

import com.example.data.model.ComplianceReport
import com.example.data.model.ReportResolutionStatus
import com.example.data.model.ReportScope
import com.example.data.model.StaffMember
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GroupExportSubRow(
    val name: String,
    val className: String,
    val subject: String
)

data class NormalizedReportRow(
    val date: String,
    val name: String,
    val unit: String,
    val className: String,
    val subject: String,
    val exception: String,
    val effect: String,
    val driverName: String,
    val busNumber: String,
    val complianceCheck: String,
    val isGroup: Boolean = false,
    val subRows: List<GroupExportSubRow> = emptyList()
)

data class PreparedExportDataset(
    val scope: ReportScope,
    val format: String,
    val records: List<ComplianceReport>,
    val rows: List<NormalizedReportRow>,
    val startDate: String,
    val endDate: String,
    val periodDisplay: String,
    val filename: String,
    val generatedDateDisplay: String
)

/**
 * Authoritative mapping between compliance exception records and the official
 * 6-column downloadable report format matching `/services/reportExportData.js`.
 */
object ReportExportData {

    val officialExportScopes: List<ReportScope> = listOf(
        ReportScope.JUNIOR_SECONDARY,
        ReportScope.SENIOR_SECONDARY,
        ReportScope.JUNIOR_PRIMARY,
        ReportScope.SENIOR_PRIMARY,
        ReportScope.NURSERY,
        ReportScope.MENTORSHIP,
        ReportScope.DISCIPLINE,
        ReportScope.MAINTENANCE,
        ReportScope.SECURITY,
        ReportScope.TRANSPORT,
        ReportScope.MINDERS,
        ReportScope.HOSTEL_KITCHEN,
        ReportScope.CDS,
        ReportScope.ALL
    )

    val standardColumns: List<String> = listOf(
        "DATE", "NAME", "CLASS", "SUBJECT", "EXCEPTIONS", "EFFECTS"
    )

    val transportColumns: List<String> = listOf(
        "DATE", "DRIVER NAME", "BUS NO.", "COMPLIANCE CHECK", "EXCEPTIONS", "EFFECTS"
    )

    private val opsScopes = setOf(
        "Maintenance", "Security", "Transport", "Minders",
        "Hostel/Kitchen", "Hostel / Kitchen", "Operation", "Operations"
    )

    private val classLocationRegex = Regex(
        "^(Basic|JSS|SSS|SS|Primary|Nursery|Creche|Grade)\\s*\\d*\\s*[A-Z]?$",
        RegexOption.IGNORE_CASE
    )
    private val nonClassLocationRegex = Regex(
        "campus|office|depot|clinic|station|main",
        RegexOption.IGNORE_CASE
    )
    private val busPattern = Regex("(Bus\\s*\\d+)", RegexOption.IGNORE_CASE)

    fun normalizeReportRow(
        record: ComplianceReport,
        staffMap: Map<String, StaffMember>
    ): NormalizedReportRow {
        val recordScope = AnalysisEngine.getRecordScope(record, staffMap)
        val staffPerson = findStaffPerson(record, staffMap)

        // 1. DATE
        val cleanDate = record.incidentDate.take(10).trim()
            .ifBlank { record.createdAt.take(10).trim() }
            .ifBlank { "—" }

        // 2. NAME & UNIT
        val isCds = recordScope.equals("CDS", ignoreCase = true)
        val isTransport = recordScope.equals("Transport", ignoreCase = true)

        val rawStaffStr = (record.staffName ?: "").trim()
        val extractedBus = extractTransportBus(record, rawStaffStr)
        val extractedDriver = extractTransportDriver(record, rawStaffStr)

        val rawComplianceCheck = record.complianceCheck.trim()
            .ifBlank { record.category.trim() }

        val rawName = when {
            isCds -> "Corporate Digital Services (CDS)"
            isTransport && extractedDriver.isNotBlank() -> extractedDriver
            else -> rawStaffStr
                .ifBlank { staffPerson?.name.orEmpty().trim() }
                .ifBlank { "—" }
        }

        val cleanName = if (
            rawName != "—" &&
            !rawName.contains(recordScope, ignoreCase = true) &&
            !isCds &&
            !isTransport
        ) {
            "$rawName ($recordScope)"
        } else {
            rawName
        }

        // 3. CLASS / BUS NO.
        val isOps = opsScopes.any { it.equals(recordScope, ignoreCase = true) }
        val isDiscipline = recordScope.equals("Discipline", ignoreCase = true) ||
            recordScope.equals("Mentorship", ignoreCase = true)

        var cleanClass = "—"
        if (isTransport && extractedBus.isNotBlank()) {
            cleanClass = extractedBus
        } else if (!isCds && !isOps) {
            val rawClass = record.className.orEmpty().trim()
            if (rawClass.isNotBlank() &&
                !nonClassLocationRegex.containsMatchIn(rawClass) &&
                !rawClass.contains(";") &&
                !rawClass.contains(". ")
            ) {
                cleanClass = rawClass
            } else {
                val loc = staffPerson?.assignedLocation.orEmpty().trim()
                if (loc.isNotBlank() && classLocationRegex.matches(loc)) {
                    cleanClass = loc
                }
            }
        }

        // 4. SUBJECT / COMPLIANCE CHECK
        var cleanSubject = "—"
        if (isTransport && rawComplianceCheck.isNotBlank()) {
            cleanSubject = rawComplianceCheck
        } else if (!isCds && !isOps && !isDiscipline) {
            val rawSubj = record.subject.orEmpty().trim()
            if (rawSubj.isNotBlank() &&
                !rawSubj.equals("N/A", ignoreCase = true) &&
                !rawSubj.equals("NA", ignoreCase = true) &&
                !rawSubj.equals("General", ignoreCase = true)
            ) {
                cleanSubject = rawSubj
            }
        } else if (isOps && rawComplianceCheck.isNotBlank()) {
            cleanSubject = rawComplianceCheck
        }

        // 5. EXCEPTIONS & 6. EFFECTS
        val cleanException = record.exceptionText.trim().ifBlank { "—" }
        val cleanEffect = record.effectText.trim().ifBlank { "—" }

        // 7. COMBINED GROUP REPORT SUB-ROWS (Academic multi-staff submissions)
        if (!isCds && !isTransport && !isOps && record.isGroupReport && record.groupMembers.isNotEmpty()) {
            val subRows = record.groupMembers.map { m ->
                val memberStaff = staffMap[m.staffId.lowercase()]
                    ?: m.staffCode.takeIf { it.isNotBlank() }?.let { staffMap["code:${it.lowercase()}"] }
                val rawMemberName = m.staffName.trim()
                    .ifBlank { memberStaff?.name.orEmpty().trim() }
                    .ifBlank { "—" }
                val deptLabel = m.staffGroup.trim()
                    .ifBlank { memberStaff?.let { GroupReportHelpers.resolveStaffDepartment(it) }.orEmpty() }
                    .ifBlank { "Academic" }
                val formattedName = if (
                    rawMemberName != "—" &&
                    !rawMemberName.contains(deptLabel, ignoreCase = true)
                ) {
                    "$rawMemberName ($deptLabel)"
                } else {
                    rawMemberName
                }
                val formattedClass = m.className.trim().ifBlank { "None" }
                val formattedSubject = m.subjectName.trim()
                    .takeUnless {
                        it.equals("N/A", ignoreCase = true) ||
                            it.equals("NA", ignoreCase = true) ||
                            it.equals("General", ignoreCase = true)
                    }
                    .orEmpty()
                    .ifBlank { "—" }
                GroupExportSubRow(
                    name = formattedName,
                    className = formattedClass,
                    subject = formattedSubject
                )
            }
            return NormalizedReportRow(
                date = cleanDate,
                name = subRows.joinToString(", ") { it.name },
                unit = recordScope,
                className = subRows.joinToString(", ") { it.className },
                subject = subRows.joinToString(", ") { it.subject },
                exception = cleanException,
                effect = cleanEffect,
                driverName = extractedDriver.ifBlank { cleanName },
                busNumber = extractedBus.ifBlank { cleanClass },
                complianceCheck = rawComplianceCheck.ifBlank { cleanSubject },
                isGroup = true,
                subRows = subRows
            )
        }

        return NormalizedReportRow(
            date = cleanDate,
            name = cleanName,
            unit = recordScope,
            className = cleanClass,
            subject = cleanSubject,
            exception = cleanException,
            effect = cleanEffect,
            driverName = extractedDriver.ifBlank { cleanName },
            busNumber = extractedBus.ifBlank { cleanClass },
            complianceCheck = rawComplianceCheck.ifBlank { cleanSubject }
        )
    }

    private fun extractTransportBus(record: ComplianceReport, rawStaff: String): String {
        val rawClass = record.className.orEmpty().trim()
        if (rawClass.isNotBlank() && rawClass.contains("bus", ignoreCase = true)) return rawClass
        busPattern.find(rawStaff)?.groupValues?.getOrNull(1)?.let { return it }
        busPattern.find(record.exceptionText)?.groupValues?.getOrNull(1)?.let { return it }
        return rawClass
    }

    private fun extractTransportDriver(record: ComplianceReport, rawStaff: String): String {
        val loc = record.assignedLocation.orEmpty().trim()
        if (loc.isNotBlank()) return loc
        // Android stores "Bus X (Driver Name)" in staffName for Transport when submitted on mobile
        val parenMatch = Regex("\\(([^)]+)\\)").find(rawStaff)
        if (parenMatch != null) {
            return parenMatch.groupValues[1].trim()
        }
        return rawStaff
    }

    private fun findStaffPerson(
        record: ComplianceReport,
        staffMap: Map<String, StaffMember>
    ): StaffMember? {
        record.staffId?.lowercase()?.let { staffMap[it] }?.let { return it }
        record.staffCode?.lowercase()?.let { staffMap["code:$it"] }?.let { return it }
        record.staffName?.lowercase()?.let { staffMap["name:$it"] }?.let { return it }
        return null
    }

    /**
     * Audit & Download Eligibility Guard:
     * Excludes any unverified/problematic record (UNSEEN, not yet marked seen, FLAGGED,
     * ACTION_REQUIRED, or REJECTED) from official downloadable dossiers.
     */
    fun isExportEligible(report: ComplianceReport): Boolean {
        if (!report.isSeen) return false
        val review = report.reviewStatus.trim().uppercase(Locale.US)
        if (review == "UNSEEN" || review == "FLAGGED") return false
        if (report.status == ReportResolutionStatus.ACTION_REQUIRED ||
            report.status == ReportResolutionStatus.REJECTED
        ) {
            return false
        }
        return true
    }

    fun getDownloadRecords(
        scope: ReportScope,
        records: List<ComplianceReport>,
        startDate: String = "",
        endDate: String = "",
        term: String = "ALL",
        staffMap: Map<String, StaffMember>
    ): List<ComplianceReport> {
        return AnalysisEngine.filterReports(
            reports = records,
            term = term,
            startDate = startDate,
            endDate = endDate
        ).filter {
            isExportEligible(it) && AnalysisEngine.recordMatchesScope(it, scope, staffMap)
        }.sortedByDescending {
            it.incidentDate.take(10).ifBlank { it.createdAt.take(10) }
        }
    }

    fun generateFilename(
        scope: ReportScope,
        extension: String,
        startDate: String = "",
        endDate: String = ""
    ): String {
        val cleanScope = scope.key
            .replace("Hostel / Kitchen", "Hostel_Kitchen")
            .replace(Regex("[^a-zA-Z0-9]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "All" }

        val periodStr = when {
            startDate.isNotBlank() && endDate.isNotBlank() -> "${startDate}_to_${endDate}"
            startDate.isNotBlank() -> "From_$startDate"
            endDate.isNotBlank() -> "Up_To_$endDate"
            else -> "All_Dates"
        }
        return "${cleanScope}_Compliance_Exception_Report_${periodStr}.${extension.lowercase()}"
    }

    fun prepareDataset(
        scope: ReportScope,
        format: String,
        records: List<ComplianceReport>,
        staffRoster: List<StaffMember>,
        startDate: String = "",
        endDate: String = "",
        term: String = "ALL"
    ): PreparedExportDataset {
        val staffMap = AnalysisEngine.buildStaffLookup(staffRoster)
        val filtered = getDownloadRecords(
            scope = scope,
            records = records,
            startDate = startDate,
            endDate = endDate,
            term = term,
            staffMap = staffMap
        )
        val rows = filtered.map { normalizeReportRow(it, staffMap) }
        if (filtered.size != rows.size) {
            throw IllegalStateException(
                "Generation Data Integrity Failure: Selected source count (${filtered.size}) does not match table rows written (${rows.size})."
            )
        }
        val cleanFormat = format.uppercase(Locale.US)
        val ext = if (cleanFormat == "DOCX") "docx" else "pdf"
        val periodDisplay = when {
            startDate.isNotBlank() && endDate.isNotBlank() -> "$startDate to $endDate"
            startDate.isNotBlank() -> "From $startDate onward"
            endDate.isNotBlank() -> "Up to $endDate"
            term != "ALL" -> term
            else -> "All available dates"
        }
        val genDate = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())
        return PreparedExportDataset(
            scope = scope,
            format = cleanFormat,
            records = filtered,
            rows = rows,
            startDate = startDate,
            endDate = endDate,
            periodDisplay = periodDisplay,
            filename = generateFilename(scope, ext, startDate, endDate),
            generatedDateDisplay = genDate
        )
    }
}
