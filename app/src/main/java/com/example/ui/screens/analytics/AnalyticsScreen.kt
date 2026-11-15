package com.example.ui.screens.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AcademicWeekEngine
import com.example.data.model.ComplianceReport
import com.example.data.model.ReportScope
import com.example.data.model.SeverityLevel
import com.example.data.model.StaffMember
import com.example.data.model.UserRole
import com.example.domain.AnalysisEngine
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.CircularRingMetricTile
import com.example.ui.components.GlassCapsuleProgressBar
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowingPrimaryButton
import com.example.ui.components.MicroLabel
import com.example.ui.components.glassFieldColors
import com.example.ui.theme.ComplianceTheme

@Composable
fun AnalyticsScreen(
    reports: List<ComplianceReport>,
    staffList: List<StaffMember>,
    selectedTerm: String,
    onSelectTerm: (String) -> Unit,
    onExportPdf: (ReportScope, String, String, String) -> Unit,
    userRole: UserRole = UserRole.ADMIN,
    currentUserId: String = ""
) {
    val ext = ComplianceTheme.extended
    val isAdmin = userRole.isAdminOrAbove
    val scopedReports = remember(reports, isAdmin, currentUserId) {
        if (isAdmin) reports
        else if (currentUserId.isNotBlank()) reports.filter { it.reportedBy.equals(currentUserId, ignoreCase = true) }
        else reports
    }
    var datePreset by remember { mutableStateOf("ALL") } // ALL | WEEK | MONTH | CUSTOM
    var customStart by remember { mutableStateOf("") }
    var customEnd by remember { mutableStateOf("") }

    val (startDate, endDate) = remember(datePreset, customStart, customEnd) {
        when (datePreset) {
            "WEEK" -> AcademicWeekEngine.currentWeekRangeBounds()
            "MONTH" -> AcademicWeekEngine.currentMonthRangeBounds()
            "CUSTOM" -> customStart to customEnd
            else -> "" to ""
        }
    }

    val summary = remember(scopedReports, staffList, selectedTerm, startDate, endDate) {
        AnalysisEngine.calculateAnalysisSummary(scopedReports, staffList, selectedTerm, startDate, endDate)
    }

    val activeRatio = if (summary.totalExceptions > 0) summary.activeCases.toFloat() / summary.totalExceptions else 0f
    val resolvedRatio = if (summary.totalExceptions > 0) summary.resolvedCases.toFloat() / summary.totalExceptions else 0f

    AtmosphericGlassBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    MicroLabel(if (isAdmin) "Directorate Intelligence" else "Personal Account Telemetry")
                    Text(
                        text = if (isAdmin) "Compliance Analysis" else "My Exception Analysis",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Frosted Term + Date Preset pills
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("First Term", "Second Term", "Third Term", "ALL").forEach { term ->
                            val selected = selectedTerm == term
                            Surface(
                                onClick = { onSelectTerm(term) },
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.primary else ext.glassBottom,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else ext.glassRimEnd)
                            ) {
                                Text(
                                    text = if (term == "ALL") "All Terms" else term,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "ALL" to "Entire Period",
                            "WEEK" to "This Week",
                            "MONTH" to "This Month",
                            "CUSTOM" to "Custom Dates"
                        ).forEach { (key, label) ->
                            val selected = datePreset == key
                            Surface(
                                onClick = { datePreset = key },
                                shape = CircleShape,
                                color = if (selected) ext.indigo else ext.glassBottom,
                                contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(1.dp, if (selected) ext.indigo else ext.glassRimEnd)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    if (datePreset == "CUSTOM") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = customStart,
                                onValueChange = { customStart = it },
                                label = { Text("From (YYYY-MM-DD)") },
                                singleLine = true,
                                colors = glassFieldColors(),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customEnd,
                                onValueChange = { customEnd = it },
                                label = { Text("To (YYYY-MM-DD)") },
                                singleLine = true,
                                colors = glassFieldColors(),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Executive Ring-Progress Summary Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        CircularRingMetricTile(
                            title = "Total Cases",
                            value = "${summary.totalExceptions}",
                            subtitle = "Filtered window",
                            progress = if (summary.totalExceptions > 0) 0.85f else 0.1f,
                            icon = Icons.Default.QueryStats,
                            ringColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        CircularRingMetricTile(
                            title = "Active",
                            value = "${summary.activeCases}",
                            subtitle = "In progress / open",
                            progress = activeRatio,
                            icon = Icons.Default.PendingActions,
                            ringColor = ext.amber,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    CircularRingMetricTile(
                        title = "Resolved Cases",
                        value = "${summary.resolvedCases}",
                        subtitle = "${(resolvedRatio * 100).toInt()}% closure rate",
                        progress = resolvedRatio,
                        icon = Icons.Default.CheckCircle,
                        ringColor = ext.emerald,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Glowing Frosted Canvas Division Bar Chart
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 18.dp) {
                    MicroLabel("Exceptions by Division")
                    Spacer(Modifier.height(14.dp))
                    val maxVal = (summary.divisionBreakdown.maxOfOrNull { it.totalReports } ?: 1).coerceAtLeast(1)
                    val colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        ext.amber,
                        ext.indigo,
                        ext.rose
                    )
                    val gridLineColor = ext.glassRimEnd

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        val guideSteps = 3
                        for (g in 1..guideSteps) {
                            val y = size.height * (g / (guideSteps + 1f))
                            drawLine(
                                color = gridLineColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        val count = summary.divisionBreakdown.size.coerceAtLeast(1)
                        val slotW = size.width / count
                        val barW = slotW * 0.42f
                        summary.divisionBreakdown.forEachIndexed { idx, div ->
                            val hRatio = div.totalReports.toFloat() / maxVal.toFloat()
                            val barH = (size.height - 16.dp.toPx()) * hRatio
                            val left = idx * slotW + (slotW - barW) / 2f
                            val top = size.height - barH
                            val barColor = colors[idx % colors.size]
                            drawRoundRect(
                                color = barColor.copy(alpha = 0.14f),
                                topLeft = Offset(left, 8.dp.toPx()),
                                size = Size(barW, size.height - 8.dp.toPx()),
                                cornerRadius = CornerRadius(18f, 18f)
                            )
                            if (barH > 0f) {
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        listOf(barColor, barColor.copy(alpha = 0.72f))
                                    ),
                                    topLeft = Offset(left, top),
                                    size = Size(barW, barH),
                                    cornerRadius = CornerRadius(18f, 18f)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        summary.divisionBreakdown.forEachIndexed { idx, div ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(8.dp).background(colors[idx % colors.size], CircleShape))
                                    Text(
                                        text = div.division.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "${div.totalReports}",
                                    style = MaterialTheme.typography.titleSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Academic Section Breakdown (hide school-wide per-head ratio for Standard users)
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 18.dp) {
                    MicroLabel(
                        if (isAdmin) "Academic Section Breakdown & Per-Head Ratio"
                        else "My Exceptions by Academic Section"
                    )
                    Spacer(Modifier.height(12.dp))
                    val maxSec = (summary.sectionBreakdowns.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        summary.sectionBreakdowns.forEach { sec ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = sec.sectionName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        if (isAdmin) "${sec.count} (${sec.perHeadRatio}/head)" else "${sec.count}",
                                        style = MaterialTheme.typography.labelMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                GlassCapsuleProgressBar(
                                    progress = sec.count.toFloat() / maxSec.toFloat(),
                                    fillColor = MaterialTheme.colorScheme.primary,
                                    height = 10.dp
                                )
                            }
                        }
                    }
                }
            }

            if (isAdmin) {
                // Discipline Per-Head Ratio (Admin / Directorate only)
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 18.dp) {
                        MicroLabel("Student Discipline Per-Head Exposure")
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(ext.roseContainer.copy(alpha = 0.65f))
                                    .border(1.dp, ext.rose.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                                    .padding(14.dp)
                            ) {
                                MicroLabel("Primary Cohort", color = ext.rose)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${summary.disciplinePrimaryCount} cases",
                                    style = MaterialTheme.typography.titleMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${summary.disciplinePrimaryRatio} per pupil",
                                    style = MaterialTheme.typography.labelSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                                    color = ext.rose
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(ext.amberContainer.copy(alpha = 0.65f))
                                    .border(1.dp, ext.amber.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                                    .padding(14.dp)
                            ) {
                                MicroLabel("Secondary Cohort", color = ext.amber)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${summary.disciplineSecondaryCount} cases",
                                    style = MaterialTheme.typography.titleMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${summary.disciplineSecondaryRatio} per student",
                                    style = MaterialTheme.typography.labelSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                                    color = ext.amber
                                )
                            }
                        }
                    }
                }

                // Derived Staff Severity Tiers (Admin / Directorate only)
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 18.dp) {
                        MicroLabel("Derived Staff Severity Distribution")
                        Spacer(Modifier.height(12.dp))
                        val tiers = listOf(
                            SeverityLevel.CRITICAL to ext.rose,
                            SeverityLevel.HIGH to Color(0xFFFF6B2C),
                            SeverityLevel.MEDIUM to ext.amber,
                            SeverityLevel.LOW to MaterialTheme.colorScheme.primary,
                            SeverityLevel.NONE to ext.emerald
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            tiers.forEach { (tier, color) ->
                                val c = summary.staffSeverityCounts[tier] ?: 0
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
                                        Text(
                                            text = tier.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        "$c staff",
                                        style = MaterialTheme.typography.titleSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                                        fontWeight = FontWeight.Bold,
                                        color = color
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Personal Most Cited Standards breakdown for Standard users
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 18.dp) {
                        MicroLabel("My Most Cited Compliance Checks")
                        Spacer(Modifier.height(12.dp))
                        if (summary.categoryBreakdowns.isEmpty()) {
                            Text(
                                text = "No exceptions logged in this filtered window.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val maxCat = (summary.categoryBreakdowns.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                summary.categoryBreakdowns.forEach { cat ->
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(
                                                text = cat.categoryName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "${cat.count}",
                                                style = MaterialTheme.typography.labelLarge.merge(TextStyle(fontFeatureSettings = "tnum")),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        GlassCapsuleProgressBar(
                                            progress = cat.count.toFloat() / maxCat.toFloat(),
                                            fillColor = MaterialTheme.colorScheme.primary,
                                            height = 8.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Official Audit PDF Export Card
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 18.dp) {
                    MicroLabel(if (isAdmin) "Audit Deliverable" else "Personal Export")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isAdmin) "Export Filtered Analysis PDF" else "Export My Exceptions PDF",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isAdmin) {
                            "Generates an official 6-column landscape PDF covering the selected term and date window."
                        } else {
                            "Generates a landscape PDF covering only your submitted exceptions for the selected term and date window."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    GlowingPrimaryButton(
                        onClick = { onExportPdf(ReportScope.ALL, selectedTerm, startDate, endDate) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("analytics_export_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate & Share PDF Report", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
