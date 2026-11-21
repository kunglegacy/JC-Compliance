package com.example.domain

import com.example.data.model.AuthoritativeGrandTotals
import com.example.data.model.CategoryFrequencyItem
import com.example.data.model.CategoryPatternData
import com.example.data.model.CategorySectionMatrix
import com.example.data.model.CategorySectionMatrixRow
import com.example.data.model.CategorySectionMatrixTotals
import com.example.data.model.ClassAnalysisData
import com.example.data.model.ClassMetricItem
import com.example.data.model.ComplianceDivision
import com.example.data.model.ComplianceReport
import com.example.data.model.ConcentrationMetrics
import com.example.data.model.DeltaMetric
import com.example.data.model.DeltaStatus
import com.example.data.model.DisciplineAnalysisData
import com.example.data.model.DisciplineMetricItem
import com.example.data.model.DisciplineScopeData
import com.example.data.model.DrillDownAnalytics
import com.example.data.model.NameCountPct
import com.example.data.model.OverallTrendData
import com.example.data.model.PatternObservation
import com.example.data.model.SectionAnalysisData
import com.example.data.model.SectionClassGroup
import com.example.data.model.SectionContributionItem
import com.example.data.model.SectionMetricItem
import com.example.data.model.SegmentConfig
import com.example.data.model.SegmentComparisonRow
import com.example.data.model.SegmentTermStats
import com.example.data.model.SeverityLevel
import com.example.data.model.StaffMember
import com.example.data.model.SubcategoryCount
import com.example.data.model.TermAnalysisResult
import com.example.data.model.TermAnalysisSnapshot
import com.example.data.model.TermComparisonData
import com.example.data.model.TermTrendPoint
import java.util.Locale
import kotlin.math.roundToInt

object AuthoritativeAnalysisBuilder {

    val CANONICAL_TERMS = listOf("First Term", "Second Term", "Third Term")

    val SEGMENT_CONFIGS = listOf(
        SegmentConfig("juniorPrimary", "Junior Primary", "PRIMARY", description = "Basic 1 to 3"),
        SegmentConfig("seniorPrimary", "Senior Primary", "PRIMARY", description = "Basic 4 to 5"),
        SegmentConfig("juniorSeniorPrimary", "Junior/Senior Primary", "PRIMARY", isCombined = true, description = "Combined (1-5)"),
        SegmentConfig("juniorSecondary", "Junior Secondary", "SECONDARY", description = "JSS 1 to 3"),
        SegmentConfig("seniorSecondary", "Senior Secondary", "SECONDARY", description = "SS 1 to 3"),
        SegmentConfig("secondaryCombined", "Sec. Combined", "SECONDARY", isCombined = true, description = "Combined (JSS-SS3)"),
        SegmentConfig("nursery", "Nursery", "NURSERY", description = "Early Years / KG"),
        SegmentConfig("academicTotal", "Total Academic", "TOTAL", isTotal = true, description = "All academic divisions")
    )

    private val CORE_SECTIONS = listOf(
        "juniorPrimary" to "Junior Primary",
        "seniorPrimary" to "Senior Primary",
        "juniorSecondary" to "Junior Secondary",
        "seniorSecondary" to "Senior Secondary",
        "nursery" to "Nursery"
    )

    fun isCdsRecord(r: ComplianceReport): Boolean {
        val div = r.division.trim().lowercase(Locale.US)
        val dept = r.department.orEmpty().trim().lowercase(Locale.US)
        if (div == "cds" || dept == "cds") return true
        if (r.targetType.equals("division", ignoreCase = true) && (div.isBlank() || div == "cds")) return true
        return false
    }

    fun getActiveStaffDenominator(staffList: List<StaffMember>): List<StaffMember> {
        return staffList.filter { s ->
            if (!s.isActive) return@filter false
            val dept = s.department.ifBlank {
                if (s.category.contains("Non", ignoreCase = true)) "Operation" else "Academic"
            }.trim().lowercase(Locale.US)
            dept == "academic" || dept == "operation" || dept == "operations" || dept == "discipline"
        }
    }

    fun resolveSectionKey(r: ComplianceReport, staff: StaffMember?): String {
        val explicit = r.schoolSection.orEmpty().lowercase(Locale.US)
        val text = buildString {
            append(explicit).append(' ')
            append(r.className.orEmpty()).append(' ')
            append(staff?.schoolSection.orEmpty()).append(' ')
            append(staff?.assignedLocation.orEmpty()).append(' ')
            append(staff?.staffGroup.orEmpty()).append(' ')
            append(r.staffGroup.orEmpty())
        }.lowercase(Locale.US)

        return when {
            text.contains("creche") || text.contains("nursery") || text.contains("kg") || text.contains("reception") || text.contains("playgroup") -> "nursery"
            text.contains("basic 1") || text.contains("basic 2") || text.contains("basic 3") ||
                text.contains("junior primary") || text.contains("jnr primary") || text.contains("jnr pri") ||
                text.contains("pry 1") || text.contains("pry 2") || text.contains("pry 3") -> "juniorPrimary"
            text.contains("basic 4") || text.contains("basic 5") || text.contains("basic 6") ||
                text.contains("senior primary") || text.contains("snr primary") || text.contains("snr pri") ||
                text.contains("pry 4") || text.contains("pry 5") || text.contains("pry 6") -> "seniorPrimary"
            text.contains("jss") || text.contains("junior secondary") || text.contains("jnr college") || text.contains("jnr coll") -> "juniorSecondary"
            text.contains("ss 1") || text.contains("ss 2") || text.contains("ss 3") || text.contains("sss") ||
                text.contains("senior secondary") || text.contains("snr college") || text.contains("snr col") -> "seniorSecondary"
            else -> "juniorSecondary"
        }
    }

