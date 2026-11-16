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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Locale
import com.example.data.model.ComplianceReport
import com.example.data.model.OfficerMetric
import com.example.data.model.OfficerSortOption
import com.example.domain.AnalysisEngine
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlassCard
import com.example.ui.components.MicroLabel
import com.example.ui.components.SearchInputBar
import com.example.ui.components.SubScreenTopRow
import com.example.ui.components.glassFieldColors
import com.example.ui.theme.ComplianceTheme

@Composable
fun OfficerAnalyticsScreen(
    reports: List<ComplianceReport>,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val ext = ComplianceTheme.extended
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(OfficerSortOption.HIGHEST_TOTAL) }

    val officers = remember(reports, fromDate, toDate, search, sortOption) {
        AnalysisEngine.calculateOfficerMetrics(
            reports = reports,
            startDate = fromDate,
            endDate = toDate,
            searchQuery = search,
            sortOption = sortOption
        )
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = fromDate,
                        onValueChange = { fromDate = it },
                        label = { Text("From (YYYY-MM-DD)") },
                        singleLine = true,
                        colors = glassFieldColors(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("officer_from_date")
                    )
                    OutlinedTextField(
                        value = toDate,
                        onValueChange = { toDate = it },
                        label = { Text("To (YYYY-MM-DD)") },
                        singleLine = true,
                        colors = glassFieldColors(),
                        shape = RoundedCornerShape(16.dp),
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
                        OfficerStatCard(officer)
                    }
                }
            }
        }
    }
}

@Composable
private fun OfficerStatCard(officer: OfficerMetric) {
    val ext = ComplianceTheme.extended
    val total = officer.totalReports.coerceAtLeast(1).toFloat()
    val seenWeight = officer.seenCount.toFloat() / total
    val unseenWeight = officer.unseenCount.toFloat() / total
    val rejectedWeight = officer.rejectedCount.toFloat() / total

    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 16.dp) {
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
            Column(horizontalAlignment = Alignment.End) {
                MicroLabel("Total")
                Text(
                    text = "${officer.totalReports}",
                    style = MaterialTheme.typography.headlineSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
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
