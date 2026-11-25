package com.example.ui.screens.analytics

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.local.AcademicWeekEngine
import com.example.data.model.ComplianceDivision
import com.example.data.model.ComplianceReport
import com.example.data.model.DrillDownContext
import com.example.data.model.ReportScope
import com.example.data.model.SeverityLevel
import com.example.data.model.StaffMember
import com.example.data.model.SystemSettings
import com.example.data.model.TermAnalysisSnapshot
import com.example.data.model.UserRole
import com.example.domain.AnalysisEngine
import com.example.domain.AuthoritativeAnalysisBuilder
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.theme.ComplianceTheme
import com.example.ui.theme.LocalBottomFooterClearance
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun AnalyticsScreen(
    reports: List<ComplianceReport>,
    staffList: List<StaffMember>,
    snapshots: List<TermAnalysisSnapshot> = emptyList(),
    systemSettings: SystemSettings = SystemSettings(),
    selectedTerm: String,
    isRefreshing: Boolean = false,
    onSelectTerm: (String) -> Unit,
    onSyncDatabase: () -> Unit = {},
    onExportReport: (ReportScope, String, String, String, String) -> Unit,
    userRole: UserRole = UserRole.ADMIN,
    currentUserId: String = ""
) {
    val isAdmin = userRole.isAdminOrAbove
    val sessionLabel = systemSettings.academicSession.ifBlank { "2025/2026" }
    val scopedReports = remember(reports, isAdmin, currentUserId) {
        if (isAdmin) reports
        else if (currentUserId.isNotBlank()) reports.filter { it.reportedBy.equals(currentUserId, ignoreCase = true) }
        else reports
    }

    var datePreset by remember { mutableStateOf("ALL") } // ALL | WEEK | DAYS_30 | MONTH | CUSTOM
    var customStart by remember { mutableStateOf("") }
    var customEnd by remember { mutableStateOf("") }
    var activeJumpKey by remember { mutableStateOf("summary") }
    var drillDownContext by remember { mutableStateOf<DrillDownContext?>(null) }

    val (startDate, endDate, periodLabel) = remember(datePreset, customStart, customEnd, selectedTerm) {
        when (datePreset) {
            "WEEK" -> {
                val (s, e) = AcademicWeekEngine.currentWeekRangeBounds()
                Triple(s, e, "Current Week ($s to $e)")
            }
            "DAYS_30" -> {
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val endCal = Calendar.getInstance()
                val startCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
                val s = fmt.format(startCal.time)
                val e = fmt.format(endCal.time)
                Triple(s, e, "Last 30 Days")
            }
            "MONTH" -> {
                val (s, e) = AcademicWeekEngine.currentMonthRangeBounds()
                Triple(s, e, "Current Month")
            }
            "CUSTOM" -> {
                val label = when {
                    customStart.isNotBlank() && customEnd.isNotBlank() -> "$customStart to $customEnd"
                    customStart.isNotBlank() -> "From $customStart"
                    customEnd.isNotBlank() -> "Until $customEnd"
                    else -> "Custom Date Range"
                }
                Triple(customStart, customEnd, label)
            }
            else -> Triple("", "", if (selectedTerm == "ALL") "All Recorded Terms" else "Full $selectedTerm")
        }
    }

    val analysis = remember(scopedReports, staffList, snapshots, selectedTerm, sessionLabel, periodLabel, startDate, endDate) {
        AuthoritativeAnalysisBuilder.resolveAuthoritativeTermAnalysis(
            term = selectedTerm,
            session = sessionLabel,
            allReports = scopedReports,
            staffList = staffList,
            snapshots = if (isAdmin) snapshots else emptyList(),
            periodLabel = periodLabel,
            startDate = startDate,
            endDate = endDate
        )
    }

    val filteredTermReports = remember(scopedReports, selectedTerm, startDate, endDate) {
        AnalysisEngine.filterReports(
            reports = scopedReports,
            term = selectedTerm,
            startDate = startDate,
            endDate = endDate
        )
    }

    val staffLookup = remember(staffList) { AnalysisEngine.buildStaffLookup(staffList) }

    fun findStaffFor(r: ComplianceReport): StaffMember? {
        r.staffId?.lowercase(Locale.US)?.let { staffLookup[it] }?.let { return it }
        r.staffCode?.lowercase(Locale.US)?.let { staffLookup["code:$it"] }?.let { return it }
        r.staffName?.lowercase(Locale.US)?.let { staffLookup["name:$it"] }?.let { return it }
        return null
    }

    fun handleDrillCategory(categoryName: String) {
        val snapAnalytics = if (analysis.isHydratedFromDurableSnapshot) {
            AuthoritativeAnalysisBuilder.buildSnapshotDrillDownAnalytics("category", categoryName, analysis)
        } else null
        val matched = if (analysis.isHydratedFromDurableSnapshot) emptyList() else {
            filteredTermReports.filter {
                !AuthoritativeAnalysisBuilder.isCdsRecord(it) &&
                    AuthoritativeAnalysisBuilder.normalizeCategory(it).equals(categoryName, ignoreCase = true)
            }
        }
        drillDownContext = DrillDownContext(
            type = "category",
            title = categoryName,
            subtitle = "Aggregated category breakdown • ${analysis.term}",
            exceptions = matched,
            snapshotAnalytics = snapAnalytics
        )
    }

    fun handleDrillSection(sectionKey: String, sectionLabel: String) {
        val snapAnalytics = if (analysis.isHydratedFromDurableSnapshot) {
            AuthoritativeAnalysisBuilder.buildSnapshotDrillDownAnalytics("section", sectionKey, analysis)
        } else null
        val matched = if (analysis.isHydratedFromDurableSnapshot) emptyList() else {
            filteredTermReports.filter { r ->
                !AuthoritativeAnalysisBuilder.isCdsRecord(r) &&
                    AnalysisEngine.resolveDivision(r, findStaffFor(r)) == ComplianceDivision.ACADEMIC &&
                    AuthoritativeAnalysisBuilder.resolveSectionKey(r, findStaffFor(r)) == sectionKey
            }
        }
        drillDownContext = DrillDownContext(
            type = "section",
            title = sectionLabel,
            subtitle = "Section exception concentration & department spread",
            exceptions = matched,
            snapshotAnalytics = snapAnalytics
        )
    }

    fun handleDrillDiscipline(disciplineName: String, scopeLabel: String) {
        val snapAnalytics = if (analysis.isHydratedFromDurableSnapshot) {
            AuthoritativeAnalysisBuilder.buildSnapshotDrillDownAnalytics("discipline", disciplineName, analysis)
        } else null
        val matched = if (analysis.isHydratedFromDurableSnapshot) emptyList() else {
            filteredTermReports.filter { r ->
                if (AuthoritativeAnalysisBuilder.isCdsRecord(r)) return@filter false
                val stf = findStaffFor(r)
                val secKey = AuthoritativeAnalysisBuilder.resolveSectionKey(r, stf)
                val scopeOk = when {
                    scopeLabel.contains("Primary", ignoreCase = true) ->
                        secKey == "juniorPrimary" || secKey == "seniorPrimary" || secKey == "nursery"
                    scopeLabel.contains("Secondary", ignoreCase = true) ->
                        secKey == "juniorSecondary" || secKey == "seniorSecondary"
                    else -> true
                }
                val dept = AuthoritativeAnalysisBuilder.normalizeAcademicDepartment(
                    r.disciplineGroup,
                    r.subject,
                    r.staffGroup ?: stf?.staffGroup,
                    r.division
                )
                scopeOk && dept.equals(disciplineName, ignoreCase = true)
            }
        }
        drillDownContext = DrillDownContext(
            type = "discipline",
            title = "$disciplineName ($scopeLabel)",
            subtitle = "Departmental compliance breakdown across classrooms",
            exceptions = matched,
            snapshotAnalytics = snapAnalytics
        )
    }

    fun handleDrillKpi(kpiKey: String, title: String, subtitle: String) {
        val snapAnalytics = if (analysis.isHydratedFromDurableSnapshot) {
            AuthoritativeAnalysisBuilder.buildSnapshotDrillDownAnalytics(kpiKey, title, analysis)
        } else null
        val matched = if (analysis.isHydratedFromDurableSnapshot) emptyList() else {
            val nonCds = filteredTermReports.filter { !AuthoritativeAnalysisBuilder.isCdsRecord(it) }
            when (kpiKey) {
                "CDS_TOTAL" -> filteredTermReports.filter { AuthoritativeAnalysisBuilder.isCdsRecord(it) }
                "SEEN_EXCEPTIONS" -> filteredTermReports.filter { it.isSeen }
                "UNSEEN_EXCEPTIONS" -> filteredTermReports.filter { !it.isSeen }
                "SEVERITY_CRITICAL", "SEVERITY_HIGH", "SEVERITY_MEDIUM", "SEVERITY_LOW" -> {
                    val targetLevel = when (kpiKey) {
                        "SEVERITY_CRITICAL" -> SeverityLevel.CRITICAL
                        "SEVERITY_HIGH" -> SeverityLevel.HIGH
                        "SEVERITY_MEDIUM" -> SeverityLevel.MEDIUM
                        else -> SeverityLevel.LOW
                    }
                    val countsByStaff = mutableMapOf<String, Int>()
                    val denom = AuthoritativeAnalysisBuilder.getActiveStaffDenominator(staffList)
                    val matchingStaffIds = denom.filter { person ->
                        val c = nonCds.count { r ->
                            (person.id.isNotBlank() && r.staffId == person.id) ||
                                (person.staffCode.isNotBlank() && r.staffCode.equals(person.staffCode, true)) ||
                                r.staffName.equals(person.name, true)
                        }
                        countsByStaff[person.id] = c
                        SeverityLevel.fromCount(c) == targetLevel
                    }.map { it.id.lowercase(Locale.US) }.toSet()

                    nonCds.filter { r ->
                        val stf = findStaffFor(r)
                        val key = (r.staffId ?: stf?.id).orEmpty().lowercase(Locale.US)
                        key in matchingStaffIds
                    }
                }
                "STAFF_WITH_EXCEPTIONS" -> nonCds
                else -> filteredTermReports
            }
        }
        drillDownContext = DrillDownContext(
            type = "kpi",
            title = title,
            subtitle = subtitle,
            exceptions = matched,
            snapshotAnalytics = snapAnalytics
        )
    }

    val context = LocalContext.current
    fun openCustomDatePicker(initial: String, onPicked: (String) -> Unit) {
        val cal = Calendar.getInstance()
        try {
            if (initial.length == 10) {
                val parts = initial.split("-")
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        } catch (_: Exception) {}
        DatePickerDialog(
            context,
            { _, y, m, d ->
                onPicked(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val jumpSections = remember(isAdmin, analysis.isHydratedFromDurableSnapshot) {
        buildList {
            add("summary" to "Summary")
            add("comparison" to "Compare")
            add("trends" to "Trends")
            add("sections" to "Sections")
            add("disciplines" to "Departments")
            add("classes" to "Classes")
            add("categories" to "Categories")
            if (isAdmin) add("cds" to "CDS")
            add("matrix" to "Matrix")
            add("export" to "Export")
        }
    }

    val sectionIndexMap = remember(analysis.isHydratedFromDurableSnapshot, analysis.isCorruptOrUnverified, isAdmin) {
        val map = mutableMapOf<String, Int>()
        var idx = 2 // 0=Hero, 1=QuickJumpBar (or Banner at 1 + QuickJumpBar at 2)
        if (analysis.isHydratedFromDurableSnapshot || analysis.isCorruptOrUnverified) {
            idx += 1
        }
        map["summary"] = idx++       // ExecutivePersonnelMetricsSection
        idx++                        // DerivedSeverityAndAuditSection
        map["comparison"] = idx++    // TermComparisonSection
        map["trends"] = idx++        // OverallTrendSection
        map["sections"] = idx++      // SectionAnalysisPanel
        map["disciplines"] = idx++   // DisciplineAnalysisPanel
        map["classes"] = idx++       // ClassAnalysisPanel
        map["categories"] = idx++    // ExceptionCategoryPatternPanel
        if (isAdmin) {
            map["cds"] = idx++       // CdsDivisionAnalysisPanel
        }
        map["matrix"] = idx++        // MultidimensionalMatrixSection
        map["export"] = idx          // AnalysisExportSection
        map
    }

    val p = ComplianceTheme.palette
    val hasOverlay = drillDownContext != null
    val overlayBlurRadius by animateDpAsState(
        targetValue = if (hasOverlay) 20.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "analytics_overlay_blur"
    )

    AtmosphericGlassBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 860.dp)
                    .then(if (overlayBlurRadius > 0.dp) Modifier.blur(overlayBlurRadius) else Modifier)
                    .statusBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = LocalBottomFooterClearance.current),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "hero_header") {
                    AnalysisHeroGlassHeader(
                        selectedTerm = selectedTerm,
                        sessionLabel = sessionLabel,
                        periodLabel = periodLabel,
                        analysis = analysis,
                        allReports = scopedReports,
                        snapshots = if (isAdmin) snapshots else emptyList(),
                        datePreset = datePreset,
                        customStart = customStart,
                        customEnd = customEnd,
                        isRefreshing = isRefreshing,
                        isAdmin = isAdmin,
                        onSelectTerm = onSelectTerm,
                        onSelectPreset = { datePreset = it },
                        onPickStartDate = { openCustomDatePicker(customStart) { customStart = it } },
                        onClearStartDate = { customStart = "" },
                        onPickEndDate = { openCustomDatePicker(customEnd) { customEnd = it } },
                        onClearEndDate = { customEnd = "" },
                        onSyncDatabase = onSyncDatabase
                    )
                }

                if (analysis.isHydratedFromDurableSnapshot || analysis.isCorruptOrUnverified) {
                    item(key = "snapshot_status_banner") {
                        SnapshotStatusBanner(analysis = analysis)
                    }
                }

                item(key = "quick_jump_bar") {
                    AnalyticsQuickJumpBar(
                        sections = jumpSections,
                        activeKey = activeJumpKey,
                        onSelect = { key ->
                            activeJumpKey = key
                            val targetIdx = sectionIndexMap[key] ?: 0
                            coroutineScope.launch {
                                listState.animateScrollToItem(targetIdx.coerceAtLeast(0))
                            }
                        }
                    )
                }

                item(key = "executive_metrics") {
                    ExecutivePersonnelMetricsSection(
                        analysis = analysis,
                        isAdmin = isAdmin,
                        onDrillKpi = ::handleDrillKpi
                    )
                }

                item(key = "severity_and_audit") {
                    DerivedSeverityAndAuditSection(
                        analysis = analysis,
                        isAdmin = isAdmin,
                        onDrillKpi = ::handleDrillKpi
                    )
                }

                item(key = "term_comparison") {
                    TermComparisonSection(comparisons = analysis.comparisons)
                }

                item(key = "overall_trend") {
                    OverallTrendSection(trends = analysis.trends)
                }

                item(key = "section_analysis") {
                    SectionAnalysisPanel(
                        sections = analysis.sections,
                        priorTermName = analysis.comparisons.priorTerm,
                        onDrillSection = ::handleDrillSection
                    )
                }

                item(key = "discipline_analysis") {
                    DisciplineAnalysisPanel(
                        disciplines = analysis.disciplines,
                        onDrillDiscipline = ::handleDrillDiscipline
                    )
                }

                item(key = "class_analysis") {
                    ClassAnalysisPanel(classes = analysis.classes)
                }

                item(key = "category_patterns") {
                    ExceptionCategoryPatternPanel(
                        categoryPatterns = analysis.categoryPatterns,
                        onDrillCategory = ::handleDrillCategory
                    )
                }

                if (isAdmin) {
                    item(key = "cds_division") {
                        CdsDivisionAnalysisPanel(
                            grandTotals = analysis.grandTotals,
                            onDrillCds = {
                                handleDrillKpi(
                                    "CDS_TOTAL",
                                    "CDS Infrastructure Domain",
                                    "Institution-level Community Development Service compliance logs"
                                )
                            }
                        )
                    }
                }

                item(key = "multidimensional_matrix") {
                    MultidimensionalMatrixSection(
                        matrix = analysis.matrix,
                        activeTerm = analysis.term,
                        onDrillCategory = ::handleDrillCategory
                    )
                }

                item(key = "export_studio") {
                    AnalysisExportSection(
                        isAdmin = isAdmin,
                        selectedTerm = selectedTerm,
                        startDate = startDate,
                        endDate = endDate,
                        onExportDossier = onExportReport
                    )
                }
            }

            AnimatedVisibility(
                visible = hasOverlay,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(180))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (p.isDark) Color(0xFF06080D).copy(alpha = 0.56f)
                            else Color(0xFF0F172A).copy(alpha = 0.34f)
                        )
                )
            }
        }
    }

    drillDownContext?.let { ctx ->
        val drillAnalytics = remember(ctx, staffList) {
            ctx.snapshotAnalytics ?: AuthoritativeAnalysisBuilder.buildDrillDownAnalytics(ctx.exceptions, staffList)
        }
        AnalysisDrillDownDialog(
            context = ctx,
            analytics = drillAnalytics,
            onDismiss = { drillDownContext = null }
        )
    }
}