    fun sectionLabelForKey(key: String): String = when (key) {
        "juniorPrimary" -> "Junior Primary"
        "seniorPrimary" -> "Senior Primary"
        "juniorSecondary" -> "Junior Secondary"
        "seniorSecondary" -> "Senior Secondary"
        "nursery" -> "Nursery"
        "juniorSeniorPrimary" -> "Junior/Senior Primary"
        "secondaryCombined" -> "Sec. Combined"
        "academicTotal" -> "Total Academic"
        else -> key
    }

    fun normalizeAcademicDepartment(
        disciplineGroup: String?,
        subjectName: String?,
        staffGroup: String?,
        division: String? = null
    ): String {
        val div = division.orEmpty().trim().lowercase(Locale.US)
        if (div == "cds") return "CDS"
        if (div.startsWith("operat")) return "Operations"
        if (div == "discipline") return "Discipline"

        val validCanonical = setOf("Life Sciences", "Physical Sciences", "Social Sciences", "Languages")
        val dg = disciplineGroup.orEmpty().trim()
        if (dg in validCanonical) return dg
        val sg = staffGroup.orEmpty().trim()
        if (sg in validCanonical) return sg

        val combined = "${dg} ${subjectName.orEmpty()} ${sg}".lowercase(Locale.US)
        return when {
            combined.contains("bio") || combined.contains("agric") || combined.contains("livestock") ||
                combined.contains("basic science") || combined.contains("food") || combined.contains("home econ") ||
                combined.contains("health") || combined.contains("life science") -> "Life Sciences"
            combined.contains("math") || combined.contains("physic") || combined.contains("chem") ||
                combined.contains("tech") || combined.contains("compu") || combined.contains("ict") ||
                combined.contains("draw") || combined.contains("physical science") -> "Physical Sciences"
            combined.contains("lang") || combined.contains("eng") || combined.contains("lit") ||
                combined.contains("fren") || combined.contains("yoruba") || combined.contains("igbo") ||
                combined.contains("speech") || combined.contains("compo") || combined.contains("compre") ||
                combined.contains("grammar") -> "Languages"
            combined.contains("econ") || combined.contains("commer") || combined.contains("account") ||
                combined.contains("gov") || combined.contains("hist") || combined.contains("crs") ||
                combined.contains("irs") || combined.contains("civic") || combined.contains("citizen") ||
                combined.contains("social") || combined.contains("bus") || combined.contains("art") ||
                combined.contains("cca") || combined.contains("music") || combined.contains("phe") ||
                combined.contains("geog") -> "Social Sciences"
            else -> "Physical Sciences"
        }
    }

    fun normalizeCategory(r: ComplianceReport): String =
        r.complianceCheck.ifBlank { r.category }.ifBlank { "General Check" }.trim()

    fun buildDeltaMetric(
        current: Float,
        previous: Float,
        hasComparativeHistory: Boolean,
        isFloatMetric: Boolean = false
    ): DeltaMetric {
        if (!hasComparativeHistory || previous <= 0f) {
            return DeltaMetric(
                current = current,
                previous = previous,
                absoluteChange = 0f,
                percentageChange = null,
                hasBaseline = false,
                status = DeltaStatus.NO_PRIOR_BASELINE,
                isFloatMetric = isFloatMetric
            )
        }
        val diff = current - previous
        val roundedDiff = if (isFloatMetric) ((diff * 100f).roundToInt() / 100f) else diff.roundToInt().toFloat()
        if (roundedDiff == 0f) {
            return DeltaMetric(
                current = current,
                previous = previous,
                absoluteChange = 0f,
                percentageChange = 0,
                hasBaseline = true,
                status = DeltaStatus.UNCHANGED,
                isFloatMetric = isFloatMetric
            )
        }
        val pct = ((diff / previous) * 100f).roundToInt()
        return DeltaMetric(
            current = current,
            previous = previous,
            absoluteChange = roundedDiff,
            percentageChange = pct,
            hasBaseline = true,
            status = if (roundedDiff > 0f) DeltaStatus.INCREASED else DeltaStatus.DECREASED,
            isFloatMetric = isFloatMetric
        )
    }

    private fun staffIdentityKey(r: ComplianceReport): String? {
        r.staffId?.takeIf { it.isNotBlank() }?.let { return "id:${it.lowercase(Locale.US)}" }
        r.staffCode?.takeIf { it.isNotBlank() }?.let { return "code:${it.trim().lowercase(Locale.US)}" }
        r.staffName?.takeIf { it.isNotBlank() }?.let { return "name:${it.trim().lowercase(Locale.US)}" }
        return null
    }

