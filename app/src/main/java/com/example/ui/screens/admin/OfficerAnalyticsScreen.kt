package com.example.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Locale
import com.example.data.model.ComplianceReport
import com.example.data.model.OfficerMetric
import com.example.data.model.OfficerSortOption
import com.example.data.model.ReportResolutionStatus
import com.example.domain.AnalysisEngine
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.EditedBadgePill
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlassCard
import com.example.ui.components.MicroLabel
import com.example.ui.components.ResolutionBadge
import com.example.ui.components.SearchInputBar
import com.example.ui.components.SeenBadge
import com.example.ui.components.SubScreenTopRow
import com.example.ui.theme.ComplianceTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficerAnalyticsScreen(
    reports: List<ComplianceReport>,
    onSelectReport: (ComplianceReport) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val ext = ComplianceTheme.extended
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(OfficerSortOption.HIGHEST_TOTAL) }
    var selectedOfficerKey by remember { mutableStateOf<String?>(null) }

    val officers = remember(reports, fromDate, toDate, search, sortOption) {
        AnalysisEngine.calculateOfficerMetrics(
            reports = reports,
            startDate = fromDate,
            endDate = toDate,
            searchQuery = search,
            sortOption = sortOption
        )
    }
    val selectedOfficer = remember(officers, selectedOfficerKey) {
        selectedOfficerKey?.let { key -> officers.firstOrNull { it.officerKey == key } }
    }

    AtmosphericGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            SubScreenTopRow(
                title = "Reporting Statistics",
                subtitle = "Officer submission telemetry",
                onBack = onBack
            )

            val context = LocalContext.current

            fun openDateFilterPicker(initial: String, onPicked: (String) -> Unit) {
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OfficerCalendarFilterField(
                        label = "FROM DATE",
                        value = fromDate,
                        onOpenPicker = { openDateFilterPicker(fromDate) { fromDate = it } },
                        onClear = { fromDate = "" },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("officer_from_date")
                    )
                    OfficerCalendarFilterField(
                        label = "TO DATE",
                        value = toDate,
                        onOpenPicker = { openDateFilterPicker(toDate) { toDate = it } },
                        onClear = { toDate = "" },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("officer_to_date")
                    )
                }

                SearchInputBar(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = "Search officer name or email...",
                    testTag = "officer_search_input"
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OfficerSortOption.entries.forEach { opt ->
                        val selected = sortOption == opt
                        Surface(
                            onClick = { sortOption = opt },
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primary else ext.glassBottom,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else ext.glassRimEnd)
                        ) {
                            Text(
                                text = opt.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (officers.isEmpty()) {
                    item {
                        EmptyStatePanel(
                            icon = Icons.Default.Assessment,
                            title = "No Officer Records",
                            subtitle = "No reporting submissions found matching the date range."
                        )
                    }
                } else {
                    items(officers, key = { it.officerKey }) { officer ->
                        OfficerStatCard(
                            officer = officer,
                            onClick = { selectedOfficerKey = officer.officerKey }
                        )
                    }
                }
            }
        }
    }

    selectedOfficer?.let { officer ->
        OfficerExceptionsDrillDownSheet(
            officer = officer,
            fromDate = fromDate,
            toDate = toDate,
            onDismiss = { selectedOfficerKey = null },
            onSelectReport = { report ->
                selectedOfficerKey = null
                onSelectReport(report)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfficerExceptionsDrillDownSheet(
    officer: OfficerMetric,
    fromDate: String,
    toDate: String,
    onDismiss: () -> Unit,
    onSelectReport: (ComplianceReport) -> Unit
) {
    val ext = ComplianceTheme.extended
    var statusFilter by remember(officer.officerKey) { mutableStateOf("ALL") }

    val dateScopeLabel = remember(fromDate, toDate) {
        when {
            fromDate.isNotBlank() && toDate.isNotBlank() -> "$fromDate to $toDate"
            fromDate.isNotBlank() -> "From $fromDate"
            toDate.isNotBlank() -> "Up to $toDate"
            else -> "All Dates"
        }
    }

    val filteredReports = remember(officer.reports, statusFilter) {
        when (statusFilter) {
            "SEEN" -> officer.reports.filter { it.isSeen }
            "UNSEEN" -> officer.reports.filter { !it.isSeen }
            "REJECTED" -> officer.reports.filter { it.status == ReportResolutionStatus.REJECTED }
            else -> officer.reports
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (ext.isDark) Color(0xFF131720) else Color(0xFFF4F7FB),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MicroLabel("Directorate Submissions • $dateScopeLabel")
                    Text(
                        text = officer.officerName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (officer.officerEmail.isNotBlank()) {
                        Text(
                            text = officer.officerEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    MicroLabel("Filtered")
                    Text(
                        text = "${filteredReports.size}",
                        style = MaterialTheme.typography.headlineSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "All (${officer.totalReports})",
                    "SEEN" to "Seen (${officer.seenCount})",
                    "UNSEEN" to "Unseen (${officer.unseenCount})",
                    "REJECTED" to "Rejected (${officer.rejectedCount})"
                ).forEach { (key, label) ->
                    val selected = statusFilter == key
                    Surface(
                        onClick = { statusFilter = key },
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primary else ext.glassBottom,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else ext.glassRimEnd),
                        modifier = Modifier.testTag("officer_drilldown_filter_${key.lowercase()}")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filteredReports.isEmpty()) {
                    item {
                        EmptyStatePanel(
                            icon = Icons.Default.Assessment,
                            title = "No Exceptions in Filter",
                            subtitle = "No submissions match this status within $dateScopeLabel."
                        )
                    }
                } else {
                    items(filteredReports, key = { it.id }) { report ->
                        GlassCard(
                            onClick = { onSelectReport(report) },
                            cornerRadius = 20.dp,
                            contentPadding = 14.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("officer_exception_row_${report.id}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MicroLabel("${report.incidentDate} • ${report.division}")
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (report.isEdited) EditedBadgePill()
                                    SeenBadge(isSeen = report.isSeen, isPending = report.isPending)
                                    ResolutionBadge(report.status)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = report.complianceCheck.ifBlank { report.category.ifBlank { "Compliance Exception" } },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val targetLabel = listOfNotNull(
                                report.staffName?.takeIf { it.isNotBlank() },
                                report.className?.takeIf { it.isNotBlank() },
                                report.subject?.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")
                            if (targetLabel.isNotBlank()) {
                                Text(
                                    text = targetLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (report.exceptionText.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = report.exceptionText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
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

@Composable
private fun OfficerStatCard(
    officer: OfficerMetric,
    onClick: () -> Unit
) {
    val ext = ComplianceTheme.extended
    val total = officer.totalReports.coerceAtLeast(1).toFloat()
    val seenWeight = officer.seenCount.toFloat() / total
    val unseenWeight = officer.unseenCount.toFloat() / total
    val rejectedWeight = officer.rejectedCount.toFloat() / total

    GlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("officer_stat_card_${officer.officerKey}"),
        cornerRadius = 24.dp,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = officer.officerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (officer.officerEmail.isNotBlank()) {
                    Text(
                        text = officer.officerEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    MicroLabel("Total")
                    Text(
                        text = "${officer.totalReports}",
                        style = MaterialTheme.typography.headlineSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View ${officer.officerName} exceptions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Segmented glass ratio capsule bar (Seen emerald / Unseen amber / Rejected rose)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                .border(0.8.dp, ext.glassRimEnd, CircleShape)
        ) {
            if (seenWeight > 0f) {
                Box(
                    modifier = Modifier
                        .weight(seenWeight)
                        .fillMaxHeight()
                        .background(ext.emerald)
                )
            }
            if (unseenWeight > 0f) {
                Box(
                    modifier = Modifier
                        .weight(unseenWeight)
                        .fillMaxHeight()
                        .background(ext.amber)
                )
            }
            if (rejectedWeight > 0f) {
                Box(
                    modifier = Modifier
                        .weight(rejectedWeight)
                        .fillMaxHeight()
                        .background(ext.rose)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricMiniStat("Seen", officer.seenCount, ext.emerald)
            MetricMiniStat("Unseen", officer.unseenCount, ext.amber)
            MetricMiniStat("Rejected", officer.rejectedCount, ext.rose)
        }
    }
}

@Composable
private fun MetricMiniStat(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MicroLabel(label, color = color)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}

@Composable
private fun OfficerCalendarFilterField(
    label: String,
    value: String,
    onOpenPicker: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = ComplianceTheme.extended
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ext.glassBottom)
            .border(1.dp, if (value.isNotBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else ext.glassRimEnd, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenPicker)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value.ifBlank { "Select date" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (value.isNotBlank()) FontWeight.Bold else FontWeight.Medium,
                color = if (value.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (value.isNotBlank()) {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            IconButton(onClick = onOpenPicker) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Pick $label",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
