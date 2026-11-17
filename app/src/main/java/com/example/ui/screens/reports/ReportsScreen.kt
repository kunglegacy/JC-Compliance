package com.example.ui.screens.reports

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ComplianceReport
import com.example.data.model.ReportResolutionStatus
import com.example.data.model.ReportScope
import com.example.data.model.StaffMember
import com.example.domain.AnalysisEngine
import com.example.ui.components.LivingAtmosphericBackground
import com.example.ui.components.SeverityBadge
import com.example.ui.components.StatusGlassChip
import com.example.ui.components.complianceGlassCard
import com.example.ui.screens.dashboard.RecentReportCard
import com.example.ui.theme.LocalCompliancePalette
import java.util.Calendar
import java.util.Locale

@Composable
fun ReportsScreen(
    reports: List<ComplianceReport>,
    staffList: List<StaffMember>,
    selectedTerm: String,
    initialScope: ReportScope = ReportScope.ALL,
    onSelectTerm: (String) -> Unit,
    onExportPdf: (ReportScope, String) -> Unit,
    onSelectReport: (ComplianceReport) -> Unit
) {
    val context = LocalContext.current
    val p = LocalCompliancePalette.current

    var selectedScope by remember(initialScope) { mutableStateOf(initialScope) }
    var viewMode by remember { mutableIntStateOf(0) } // 0 = By Exception, 1 = By Staff
    var search by remember { mutableStateOf("") }
    var seenFilter by remember { mutableStateOf("ALL") }
    var selectedDateFilter by remember { mutableStateOf<String?>(null) }

    // Dynamic collapsible filter drawer state (Search + Divisions + Seen/Unseen)
    var filtersExpanded by remember { mutableStateOf(true) }
    var scrollDragDelta by remember { mutableFloatStateOf(0f) }

    val listState = rememberLazyListState()
    val staffMap = remember(staffList) { AnalysisEngine.buildStaffLookup(staffList) }

    val filteredReports = remember(reports, selectedTerm, selectedScope, search, seenFilter, selectedDateFilter, staffMap) {
        val q = search.trim().lowercase()
        val dateNeedle = selectedDateFilter?.trim().orEmpty()
        AnalysisEngine.filterReports(reports, term = selectedTerm)
            .filter { r ->
                if (!AnalysisEngine.recordMatchesScope(r, selectedScope, staffMap)) return@filter false
                when (seenFilter) {
                    "ALL" -> Unit
                    "UNSEEN" -> if (r.isSeen) return@filter false
                    else -> if (!r.status.key.equals(seenFilter, ignoreCase = true)) return@filter false
                }
                if (dateNeedle.isNotBlank() && !r.incidentDate.startsWith(dateNeedle)) return@filter false
                if (q.isNotBlank()) {
                    val hay = "${r.staffName.orEmpty()} ${r.staffCode.orEmpty()} ${r.complianceCheck} ${r.exceptionText} ${r.className.orEmpty()} ${r.reporterName}".lowercase()
                    if (!hay.contains(q)) return@filter false
                }
                true
            }
    }

    val staffAggregates = remember(filteredReports, staffList) {
        AnalysisEngine.calculateStaffAggregates(filteredReports, staffList)
    }

    val activeFilterCount = remember(search, selectedScope, seenFilter, selectedDateFilter) {
        var count = 0
        if (search.isNotBlank()) count++
        if (selectedScope != ReportScope.ALL) count++
        if (seenFilter != "ALL") count++
        if (!selectedDateFilter.isNullOrBlank()) count++
        count
    }

    // Dynamic NestedScrollConnection: swipe up/scroll down collapses filters, swipe down/scroll up reveals them
    val dynamicFilterScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = available.y
                if (dy < 0f) {
                    scrollDragDelta = if (scrollDragDelta < 0f) scrollDragDelta + dy else dy
                    if (scrollDragDelta < -32f && filtersExpanded) {
                        filtersExpanded = false
                        scrollDragDelta = 0f
                    }
                } else if (dy > 0f) {
                    scrollDragDelta = if (scrollDragDelta > 0f) scrollDragDelta + dy else dy
                    if (scrollDragDelta > 42f && !filtersExpanded) {
                        filtersExpanded = true
                        scrollDragDelta = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }

    fun openSystemDateFilter() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                selectedDateFilter = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    LivingAtmosphericBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .nestedScroll(dynamicFilterScrollConnection)
        ) {
            // 1. Always-Visible Compact Top Header + Mode Switch
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "INSTITUTIONAL ARCHIVE",
                            color = p.textSecondary,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Compliance Reports",
                            color = p.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier
                            .height(42.dp)
                            .shadow(12.dp, CircleShape, spotColor = p.tangerine.copy(alpha = 0.42f))
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(p.tangerine, p.tangerineEnd)))
                            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                            .clickable { onExportPdf(selectedScope, selectedTerm) }
                            .padding(horizontal = 15.dp)
                            .testTag("export_reports_pdf_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PictureAsPdf,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "PDF",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Segmented Mode Switch (By Exception / By Staff)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .complianceGlassCard(50.dp)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReportsModeSegment(
                        label = "By Exception (${filteredReports.size})",
                        selected = viewMode == 0,
                        onClick = { viewMode = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reports_mode_exception")
                    )
                    ReportsModeSegment(
                        label = "By Staff (${staffAggregates.size})",
                        selected = viewMode == 1,
                        onClick = { viewMode = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reports_mode_staff")
                    )
                }
            }

            // 2. Dynamic Swipe-Up Collapsible Filter Drawer (Terms, Search, Divisions, Seen/Unseen, Date)
            AnimatedVisibility(
                visible = filtersExpanded,
                enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut() + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                var drawerDrag by remember { mutableFloatStateOf(0f) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .complianceGlassCard(22.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = { drawerDrag = 0f },
                                onDragCancel = { drawerDrag = 0f }
                            ) { _, dragAmount ->
                                drawerDrag += dragAmount
                                if (drawerDrag < -28f) {
                                    filtersExpanded = false
                                    drawerDrag = 0f
                                }
                            }
                        }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Search + System Calendar Date Filter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportsGlassSearchBar(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = "Search teacher, class, check...",
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(
                                    if (selectedDateFilter != null) p.tangerine.copy(alpha = 0.20f)
                                    else p.trackFill
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selectedDateFilter != null) p.tangerine else p.borderBottom,
                                    shape = RoundedCornerShape(15.dp)
                                )
                                .clickable {
                                    if (selectedDateFilter != null) selectedDateFilter = null
                                    else openSystemDateFilter()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selectedDateFilter != null) Icons.Rounded.Close else Icons.Rounded.CalendarMonth,
                                contentDescription = "Filter by date",
                                tint = if (selectedDateFilter != null) p.tangerine else p.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Term Selector Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("First Term", "Second Term", "Third Term", "ALL").forEach { term ->
                            GlassFilterChip(
                                label = if (term == "ALL") "All Terms" else term,
                                selected = selectedTerm == term,
                                onClick = { onSelectTerm(term) }
                            )
                        }
                    }

                    // Divisions / Scope Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportScope.entries.forEach { scope ->
                            GlassFilterChip(
                                label = scope.label,
                                selected = selectedScope == scope,
                                activeColor = p.emerald,
                                onClick = { selectedScope = scope }
                            )
                        }
                    }

                    // Seen / Unseen + Swipe-Up Collapse Handle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val statusFilterOptions = listOf(
                                "ALL" to "All",
                                "UNSEEN" to "Unseen",
                                ReportResolutionStatus.OPEN.key to ReportResolutionStatus.OPEN.label,
                                ReportResolutionStatus.PENDING.key to ReportResolutionStatus.PENDING.label,
                                ReportResolutionStatus.ACTION_REQUIRED.key to ReportResolutionStatus.ACTION_REQUIRED.label,
                                ReportResolutionStatus.ACCEPTED.key to ReportResolutionStatus.ACCEPTED.label,
                                ReportResolutionStatus.REJECTED.key to ReportResolutionStatus.REJECTED.label
                            )
                            statusFilterOptions.forEach { (k, lbl) ->
                                GlassFilterChip(
                                    label = lbl,
                                    selected = seenFilter == k,
                                    onClick = { seenFilter = k }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(p.trackFill)
                                .clickable { filtersExpanded = false }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ExpandLess,
                                contentDescription = "Collapse filters",
                                tint = p.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Hide",
                                color = p.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 3. Compact Pull-Down / Tap Pill When Filters Are Swiped Up
            AnimatedVisibility(
                visible = !filtersExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                var handleDrag by remember { mutableFloatStateOf(0f) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .complianceGlassCard(50.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = { handleDrag = 0f },
                                onDragCancel = { handleDrag = 0f }
                            ) { _, dragAmount ->
                                handleDrag += dragAmount
                                if (handleDrag > 20f) {
                                    filtersExpanded = true
                                    handleDrag = 0f
                                }
                            }
                        }
                        .clickable { filtersExpanded = true }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = null,
                            tint = p.tangerine,
                            modifier = Modifier.size(16.dp)
                        )
                        val summaryText = buildString {
                            append(if (selectedTerm == "ALL") "All Terms" else selectedTerm)
                            append(" • ")
                            append(selectedScope.label)
                            if (seenFilter != "ALL") {
                                val niceLabel = when (seenFilter) {
                                    "UNSEEN" -> "Unseen"
                                    else -> ReportResolutionStatus.fromKey(seenFilter).label
                                }
                                append(" • $niceLabel")
                            }
                            if (!selectedDateFilter.isNullOrBlank()) append(" • $selectedDateFilter")
                        }
                        Text(
                            text = summaryText,
                            color = p.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (activeFilterCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(p.tangerine.copy(alpha = 0.2f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$activeFilterCount",
                                    color = p.tangerine,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = "Expand filters",
                            tint = p.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 4. Maximum-Real-Estate Reports Feed (Exceptions or Staff)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (viewMode == 0) {
                    if (filteredReports.isEmpty()) {
                        item {
                            ReportsEmptyGlassCard(
                                title = "No Matching Reports",
                                subtitle = "Try adjusting your scope, term, date, or search filter.",
                                showReset = activeFilterCount > 0,
                                onReset = {
                                    search = ""
                                    selectedScope = ReportScope.ALL
                                    seenFilter = "ALL"
                                    selectedDateFilter = null
                                }
                            )
                        }
                    } else {
                        items(filteredReports, key = { it.id }) { report ->
                            RecentReportCard(
                                report = report,
                                resolvedDivision = AnalysisEngine.resolveDivision(report, staffMap[report.staffId?.lowercase()]),
                                onClick = { onSelectReport(report) }
                            )
                        }
                    }
                } else {
                    if (staffAggregates.isEmpty()) {
                        item {
                            ReportsEmptyGlassCard(
                                title = "No Staff Cited",
                                subtitle = "No staff exceptions recorded under the current filter.",
                                showReset = activeFilterCount > 0,
                                onReset = {
                                    search = ""
                                    selectedScope = ReportScope.ALL
                                    seenFilter = "ALL"
                                    selectedDateFilter = null
                                }
                            )
                        }
                    } else {
                        items(staffAggregates, key = { it.staffKey }) { item ->
                            val initials = remember(item.staffName) {
                                item.staffName
                                    .trim()
                                    .split(Regex("\\s+"))
                                    .filter { it.isNotBlank() }
                                    .take(2)
                                    .joinToString("") { it.first().uppercaseChar().toString() }
                                    .ifBlank { "S" }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .complianceGlassCard(24.dp)
                                    .clickable { item.reports.firstOrNull()?.let(onSelectReport) }
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(p.tangerine.copy(alpha = if (p.isDark) 0.20f else 0.14f))
                                            .border(1.dp, p.tangerine.copy(alpha = 0.45f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initials,
                                            color = p.tangerine,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${item.department.uppercase()}${if (item.staffCode.isNotBlank()) " • ${item.staffCode}" else ""}",
                                            color = p.textSecondary,
                                            fontSize = 10.sp,
                                            letterSpacing = 1.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.staffName,
                                            color = p.textPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    SeverityBadge(item.severity)
                                }

                                HorizontalDivider(thickness = 0.8.dp, color = p.borderBottom)

                                val pendingCount = item.reports.count { it.isPending }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusGlassChip(p.tangerine, "${item.exceptionCount} Total")
                                    StatusGlassChip(p.emerald, "${item.seenCount} Seen")
                                    if (pendingCount > 0) {
                                        StatusGlassChip(p.tangerine, "$pendingCount Pending")
                                    }
                                    if (item.unseenCount > 0) {
                                        StatusGlassChip(p.rose, "${item.unseenCount} Unseen")
                                    }
                                }

                                item.reports.firstOrNull()?.let { latest ->
                                    Text(
                                        text = "Latest (${latest.incidentDate}): ${latest.complianceCheck}",
                                        color = p.tangerine,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportsModeSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    Box(
        modifier = modifier
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(Brush.horizontalGradient(listOf(p.tangerine, p.tangerineEnd)))
                else Modifier.background(Color.Transparent)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else p.textPrimary,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GlassFilterChip(
    label: String,
    selected: Boolean,
    activeColor: Color = LocalCompliancePalette.current.tangerine,
    onClick: () -> Unit
) {
    val p = LocalCompliancePalette.current
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier
                        .background(activeColor)
                        .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                } else {
                    Modifier
                        .background(p.trackFill)
                        .border(1.dp, p.borderBottom, CircleShape)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else p.textPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun ReportsGlassSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(p.trackFill)
            .border(1.dp, p.borderBottom, RoundedCornerShape(15.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = p.textSecondary,
            modifier = Modifier.size(17.dp)
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = p.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = p.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(p.tangerine),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Clear search",
                tint = p.textSecondary,
                modifier = Modifier
                    .size(17.dp)
                    .clickable { onValueChange("") }
            )
        }
    }
}

@Composable
private fun ReportsEmptyGlassCard(
    title: String,
    subtitle: String,
    showReset: Boolean,
    onReset: () -> Unit
) {
    val p = LocalCompliancePalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .complianceGlassCard(26.dp)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(p.trackFill)
                .border(1.dp, p.borderTop, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                tint = p.tangerine,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(title, color = p.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = p.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (showReset) {
            TextButton(onClick = onReset) {
                Text("Reset Filters", color = p.tangerine, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}