    private fun computeSegmentStatsForReports(
        termReports: List<ComplianceReport>,
        staffMap: Map<String, StaffMember>
    ): Map<String, SegmentTermStats> {
        val acadReports = termReports.filter { !isCdsRecord(it) && AnalysisEngine.resolveDivision(it) == ComplianceDivision.ACADEMIC }
        val bySection = acadReports.groupBy { r ->
            val stf = findStaff(r, staffMap)
            resolveSectionKey(r, stf)
        }

        fun statsFor(list: List<ComplianceReport>): SegmentTermStats {
            val staffCount = list.mapNotNull(::staffIdentityKey).toSet().size
            val perHead = if (staffCount > 0) ((list.size * 100f) / staffCount).roundToInt() / 100f else 0f
            return SegmentTermStats(list.size, staffCount, perHead)
        }

        val jp = bySection["juniorPrimary"].orEmpty()
        val sp = bySection["seniorPrimary"].orEmpty()
        val js = bySection["juniorSecondary"].orEmpty()
        val ss = bySection["seniorSecondary"].orEmpty()
        val nur = bySection["nursery"].orEmpty()

        return mapOf(
            "juniorPrimary" to statsFor(jp),
            "seniorPrimary" to statsFor(sp),
            "juniorSeniorPrimary" to statsFor(jp + sp),
            "juniorSecondary" to statsFor(js),
            "seniorSecondary" to statsFor(ss),
            "secondaryCombined" to statsFor(js + ss),
            "nursery" to statsFor(nur),
            "academicTotal" to statsFor(acadReports)
        )
    }

    private fun findStaff(r: ComplianceReport, staffMap: Map<String, StaffMember>): StaffMember? {
        r.staffId?.lowercase(Locale.US)?.let { staffMap[it] }?.let { return it }
        r.staffCode?.lowercase(Locale.US)?.let { staffMap["code:$it"] }?.let { return it }
        r.staffName?.lowercase(Locale.US)?.let { staffMap["name:$it"] }?.let { return it }
        return null
    }

    fun resolveAuthoritativeTermAnalysis(
        term: String,
        session: String,
        allReports: List<ComplianceReport>,
        staffList: List<StaffMember>,
        snapshots: List<TermAnalysisSnapshot>,
        periodLabel: String,
        startDate: String = "",
        endDate: String = ""
    ): TermAnalysisResult {
        val activeDenominator = getActiveStaffDenominator(staffList)
        val staffMap = AnalysisEngine.buildStaffLookup(staffList)

        // 1. Build multi-term stats across CANONICAL_TERMS honoring Single Authoritative Source per term
        val segmentStatsByTerm = mutableMapOf<String, Map<String, SegmentTermStats>>()
        val rawReportsByTerm = mutableMapOf<String, List<ComplianceReport>>()
        val trendPoints = mutableListOf<TermTrendPoint>()
        val availableTerms = mutableListOf<String>()

        for (tName in CANONICAL_TERMS) {
            val tRaw = allReports.filter { it.term.equals(tName, ignoreCase = true) }
            if (tRaw.isNotEmpty()) {
                availableTerms.add(tName)
                rawReportsByTerm[tName] = tRaw
                segmentStatsByTerm[tName] = computeSegmentStatsForReports(tRaw, staffMap)
                val acad = tRaw.count { !isCdsRecord(it) && AnalysisEngine.resolveDivision(it) == ComplianceDivision.ACADEMIC }
                val ops = tRaw.count { AnalysisEngine.resolveDivision(it) == ComplianceDivision.OPERATIONS }
                val disc = tRaw.count { AnalysisEngine.resolveDivision(it) == ComplianceDivision.DISCIPLINE }
                val cds = tRaw.count { isCdsRecord(it) }
                val aff = tRaw.filter { !isCdsRecord(it) }.mapNotNull(::staffIdentityKey).toSet().size
                val ph = if (aff > 0) ((tRaw.size * 100f) / aff).roundToInt() / 100f else 0f
                trendPoints.add(TermTrendPoint(tName, tRaw.size, acad, ops, disc, cds, aff, ph, isFromSnapshot = false))
            } else {
                val snap = snapshots.firstOrNull { it.term.equals(tName, ignoreCase = true) && it.isVerified }
                if (snap != null) {
                    availableTerms.add(tName)
                    segmentStatsByTerm[tName] = SnapshotHydrator.extractSegmentStatsFromSnapshot(snap)
                    trendPoints.add(
                        TermTrendPoint(
                            termName = tName,
                            totalExceptions = snap.totalQualifyingExceptions,
                            academicExceptions = snap.academicExceptionsCount,
                            operationsExceptions = snap.operationsExceptionsCount,
                            disciplineExceptions = snap.disciplineExceptionsCount,
                            cdsExceptions = snap.cdsExceptionsCount,
                            affectedStaffCount = snap.affectedStaffCount,
                            perHead = snap.overallPerHead,
                            isFromSnapshot = true
                        )
                    )
                }
            }
        }

        // Determine priorTerm relative to selected term
        val selectedIndex = CANONICAL_TERMS.indexOfFirst { it.equals(term, ignoreCase = true) }
        val priorTerm = if (selectedIndex > 0) {
            (selectedIndex - 1 downTo 0)
                .map { CANONICAL_TERMS[it] }
                .firstOrNull { it in availableTerms }
        } else null
        val hasComparativeHistory = priorTerm != null && availableTerms.size >= 2

        val currentStatsMap = if (term.equals("ALL", ignoreCase = true)) {
            computeSegmentStatsForReports(allReports, staffMap)
        } else {
            segmentStatsByTerm[term].orEmpty()
        }
        val priorStatsMap = if (priorTerm != null) segmentStatsByTerm[priorTerm].orEmpty() else emptyMap()

        val segmentComparisons = SEGMENT_CONFIGS.associate { cfg ->
            val cur = currentStatsMap[cfg.key] ?: SegmentTermStats()
            val prev = priorStatsMap[cfg.key] ?: SegmentTermStats()
            cfg.key to SegmentComparisonRow(
                segmentKey = cfg.key,
                exceptionComparison = buildDeltaMetric(cur.exceptionCount.toFloat(), prev.exceptionCount.toFloat(), hasComparativeHistory),
                affectedStaffComparison = buildDeltaMetric(cur.affectedStaffCount.toFloat(), prev.affectedStaffCount.toFloat(), hasComparativeHistory),
                perHeadComparison = buildDeltaMetric(cur.perHead, prev.perHead, hasComparativeHistory, isFloatMetric = true)
            )
        }

        val curTotalSeg = currentStatsMap["academicTotal"] ?: SegmentTermStats()
        val prevTotalSeg = priorStatsMap["academicTotal"] ?: SegmentTermStats()
        val comparisonData = TermComparisonData(
            activeTerm = if (term == "ALL") "All Terms" else term,
            priorTerm = priorTerm,
            availableTerms = availableTerms.ifEmpty { listOf(term) },
            hasComparativeHistory = hasComparativeHistory,
            segmentStatsByTerm = segmentStatsByTerm,
            segmentComparisons = segmentComparisons,
            totalExceptionsChange = buildDeltaMetric(curTotalSeg.exceptionCount.toFloat(), prevTotalSeg.exceptionCount.toFloat(), hasComparativeHistory),
            affectedStaffChange = buildDeltaMetric(curTotalSeg.affectedStaffCount.toFloat(), prevTotalSeg.affectedStaffCount.toFloat(), hasComparativeHistory),
            perHeadChange = buildDeltaMetric(curTotalSeg.perHead, prevTotalSeg.perHead, hasComparativeHistory, isFloatMetric = true)
        )

        val trendPct = if (trendPoints.size >= 2 && trendPoints.first().totalExceptions > 0) {
            val f = trendPoints.first().totalExceptions.toFloat()
            val l = trendPoints.last().totalExceptions.toFloat()
            (((l - f) / f) * 100f).roundToInt()
        } else null
        val overallTrend = OverallTrendData(points = trendPoints, availableTerms = availableTerms, netChangePct = trendPct)

        // 2. Single Authoritative Source selection for active term
        val termRaw = if (term.equals("ALL", ignoreCase = true)) {
            allReports
        } else {
            rawReportsByTerm[term].orEmpty()
        }

        if (termRaw.isEmpty() && !term.equals("ALL", ignoreCase = true)) {
            val snap = snapshots.firstOrNull { it.term.equals(term, ignoreCase = true) && it.isVerified }
                ?: snapshots.firstOrNull { it.term.equals(term, ignoreCase = true) }
            if (snap != null) {
                return SnapshotHydrator.hydrateTermAnalysis(
                    snap = snap,
                    term = term,
                    session = session,
                    periodLabel = periodLabel,
                    staffDenominatorFallback = activeDenominator.size,
                    comparisons = comparisonData,
                    trends = overallTrend
                )
            }
        }

        // Apply active date range filter on raw records
        val filtered = AnalysisEngine.filterReports(termRaw, term = "ALL", startDate = startDate, endDate = endDate)
        val priorRaw = if (priorTerm != null) rawReportsByTerm[priorTerm].orEmpty() else emptyList()

        return buildFromRawReports(
            term = term,
            session = session,
            periodLabel = periodLabel,
            filtered = filtered,
            priorRaw = priorRaw,
            activeDenominator = activeDenominator,
            staffMap = staffMap,
            comparisons = comparisonData,
            trends = overallTrend
        )
    }

