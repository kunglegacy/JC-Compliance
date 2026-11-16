package com.example.ui.screens.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.local.AcademicWeekEngine
import com.example.data.model.ComplianceDivision
import com.example.data.model.ComplianceReport
import com.example.data.model.ReportResolutionStatus
import com.example.data.model.StaffMember
import com.example.data.model.SystemSettings
import com.example.data.model.ThemePreference
import com.example.data.model.UserRole
import com.example.domain.AnalysisEngine
import com.example.ui.components.AuditScoreHeroCard
import com.example.ui.components.CircularRingMetricTile
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.EvidenceThumbItem
import com.example.ui.components.GlassCircleButton
import com.example.ui.components.LivingAtmosphericBackground
import com.example.ui.components.MicroLabel
import com.example.ui.components.ResolutionBadge
import com.example.ui.components.SchoolCrestBadge
import com.example.ui.components.SeenBadge
import com.example.ui.components.SiteMapEvidenceCard
import com.example.ui.components.StatusGlassChip
import com.example.ui.components.complianceGlassCard
import com.example.ui.screens.reports.EvidencePreviewItem
import com.example.ui.screens.reports.EvidenceViewerDialog
import com.example.ui.theme.ComplianceTheme
import com.example.ui.theme.JCBestTheme
import com.example.ui.theme.LocalCompliancePalette

