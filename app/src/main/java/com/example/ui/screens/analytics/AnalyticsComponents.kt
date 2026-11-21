package com.example.ui.screens.analytics

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DeltaMetric
import com.example.data.model.DeltaStatus
import com.example.ui.components.MicroLabel
import com.example.ui.theme.ComplianceTheme
import java.util.Locale
import kotlin.math.abs

@Composable
fun AnimatedIntCounter(
    targetValue: Int,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    color: Color = MaterialTheme.colorScheme.onSurface,
    suffix: String = ""
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { armed = true }
    val animated by animateIntAsState(
        targetValue = if (armed) targetValue else 0,
        animationSpec = tween(durationMillis = 680, easing = FastOutSlowInEasing),
        label = "int_counter"
    )
    Text(
        text = "$animated$suffix",
        style = style.merge(TextStyle(fontFeatureSettings = "tnum")),
        fontWeight = fontWeight,
        color = color
    )
}

@Composable
fun AnimatedFloatCounter(
    targetValue: Float,
    decimals: Int = 2,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    color: Color = MaterialTheme.colorScheme.onSurface,
    suffix: String = ""
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { armed = true }
    val animated by animateFloatAsState(
        targetValue = if (armed) targetValue else 0f,
        animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing),
        label = "float_counter"
    )
    Text(
        text = String.format(Locale.US, "%.${decimals}f%s", animated, suffix),
        style = style.merge(TextStyle(fontFeatureSettings = "tnum")),
        fontWeight = fontWeight,
        color = color
    )
}

@Composable
fun AnimatedGlassProgressBar(
    progress: Float,
    fillColor: Color,
    secondaryColor: Color = fillColor.copy(alpha = 0.72f),
    modifier: Modifier = Modifier,
    height: Dp = 10.dp
) {
    val ext = ComplianceTheme.extended
    val p = ComplianceTheme.palette
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(progress) { armed = true }

    val target = if (armed) progress.coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 260f),
        label = "bar_progress"
    )

    val shape = CircleShape
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(p.trackFill, shape)
            .border(0.75.dp, ext.glassRimEnd, shape)
    ) {
        if (animated > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(Brush.horizontalGradient(listOf(secondaryColor, fillColor)))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height * 0.42f)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun DeltaBadgePill(
    comparison: DeltaMetric?,
    invertSentiment: Boolean = true,
    compact: Boolean = false
) {
    val ext = ComplianceTheme.extended
    if (comparison == null || !comparison.hasBaseline || comparison.status == DeltaStatus.NO_PRIOR_BASELINE) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(ComplianceTheme.palette.trackFill)
                .border(0.75.dp, ext.glassRimEnd, RoundedCornerShape(50))
                .padding(horizontal = if (compact) 7.dp else 9.dp, vertical = 4.dp)
        ) {
            Text(
                text = "No baseline",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        return
    }

    val absFormatted = if (comparison.isFloatMetric) {
        String.format(Locale.US, "%.2f", abs(comparison.absoluteChange))
    } else {
        "${abs(comparison.absoluteChange.toInt())}"
    }
    val pctVal = comparison.percentageChange ?: 0

    val (icon, tint, bg, label) = when (comparison.status) {
        DeltaStatus.UNCHANGED -> listOf(
            Icons.Default.Remove,
            MaterialTheme.colorScheme.onSurfaceVariant,
            ComplianceTheme.palette.trackFill,
            "0 (0%)"
        )
        DeltaStatus.INCREASED -> {
            val c = if (invertSentiment) ext.rose else ext.emerald
            listOf(
                Icons.AutoMirrored.Filled.TrendingUp,
                c,
                c.copy(alpha = 0.14f),
                "+$absFormatted (+$pctVal%)"
            )
        }
        DeltaStatus.DECREASED -> {
            val c = if (invertSentiment) ext.emerald else ext.rose
            listOf(
                Icons.AutoMirrored.Filled.TrendingDown,
                c,
                c.copy(alpha = 0.14f),
                "-$absFormatted ($pctVal%)"
            )
        }
        DeltaStatus.NO_PRIOR_BASELINE -> listOf(
            Icons.Default.Remove,
            MaterialTheme.colorScheme.onSurfaceVariant,
            ComplianceTheme.palette.trackFill,
            "No baseline"
        )
    }

    val resolvedIcon = icon as ImageVector
    val resolvedTint = tint as Color
    val resolvedBg = bg as Color
    val resolvedLabel = label as String

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(resolvedBg)
            .border(0.75.dp, resolvedTint.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = if (compact) 7.dp else 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = resolvedIcon,
            contentDescription = null,
            tint = resolvedTint,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = resolvedLabel,
            style = MaterialTheme.typography.labelSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
            fontWeight = FontWeight.Bold,
            color = resolvedTint,
            maxLines = 1
        )
    }
}

@Composable
fun AnalyticsSectionHeader(
    icon: ImageVector,
    tint: Color,
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    badge: (@Composable () -> Unit)? = null
) {
    val ext = ComplianceTheme.extended
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tint.copy(alpha = 0.15f))
                .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            MicroLabel(text = eyebrow, color = tint)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (badge != null) {
            badge()
        } else {
            Spacer(Modifier.width(2.dp))
        }
    }
    Spacer(Modifier.height(2.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(tint.copy(alpha = 0.32f), ext.glassRimEnd, Color.Transparent)
                )
            )
    )
}

@Composable
fun AnalyticsQuickJumpBar(
    sections: List<Pair<String, String>>,
    activeKey: String,
    onSelect: (String) -> Unit
) {
    val ext = ComplianceTheme.extended
    val p = ComplianceTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sections.forEach { (key, label) ->
            val selected = activeKey == key
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else ext.glassBottom
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else p.borderBottom,
                        RoundedCornerShape(50)
                    )
                    .clickable { onSelect(key) }
                    .testTag("analytics_jump_$key")
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else p.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun AnalyticsCalendarDateField(
    label: String,
    value: String,
    onOpenPicker: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = ComplianceTheme.extended
    val p = ComplianceTheme.palette
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ext.glassBottom)
            .border(
                1.dp,
                if (value.isNotBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else p.borderBottom,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onOpenPicker)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.8.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value.ifBlank { "Select date" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (value.isNotBlank()) FontWeight.Bold else FontWeight.Medium,
                color = if (value.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
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

@Composable
fun analyticsSubCardFill(): Color {
    val p = ComplianceTheme.palette
    return if (p.isDark) Color(0xFF1A202D).copy(alpha = 0.80f)
    else Color(0xFFE9EFF8).copy(alpha = 0.86f)
}