    private fun buildFromRawReports(
        term: String,
        session: String,
        periodLabel: String,
        filtered: List<ComplianceReport>,
        priorRaw: List<ComplianceReport>,
        activeDenominator: List<StaffMember>,
        staffMap: Map<String, StaffMember>,
        comparisons: TermComparisonData,
        trends: OverallTrendData
    ): TermAnalysisResult {
        val staffExceptions = filtered.filter { !isCdsRecord(it) }
        val cdsExceptions = filtered.filter { isCdsRecord(it) }
        val acadReports = staffExceptions.filter { AnalysisEngine.resolveDivision(it, findStaff(it, staffMap)) == ComplianceDivision.ACADEMIC }
        val opsReports = staffExceptions.filter { AnalysisEngine.resolveDivision(it, findStaff(it, staffMap)) == ComplianceDivision.OPERATIONS }
        val discReports = staffExceptions.filter { AnalysisEngine.resolveDivision(it, findStaff(it, staffMap)) == ComplianceDivision.DISCIPLINE }

        // Derived Staff Severity over active denominator
        var critCount = 0
        var highCount = 0
        var medCount = 0
        var lowCount = 0
        var affectedCount = 0

        activeDenominator.forEach { person ->
            val count = staffExceptions.count { r ->
                (person.id.isNotBlank() && r.staffId == person.id) ||
                    (person.staffCode.isNotBlank() && r.staffCode.equals(person.staffCode, true)) ||
                    (r.staffName.equals(person.name, true))
            }
            if (count > 0) affectedCount++
            when (SeverityLevel.fromCount(count)) {
                SeverityLevel.CRITICAL -> critCount++
                SeverityLevel.HIGH -> highCount++
                SeverityLevel.MEDIUM -> medCount++
                SeverityLevel.LOW -> lowCount++
                SeverityLevel.NONE -> Unit
            }
        }

        val totalDenom = activeDenominator.size
        val cleanCount = (totalDenom - affectedCount).coerceAtLeast(0)
        val flaggedPct = if (totalDenom > 0) ((affectedCount * 100f) / totalDenom).roundToInt() else 0
        val cleanPct = if (totalDenom > 0) ((cleanCount * 100f) / totalDenom).roundToInt() else 0
        val seenTotal = filtered.count { it.isSeen }
        val seenCds = cdsExceptions.count { it.isSeen }
        val overallPerHead = if (affectedCount > 0) ((staffExceptions.size * 100f) / affectedCount).roundToInt() / 100f else 0f

        val grandTotals = AuthoritativeGrandTotals(
            staffDenominator = totalDenom,
            affectedStaffCount = affectedCount,
            staffWithExceptionsPercentage = flaggedPct,
            cleanStaffCount = cleanCount,
            cleanStaffPercentage = cleanPct,
            totalExceptions = filtered.size,
            academicExceptions = acadReports.size,
            operationsExceptions = opsReports.size,
            disciplineExceptions = discReports.size,
            cdsExceptions = cdsExceptions.size,
            seenExceptions = seenTotal,
            unseenExceptions = filtered.size - seenTotal,
            seenCdsCount = seenCds,
            unseenCdsCount = cdsExceptions.size - seenCds,
            overallPerHead = overallPerHead,
            criticalStaffCount = critCount,
            highStaffCount = highCount,
            mediumStaffCount = medCount,
            lowStaffCount = lowCount
        )

        // Section Analysis
        val acadBySection = acadReports.groupBy { resolveSectionKey(it, findStaff(it, staffMap)) }
        val rankedSections = CORE_SECTIONS.map { (key, label) ->
            val list = acadBySection[key].orEmpty()
            val stfCount = list.mapNotNull(::staffIdentityKey).toSet().size
            val ph = if (stfCount > 0) ((list.size * 100f) / stfCount).roundToInt() / 100f else 0f
            val share = if (acadReports.isNotEmpty()) ((list.size * 100f) / acadReports.size).roundToInt() else 0
            SectionMetricItem(
                key = key,
                label = label,
                rank = 0,
                exceptionCount = list.size,
                affectedStaffCount = stfCount,
                perHead = ph,
                shareOfAcademic = share,
                historicalDelta = comparisons.segmentComparisons[key]?.exceptionComparison
            )
        }.sortedByDescending { it.exceptionCount }.mapIndexed { idx, item -> item.copy(rank = idx + 1) }

        val sectionsData = SectionAnalysisData(
            rankedList = rankedSections,
            primaryCombinedCount = acadBySection["juniorPrimary"].orEmpty().size + acadBySection["seniorPrimary"].orEmpty().size,
            secondaryCombinedCount = acadBySection["juniorSecondary"].orEmpty().size + acadBySection["seniorSecondary"].orEmpty().size,
            totalAcademicExceptions = acadReports.size
        )

        // Discipline Analysis
        val hasPrior = comparisons.hasComparativeHistory
        fun buildDisciplineScope(currentList: List<ComplianceReport>, priorList: List<ComplianceReport>): DisciplineScopeData {
            val priorByDisc = priorList.groupBy { r ->
                val stf = findStaff(r, staffMap)
                normalizeAcademicDepartment(r.disciplineGroup, r.subject, r.staffGroup ?: stf?.staffGroup, r.division)
            }
            val grouped = currentList.groupBy { r ->
                val stf = findStaff(r, staffMap)
                normalizeAcademicDepartment(r.disciplineGroup, r.subject, r.staffGroup ?: stf?.staffGroup, r.division)
            }
            val totalExc = currentList.size
            val rows = grouped.entries.map { (disc, items) ->
                val staffKeys = items.mapNotNull(::staffIdentityKey).toSet()
                val teacherCount = staffKeys.size.coerceAtLeast(if (items.isNotEmpty()) 1 else 0)
                val ph = if (teacherCount > 0) ((items.size * 100f) / teacherCount).roundToInt() / 100f else 0f
                val share = if (totalExc > 0) ((items.size * 100f) / totalExc).roundToInt() else 0
                val names = items.mapNotNull { it.staffName?.trim()?.takeIf { n -> n.isNotBlank() } }.distinct()
                val priorCount = priorByDisc[disc].orEmpty().size.toFloat()
                DisciplineMetricItem(
                    rank = 0,
                    discipline = disc,
                    exceptionCount = items.size,
                    affectedTeachers = staffKeys.size,
                    perHead = ph,
                    shareOfScope = share,
                    historicalChange = buildDeltaMetric(items.size.toFloat(), priorCount, hasPrior),
                    teacherNamesSummary = names.joinToString(", ")
                )
            }.sortedByDescending { it.exceptionCount }.mapIndexed { idx, row -> row.copy(rank = idx + 1) }

            val allTeachers = currentList.mapNotNull(::staffIdentityKey).toSet().size
            val scopePh = if (allTeachers > 0) ((totalExc * 100f) / allTeachers).roundToInt() / 100f else 0f
            return DisciplineScopeData(rows = rows, totalExceptions = totalExc, totalTeachers = allTeachers, perHead = scopePh)
        }

        val primaryKeys = setOf("juniorPrimary", "seniorPrimary", "nursery")
        val secondaryKeys = setOf("juniorSecondary", "seniorSecondary")
        val curPrimaryDisc = acadReports.filter { resolveSectionKey(it, findStaff(it, staffMap)) in primaryKeys }
        val curSecondaryDisc = acadReports.filter { resolveSectionKey(it, findStaff(it, staffMap)) in secondaryKeys }
        val priorAcad = priorRaw.filter { !isCdsRecord(it) && AnalysisEngine.resolveDivision(it, findStaff(it, staffMap)) == ComplianceDivision.ACADEMIC }
        val priorPrimaryDisc = priorAcad.filter { resolveSectionKey(it, findStaff(it, staffMap)) in primaryKeys }
        val priorSecondaryDisc = priorAcad.filter { resolveSectionKey(it, findStaff(it, staffMap)) in secondaryKeys }

        val discAnalysis = DisciplineAnalysisData(
            primary = buildDisciplineScope(curPrimaryDisc, priorPrimaryDisc),
            secondary = buildDisciplineScope(curSecondaryDisc, priorSecondaryDisc),
            allDisciplines = buildDisciplineScope(acadReports, priorAcad).rows,
            priorTerm = comparisons.priorTerm
        )

        // Class Analysis
        val classRelevant = staffExceptions.filter {
            val div = AnalysisEngine.resolveDivision(it, findStaff(it, staffMap))
            div == ComplianceDivision.ACADEMIC || div == ComplianceDivision.DISCIPLINE
        }
        var unspecifiedCount = 0
        val byClassMap = mutableMapOf<Pair<String, String>, MutableList<ComplianceReport>>()
        classRelevant.forEach { r ->
            val stf = findStaff(r, staffMap)
            val secKey = resolveSectionKey(r, stf)
            val cls = r.className?.trim().takeIf { !it.isNullOrBlank() } ?: run {
                unspecifiedCount++
                "Not specified"
            }
            byClassMap.getOrPut(secKey to cls) { mutableListOf() }.add(r)
        }

        val secGroups = CORE_SECTIONS.mapNotNull { (secKey, secLabel) ->
            val entries = byClassMap.filterKeys { it.first == secKey }
            if (entries.isEmpty()) return@mapNotNull null
            val secTotal = entries.values.sumOf { it.size }
            val secStaff = entries.values.flatten().mapNotNull(::staffIdentityKey).toSet().size
            val secPh = if (secStaff > 0) ((secTotal * 100f) / secStaff).roundToInt() / 100f else 0f
            val secClasses = entries.entries.map { (pair, items) ->
                val subs = items.groupBy(::normalizeCategory)
                    .map { (cat, list) -> SubcategoryCount(cat, list.size) }
                    .sortedByDescending { it.count }
                val stfCount = items.mapNotNull(::staffIdentityKey).toSet().size
                val share = if (secTotal > 0) ((items.size * 100f) / secTotal).roundToInt() else 0
                ClassMetricItem(
                    rank = 0,
                    className = pair.second,
                    sectionKey = secKey,
                    sectionLabel = secLabel,
                    exceptionCount = items.size,
                    affectedStaffCount = stfCount,
                    shareOfSection = share,
                    topCategory = subs.firstOrNull()?.category ?: "General Check",
                    subcategories = subs,
                    topSubcategoriesSummary = subs.take(2).joinToString(" · ") { "${it.category} (${it.count})" }
                )
            }.sortedByDescending { it.exceptionCount }.mapIndexed { idx, item -> item.copy(rank = idx + 1) }

            SectionClassGroup(
                sectionKey = secKey,
                sectionLabel = secLabel,
                totalExceptions = secTotal,
                affectedStaffCount = secStaff,
                perHead = secPh,
                classes = secClasses
            )
        }.sortedByDescending { it.totalExceptions }

        val allClassesRanked = secGroups.flatMap { it.classes }
            .sortedByDescending { it.exceptionCount }
            .mapIndexed { idx, item -> item.copy(rank = idx + 1) }

        val classData = ClassAnalysisData(
            bySection = secGroups,
            allClassesRanked = allClassesRanked,
            totalClassesCount = allClassesRanked.count { it.className != "Not specified" },
            unspecifiedCount = unspecifiedCount
        )

        // Category & Pattern Analysis (Non-CDS exceptions)
        val priorNonCds = priorRaw.filter { !isCdsRecord(it) }
        val priorCatCounts = priorNonCds.groupingBy(::normalizeCategory).eachCount()
        val catGroups = staffExceptions.groupBy(::normalizeCategory)
            .entries
            .sortedByDescending { it.value.size }

        val catTotal = staffExceptions.size
        var runningSum = 0
        var catsFor80 = 0
        val rankedCats = catGroups.mapIndexed { idx, (catName, items) ->
            runningSum += items.size
            val pct = if (catTotal > 0) ((items.size * 100f) / catTotal).roundToInt() else 0
            val cumPct = if (catTotal > 0) ((runningSum * 100f) / catTotal).roundToInt().coerceAtMost(100) else 0
            if (catsFor80 == 0 && cumPct >= 80) catsFor80 = idx + 1
            val secSet = items.map { item ->
                val div = AnalysisEngine.resolveDivision(item, findStaff(item, staffMap))
                if (div == ComplianceDivision.ACADEMIC) sectionLabelForKey(resolveSectionKey(item, findStaff(item, staffMap)))
                else div.label
            }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} (${it.value})" }

            CategoryFrequencyItem(
                rank = idx + 1,
                category = catName,
                occurrences = items.size,
                percentage = pct,
                cumulativePercentage = cumPct,
                affectedSectionsSummary = secSet,
                historicalDelta = buildDeltaMetric(items.size.toFloat(), (priorCatCounts[catName] ?: 0).toFloat(), hasPrior)
            )
        }
        if (catsFor80 == 0 && rankedCats.isNotEmpty()) catsFor80 = rankedCats.size

