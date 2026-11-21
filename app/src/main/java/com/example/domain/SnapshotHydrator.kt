package com.example.domain

import com.example.data.model.AuthoritativeGrandTotals
import com.example.data.model.CategoryFrequencyItem
import com.example.data.model.CategoryPatternData
import com.example.data.model.CategorySectionMatrix
import com.example.data.model.CategorySectionMatrixRow
import com.example.data.model.CategorySectionMatrixTotals
import com.example.data.model.ClassAnalysisData
import com.example.data.model.ClassMetricItem
import com.example.data.model.ConcentrationMetrics
import com.example.data.model.DeltaMetric
import com.example.data.model.DeltaStatus
import com.example.data.model.DisciplineAnalysisData
import com.example.data.model.DisciplineMetricItem
import com.example.data.model.DisciplineScopeData
import com.example.data.model.OverallTrendData
import com.example.data.model.PatternObservation
import com.example.data.model.SectionAnalysisData
import com.example.data.model.SectionClassGroup
import com.example.data.model.SectionContributionItem
import com.example.data.model.SectionMetricItem
import com.example.data.model.SegmentTermStats
import com.example.data.model.SubcategoryCount
import com.example.data.model.TermAnalysisResult
import com.example.data.model.TermAnalysisSnapshot
import com.example.data.model.TermComparisonData
import com.example.data.model.TermTrendPoint
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

object SnapshotHydrator {

