package com.example.ui.screens.reports

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ComplianceReport
import com.example.data.model.ReportScope
import com.example.data.model.StaffMember
import com.example.domain.AnalysisEngine
import com.example.domain.ReportExportData
import com.example.ui.components.complianceGlassCard
import com.example.ui.theme.LocalCompliancePalette
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class ExportDateBounds(
    val start: String,
    val end: String,
    val label: String
)

private data class ScopeCardItem(
    val scope: ReportScope,
    val icon: ImageVector,
    val count: Int
)

private data class ExportFeedback(
    val isError: Boolean,
    val message: String
)

@Composable
fun DownloadReportsCard(
    reports: List<ComplianceReport>,
    staffList: List<StaffMember>,
    selectedTerm: String,
    initialScope: ReportScope = ReportScope.ALL,
    onGenerateReport: suspend (
        scope: ReportScope,
        format: String,
        startDate: String,
        endDate: String,
        term: String
    ) -> Result<String>,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var isExpanded by remember { mutableStateOf(false) }
    var selectedScope by remember(initialScope) {
        mutableStateOf(
            if (initialScope in ReportExportData.officialExportScopes) initialScope else ReportScope.ALL
        )
    }
    var selectedFormat by remember { mutableStateOf("PDF") }
    var timeRangePreset by remember { mutableStateOf("ALL") }
    var customStartDate by remember { mutableStateOf("") }
    var customEndDate by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<ExportFeedback?>(null) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "export_card_chevron"
    )

    val activeDateBounds = remember(timeRangePreset, customStartDate, customEndDate, selectedTerm) {
        val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayCal = Calendar.getInstance()
        val todayStr = isoFmt.format(todayCal.time)

        when (timeRangePreset) {
            "THIS_WEEK" -> {
                val cal = Calendar.getInstance()
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val diffToMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
                cal.add(Calendar.DAY_OF_MONTH, -diffToMonday)
                ExportDateBounds(isoFmt.format(cal.time), todayStr, "This Week")
            }
            "LAST_WEEK" -> {
                val cal = Calendar.getInstance()
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val diffToMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
                cal.add(Calendar.DAY_OF_MONTH, -diffToMonday - 7)
                val lastMonday = isoFmt.format(cal.time)
                cal.add(Calendar.DAY_OF_MONTH, 6)
                val lastSunday = isoFmt.format(cal.time)
                ExportDateBounds(lastMonday, lastSunday, "Last Week")
            }
            "LAST_MONTH" -> {
                val first = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val last = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.DAY_OF_MONTH, -1)
                }
                ExportDateBounds(isoFmt.format(first.time), isoFmt.format(last.time), "Last Month")
            }
            "CUSTOM" -> {
                val lbl = if (customStartDate.isNotBlank() && customEndDate.isNotBlank()) {
                    "$customStartDate → $customEndDate"
                } else {
                    "Pick Custom Dates"
                }
                ExportDateBounds(customStartDate, customEndDate, lbl)
            }
            else -> {
                val lbl = if (selectedTerm == "ALL") "All Time" else "$selectedTerm • All"
                ExportDateBounds("", "", lbl)
            }
        }
    }

    val staffMap = remember(staffList) { AnalysisEngine.buildStaffLookup(staffList) }

    val filteredReports = remember(reports, selectedTerm, activeDateBounds) {
        AnalysisEngine.filterReports(
            reports = reports,
            term = selectedTerm,
            startDate = activeDateBounds.start,
            endDate = activeDateBounds.end
        ).filter { ReportExportData.isExportEligible(it) }
    }

    val visibleScopeItems = remember(filteredReports, staffMap) {
        val counts = mutableMapOf<String, Int>()
        filteredReports.forEach { rec ->
            val resolved = AnalysisEngine.getRecordScope(rec, staffMap)
            val normalizedKey = if (resolved == "Hostel / Kitchen") "Hostel/Kitchen" else resolved
            counts[normalizedKey] = (counts[normalizedKey] ?: 0) + 1
        }
        val allScopes = ReportExportData.officialExportScopes.map { sc ->
            val count = if (sc == ReportScope.ALL) filteredReports.size else (counts[sc.key] ?: 0)
            ScopeCardItem(sc, iconForScope(sc), count)
        }
        val nonZero = allScopes.filter { it.count > 0 }
        nonZero.ifEmpty { listOf(ScopeCardItem(ReportScope.ALL, Icons.Rounded.Layers, 0)) }
    }

    LaunchedEffect(visibleScopeItems) {
        if (visibleScopeItems.none { it.scope == selectedScope }) {
            selectedScope = visibleScopeItems.first().scope
        }
    }

    val activeScopeItem = remember(visibleScopeItems, selectedScope) {
        visibleScopeItems.firstOrNull { it.scope == selectedScope } ?: visibleScopeItems.first()
    }

    val customRangeReady = timeRangePreset != "CUSTOM" ||
        (customStartDate.isNotBlank() && customEndDate.isNotBlank() && customStartDate <= customEndDate)

    fun parseIsoMillis(iso: String): Long? {
        if (iso.length != 10) return null
        return runCatching {
            val parts = iso.split("-")
            Calendar.getInstance().apply {
                set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.getOrNull()
    }

    fun openPicker(
        currentIso: String,
        minIso: String? = null,
        maxIso: String? = null,
        onPicked: (String) -> Unit
    ) {
        val cal = Calendar.getInstance()
        runCatching {
            if (currentIso.length == 10) {
                val parts = currentIso.split("-")
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        }
        val dialog = DatePickerDialog(
            context,
            { _, y, m, d ->
                onPicked(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        minIso?.let(::parseIsoMillis)?.let { dialog.datePicker.minDate = it }
        maxIso?.let(::parseIsoMillis)?.let { dialog.datePicker.maxDate = it }
        dialog.show()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .complianceGlassCard(26.dp)
            .testTag("download_reports_card")
    ) {
        // 1. Executive Header Trigger Bar (Always Informative)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isExpanded = !isExpanded
                }
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("export_reports_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                p.tangerine.copy(alpha = 0.24f),
                                p.tangerineEnd.copy(alpha = 0.12f)
                            )
                        )
                    )
                    .border(1.dp, p.tangerine.copy(alpha = 0.45f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Export Reports",
                    tint = p.tangerine,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "DOCUMENT EXPORT STUDIO",
                    color = p.textSecondary,
                    fontSize = 9.sp,
                    letterSpacing = 1.1.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Export Reports",
                        color = p.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(p.emerald.copy(alpha = 0.14f))
                            .border(1.dp, p.emerald.copy(alpha = 0.38f), CircleShape)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = p.emerald,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "OFFICIAL",
                            color = p.emerald,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
                Text(
                    text = "${activeScopeItem.scope.label} • $selectedFormat • ${activeScopeItem.count} logs",
                    color = p.textSecondary,
                    fontSize = 12.sp,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(p.trackFill)
                    .border(1.dp, p.borderBottom, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse Export Reports" else "Expand Export Reports",
                    tint = p.textPrimary,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(chevronRotation)
                )
            }
        }

        // 2. Expandable 3-Stage Export Workbench
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut() + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HorizontalDivider(thickness = 0.8.dp, color = p.borderBottom)

                // STAGE 01: FORMAT & TIMEFRAME
                SectionStageHeader(step = "01", title = "FORMAT & PERIOD")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.trackFill)
                        .border(1.dp, p.borderBottom, RoundedCornerShape(18.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FormatOptionPill(
                        label = "PDF",
                        subtitle = "Official Print",
                        icon = Icons.Rounded.PictureAsPdf,
                        selected = selectedFormat == "PDF",
                        activeColor = p.rose,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedFormat = "PDF"
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_format_pdf")
                    )
                    FormatOptionPill(
                        label = "DOCX",
                        subtitle = "Editable Word",
                        icon = Icons.Rounded.Description,
                        selected = selectedFormat == "DOCX",
                        activeColor = p.tangerine,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedFormat = "DOCX"
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_format_docx")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to "All Time",
                        "THIS_WEEK" to "This Week",
                        "LAST_WEEK" to "Last Week",
                        "LAST_MONTH" to "Last Month",
                        "CUSTOM" to "Custom"
                    ).forEach { (id, label) ->
                        val active = timeRangePreset == id
                        val bgColor by animateColorAsState(
                            targetValue = if (active) p.tangerine.copy(alpha = 0.22f) else p.trackFill,
                            label = "range_bg"
                        )
                        val borderColor by animateColorAsState(
                            targetValue = if (active) p.tangerine else p.borderBottom,
                            label = "range_border"
                        )
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(bgColor)
                                .border(1.dp, borderColor, CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    timeRangePreset = id
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                                .testTag("export_range_$id"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (active) p.tangerine else p.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = timeRangePreset == "CUSTOM",
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExportDateChip(
                                label = "FROM",
                                value = customStartDate,
                                onClick = {
                                    openPicker(
                                        currentIso = customStartDate,
                                        maxIso = customEndDate.takeIf { it.isNotBlank() }
                                    ) { picked ->
                                        customStartDate = picked
                                        if (customEndDate.isNotBlank() && customEndDate < picked) {
                                            customEndDate = picked
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_custom_start")
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = p.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            ExportDateChip(
                                label = "TO",
                                value = customEndDate,
                                onClick = {
                                    openPicker(
                                        currentIso = customEndDate.ifBlank { customStartDate },
                                        minIso = customStartDate.takeIf { it.isNotBlank() }
                                    ) { picked ->
                                        customEndDate = picked
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_custom_end")
                            )
                        }
                        if (!customRangeReady) {
                            Text(
                                text = "Select both start and end dates from the calendar to enable export.",
                                color = p.tangerine,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // STAGE 02: SCOPE & DIVISIONS GRID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionStageHeader(step = "02", title = "DIVISIONS & SCOPE")
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(p.trackFill)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${visibleScopeItems.size} active",
                            color = p.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleScopeItems.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { item ->
                                ExportScopeTile(
                                    item = item,
                                    selected = selectedScope == item.scope,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedScope = item.scope
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("export_scope_${item.scope.name.lowercase()}")
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // STAGE 03: DISPATCH CONSOLE & PRIMARY CTA
                SectionStageHeader(step = "03", title = "DISPATCH SUMMARY")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(p.glassBottom)
                        .border(1.dp, if (p.isDark) Color.White.copy(alpha = 0.12f) else p.borderBottom, RoundedCornerShape(20.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ParamChip(
                            label = "Format",
                            value = selectedFormat,
                            accent = if (selectedFormat == "PDF") p.rose else p.tangerine,
                            modifier = Modifier.weight(0.85f)
                        )
                        ParamChip(
                            label = "Records",
                            value = "${activeScopeItem.count} logs",
                            accent = if (activeScopeItem.count > 0) p.emerald else p.textSecondary,
                            modifier = Modifier.weight(1.15f)
                        )
                    }
                    ParamChip(
                        label = "Period",
                        value = activeDateBounds.label,
                        accent = p.textPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val ctaEnabled = !isGenerating && customRangeReady
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .alpha(if (ctaEnabled || isGenerating) 1f else 0.5f)
                            .shadow(
                                14.dp,
                                RoundedCornerShape(16.dp),
                                spotColor = p.tangerine.copy(alpha = 0.40f)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.horizontalGradient(listOf(p.tangerine, p.tangerineEnd)))
                            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                            .clickable(enabled = ctaEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isGenerating = true
                                feedback = null
                                coroutineScope.launch {
                                    val res = onGenerateReport(
                                        selectedScope,
                                        selectedFormat,
                                        activeDateBounds.start,
                                        activeDateBounds.end,
                                        selectedTerm
                                    )
                                    res.onSuccess { msg ->
                                        feedback = ExportFeedback(isError = false, message = msg)
                                    }.onFailure { err ->
                                        feedback = ExportFeedback(
                                            isError = true,
                                            message = err.message ?: "Report generation failed."
                                        )
                                    }
                                    isGenerating = false
                                }
                            }
                            .testTag("export_generate_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Generating $selectedFormat...",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Generate Official $selectedFormat",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Inline Dismissible Success / Error Feedback Banner
                AnimatedVisibility(visible = feedback != null) {
                    feedback?.let { fb ->
                        val accent = if (fb.isError) p.rose else p.emerald
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(accent.copy(alpha = 0.14f))
                                .border(1.dp, accent.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("export_feedback_banner"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (fb.isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Check,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = fb.message,
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss feedback",
                                tint = accent.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { feedback = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionStageHeader(step: String, title: String) {
    val p = LocalCompliancePalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(p.tangerine.copy(alpha = 0.16f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = step,
                color = p.tangerine,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
        Text(
            text = title,
            color = p.textSecondary,
            fontSize = 10.sp,
            letterSpacing = 1.1.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FormatOptionPill(
    label: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    val bgColor by animateColorAsState(
        targetValue = if (selected) activeColor.copy(alpha = 0.18f) else Color.Transparent,
        label = "fmt_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) activeColor.copy(alpha = 0.48f) else Color.Transparent,
        label = "fmt_border"
    )
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) activeColor else p.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                color = if (selected) activeColor else p.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 14.sp
            )
            Text(
                text = subtitle,
                color = p.textSecondary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ExportDateChip(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(p.trackFill)
            .border(
                1.dp,
                if (value.isNotBlank()) p.tangerine.copy(alpha = 0.65f) else p.borderBottom,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = p.textSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = value.ifBlank { "Select" },
                color = if (value.isNotBlank()) p.textPrimary else p.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(fontFeatureSettings = "tnum"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Rounded.CalendarMonth,
            contentDescription = "Select $label date",
            tint = p.tangerine,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ExportScopeTile(
    item: ScopeCardItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    val accent = p.tangerine
    val bgColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.16f) else p.glassBottom,
        label = "scope_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent else if (p.isDark) Color.White.copy(alpha = 0.12f) else p.borderBottom,
        label = "scope_border"
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) accent else p.trackFill),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.scope.label,
                    tint = if (selected) Color.White else p.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (selected) accent else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (selected) accent else p.borderBottom,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.scope.label,
                color = p.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (selected) accent.copy(alpha = 0.20f)
                        else p.trackFill
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${item.count} logs",
                    color = if (selected) accent else p.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }
    }
}

@Composable
private fun ParamChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(p.glassBottom)
            .border(1.dp, p.borderBottom, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = p.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = value,
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun iconForScope(scope: ReportScope): ImageVector = when (scope) {
    ReportScope.JUNIOR_SECONDARY, ReportScope.SENIOR_SECONDARY -> Icons.Rounded.School
    ReportScope.JUNIOR_PRIMARY, ReportScope.SENIOR_PRIMARY -> Icons.Rounded.MenuBook
    ReportScope.NURSERY -> Icons.Rounded.ChildCare
    ReportScope.MENTORSHIP -> Icons.Rounded.Groups
    ReportScope.DISCIPLINE -> Icons.Rounded.Gavel
    ReportScope.MAINTENANCE -> Icons.Rounded.Build
    ReportScope.SECURITY -> Icons.Rounded.Security
    ReportScope.TRANSPORT -> Icons.Rounded.DirectionsBus
    ReportScope.MINDERS -> Icons.Rounded.Visibility
    ReportScope.HOSTEL_KITCHEN -> Icons.Rounded.Restaurant
    ReportScope.CDS -> Icons.Rounded.Dns
    ReportScope.ALL, ReportScope.ACADEMIC, ReportScope.OPERATIONS -> Icons.Rounded.Layers
}