        val top3List = rankedCats.take(3)
        val top3Share = if (catTotal > 0) ((top3List.sumOf { it.occurrences } * 100f) / catTotal).roundToInt() else 0
        val concentration = ConcentrationMetrics(
            top3Count = top3List.size,
            top3Share = top3Share,
            categoriesFor80PercentVolume = catsFor80,
            totalCategories = rankedCats.size
        )

        val secContributions = buildList {
            CORE_SECTIONS.forEach { (key, label) ->
                val count = acadBySection[key].orEmpty().size
                if (count > 0) {
                    add(
                        SectionContributionItem(
                            sectionKey = key,
                            section = label,
                            occurrences = count,
                            percentage = if (catTotal > 0) ((count * 100f) / catTotal).roundToInt() else 0,
                            isInstitutionLevel = false
                        )
                    )
                }
            }
            if (opsReports.isNotEmpty()) {
                add(
                    SectionContributionItem(
                        sectionKey = "Operation",
                        section = "Operations",
                        occurrences = opsReports.size,
                        percentage = if (catTotal > 0) ((opsReports.size * 100f) / catTotal).roundToInt() else 0,
                        isInstitutionLevel = true
                    )
                )
            }
            if (discReports.isNotEmpty()) {
                add(
                    SectionContributionItem(
                        sectionKey = "Discipline",
                        section = "Discipline",
                        occurrences = discReports.size,
                        percentage = if (catTotal > 0) ((discReports.size * 100f) / catTotal).roundToInt() else 0,
                        isInstitutionLevel = true
                    )
                )
            }
        }.sortedByDescending { it.occurrences }