    private fun parseObj(raw: String): JSONObject? =
        raw.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }

    fun extractSegmentStatsFromSnapshot(snap: TermAnalysisSnapshot): Map<String, SegmentTermStats> {
        val full = parseObj(snap.rawFullSnapshotJson)
        val sectionsObj = full?.optJSONObject("sections") ?: parseObj(snap.rawSectionMetricsJson)
        val out = mutableMapOf<String, SegmentTermStats>()

        val mapping = listOf(
            "juniorPrimary" to listOf("juniorPrimary", "junior_primary", "Junior Primary"),
            "seniorPrimary" to listOf("seniorPrimary", "senior_primary", "Senior Primary"),
            "juniorSeniorPrimary" to listOf("juniorSeniorPrimary", "primaryCombined", "Primary"),
            "juniorSecondary" to listOf("juniorSecondary", "junior_secondary", "Junior Secondary"),
            "seniorSecondary" to listOf("seniorSecondary", "senior_secondary", "Senior Secondary"),
            "secondaryCombined" to listOf("secondaryCombined", "juniorSeniorSecondary", "Secondary"),
            "nursery" to listOf("nursery", "Nursery"),
            "academicTotal" to listOf("academicTotal", "totalAcademic", "Total Academic")
        )

        if (sectionsObj != null) {
            for ((canonicalKey, aliases) in mapping) {
                val obj = aliases.firstNotNullOfOrNull { sectionsObj.optJSONObject(it) }
                if (obj != null) {
                    val exc = obj.optInt("exceptionCount", obj.optInt("count", 0))
                    val stf = obj.optInt("affectedStaffCount", obj.optInt("staffCount", 0))
                    val ph = obj.optDouble("perHead", if (stf > 0) exc.toDouble() / stf else 0.0).toFloat()
                    out[canonicalKey] = SegmentTermStats(exc, stf, ph)
                }
            }
            val ranked = sectionsObj.optJSONArray("rankedList")
            if (ranked != null) {
                for (i in 0 until ranked.length()) {
                    val item = ranked.optJSONObject(i) ?: continue
                    val key = item.optString("key")
                    if (key.isNotBlank() && !out.containsKey(key)) {
                        val exc = item.optInt("exceptionCount", 0)
                        val stf = item.optInt("affectedStaffCount", 0)
                        val ph = item.optDouble("perHead", 0.0).toFloat()
                        out[key] = SegmentTermStats(exc, stf, ph)
                    }
                }
            }
        }

        if (!out.containsKey("juniorSeniorPrimary")) {
            val jp = out["juniorPrimary"] ?: SegmentTermStats()
            val sp = out["seniorPrimary"] ?: SegmentTermStats()
            val exc = jp.exceptionCount + sp.exceptionCount
            val stf = jp.affectedStaffCount + sp.affectedStaffCount
            out["juniorSeniorPrimary"] = SegmentTermStats(exc, stf, if (stf > 0) (exc * 100f / stf).roundToInt() / 100f else 0f)
        }
        if (!out.containsKey("secondaryCombined")) {
            val js = out["juniorSecondary"] ?: SegmentTermStats()
            val ss = out["seniorSecondary"] ?: SegmentTermStats()
            val exc = js.exceptionCount + ss.exceptionCount
            val stf = js.affectedStaffCount + ss.affectedStaffCount
            out["secondaryCombined"] = SegmentTermStats(exc, stf, if (stf > 0) (exc * 100f / stf).roundToInt() / 100f else 0f)
        }
        if (!out.containsKey("academicTotal")) {
            val exc = snap.academicExceptionsCount
            val stf = snap.affectedStaffCount
            out["academicTotal"] = SegmentTermStats(exc, stf, if (stf > 0) (exc * 100f / stf).roundToInt() / 100f else 0f)
        }

        return out
    }

    fun hydrateTermAnalysis(
        snap: TermAnalysisSnapshot,
        term: String,
        session: String,
        periodLabel: String,
        staffDenominatorFallback: Int,
        comparisons: TermComparisonData,
        trends: OverallTrendData
    ): TermAnalysisResult {
        if (!snap.isVerified) {
            return TermAnalysisResult(
                term = term,
                session = session,
                periodLabel = periodLabel,
                isFromRawData = false,
                isHydratedFromDurableSnapshot = false,
                isCorruptOrUnverified = true,
                snapshotStatus = snap.status,
                snapshotVersion = snap.snapshotVersion,
                officialTermEndDate = snap.officialTermEndDate,
                errorMessage = "Historical snapshot (${snap.status}) cannot be verified for authoritative display.",
                comparisons = comparisons,
                trends = trends
            )
        }

        val full = parseObj(snap.rawFullSnapshotJson)
        val gtJson = full?.optJSONObject("grandTotals") ?: parseObj(snap.rawExecutiveSummaryJson)
        val sevJson = full?.optJSONObject("severityDistribution") ?: parseObj(snap.rawSeverityDistributionJson)

        val staffDenom = gtJson?.optInt("staffDenominator", snap.staffPopulationDenominator)
            ?.takeIf { it > 0 } ?: snap.staffPopulationDenominator.takeIf { it > 0 } ?: staffDenominatorFallback
        val affected = gtJson?.optInt("affectedStaffCount", snap.affectedStaffCount) ?: snap.affectedStaffCount
        val clean = gtJson?.optInt("cleanStaffCount", snap.cleanStaffCount)
            ?: (staffDenom - affected).coerceAtLeast(0)
        val totalExc = gtJson?.optInt("totalExceptions", snap.totalQualifyingExceptions)
            ?: snap.totalQualifyingExceptions
        val acadExc = gtJson?.optInt("academicExceptions", snap.academicExceptionsCount)
            ?: snap.academicExceptionsCount
        val opsExc = gtJson?.optInt("operationsExceptions", snap.operationsExceptionsCount)
            ?: snap.operationsExceptionsCount
        val discExc = gtJson?.optInt("disciplineExceptions", snap.disciplineExceptionsCount)
            ?: snap.disciplineExceptionsCount
        val cdsExc = gtJson?.optInt("cdsExceptions", snap.cdsExceptionsCount)
            ?: snap.cdsExceptionsCount

        val flaggedPct = if (staffDenom > 0) ((affected * 100f) / staffDenom).roundToInt() else 0
        val cleanPct = if (staffDenom > 0) ((clean * 100f) / staffDenom).roundToInt() else 0

        val grandTotals = AuthoritativeGrandTotals(
            staffDenominator = staffDenom,
            affectedStaffCount = affected,
            staffWithExceptionsPercentage = gtJson?.optInt("staffWithExceptionsPercentage", flaggedPct) ?: flaggedPct,
            cleanStaffCount = clean,
            cleanStaffPercentage = gtJson?.optInt("cleanStaffPercentage", cleanPct) ?: cleanPct,
            totalExceptions = totalExc,
            academicExceptions = acadExc,
            operationsExceptions = opsExc,
            disciplineExceptions = discExc,
            cdsExceptions = cdsExc,
            seenExceptions = gtJson?.optInt("seenExceptions", totalExc) ?: totalExc,
            unseenExceptions = gtJson?.optInt("unseenExceptions", 0) ?: 0,
            seenCdsCount = cdsExc,
            unseenCdsCount = 0,
            overallPerHead = snap.overallPerHead,
            criticalStaffCount = sevJson?.optInt("critical", sevJson.optInt("Critical", 0)) ?: 0,
            highStaffCount = sevJson?.optInt("high", sevJson.optInt("High", 0)) ?: 0,
            mediumStaffCount = sevJson?.optInt("medium", sevJson.optInt("Medium", 0)) ?: 0,
            lowStaffCount = sevJson?.optInt("low", sevJson.optInt("Low", 0)) ?: 0
        )

        val secObj = full?.optJSONObject("sections") ?: parseObj(snap.rawSectionMetricsJson)
        val sections = parseSectionAnalysis(secObj, acadExc, comparisons)

        val discObj = full?.optJSONObject("disciplines") ?: parseObj(snap.rawDisciplineMetricsJson)
        val disciplines = parseDisciplineAnalysis(discObj, comparisons.priorTerm)

        val classObj = full?.optJSONObject("classes") ?: parseObj(snap.rawClassMetricsJson)
        val classes = parseClassAnalysis(classObj)

        val patObj = full?.optJSONObject("categoryPatterns") ?: parseObj(snap.rawCategoryPatternsJson)
        val patterns = parseCategoryPatterns(patObj, comparisons.priorTerm, sections.rankedList, acadExc)

        val matObj = full?.optJSONObject("categories") ?: parseObj(snap.rawCategoryMetricsJson)
        val matrix = parseMatrix(matObj)

        return TermAnalysisResult(
            term = term,
            session = snap.session.ifBlank { session },
            periodLabel = periodLabel,
            isFromRawData = false,
            isHydratedFromDurableSnapshot = true,
            isCorruptOrUnverified = false,
            snapshotStatus = snap.status,
            snapshotVersion = snap.snapshotVersion,
            officialTermEndDate = snap.officialTermEndDate,
            grandTotals = grandTotals,
            comparisons = comparisons,
            sections = sections,
            disciplines = disciplines,
            classes = classes,
            categoryPatterns = patterns,
            trends = trends,
            matrix = matrix
        )
    }

    private fun parseDelta(obj: JSONObject?): DeltaMetric? {
        if (obj == null) return null
        val cur = obj.optDouble("current", 0.0).toFloat()
        val prev = obj.optDouble("previous", 0.0).toFloat()
        val abs = obj.optDouble("absoluteChange", 0.0).toFloat()
        val pct = if (obj.has("percentageChange") && !obj.isNull("percentageChange")) obj.optInt("percentageChange") else null
        val hasBase = obj.optBoolean("hasBaseline", prev > 0f)
        val statusStr = obj.optString("status", "")
        val status = when {
            !hasBase || statusStr.contains("no_prior", true) -> DeltaStatus.NO_PRIOR_BASELINE
            abs == 0f || statusStr == "unchanged" -> DeltaStatus.UNCHANGED
            abs > 0f -> DeltaStatus.INCREASED
            else -> DeltaStatus.DECREASED
        }
        return DeltaMetric(cur, prev, abs, pct, hasBase, status)
    }

    private fun parseSectionAnalysis(
        secObj: JSONObject?,
        acadTotal: Int,
        comparisons: TermComparisonData
    ): SectionAnalysisData {
        if (secObj == null) return SectionAnalysisData(totalAcademicExceptions = acadTotal)
        val arr = secObj.optJSONArray("rankedList") ?: JSONArray()
        val ranked = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val key = o.optString("key")
                val exc = o.optInt("exceptionCount", 0)
                val stf = o.optInt("affectedStaffCount", 0)
                val ph = o.optDouble("perHead", 0.0).toFloat()
                val share = o.optInt("shareOfAcademic", if (acadTotal > 0) ((exc * 100f) / acadTotal).roundToInt() else 0)
                add(
                    SectionMetricItem(
                        key = key,
                        label = o.optString("label", key),
                        rank = o.optInt("rank", i + 1),
                        exceptionCount = exc,
                        affectedStaffCount = stf,
                        perHead = ph,
                        shareOfAcademic = share,
                        historicalDelta = comparisons.segmentComparisons[key]?.exceptionComparison
                    )
                )
            }
        }
        val pri = secObj.optJSONObject("juniorSeniorPrimary")?.optInt("exceptionCount", 0)
            ?: ranked.filter { it.key == "juniorPrimary" || it.key == "seniorPrimary" }.sumOf { it.exceptionCount }
        val sec = secObj.optJSONObject("juniorSeniorSecondary")?.optInt("exceptionCount", 0)
            ?: ranked.filter { it.key == "juniorSecondary" || it.key == "seniorSecondary" }.sumOf { it.exceptionCount }
        return SectionAnalysisData(
            rankedList = ranked,
            primaryCombinedCount = pri,
            secondaryCombinedCount = sec,
            totalAcademicExceptions = acadTotal
        )
    }

    private fun parseDisciplineRows(arr: JSONArray?): List<DisciplineMetricItem> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    DisciplineMetricItem(
                        rank = o.optInt("rank", i + 1),
                        discipline = o.optString("discipline"),
                        exceptionCount = o.optInt("exceptionCount", 0),
                        affectedTeachers = o.optInt("affectedTeachers", 0),
                        perHead = o.optDouble("perHead", 0.0).toFloat(),
                        shareOfScope = o.optInt("shareOfScope", o.optInt("shareOfTotal", 0)),
                        historicalChange = parseDelta(o.optJSONObject("historicalChange")),
                        teacherNamesSummary = o.optString("teacherNamesSummary", "")
                    )
                )
            }
        }
    }

    private fun parseDisciplineAnalysis(discObj: JSONObject?, priorTerm: String?): DisciplineAnalysisData {
        if (discObj == null) return DisciplineAnalysisData(priorTerm = priorTerm)
        fun parseScope(key: String): DisciplineScopeData {
            val sObj = discObj.optJSONObject(key) ?: return DisciplineScopeData()
            val rows = parseDisciplineRows(sObj.optJSONArray("rows"))
            val tot = sObj.optJSONObject("totals")
            return DisciplineScopeData(
                rows = rows,
                totalExceptions = tot?.optInt("exceptionCount", rows.sumOf { it.exceptionCount }) ?: rows.sumOf { it.exceptionCount },
                totalTeachers = tot?.optInt("affectedTeachers", rows.sumOf { it.affectedTeachers }) ?: rows.sumOf { it.affectedTeachers },
                perHead = tot?.optDouble("perHead", 0.0)?.toFloat() ?: 0f
            )
        }
        return DisciplineAnalysisData(
            primary = parseScope("primary"),
            secondary = parseScope("secondary"),
            allDisciplines = parseDisciplineRows(discObj.optJSONArray("allDisciplines")),
            priorTerm = discObj.optString("priorTerm").takeIf { it.isNotBlank() } ?: priorTerm
        )
    }

    private fun parseClassAnalysis(classObj: JSONObject?): ClassAnalysisData {
        if (classObj == null) return ClassAnalysisData()
        fun parseClassItem(o: JSONObject, fallbackIdx: Int): ClassMetricItem {
            val subsArr = o.optJSONArray("subcategories") ?: JSONArray()
            val subs = buildList {
                for (j in 0 until subsArr.length()) {
                    val s = subsArr.optJSONObject(j) ?: continue
                    add(SubcategoryCount(s.optString("category"), s.optInt("count", 0)))
                }
            }
            return ClassMetricItem(
                rank = o.optInt("rank", fallbackIdx + 1),
                className = o.optString("className", "Not specified"),
                sectionKey = o.optString("sectionKey", ""),
                sectionLabel = o.optString("sectionLabel", ""),
                exceptionCount = o.optInt("exceptionCount", 0),
                affectedStaffCount = o.optInt("affectedStaffCount", 0),
                shareOfSection = o.optInt("shareOfSection", 0),
                topCategory = o.optString("topCategory", subs.firstOrNull()?.category ?: "General Check"),
                subcategories = subs,
                topSubcategoriesSummary = o.optString("topSubcategoriesSummary", "")
            )
        }

        val allArr = classObj.optJSONArray("allClassesRanked") ?: JSONArray()
        val allRanked = buildList {
            for (i in 0 until allArr.length()) {
                val o = allArr.optJSONObject(i) ?: continue
                add(parseClassItem(o, i))
            }
        }
        val bySecArr = classObj.optJSONArray("bySection") ?: JSONArray()
        val bySec = buildList {
            for (i in 0 until bySecArr.length()) {
                val g = bySecArr.optJSONObject(i) ?: continue
                val clsArr = g.optJSONArray("classes") ?: JSONArray()
                val clsList = buildList {
                    for (j in 0 until clsArr.length()) {
                        val c = clsArr.optJSONObject(j) ?: continue
                        add(parseClassItem(c, j))
                    }
                }
                add(
                    SectionClassGroup(
                        sectionKey = g.optString("sectionKey"),
                        sectionLabel = g.optString("sectionLabel"),
                        totalExceptions = g.optInt("totalExceptions", clsList.sumOf { it.exceptionCount }),
                        affectedStaffCount = g.optInt("affectedStaffCount", 0),
                        perHead = g.optDouble("perHead", 0.0).toFloat(),
                        classes = clsList
                    )
                )
            }
        }
        return ClassAnalysisData(
            bySection = bySec,
            allClassesRanked = allRanked,
            totalClassesCount = classObj.optInt("totalClassesCount", allRanked.size),
            unspecifiedCount = classObj.optInt("unspecifiedCount", 0)
        )
    }

    private fun parseCategoryPatterns(
        patObj: JSONObject?,
        priorTermName: String?,
        sectionsFallback: List<SectionMetricItem>,
        acadTotal: Int
    ): CategoryPatternData {
        if (patObj == null) return CategoryPatternData(priorTermName = priorTermName)
        fun parseCats(arr: JSONArray?): List<CategoryFrequencyItem> {
            if (arr == null) return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        CategoryFrequencyItem(
                            rank = o.optInt("rank", i + 1),
                            category = o.optString("category"),
                            occurrences = o.optInt("occurrences", 0),
                            percentage = o.optInt("percentage", 0),
                            cumulativePercentage = o.optInt("cumulativePercentage", 0),
                            affectedSectionsSummary = o.optString("affectedSectionsSummary", ""),
                            historicalDelta = parseDelta(o.optJSONObject("historicalDelta"))
                        )
                    )
                }
            }
        }
        val ranked = parseCats(patObj.optJSONArray("rankedCategories"))
        val topDrivers = parseCats(patObj.optJSONArray("topDrivers")).ifEmpty { ranked.take(3) }
        val concObj = patObj.optJSONObject("concentration")
        val conc = ConcentrationMetrics(
            top3Count = concObj?.optInt("top3Count", minOf(3, ranked.size)) ?: minOf(3, ranked.size),
            top3Share = concObj?.optInt("top3Share", 0) ?: 0,
            categoriesFor80PercentVolume = concObj?.optInt("categoriesFor80PercentVolume", 0) ?: 0,
            totalCategories = concObj?.optInt("totalCategories", ranked.size) ?: ranked.size
        )
        val secArr = patObj.optJSONArray("sectionContributions")
        val secContribs = if (secArr != null) {
            buildList {
                for (i in 0 until secArr.length()) {
                    val o = secArr.optJSONObject(i) ?: continue
                    val secName = o.optString("section")
                    add(
                        SectionContributionItem(
                            sectionKey = o.optString("sectionKey", secName),
                            section = secName,
                            occurrences = o.optInt("occurrences", 0),
                            percentage = o.optInt("percentage", 0),
                            isInstitutionLevel = o.optBoolean("isInstitutionLevel", false)
                        )
                    )
                }
            }
        } else {
            sectionsFallback.map {
                SectionContributionItem(
                    sectionKey = it.key,
                    section = it.label,
                    occurrences = it.exceptionCount,
                    percentage = if (acadTotal > 0) ((it.exceptionCount * 100f) / acadTotal).roundToInt() else 0
                )
            }
        }
        val obsArr = patObj.optJSONArray("observations") ?: JSONArray()
        val obsList = buildList {
            for (i in 0 until obsArr.length()) {
                val o = obsArr.optJSONObject(i) ?: continue
                add(
                    PatternObservation(
                        id = o.optString("id", "obs_$i"),
                        title = o.optString("title"),
                        statement = o.optString("statement"),
                        highlight = o.optString("highlight")
                    )
                )
            }
        }
        return CategoryPatternData(
            rankedCategories = ranked,
            topDrivers = topDrivers,
            concentration = conc,
            sectionContributions = secContribs,
            observations = obsList,
            priorTermName = patObj.optString("priorTermName").takeIf { it.isNotBlank() } ?: priorTermName
        )
    }

    private fun parseMatrix(matObj: JSONObject?): CategorySectionMatrix {
        if (matObj == null) return CategorySectionMatrix()
        val rowsArr = matObj.optJSONArray("rows") ?: JSONArray()
        val rows = buildList {
            for (i in 0 until rowsArr.length()) {
                val o = rowsArr.optJSONObject(i) ?: continue
                add(
                    CategorySectionMatrixRow(
                        category = o.optString("category"),
                        jnrColl = o.optInt("jnrColl", 0),
                        snrCol = o.optInt("snrCol", 0),
                        jnrPri = o.optInt("jnrPri", 0),
                        snrPri = o.optInt("snrPri", 0),
                        nur = o.optInt("nur", 0),
                        rowTotal = o.optInt("rowTotal", 0)
                    )
                )
            }
        }
        val ct = matObj.optJSONObject("columnTotals")
        val totals = CategorySectionMatrixTotals(
            jnrColl = ct?.optInt("jnrColl", rows.sumOf { it.jnrColl }) ?: rows.sumOf { it.jnrColl },
            snrCol = ct?.optInt("snrCol", rows.sumOf { it.snrCol }) ?: rows.sumOf { it.snrCol },
            jnrPri = ct?.optInt("jnrPri", rows.sumOf { it.jnrPri }) ?: rows.sumOf { it.jnrPri },
            snrPri = ct?.optInt("snrPri", rows.sumOf { it.snrPri }) ?: rows.sumOf { it.snrPri },
            nur = ct?.optInt("nur", rows.sumOf { it.nur }) ?: rows.sumOf { it.nur },
            grandTotal = ct?.optInt("grandTotal", rows.sumOf { it.rowTotal }) ?: rows.sumOf { it.rowTotal }
        )
        return CategorySectionMatrix(rows = rows, columnTotals = totals)
    }
}
