package com.example.ui.screens.analytics

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DisciplineAnalysisData
import com.example.data.model.SectionAnalysisData
import com.example.ui.components.GlassCard
import com.example.ui.components.MicroLabel
import com.example.ui.theme.ComplianceTheme
import java.util.Locale

@Composable
fun SectionAnalysisPanel(
    sections: SectionAnalysisData,
    priorTermName: String?,
    onDrillSection: (sectionKey: String, sectionLabel: String) -> Unit
) {
    val ext = ComplianceTheme.extended
    val p = ComplianceTheme.palette
    val primary = MaterialTheme.colorScheme.primary
    val ranked = sections.rankedList

    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, contentPadding = 16.dp) {
        AnalyticsSectionHeader(
            icon = Icons.Default.Apartment,
            tint = primary,
            eyebrow = "Section Performance",
            title = "Academic Division Breakdown",
            subtitle = "Ranked across Nursery, Primary & Secondary schools (tap row to drill down)."
        )

        Spacer(Modifier.height(14.dp))

        if (ranked.isEmpty() || sections.totalAcademicExceptions == 0) {
            Text(
                text = "No academic section records available for this window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@GlassCard
        }

        val topVolume = ranked.first()
        val topIntensity = ranked.maxByOrNull { it.perHead } ?: topVolume

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HighlightGlassMiniCard(
                label = "Highest Volume",
                headline = topVolume.label,
                metric = "${topVolume.exceptionCount} (${topVolume.shareOfAcademic}%)",
                accent = primary,
                modifier = Modifier.weight(1f)
            )
            HighlightGlassMiniCard(
                label = "Peak Per-Head",
                headline = topIntensity.label,
                metric = "${topIntensity.perHeadText} avg",
                accent = ext.amber,
                modifier = Modifier.weight(1f)
            )
            HighlightGlassMiniCard(
                label = "Pri vs Sec",
                headline = "${sections.primaryCombinedCount} vs ${sections.secondaryCombinedCount}",
                metric = "Pri / Sec split",
                accent = ext.indigo,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(14.dp))

        val maxVal = (ranked.maxOfOrNull { it.exceptionCount } ?: 1).coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ranked.forEach { sec ->
                val barColor = when (sec.rank) {
                    1 -> ext.rose
                    2 -> primary
                    3 -> ext.amber
                    else -> ext.indigo
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(analyticsSubCardFill())
                        .border(1.dp, ext.glassRimEnd, RoundedCornerShape(16.dp))
                        .clickable { onDrillSection(sec.key, sec.label) }
                        .testTag("section_bar_${sec.key}")
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            RankBadge(rank = sec.rank, tint = barColor)
                            Column {
                                Text(
                                    text = sec.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${sec.affectedStaffCount} staff • ${sec.perHeadText}/head • ${sec.shareOfAcademic}% share",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${sec.exceptionCount}",
                                style = MaterialTheme.typography.titleMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
                                fontWeight = FontWeight.ExtraBold,
                                color = barColor
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Drill into ${sec.label}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    AnimatedGlassProgressBar(
                        progress = sec.exceptionCount.toFloat() / maxVal.toFloat(),
                        fillColor = barColor,
                        height = 8.dp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        MicroLabel(
            text = if (priorTermName != null) "Section Breakdown vs $priorTermName" else "Section Metrics Table"
        )
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(analyticsSubCardFill())
                .border(1.dp, ext.glassRimEnd, RoundedCornerShape(16.dp))
                .horizontalScroll(rememberScrollState())
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .background(ext.glassTop)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text("SECTION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(128.dp))
                    Text("EXC", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(50.dp), textAlign = TextAlign.End)
                    Text("STAFF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(54.dp), textAlign = TextAlign.End)
                    Text("PER HEAD", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(68.dp), textAlign = TextAlign.End)
                    Spacer(Modifier.width(10.dp))
                    Text("DELTA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(106.dp), textAlign = TextAlign.End)
                }
                ranked.forEach { sec ->
                    Row(
                        modifier = Modifier
                            .border(0.5.dp, ext.glassRimEnd.copy(alpha = 0.45f))
                            .clickable { onDrillSection(sec.key, sec.label) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(sec.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(128.dp))
                        Text("${sec.exceptionCount}", style = MaterialTheme.typography.bodySmall.merge(TextStyle(fontFeatureSettings = "tnum")), fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp), textAlign = TextAlign.End)
                        Text("${sec.affectedStaffCount}", style = MaterialTheme.typography.bodySmall.merge(TextStyle(fontFeatureSettings = "tnum")), modifier = Modifier.width(54.dp), textAlign = TextAlign.End)
                        Text(sec.perHeadText, style = MaterialTheme.typography.bodySmall.merge(TextStyle(fontFeatureSettings = "tnum")), modifier = Modifier.width(68.dp), textAlign = TextAlign.End)
                        Spacer(Modifier.width(10.dp))
                        Box(modifier = Modifier.width(106.dp), contentAlignment = Alignment.CenterEnd) {
                            DeltaBadgePill(comparison = sec.historicalDelta, compact = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DisciplineAnalysisPanel(
    disciplines: DisciplineAnalysisData,
    onDrillDiscipline: (disciplineName: String, scopeLabel: String) -> Unit
) {
    val ext = ComplianceTheme.extended
    val p = ComplianceTheme.palette
    val primary = MaterialTheme.colorScheme.primary
    var scopeTab by remember { mutableStateOf("SECONDARY") }

    val scopeRows = remember(scopeTab, disciplines) {
        when (scopeTab) {
            "PRIMARY" -> disciplines.primary.rows
            "SECONDARY" -> disciplines.secondary.rows
            else -> disciplines.allDisciplines
        }
    }
    val scopeTotalExc = when (scopeTab) {
        "PRIMARY" -> disciplines.primary.totalExceptions
        "SECONDARY" -> disciplines.secondary.totalExceptions
        else -> disciplines.allDisciplines.sumOf { it.exceptionCount }
    }
    val scopeTotalStaff = when (scopeTab) {
        "PRIMARY" -> disciplines.primary.totalTeachers
        "SECONDARY" -> disciplines.secondary.totalTeachers
        else -> disciplines.allDisciplines.sumOf { it.affectedTeachers }
    }
    val scopeLabel = when (scopeTab) {
        "PRIMARY" -> "Primary & Nursery"
        "SECONDARY" -> "Secondary Schools"
        else -> "All Academic Schools"
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, contentPadding = 16.dp) {
        AnalyticsSectionHeader(
            icon = Icons.Default.School,
            tint = ext.amber,
            eyebrow = "Canonical Academic Departments",
            title = "Department Analysis",
            subtitle = "Life Sciences · Physical Sciences · Social Sciences · Languages"
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "SECONDARY" to "Secondary (${disciplines.secondary.totalExceptions})",
                "PRIMARY" to "Primary & Nur (${disciplines.primary.totalExceptions})",
                "ALL" to "All Academic"
            ).forEach { (key, label) ->
                val active = scopeTab == key
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) primary else p.trackFill)
                        .border(1.dp, if (active) primary else p.borderBottom, RoundedCornerShape(12.dp))
                        .clickable { scopeTab = key }
                        .testTag("discipline_tab_${key.lowercase(Locale.US)}")
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (scopeRows.isEmpty()) {
            Text(
                text = "No department records in $scopeLabel for this window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@GlassCard
        }

        val perHeadScope = if (scopeTotalStaff > 0) String.format(Locale.US, "%.2f", scopeTotalExc.toFloat() / scopeTotalStaff) else "0.00"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HighlightGlassMiniCard("Total Flags", "$scopeTotalExc", scopeLabel, ext.amber, Modifier.weight(1f))
            HighlightGlassMiniCard("Staff Flagged", "$scopeTotalStaff", "In active scope", ext.indigo, Modifier.weight(1f))
            HighlightGlassMiniCard("Avg Intensity", perHeadScope, "Per teacher", primary, Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        val maxDisc = (scopeRows.maxOfOrNull { it.exceptionCount } ?: 1).coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            scopeRows.forEach { d ->
                val barColor = when (d.discipline) {
                    "Life Sciences" -> ext.emerald
                    "Physical Sciences" -> ext.indigo
                    "Social Sciences" -> ext.amber
                    "Languages" -> ext.rose
                    else -> primary
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(analyticsSubCardFill())
                        .border(1.dp, ext.glassRimEnd, RoundedCornerShape(16.dp))
                        .clickable { onDrillDiscipline(d.discipline, scopeLabel) }
                        .testTag("discipline_row_${d.discipline.lowercase(Locale.US).replace(" ", "_")}")
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            RankBadge(rank = d.rank, tint = barColor)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = d.discipline,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${d.affectedTeachers} teachers • ${d.perHeadText}/head • ${d.shareOfScope}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (d.teacherNamesSummary.isNotBlank()) {
                                    Text(
                                        text = d.teacherNamesSummary,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${d.exceptionCount}",
                                style = MaterialTheme.typography.titleMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
                                fontWeight = FontWeight.ExtraBold,
                                color = barColor
                            )
                            DeltaBadgePill(comparison = d.historicalChange, compact = true)
                        }
                    }
                    AnimatedGlassProgressBar(
                        progress = d.exceptionCount.toFloat() / maxDisc.toFloat(),
                        fillColor = barColor,
                        height = 8.dp
                    )
                }
            }
        }
    }
}

@Composable
fun HighlightGlassMiniCard(
    label: String,
    headline: String,
    metric: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val ext = ComplianceTheme.extended
    val p = ComplianceTheme.palette
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(p.trackFill)
            .border(1.dp, ext.glassRimEnd, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, letterSpacing = 0.6.sp),
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1
        )
        Text(
            text = headline,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = metric,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
fun RankBadge(rank: Int, tint: Color) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.16f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
            fontWeight = FontWeight.ExtraBold,
            color = tint
        )
    }
}