        val observations = buildList {
            rankedCats.firstOrNull()?.let { top1 ->
                add(
                    PatternObservation(
                        id = "obs_leading_driver",
                        title = "Primary Exception Driver",
                        statement = "${top1.category} is the highest recorded compliance issue across active divisions.",
                        highlight = "${top1.occurrences} occurrences (${top1.percentage}% share)"
                    )
                )
            }
            if (rankedCats.isNotEmpty()) {
                add(
                    PatternObservation(
                        id = "obs_pareto_conc",
                        title = "Pareto Volume Concentration",
                        statement = "Top ${concentration.top3Count} exception categories generate ${concentration.top3Share}% of volume; 80% concentration occurs in top ${concentration.categoriesFor80PercentVolume} of ${concentration.totalCategories} checks.",
                        highlight = "${concentration.top3Share}% Top-${concentration.top3Count} Share"
                    )
                )
            }
            secContributions.firstOrNull()?.let { topSec ->
                add(
                    PatternObservation(
                        id = "obs_tier_hotspot",
                        title = "Section Hotspot Density",
                        statement = "${topSec.section} carries the largest proportional exception share among reported school tiers.",
                        highlight = "${topSec.occurrences} cases (${topSec.percentage}%)"
                    )
                )
            }
            if (affectedCount > 0) {
                add(
                    PatternObservation(
                        id = "obs_personnel_intensity",
                        title = "Personnel Exposure & Severity",
                        statement = "$affectedCount of $totalDenom active staff ($flaggedPct%) recorded exceptions; $critCount staff reached Critical tier (>=4).",
                        highlight = "${String.format(Locale.US, "%.2f", overallPerHead)} per affected staff"
                    )
                )
            }
        }

