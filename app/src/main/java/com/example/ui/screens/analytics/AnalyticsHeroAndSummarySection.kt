package com.example.ui.screens.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ComplianceReport
import com.example.data.model.DeltaMetric
import com.example.data.model.TermAnalysisResult
import com.example.data.model.TermAnalysisSnapshot
import com.example.domain.AuthoritativeAnalysisBuilder
import com.example.ui.components.GlassCard
import com.example.ui.components.MicroLabel
import com.example.ui.theme.ComplianceTheme
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisHeroGlassHeader(
    selectedTerm: String,
    sessionLabel: String,
    periodLabel: String,
    analysis: TermAnalysisResult,
    allReports: List<ComplianceReport>,
    snapshots: List<TermAnalysisSnapshot>,
    datePreset: String,
    customStart: String,
    customEnd: String,
    isRefreshing: Boolean,
    isAdmin: Boolean,
    onSelectTerm: (String) -> Unit,
    onSelectPreset: (String) -> Unit,
    onPickStartDate: () -> Unit,
    onClearStartDate: () -> Unit,
    onPickEndDate: () -> Unit,
    onClearEndDate: () -> Unit,
    onSyncDatabase: () -> Unit
) {
    val ext = ComplianceTheme.extended
    val p = ComplianceTheme.palette
    val primary = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "hero_sync")
    val spinAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "sync_rotation"
    )

    val sourceBadgeLabel: String
    val sourceBadgeColor: Color
    when {
        analysis.isHydratedFromDurableSnapshot -> {
            sourceBadgeLabel = "Verified Permanent Snapshot"
            sourceBadgeColor = ext.indigo
        }
        analysis.grandTotals.totalExceptions > 0 -> {
            sourceBadgeLabel = "Live Raw Records"
            sourceBadgeColor = ext.emerald
        }
        else -> {
            sourceBadgeLabel = "No Records Recorded"
            sourceBadgeColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp, contentPadding = 0.dp) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.92f, size.height * 0.12f),
                        radius = size.maxDimension * 0.65f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ext.indigo.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.9f),
                        radius = size.maxDimension * 0.5f
                    )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MicroLabel(
                            text = if (isAdmin) "Executive Intelligence • $sessionLabel" else "Personal Audit Lens • $sessionLabel",
                            color = primary
                        )
                        Text(
                            text = if (isAdmin) "Compliance Analysis" else "My Compliance Insights",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isAdmin) {
                                "Authoritative evaluation across ${analysis.grandTotals.staffDenominator} active staff."
                            } else {
                                "Authoritative metrics scoped to reports submitted under your account."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Row(
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ext.glassTop)
                            .border(1.dp, ext.glassRimStart, RoundedCornerShape(14.dp))
                            .clickable(enabled = !isRefreshing, onClick = onSyncDatabase)
                            .testTag("analytics_sync_button")
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync database",
                            tint = primary,
                            modifier = Modifier
                                .size(16.dp)
                                .then(if (isRefreshing) Modifier.rotate(spinAngle) else Modifier)
                        )
                        Text(
                            text = if (isRefreshing) "Syncing" else "Sync",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(sourceBadgeColor.copy(alpha = 0.14f))
                            .border(1.dp, sourceBadgeColor.copy(alpha = 0.35f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(sourceBadgeColor)
                        )
                        Text(
                            text = sourceBadgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = sourceBadgeColor
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(p.trackFill)
                            .border(1.dp, ext.glassRimEnd, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "Period: $periodLabel",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val termChoices = AuthoritativeAnalysisBuilder.CANONICAL_TERMS + "ALL"
                    termChoices.forEach { termKey ->
                        val isSelected = selectedTerm.equals(termKey, ignoreCase = true)
                        val label = if (termKey == "ALL") "All Terms" else termKey
                        val rawCount = if (termKey == "ALL") allReports.size else allReports.count { it.term.equals(termKey, true) }
                        val hasSnap = termKey != "ALL" && snapshots.any { it.term.equals(termKey, true) && it.isVerified }

                        val dotColor = when {
                            rawCount > 0 -> ext.emerald
                            hasSnap -> ext.indigo
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        }

                        Row(
                            modifier = Modifier
                                .height(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) primary else ext.glassBottom)
                                .border(
                                    1.dp,
                                    if (isSelected) primary.copy(alpha = 0.9f) else p.borderBottom,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelectTerm(termKey) }
                                .testTag("analytics_term_${termKey.lowercase(Locale.US).replace(" ", "_")}")
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else dotColor)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (!analysis.isHydratedFromDurableSnapshot) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "ALL" to "All Time",
                            "MONTH" to "This Month",
                            "DAYS_30" to "Last 30d",
                            "WEEK" to "This Week",
                            "CUSTOM" to "Custom Range"
                        ).forEach { (key, label) ->
                            val active = datePreset == key
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (active) primary.copy(alpha = 0.16f) else p.trackFill)
                                    .border(
                                        1.dp,
                                        if (active) primary.copy(alpha = 0.65f) else p.borderBottom,
                                        RoundedCornerShape(50)
                                    )
                                    .clickable { onSelectPreset(key) }
                                    .testTag("analytics_preset_${key.lowercase(Locale.US)}")
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = datePreset == "CUSTOM") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnalyticsCalendarDateField(
                                label = "FROM DATE",
                                value = customStart,
                                onOpenPicker = onPickStartDate,
                                onClear = onClearStartDate,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("analytics_from_date")
                            )
                            AnalyticsCalendarDateField(
                                label = "TO DATE",
                                value = customEnd,
                                onOpenPicker = onPickEndDate,
                                onClear = onClearEndDate,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("analytics_to_date")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SnapshotStatusBanner(analysis: TermAnalysisResult) {
    val ext = ComplianceTheme.extended
    if (analysis.isCorruptOrUnverified) {
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, contentPadding = 16.dp) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ext.rose.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = ext.rose
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Unverified or Corrupted Snapshot Detected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = ext.rose
                    )
                    Text(
                        text = analysis.errorMessage
                            ?: "The stored archive for ${analysis.term} failed strict reconciliation checks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else if (analysis.isHydratedFromDurableSnapshot) {
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, contentPadding = 16.dp) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ext.indigo.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = null,
                        tint = ext.indigo
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = ext.indigo,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${analysis.term} Permanent Snapshot (v${analysis.snapshotVersion ?: 1})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Raw records for this term have been cleared; metrics are hydrated directly from the immutable PostgreSQL analytical snapshot" +
                            (analysis.officialTermEndDate?.let { " (ended $it)." } ?: "."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ExecutivePersonnelMetricsSection(
    analysis: TermAnalysisResult,
    isAdmin: Boolean,
    onDrillKpi: (kpiKey: String, title: String, subtitle: String) -> Unit
) {
    val ext = ComplianceTheme.extended
    val gt = analysis.grandTotals
    val deltaComp = analysis.comparisons

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnalyticsSectionHeader(
            icon = Icons.Default.Groups,
            tint = MaterialTheme.colorScheme.primary,
            eyebrow = if (isAdmin) "Executive Overview" else "My Standing Overview",
            title = if (isAdmin) "Staff Compliance Standing & Volume" else "Personal Logged Activity",
            subtitle = if (isAdmin) {
                "Denominator excludes Archived & CDS; tap any card to drill into distribution."
            } else {
                "Aggregate summary of exceptions submitted under your officer profile."
            }
        )

        if (isAdmin) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BentoKpiGlassTile(
                    indexLabel = "01",
                    title = "Total Staff",
                    intValue = gt.staffDenominator,
                    subtitle = "Active denominator",
                    accentColor = ext.indigo,
                    icon = Icons.Default.Groups,
                    progress = 1f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_total_staff"),
                    onClick = {
                        onDrillKpi("ALL_EXCEPTIONS", "Active Staff & Exceptions", "Overview of active institutional compliance volume")
                    }
                )
                BentoKpiGlassTile(
                    indexLabel = "02",
                    title = "Flagged Staff",
                    intValue = gt.affectedStaffCount,
                    subtitle = "${gt.staffWithExceptionsPercentage}% of workforce",
                    accentColor = ext.amber,
                    icon = Icons.Default.PersonOff,
                    progress = gt.staffWithExceptionsPercentage / 100f,
                    delta = deltaComp.affectedStaffChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_flagged_staff"),
                    onClick = {
                        onDrillKpi("STAFF_WITH_EXCEPTIONS", "Staff With Exceptions", "${gt.affectedStaffCount} flagged staff in active scope")
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BentoKpiGlassTile(
                    indexLabel = "03",
                    title = "Clean Standing",
                    intValue = gt.cleanStaffCount,
                    subtitle = "${gt.cleanStaffPercentage}% zero-flag rate",
                    accentColor = ext.emerald,
                    icon = Icons.Default.VerifiedUser,
                    progress = gt.cleanStaffPercentage / 100f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_clean_standing"),
                    onClick = null
                )
                BentoKpiGlassTile(
                    indexLabel = "08",
                    title = "Total Exceptions",
                    intValue = gt.totalExceptions,
                    subtitle = "${String.format(Locale.US, "%.2f", gt.overallPerHead)} avg per flagged staff",
                    accentColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Default.BarChart,
                    progress = 1f,
                    delta = deltaComp.totalExceptionsChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_total_exceptions"),
                    onClick = {
                        onDrillKpi("ALL_EXCEPTIONS", "Total Exceptions Distribution", "All recorded exceptions in ${analysis.term}")
                    }
                )
            }

            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, contentPadding = 14.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DivisionMiniChip("Acad", gt.academicExceptions, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    DivisionMiniChip("Ops", gt.operationsExceptions, ext.amber, Modifier.weight(1f))
                    DivisionMiniChip("Disc", gt.disciplineExceptions, ext.rose, Modifier.weight(1f))
                    DivisionMiniChip("CDS", gt.cdsExceptions, ext.indigo, Modifier.weight(1f))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BentoKpiGlassTile(
                    indexLabel = "01",
                    title = "My Exceptions",
                    intValue = gt.totalExceptions,
                    subtitle = "Records in ${analysis.term}",
                    accentColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Default.BarChart,
                    progress = 1f,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDrillKpi("ALL_EXCEPTIONS", "My Logged Exceptions", "Distribution of your compliance submissions")
                    }
                )
                BentoKpiGlassTile(
                    indexLabel = "02",
                    title = "Reviewed Logs",
                    intValue = gt.seenExceptions,
                    subtitle = "${gt.unseenExceptions} still unseen",
                    accentColor = ext.emerald,
                    icon = Icons.Default.Visibility,
                    progress = if (gt.totalExceptions > 0) gt.seenExceptions.toFloat() / gt.totalExceptions else 0f,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDrillKpi("SEEN_EXCEPTIONS", "Reviewed Submissions", "Records acknowledged by administrators")
                    }
                )
            }
        }
    }
}

@Composable
private fun BentoKpiGlassTile(
    indexLabel: String,
    title: String,
    intValue: Int,
    subtitle: String,
    accentColor: Color,
    icon: ImageVector,
    progress: Float,
    delta: DeltaMetric? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 22.dp,
        contentPadding = 14.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = indexLabel,
                style = MaterialTheme.typography.labelSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        AnimatedIntCounter(
            targetValue = intValue,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(8.dp))
        AnimatedGlassProgressBar(progress = progress.coerceIn(0.05f, 1f), fillColor = accentColor, height = 6.dp)

        if (delta != null) {
            Spacer(Modifier.height(8.dp))
            DeltaBadgePill(comparison = delta, compact = true)
        }
    }
}

