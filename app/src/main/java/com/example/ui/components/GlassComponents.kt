package com.example.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ThemePreference
import com.example.ui.theme.JCBestTheme
import com.example.ui.theme.LocalCompliancePalette
import kotlin.math.roundToInt

data class EvidenceThumbItem(
    val id: String,
    val title: String,
    val mediaType: String,
    val previewModel: Any? = null
)

@Composable
fun Modifier.complianceGlassCard(radius: Dp = 24.dp): Modifier {
    val p = LocalCompliancePalette.current
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(if (p.isDark) 18.dp else 14.dp, shape, ambientColor = p.shadow, spotColor = p.shadow)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(p.glassTop, p.glassBottom)), shape)
        .border(1.dp, Brush.verticalGradient(listOf(p.borderTop, p.borderBottom)), shape)
}

@Composable
fun TangerineGlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val p = LocalCompliancePalette.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .alpha(if (enabled) 1f else 0.55f)
            .shadow(16.dp, shape, spotColor = p.tangerine.copy(alpha = 0.45f))
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(p.tangerine, p.tangerineEnd)))
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .clickable(enabled = enabled && !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Text(
                    text = text,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        } else {
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun LivingAtmosphericBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val p = LocalCompliancePalette.current
    val drift by rememberInfiniteTransition(label = "bg").animateFloat(
        initialValue = 0.18f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(p.bgBase, p.bgEnd)))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(p.glow1, Color.Transparent),
                    center = Offset(size.width * drift, size.height * 0.22f),
                    radius = size.maxDimension * 0.65f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(p.glow2, Color.Transparent),
                    center = Offset(size.width * (1f - drift), size.height * 0.72f),
                    radius = size.maxDimension * 0.60f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        if (p.isDark) Color.Black.copy(alpha = 0.34f) else Color(0xFF0F172A).copy(alpha = 0.04f)
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.45f),
                    radius = size.maxDimension * 0.82f
                )
            )
        }
        CompositionLocalProvider(LocalContentColor provides p.textPrimary) {
            Box(modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}

@Composable
fun GlassCircleIcon(
    icon: ImageVector,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = LocalCompliancePalette.current.textPrimary,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .complianceGlassCard(50.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun StatusGlassChip(
    dotColor: Color,
    text: String,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    Row(
        modifier = modifier
            .complianceGlassCard(50.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            color = p.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

@Composable
fun VerdictPill(
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val p = LocalCompliancePalette.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(
                if (selected) tint.copy(alpha = if (p.isDark) 0.28f else 0.18f)
                else p.trackFill
            )
            .border(
                width = 1.dp,
                color = if (selected) tint.copy(alpha = 0.7f) else tint.copy(alpha = 0.28f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
            Text(
                text = label,
                color = p.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Tactile Frosted Scrubber Capsule adapted from the Hume sliding 15% -> 74% control pill.
 * Supports smooth drag scrubbing with haptic steps, tabular readouts, and leading/trailing icon actions.
 */
@Composable
fun TactileEvidenceScrubberPill(
    progress: Float,
    label: String,
    active: Boolean,
    leadingIcon: ImageVector,
    trailingIcon: ImageVector,
    onProgressChange: (Float) -> Unit,
    onLeadingClick: () -> Unit,
    onTrailingClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    val haptic = LocalHapticFeedback.current
    val currentOnProgressChange by rememberUpdatedState(onProgressChange)
    var trackWidthPx by remember { mutableIntStateOf(1) }
    var lastHapticBucket by remember { mutableIntStateOf((progress * 20f).roundToInt()) }

    val clamped = progress.coerceIn(0f, 1f)
    val animatedFill by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(durationMillis = 90),
        label = "scrubber_fill"
    )

    fun dispatchProgress(xPx: Float) {
        val next = (xPx / trackWidthPx.coerceAtLeast(1)).coerceIn(0f, 1f)
        val bucket = (next * 20f).roundToInt()
        if (bucket != lastHapticBucket) {
            lastHapticBucket = bucket
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        currentOnProgressChange(next)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .complianceGlassCard(26.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (active) p.tangerine.copy(alpha = 0.22f) else p.pillFill)
                .border(1.dp, if (active) p.tangerine.copy(alpha = 0.55f) else p.borderTop, CircleShape)
                .clickable(onClick = onLeadingClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = "Primary media action",
                tint = if (active) p.tangerine else p.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(18.dp))
                .background(p.trackFill)
                .border(0.8.dp, p.borderBottom, RoundedCornerShape(18.dp))
                .onSizeChanged { trackWidthPx = it.width }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        dispatchProgress(offset.x)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        dispatchProgress(change.position.x)
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            if (animatedFill > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFill.coerceIn(0.06f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    p.tangerine.copy(alpha = if (p.isDark) 0.42f else 0.30f),
                                    p.tangerine.copy(alpha = if (p.isDark) 0.85f else 0.78f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = if (p.isDark) 0.28f else 0.55f),
                            shape = RoundedCornerShape(18.dp)
                        )
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = p.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
                Text(
                    text = "${(clamped * 100).roundToInt()}%",
                    color = p.textPrimary.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(p.trackFill)
                .border(1.dp, p.borderTop.copy(alpha = 0.35f), CircleShape)
                .clickable(onClick = onTrailingClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = "Secondary media action",
                tint = p.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun AuditScoreHeroCard(
    siteName: String,
    scorePercent: Int,
    passed: Int,
    flagged: Int,
    eyebrow: String = "COMPLIANCE SCORE",
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    val sweep by animateFloatAsState(
        targetValue = (scorePercent.coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 900),
        label = "arc"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .complianceGlassCard(26.dp)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = eyebrow.uppercase(),
                    color = p.textSecondary,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = siteName,
                    color = p.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusGlassChip(p.emerald, "$passed Passed")
                    StatusGlassChip(p.rose, "$flagged Flagged")
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier.size(76.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = p.trackFill,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.linearGradient(listOf(p.tangerine, p.emerald)),
                        startAngle = -90f,
                        sweepAngle = 360f * sweep,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "$scorePercent%",
                    color = p.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }

        // Animated capsule progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(p.trackFill)
        ) {
            if (sweep > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(sweep.coerceIn(0.03f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(p.tangerine, p.emerald)))
                )
            }
        }
    }
}

@Composable
fun SiteMapEvidenceCard(
    address: String,
    evidenceCount: Int,
    thumbnails: List<EvidenceThumbItem>,
    onThumbClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .complianceGlassCard(26.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Site Evidence Map",
                color = p.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            StatusGlassChip(p.emerald, "$evidenceCount Files")
        }

        // Adaptive mini-map aesthetic + glowing Electric Tangerine GPS pin
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(p.trackFill)
                .border(BorderStroke(0.8.dp, p.borderBottom), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(p.tangerine.copy(alpha = 0.18f))
                    .border(1.dp, p.tangerine.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = "Site GPS Pin",
                    tint = p.tangerine,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GPS VERIFIED LOCATION",
                    color = p.textSecondary,
                    fontSize = 10.sp,
                    letterSpacing = 1.1.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = address,
                    color = p.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (thumbnails.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.trackFill),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Attach photos or clips when logging exceptions to inspect them here.",
                    color = p.textSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(thumbnails, key = { idx, item -> "${item.id}_$idx" }) { idx, item ->
                    val isVideo = item.mediaType.equals("video", ignoreCase = true)
                    val isAudio = item.mediaType.equals("audio", ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(width = 96.dp, height = 66.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.trackFill)
                            .border(1.dp, p.borderTop.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .clickable { onThumbClick(idx) }
                            .testTag("site_evidence_thumb_$idx"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.previewModel != null && !isVideo && !isAudio) {
                            AsyncImage(
                                model = item.previewModel,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = when {
                                        isVideo -> Icons.Rounded.PlayCircle
                                        isAudio -> Icons.Rounded.Mic
                                        else -> Icons.Rounded.Image
                                    },
                                    contentDescription = item.title,
                                    tint = if (isVideo) p.tangerine else p.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = item.mediaType.uppercase(),
                                    color = p.textSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
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
fun ChecklistFindingRow(
    title: String,
    category: String,
    statusColor: Color,
    mediaCount: Int,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .complianceGlassCard(20.dp)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.16f))
                .border(1.dp, statusColor.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = category.uppercase(),
                color = p.textSecondary,
                fontSize = 10.sp,
                letterSpacing = 1.1.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                color = p.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = p.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (mediaCount > 0) {
            StatusGlassChip(p.tangerine, "$mediaCount Media")
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = p.textSecondary
        )
    }
}

@Composable
fun PreSubmitAuditSheet(
    checks: List<Pair<String, Boolean>>,
    isLoading: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .complianceGlassCard(30.dp)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close verification",
                        tint = p.textSecondary
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(p.trackFill)
                    .border(1.dp, p.borderTop, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.TaskAlt,
                    contentDescription = null,
                    tint = p.tangerine,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Text(
            text = "Ready to Submit Report",
            color = p.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Verify evidence attachments, institutional timestamps, and audit scope before dispatching.",
            color = p.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            checks.forEach { (label, ok) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.trackFill)
                        .border(0.8.dp, p.borderBottom, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = if (ok) p.emerald else p.tangerine,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = label,
                        color = p.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        TangerineGlowButton(
            text = if (isLoading) "Submitting Report..." else "Submit Compliance Report",
            enabled = !isLoading,
            isLoading = isLoading,
            onClick = onSubmit,
            modifier = Modifier.testTag("presubmit_confirm_button")
        )
    }
}

@Preview(name = "Dark Obsidian Blueprints", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewGlassComponentsDark() {
    JCBestTheme(themePreference = ThemePreference.DARK) {
        LivingAtmosphericBackground {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AuditScoreHeroCard(
                    siteName = "JC-Best Main Campus",
                    scorePercent = 88,
                    passed = 22,
                    flagged = 3
                )
                SiteMapEvidenceCard(
                    address = "Block B • Science & Fire Exit Wing",
                    evidenceCount = 4,
                    thumbnails = listOf(
                        EvidenceThumbItem("1", "Exit Corridor", "video"),
                        EvidenceThumbItem("2", "Lab Logbook", "image"),
                        EvidenceThumbItem("3", "Bus 4", "video")
                    ),
                    onThumbClick = {}
                )
                ChecklistFindingRow(
                    title = "Fire Extinguisher Inspection",
                    category = "Operations • Safety",
                    statusColor = LocalCompliancePalette.current.emerald,
                    mediaCount = 2,
                    onClick = {}
                )
                TactileEvidenceScrubberPill(
                    progress = 0.62f,
                    label = "00:29 / 00:48",
                    active = true,
                    leadingIcon = Icons.Rounded.Pause,
                    trailingIcon = Icons.Rounded.VolumeUp,
                    onProgressChange = {},
                    onLeadingClick = {}
                )
            }
        }
    }
}

@Preview(name = "Light Alabaster Blueprints", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun PreviewGlassComponentsLight() {
    JCBestTheme(themePreference = ThemePreference.LIGHT) {
        LivingAtmosphericBackground {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AuditScoreHeroCard(
                    siteName = "JC-Best Main Campus",
                    scorePercent = 88,
                    passed = 22,
                    flagged = 3
                )
                PreSubmitAuditSheet(
                    checks = listOf(
                        "GPS & Campus Timestamp Sealed" to true,
                        "2 Evidence Files Attached" to true,
                        "Officer Signature Verified" to true
                    ),
                    onSubmit = {}
                )
            }
        }
    }
}