        val catPatterns = CategoryPatternData(
            rankedCategories = rankedCats,
            topDrivers = top3List,
            concentration = concentration,
            sectionContributions = secContributions,
            observations = observations,
            priorTermName = comparisons.priorTerm
        )

        // Slide 5 Multidimensional Category x Section Matrix (Academic divisions)
        val matrixCats = acadReports.groupBy(::normalizeCategory)
            .entries
            .sortedByDescending { it.value.size }
        val matrixRows = matrixCats.map { (catName, items) ->
            var jc = 0
            var sc = 0
            var jp = 0
            var sp = 0
            var nu = 0
            items.forEach { item ->
                when (resolveSectionKey(item, findStaff(item, staffMap))) {
                    "juniorSecondary" -> jc++
                    "seniorSecondary" -> sc++
                    "juniorPrimary" -> jp++
                    "seniorPrimary" -> sp++
                    "nursery" -> nu++
                }
            }
            CategorySectionMatrixRow(
                category = catName,
                jnrColl = jc,
                snrCol = sc,
                jnrPri = jp,
                snrPri = sp,
                nur = nu,
                rowTotal = jc + sc + jp + sp + nu
            )
        }
        val matrixTotals = CategorySectionMatrixTotals(
            jnrColl = matrixRows.sumOf { it.jnrColl },
            snrCol = matrixRows.sumOf { it.snrCol },
            jnrPri = matrixRows.sumOf { it.jnrPri },
            snrPri = matrixRows.sumOf { it.snrPri },
            nur = matrixRows.sumOf { it.nur },
            grandTotal = matrixRows.sumOf { it.rowTotal }
        )