@Composable
private fun DivisionMiniChip(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedIntCounter(
            targetValue = count,
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DerivedSeverityAndAuditSection(
    analysis: TermAnalysisResult,
    isAdmin: Boolean,
    onDrillKpi: (kpiKey: String, title: String, subtitle: String) -> Unit
) {
    val ext = ComplianceTheme.extended
    val gt = analysis.grandTotals

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isAdmin) {
            AnalyticsSectionHeader(
                icon = Icons.Default.Shield,
                tint = ext.rose,
                eyebrow = "Staff Risk Tiers",
                title = "Derived Staff Severity Distribution",
                subtitle = "Active personnel classified by cumulative term exception counts."
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SeverityTierGlassCard(
                    tierLabel = "Critical",
                    threshold = ">= 4 flags",
                    count = gt.criticalStaffCount,
                    total = gt.staffDenominator,
                    color = ext.rose,
                    icon = Icons.Default.WarningAmber,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("severity_tier_critical"),
                    onClick = { onDrillKpi("SEVERITY_CRITICAL", "Critical Severity Staff (>=4)", "Exceptions attributed to Critical tier staff") }
                )
                SeverityTierGlassCard(
                    tierLabel = "High",
                    threshold = "3 flags",
                    count = gt.highStaffCount,
                    total = gt.staffDenominator,
                    color = ext.amber,
                    icon = Icons.Default.ErrorOutline,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("severity_tier_high"),
                    onClick = { onDrillKpi("SEVERITY_HIGH", "High Severity Staff (3)", "Exceptions attributed to High tier staff") }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SeverityTierGlassCard(
                    tierLabel = "Medium",
                    threshold = "2 flags",
                    count = gt.mediumStaffCount,
                    total = gt.staffDenominator,
                    color = MaterialTheme.colorScheme.primary,
                    icon = Icons.Default.Info,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("severity_tier_medium"),
                    onClick = { onDrillKpi("SEVERITY_MEDIUM", "Medium Severity Staff (2)", "Exceptions attributed to Medium tier staff") }
                )
                SeverityTierGlassCard(
                    tierLabel = "Low",
                    threshold = "1 flag",
                    count = gt.lowStaffCount,
                    total = gt.staffDenominator,
                    color = ext.indigo,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("severity_tier_low"),
                    onClick = { onDrillKpi("SEVERITY_LOW", "Low Severity Staff (1)", "Exceptions attributed to Low tier staff") }
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 16.dp) {
            AnalyticsSectionHeader(
                icon = Icons.Default.Visibility,
                tint = ext.emerald,
                eyebrow = "Audit Review States",
                title = "Seen vs. Unseen Triage Status",
                subtitle = "Administrative acknowledgment progress across recorded exceptions."
            )
            Spacer(Modifier.height(14.dp))

            val totalCases = gt.totalExceptions.coerceAtLeast(1)
            val seenPct = ((gt.seenExceptions * 100f) / totalCases).toInt()
            val unseenPct = ((gt.unseenExceptions * 100f) / totalCases).toInt()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(ext.amber.copy(alpha = 0.12f))
                        .border(1.dp, ext.amber.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .clickable {
                            onDrillKpi("UNSEEN_EXCEPTIONS", "9. Unseen Exceptions", "Pending supervisory acknowledgment")
                        }
                        .testTag("audit_state_unseen")
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.PendingActions, null, tint = ext.amber, modifier = Modifier.size(16.dp))
                        Text("9. UNSEEN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = ext.amber)
                    }
                    AnimatedIntCounter(targetValue = gt.unseenExceptions, style = MaterialTheme.typography.headlineSmall)
                    Text("$unseenPct% awaiting review", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedGlassProgressBar(progress = gt.unseenExceptions.toFloat() / totalCases, fillColor = ext.amber, height = 6.dp)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(ext.emerald.copy(alpha = 0.12f))
                        .border(1.dp, ext.emerald.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .clickable {
                            onDrillKpi("SEEN_EXCEPTIONS", "10. Reviewed Exceptions", "Acknowledged by administrative leadership")
                        }
                        .testTag("audit_state_seen")
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = ext.emerald, modifier = Modifier.size(16.dp))
                        Text("10. REVIEWED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = ext.emerald)
                    }
                    AnimatedIntCounter(targetValue = gt.seenExceptions, style = MaterialTheme.typography.headlineSmall)
                    Text("$seenPct% seen & logged", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedGlassProgressBar(progress = gt.seenExceptions.toFloat() / totalCases, fillColor = ext.emerald, height = 6.dp)
                }
            }
        }
    }
}

@Composable
private fun SeverityTierGlassCard(
    tierLabel: String,
    threshold: String,
    count: Int,
    total: Int,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pct = if (total > 0) ((count * 100f) / total).toInt() else 0
    GlassCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        contentPadding = 14.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                Text(tierLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = color)
            }
            Text(threshold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AnimatedIntCounter(targetValue = count, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "staff ($pct%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        AnimatedGlassProgressBar(
            progress = if (total > 0) count.toFloat() / total.toFloat() else 0f,
            fillColor = color,
            height = 6.dp
        )
    }
}
