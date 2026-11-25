package com.example.ui.screens.analytics

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.data.model.AuthoritativeGrandTotals
import com.example.data.model.CategorySectionMatrix
import com.example.data.model.DrillDownAnalytics
import com.example.data.model.DrillDownContext
import com.example.data.model.ReportScope
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowingPrimaryButton
import com.example.ui.components.MicroLabel
import com.example.ui.theme.ComplianceTheme

@Composable
fun CdsDivisionAnalysisPanel(
    grandTotals: AuthoritativeGrandTotals,
    onDrillCds: () -> Unit
) {
    val ext = ComplianceTheme.extended
    val p = ComplianceTheme.palette

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 16.dp,
        onClick = onDrillCds
    ) {
        AnalyticsSectionHeader(
            icon = Icons.Default.VolunteerActivism,
            tint = ext.indigo,
            eyebrow = "Separate Infrastructure Domain",
            title = "Community Development Service (CDS)",
            subtitle = "Excluded from active staff severity KPI denominator; tracked separately."
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HighlightGlassMiniCard("Total CDS", "${grandTotals.cdsExceptions}", "Institutional logs", ext.indigo, Modifier.weight(1f))
            HighlightGlassMiniCard("Reviewed", "${grandTotals.seenCdsCount}", "Seen logs", ext.emerald, Modifier.weight(1f))
            HighlightGlassMiniCard("Unseen", "${grandTotals.unseenCdsCount}", "Awaiting check", ext.amber, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(p.trackFill)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Tap card to inspect CDS division distribution & timeline breakdown.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MultidimensionalMatrixSection(
    matrix: CategorySectionMatrix,
    activeTerm: String,
    onDrillCategory: (String) -> Unit
) {
    val ext = ComplianceTheme.extended
    val p = ComplianceTheme.palette
    val primary = MaterialTheme.colorScheme.primary
    val hScroll = rememberScrollState()

    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, contentPadding = 16.dp) {
        AnalyticsSectionHeader(
            icon = Icons.Default.GridOn,
            tint = primary,
            eyebrow = "Slide 5 Multidimensional Matrix",
            title = "Exception Category × Section Matrix",
            subtitle = "Cross-sectional matrix for $activeTerm (tap any row for category drill-down)."
        )
        Spacer(Modifier.height(12.dp))

        if (matrix.rows.isEmpty()) {
            Text(
                text = "No academic multidimensional matrix data recorded for this period.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@GlassCard
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(analyticsSubCardFill())
                .border(1.dp, ext.glassRimEnd, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.width(146.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(ext.glassTop)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "CATEGORY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                matrix.rows.forEach { row ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .border(0.5.dp, ext.glassRimEnd.copy(alpha = 0.45f))
                            .clickable { onDrillCategory(row.category) }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = row.category,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .background(primary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "COLUMN TOTALS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = primary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(hScroll)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .background(ext.glassTop)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MatrixCellHeader("JNR COLL")
                        MatrixCellHeader("SNR COLL")
                        MatrixCellHeader("JNR PRI")
                        MatrixCellHeader("SNR PRI")
                        MatrixCellHeader("NURSERY")
                        MatrixCellHeader("TOTAL", accent = primary)
                    }

                    matrix.rows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .height(42.dp)
                                .border(0.5.dp, ext.glassRimEnd.copy(alpha = 0.45f))
                                .clickable { onDrillCategory(row.category) }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MatrixNumberCell(row.jnrColl)
                            MatrixNumberCell(row.snrCol)
                            MatrixNumberCell(row.jnrPri)
                            MatrixNumberCell(row.snrPri)
                            MatrixNumberCell(row.nur)
                            MatrixNumberCell(row.rowTotal, bold = true, accent = primary)
                        }
                    }

                    val t = matrix.columnTotals
                    Row(
                        modifier = Modifier
                            .height(42.dp)
                            .background(primary.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MatrixNumberCell(t.jnrColl, bold = true)
                        MatrixNumberCell(t.snrCol, bold = true)
                        MatrixNumberCell(t.jnrPri, bold = true)
                        MatrixNumberCell(t.snrPri, bold = true)
                        MatrixNumberCell(t.nur, bold = true)
                        MatrixNumberCell(t.grandTotal, bold = true, accent = primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixCellHeader(text: String, accent: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        fontWeight = FontWeight.ExtraBold,
        color = accent,
        textAlign = TextAlign.End,
        modifier = Modifier.width(66.dp)
    )
}

@Composable
private fun MatrixNumberCell(value: Int, bold: Boolean = false, accent: Color? = null) {
    val color = when {
        accent != null -> accent
        value == 0 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = "$value",
        style = MaterialTheme.typography.bodySmall.merge(TextStyle(fontFeatureSettings = "tnum")),
        fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium,
        color = color,
        textAlign = TextAlign.End,
        modifier = Modifier.width(66.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisDrillDownDialog(
    context: DrillDownContext,
    analytics: DrillDownAnalytics,
    onDismiss: () -> Unit
) {
    val ext = ComplianceTheme.extended
    val p = ComplianceTheme.palette
    val primary = MaterialTheme.colorScheme.primary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ApplyModalWindowBlur()
        val modalShape = RoundedCornerShape(26.dp)
        val modalGlassTop = if (p.isDark) Color(0xFF151A25).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.94f)
        val modalGlassBottom = if (p.isDark) Color(0xFF0E121A).copy(alpha = 0.93f) else Color(0xFFF2F6FC).copy(alpha = 0.92f)
        CompositionLocalProvider(LocalContentColor provides p.textPrimary) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .widthIn(max = 560.dp)
                    .heightIn(max = 680.dp)
                    .shadow(24.dp, modalShape, ambientColor = p.shadow, spotColor = p.shadow)
                    .clip(modalShape)
                    .background(Brush.verticalGradient(listOf(modalGlassTop, modalGlassBottom)), modalShape)
                    .border(
                        1.dp,
                        Brush.verticalGradient(listOf(p.borderTop, p.borderBottom)),
                        modalShape
                    )
                    .padding(18.dp)
                    .testTag("analytics_drilldown_dialog")
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MicroLabel("Pure Aggregate Drill-Down • No Case Narratives", color = primary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = context.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (context.subtitle.isNotBlank()) {
                        Text(
                            text = context.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("drilldown_close_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close drill-down")
                }
            }

            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HighlightGlassMiniCard("Volume", "${analytics.total}", "Exceptions", primary, Modifier.weight(1f))
                    HighlightGlassMiniCard("Staff", "${analytics.uniqueStaffCount}", "Affected", ext.indigo, Modifier.weight(1f))
                    HighlightGlassMiniCard("Per Head", analytics.perHeadText, "Intensity", ext.amber, Modifier.weight(1f))
                }

                val totalTriage = (analytics.seenCount + analytics.unseenCount).coerceAtLeast(1)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.trackFill)
                        .border(1.dp, ext.glassRimEnd, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = ext.emerald, modifier = Modifier.size(14.dp))
                            Text("Seen: ${analytics.seenCount}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ext.emerald)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.PendingActions, null, tint = ext.amber, modifier = Modifier.size(14.dp))
                            Text("Unseen: ${analytics.unseenCount}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ext.amber)
                        }
                    }
                    AnimatedGlassProgressBar(
                        progress = analytics.seenCount.toFloat() / totalTriage.toFloat(),
                        fillColor = ext.emerald,
                        height = 8.dp
                    )
                }

                if (analytics.bySection.isNotEmpty()) {
                    MicroLabel("Distribution by School Section")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val maxS = (analytics.bySection.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
                        analytics.bySection.forEach { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(item.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "${item.count} (${item.percentage}%)",
                                        style = MaterialTheme.typography.labelSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                                        fontWeight = FontWeight.Bold,
                                        color = primary
                                    )
                                }
                                AnimatedGlassProgressBar(progress = item.count.toFloat() / maxS.toFloat(), fillColor = primary, height = 6.dp)
                            }
                        }
                    }
                }

                if (analytics.byDiscipline.isNotEmpty()) {
                    MicroLabel("Distribution by Academic Department")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        analytics.byDiscipline.forEach { d ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ext.indigo.copy(alpha = 0.12f))
                                    .border(1.dp, ext.indigo.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "${d.name}: ${d.count} (${d.percentage}%)",
                                    style = MaterialTheme.typography.labelSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (analytics.byClass.isNotEmpty()) {
                    MicroLabel("Top Classes (${analytics.byClass.take(12).size})")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        analytics.byClass.take(12).forEach { c ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(p.trackFill)
                                    .border(0.75.dp, ext.glassRimEnd, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "${c.name}: ${c.count} (${c.percentage}%)",
                                    style = MaterialTheme.typography.labelSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (analytics.byTerm.isNotEmpty()) {
                    MicroLabel("Term Horizon")
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        analytics.byTerm.forEach { t ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(ext.emerald.copy(alpha = 0.12f))
                                    .border(1.dp, ext.emerald.copy(alpha = 0.35f), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "${t.name}: ${t.count} (${t.percentage}%)",
                                    style = MaterialTheme.typography.labelSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                                    fontWeight = FontWeight.Bold,
                                    color = ext.emerald
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
private fun ApplyModalWindowBlur(
    dimAmount: Float = 0.56f,
    blurRadiusPx: Int = 48
) {
    val view = LocalView.current
    LaunchedEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        window.setDimAmount(dimAmount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = blurRadiusPx
            }
        }
    }
}

@Composable
fun AnalysisExportSection(
    isAdmin: Boolean,
    selectedTerm: String,
    startDate: String,
    endDate: String,
    onExportDossier: (ReportScope, String, String, String, String) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, contentPadding = 18.dp) {
        MicroLabel(if (isAdmin) "Audit Deliverable Studio" else "Personal Export Studio")
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isAdmin) "Export Official Dossier (DOCX)" else "Export My Exceptions Dossier (DOCX)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (isAdmin) {
                "Generates an editable Word (.docx) dossier covering the active term and date window."
            } else {
                "Generates an editable Word (.docx) dossier covering your submitted exceptions for this window."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        GlowingPrimaryButton(
            onClick = { onExportDossier(ReportScope.ALL, "DOCX", selectedTerm, startDate, endDate) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("analytics_export_report_button")
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text("Generate & Share DOCX Report", fontWeight = FontWeight.ExtraBold)
        }
    }
}