        return TermAnalysisResult(
            term = term,
            session = session,
            periodLabel = periodLabel,
            isFromRawData = true,
            isHydratedFromDurableSnapshot = false,
            grandTotals = grandTotals,
            comparisons = comparisons,
            sections = sectionsData,
            disciplines = discAnalysis,
            classes = classData,
            categoryPatterns = catPatterns,
            trends = trends,
            matrix = CategorySectionMatrix(rows = matrixRows, columnTotals = matrixTotals)
        )
    }

    fun buildDrillDownAnalytics(
        exceptions: List<ComplianceReport>,
        staffList: List<StaffMember>
    ): DrillDownAnalytics {
        val staffMap = AnalysisEngine.buildStaffLookup(staffList)
        val total = exceptions.size
        val staffKeys = mutableSetOf<String>()
        val sectionCounts = mutableMapOf<String, Int>()
        val discCounts = mutableMapOf<String, Int>()
        val classCounts = mutableMapOf<String, Int>()
        val termCounts = mutableMapOf<String, Int>()
        var seen = 0
        var unseen = 0

        exceptions.forEach { e ->
            staffIdentityKey(e)?.let { staffKeys.add(it) }
            val stf = findStaff(e, staffMap)
            val sec = if (isCdsRecord(e)) {
                "CDS Infrastructure"
            } else {
                val div = AnalysisEngine.resolveDivision(e, stf)
                if (div == ComplianceDivision.ACADEMIC) sectionLabelForKey(resolveSectionKey(e, stf))
                else div.label
            }
            sectionCounts[sec] = (sectionCounts[sec] ?: 0) + 1

            val disc = normalizeAcademicDepartment(e.disciplineGroup, e.subject, e.staffGroup ?: stf?.staffGroup, e.division)
            discCounts[disc] = (discCounts[disc] ?: 0) + 1

            val cls = e.className?.trim()?.takeIf { it.isNotBlank() } ?: "Unassigned Class"
            classCounts[cls] = (classCounts[cls] ?: 0) + 1

            val trm = e.term.ifBlank { "Current Term" }
            termCounts[trm] = (termCounts[trm] ?: 0) + 1

            if (e.isSeen) seen++ else unseen++
        }

        fun toSorted(map: Map<String, Int>): List<NameCountPct> = map.entries
            .sortedByDescending { it.value }
            .map { (name, count) ->
                NameCountPct(name, count, if (total > 0) ((count * 100f) / total).roundToInt() else 0)
            }

        val uniqueStaff = staffKeys.size
        val perHead = if (uniqueStaff > 0) String.format(Locale.US, "%.2f", total.toFloat() / uniqueStaff) else "0.00"

        return DrillDownAnalytics(
            total = total,
            uniqueStaffCount = uniqueStaff,
            perHeadText = perHead,
            seenCount = seen,
            unseenCount = unseen,
            bySection = toSorted(sectionCounts),
            byDiscipline = toSorted(discCounts),
            byClass = toSorted(classCounts),
            byTerm = toSorted(termCounts)
        )
    }

    fun buildSnapshotDrillDownAnalytics(
        type: String,
        targetName: String,
        analysis: TermAnalysisResult
    ): DrillDownAnalytics {
        val gt = analysis.grandTotals
        val total = when (type) {
            "section" -> analysis.sections.rankedList.firstOrNull { it.key == targetName || it.label == targetName }?.exceptionCount ?: gt.totalExceptions
            "discipline" -> analysis.disciplines.allDisciplines.firstOrNull { it.discipline.equals(targetName, true) }?.exceptionCount ?: gt.totalExceptions
            "category" -> analysis.categoryPatterns.rankedCategories.firstOrNull { it.category.equals(targetName, true) }?.occurrences ?: gt.totalExceptions
            "CDS_TOTAL" -> gt.cdsExceptions
            else -> gt.totalExceptions
        }
        val staffCount = when (type) {
            "section" -> analysis.sections.rankedList.firstOrNull { it.key == targetName || it.label == targetName }?.affectedStaffCount ?: gt.affectedStaffCount
            "discipline" -> analysis.disciplines.allDisciplines.firstOrNull { it.discipline.equals(targetName, true) }?.affectedTeachers ?: gt.affectedStaffCount
            else -> gt.affectedStaffCount
        }
        val perHead = if (staffCount > 0) String.format(Locale.US, "%.2f", total.toFloat() / staffCount) else "0.00"
        val secList = analysis.sections.rankedList.filter { it.exceptionCount > 0 }.map {
            NameCountPct(it.label, it.exceptionCount, it.shareOfAcademic)
        }
        val discSum = analysis.disciplines.allDisciplines.sumOf { it.exceptionCount }.coerceAtLeast(1)
        val discList = analysis.disciplines.allDisciplines.map {
            NameCountPct(it.discipline, it.exceptionCount, ((it.exceptionCount * 100f) / discSum).roundToInt())
        }
        val clsSum = analysis.classes.allClassesRanked.sumOf { it.exceptionCount }.coerceAtLeast(1)
        val clsList = analysis.classes.allClassesRanked.take(12).map {
            NameCountPct(it.className, it.exceptionCount, ((it.exceptionCount * 100f) / clsSum).roundToInt())
        }
        return DrillDownAnalytics(
            total = total,
            uniqueStaffCount = staffCount,
            perHeadText = perHead,
            seenCount = total,
            unseenCount = 0,
            bySection = secList,
            byDiscipline = discList,
            byClass = clsList,
            byTerm = listOf(NameCountPct(analysis.term, total, 100))
        )
    }
}