@Composable
fun OverviewScreen(
    displayName: String,
    reports: List<ComplianceReport>,
    staffList: List<StaffMember>,
    settings: SystemSettings,
    selectedTerm: String,
    isRefreshing: Boolean,
    onSelectTerm: (String) -> Unit,
    onRefresh: () -> Unit,
    onNewReport: () -> Unit,
    onSelectReport: (ComplianceReport) -> Unit,
    onNavigateToDivision: (ComplianceDivision) -> Unit,
    userRole: UserRole = UserRole.ADMIN,
    currentUserId: String = "",
    onResolveSignedUrl: (suspend (String) -> Result<String>)? = null
) {
    val p = LocalCompliancePalette.current
    val ext = ComplianceTheme.extended
    val isAdmin = userRole.isAdminOrAbove
    val scopedReports = remember(reports, isAdmin, currentUserId) {
        if (isAdmin) reports
        else if (currentUserId.isNotBlank()) reports.filter { it.reportedBy.equals(currentUserId, ignoreCase = true) }
        else reports
    }
    val staffMap = remember(staffList) { AnalysisEngine.buildStaffLookup(staffList) }
    val termReports = remember(scopedReports, selectedTerm) {
        AnalysisEngine.filterReports(scopedReports, term = selectedTerm)
    }
    val weekStatus = remember(settings, selectedTerm) {
        AcademicWeekEngine.calculateAcademicWeek(settings, selectedTerm)
    }
    val summary = remember(scopedReports, staffList, selectedTerm) {
        AnalysisEngine.calculateAnalysisSummary(scopedReports, staffList, selectedTerm, "", "")
    }
    val staffAffectedCount = remember(termReports, staffList) {
        AnalysisEngine.calculateStaffAggregates(termReports, staffList).size
    }

    val actualTotal = termReports.size
    val actualSeen = remember(termReports) { termReports.count { it.isSeen } }
    val actualUnseen = remember(termReports) { termReports.count { !it.isSeen } }

    val reviewRatio = remember(actualTotal, actualSeen) {
        if (actualTotal > 0) actualSeen.toFloat() / actualTotal.toFloat() else 0f
    }
    val reviewPct = (reviewRatio * 100).toInt()
    val pendingRatio = remember(actualTotal, actualUnseen) {
        if (actualTotal > 0) actualUnseen.toFloat() / actualTotal.toFloat() else 0f
    }
    val activeRatio = remember(actualTotal, summary.activeCases) {
        if (actualTotal > 0) summary.activeCases.toFloat() / actualTotal.toFloat() else 0f
    }
    val staffRatio = remember(staffList.size, staffAffectedCount) {
        if (staffList.isNotEmpty()) (staffAffectedCount.toFloat() / staffList.size.toFloat()).coerceIn(0f, 1f) else 0.25f
    }

    LivingAtmosphericBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Compact greeting header row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SchoolCrestBadge(size = 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Welcome back,",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textSecondary
                        )
                        Text(
                            text = displayName.ifBlank { "Officer" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = p.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassCircleButton(
                            icon = Icons.Default.Refresh,
                            contentDescription = "Refresh Data",
                            onClick = { if (!isRefreshing) onRefresh() },
                            modifier = Modifier.testTag("overview_refresh_button")
                        )
                        GlassCircleButton(
                            icon = Icons.Default.Add,
                            contentDescription = "New Exception Report",
                            onClick = onNewReport,
                            tint = p.tangerine,
                            modifier = Modifier.testTag("overview_new_report_fab")
                        )
                    }
                }
            }

            // Frosted Glass Audit Score Hero Card (Electric Tangerine -> Signal Emerald arc)
            item {
                val termLabel = if (selectedTerm == "ALL") {
                    "All Terms Archive • ${settings.academicSession}"
                } else {
                    "$selectedTerm • ${weekStatus.label}"
                }
                AuditScoreHeroCard(
                    siteName = termLabel,
                    scorePercent = reviewPct,
                    passed = actualSeen,
                    flagged = actualUnseen,
                    eyebrow = if (isAdmin) "AUDIT COMPLIANCE SCORE" else "MY ACCOUNT EXCEPTION SCORE"
                )
            }

            // Term Switcher Pills
            item {
                val terms = listOf("First Term", "Second Term", "Third Term", "ALL")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    terms.forEach { term ->
                        val selected = selectedTerm == term
                        val label = if (term == "ALL") "All Terms" else term
                        Surface(
                            onClick = { onSelectTerm(term) },
                            shape = CircleShape,
                            color = if (selected) p.tangerine else p.trackFill,
                            contentColor = if (selected) Color.White else p.textPrimary,
                            border = BorderStroke(
                                1.dp,
                                if (selected) p.tangerine else p.borderBottom
                            ),
                            modifier = Modifier.testTag("overview_term_${term.lowercase().replace(" ", "_")}")
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else p.textPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // Circular Glass Division Icon Pills Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MicroLabel(if (isAdmin) "Divisional Quick Filter" else "My Exceptions by Division")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        summary.divisionBreakdown.forEach { divSummary ->
                            DivisionCirclePill(
                                division = divSummary.division,
                                count = divSummary.totalReports,
                                onClick = { onNavigateToDivision(divSummary.division) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 2x2 Grid of Bento Circular-Ring Progress Metric Tiles
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        CircularRingMetricTile(
                            title = if (isAdmin) "Exceptions" else "My Exceptions",
                            value = "${summary.totalExceptions}",
                            subtitle = if (selectedTerm == "ALL") "All sessions" else selectedTerm,
                            progress = if (summary.totalExceptions > 0) 0.85f else 0.12f,
                            icon = Icons.Default.ReportProblem,
                            ringColor = p.tangerine,
                            modifier = Modifier.weight(1f)
                        )
                        if (isAdmin) {
                            CircularRingMetricTile(
                                title = "Staff Cited",
                                value = "$staffAffectedCount",
                                subtitle = "Active roster scope",
                                progress = staffRatio,
                                icon = Icons.Default.Groups,
                                ringColor = ext.indigo,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            CircularRingMetricTile(
                                title = "Open Cases",
                                value = "${summary.activeCases}",
                                subtitle = "Awaiting resolution",
                                progress = activeRatio,
                                icon = Icons.Default.Groups,
                                ringColor = ext.indigo,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        CircularRingMetricTile(
                            title = "Reviewed",
                            value = "$actualSeen",
                            subtitle = "$reviewPct% verified",
                            progress = reviewRatio,
                            icon = Icons.Default.CheckCircle,
                            ringColor = p.emerald,
                            modifier = Modifier.weight(1f)
                        )
                        CircularRingMetricTile(
                            title = "Pending",
                            value = "$actualUnseen",
                            subtitle = "Unseen exceptions",
                            progress = pendingRatio,
                            icon = Icons.Default.HourglassTop,
                            ringColor = p.rose,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DivisionCirclePill(
    division: ComplianceDivision,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    val ext = ComplianceTheme.extended
    val (icon: ImageVector, tint: Color) = when (division) {
        ComplianceDivision.ACADEMIC -> Icons.Default.MenuBook to p.tangerine
        ComplianceDivision.OPERATIONS -> Icons.Default.Build to ext.amber
        ComplianceDivision.CDS -> Icons.Default.Computer to ext.indigo
        ComplianceDivision.DISCIPLINE -> Icons.Default.Gavel to p.rose
    }

    Column(
        modifier = modifier
            .complianceGlassCard(20.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp)
            .testTag("overview_division_${division.key}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = if (p.isDark) 0.22f else 0.14f))
                .border(1.dp, tint.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = division.label, tint = tint, modifier = Modifier.size(18.dp))
        }
        Text(
            text = division.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = p.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
            fontWeight = FontWeight.ExtraBold,
            color = tint
        )
    }
}

@Composable
fun RecentReportCard(
    report: ComplianceReport,
    resolvedDivision: ComplianceDivision,
    onClick: () -> Unit
) {
    val p = LocalCompliancePalette.current
    val hasMedia = !report.evidenceUrl.isNullOrBlank() && report.evidenceType != "none"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .complianceGlassCard(22.dp)
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag("report_card_${report.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MicroLabel("${resolvedDivision.label} • ${report.incidentDate}")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SeenBadge(report.isSeen)
                ResolutionBadge(report.status)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = (report.staffName ?: report.assignedLocation ?: report.division).ifBlank { "Exception Record" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = p.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = report.complianceCheck,
            style = MaterialTheme.typography.labelMedium,
            color = p.tangerine,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (report.exceptionText.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = report.exceptionText,
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = listOfNotNull(report.className, report.subject).joinToString(" • ").ifBlank { report.category },
                style = MaterialTheme.typography.labelSmall,
                color = p.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (hasMedia) {
                Spacer(Modifier.width(6.dp))
                StatusGlassChip(p.tangerine, report.evidenceType.uppercase())
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "By ${report.reporterName.ifBlank { "Officer" }}",
                style = MaterialTheme.typography.labelSmall,
                color = p.textSecondary
            )
        }
    }
}

@Preview(name = "Overview Smoked Obsidian", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewOverviewDark() {
    JCBestTheme(themePreference = ThemePreference.DARK) {
        OverviewScreen(
            displayName = "A. Bello",
            reports = listOf(
                ComplianceReport(
                    id = "r1",
                    reportNumber = "CMP-2026-001",
                    reportedBy = "u1",
                    reporterName = "A. Bello",
                    reporterRole = "Officer",
                    incidentDate = "2026-02-14",
                    weekNumber = 6,
                    term = "First Term",
                    staffName = "Block B Fire Corridor",
                    exceptionText = "Extinguisher inspection seal overdue in Chemistry lab wing.",
                    effectText = "Elevated safety audit exposure.",
                    category = "Operations",
                    division = "Operation",
                    targetType = "division",
                    complianceCheck = "Fire Safety Seal Check",
                    evidenceUrl = "evidence/fire_exit.mp4",
                    evidenceType = "video",
                    status = ReportResolutionStatus.OPEN,
                    isSeen = true
                )
            ),
            staffList = emptyList(),
            settings = SystemSettings(),
            selectedTerm = "First Term",
            isRefreshing = false,
            onSelectTerm = {},
            onRefresh = {},
            onNewReport = {},
            onSelectReport = {},
            onNavigateToDivision = {}
        )
    }
}

@Preview(name = "Overview Frosted Alabaster", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun PreviewOverviewLight() {
    JCBestTheme(themePreference = ThemePreference.LIGHT) {
        OverviewScreen(
            displayName = "C. Okafor",
            reports = listOf(
                ComplianceReport(
                    id = "r2",
                    reportNumber = "CMP-2026-002",
                    reportedBy = "u2",
                    reporterName = "C. Okafor",
                    reporterRole = "Officer",
                    incidentDate = "2026-02-14",
                    weekNumber = 6,
                    term = "First Term",
                    staffName = "J. Adeyemi",
                    className = "JSS 2 Gold",
                    subject = "Basic Science",
                    exceptionText = "Lesson plan submission delayed past Monday 08:00 cutoff.",
                    effectText = "Departmental review delayed.",
                    category = "Academic",
                    division = "Academic",
                    targetType = "staff",
                    complianceCheck = "Weekly Lesson Note Submission",
                    evidenceUrl = "evidence/note_log.jpg",
                    evidenceType = "image",
                    status = ReportResolutionStatus.IN_REVIEW,
                    isSeen = false
                )
            ),
            staffList = emptyList(),
            settings = SystemSettings(),
            selectedTerm = "First Term",
            isRefreshing = false,
            onSelectTerm = {},
            onRefresh = {},
            onNewReport = {},
            onSelectReport = {},
            onNavigateToDivision = {}
        )
    }
}
