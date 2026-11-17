package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ReportResolutionStatus
import com.example.data.model.SeverityLevel
import com.example.data.model.SupabaseConfig
import com.example.ui.theme.ComplianceTheme
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBackgroundDeep
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LightBackground
import com.example.ui.theme.LightBackgroundDeep

@Composable
fun AtmosphericGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    LivingAtmosphericBackground(modifier = modifier, content = content)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = ComplianceTheme.palette
    val cardModifier = modifier
        .complianceGlassCard(cornerRadius)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    CompositionLocalProvider(LocalContentColor provides p.textPrimary) {
        Column(
            modifier = cardModifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val ext = ComplianceTheme.extended
    val rimBrush = Brush.linearGradient(listOf(ext.glassRimStart, ext.glassRimEnd))
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(ext.glassTop, ext.glassBottom)), CircleShape)
            .border(1.dp, rimBrush, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun GlassIconHalo(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 76.dp
) {
    val ext = ComplianceTheme.extended
    Box(
        modifier = Modifier
            .size(size + 28.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = 0.34f), Color.Transparent),
                        center = center,
                        radius = this.size.minDimension / 2f
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(ext.glassTop, ext.glassBottom)), CircleShape)
                .border(
                    1.2.dp,
                    Brush.linearGradient(listOf(tint.copy(alpha = 0.55f), ext.glassRimEnd)),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}

/**
 * High-contrast Solar Hero Gauge Card directly inspired by the screenshot's "Daily Score / 90%" hero card.
 */
@Composable
fun SolarHeroScoreCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    percentage: Int,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val ext = ComplianceTheme.extended
    val shape = RoundedCornerShape(28.dp)
    val heroBrush = Brush.linearGradient(
        colors = listOf(ext.solarGold, Color(0xFFFFB300))
    )
    val ink = ext.heroInk

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(heroBrush, shape)
            .border(1.dp, Color.White.copy(alpha = 0.45f), shape)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = ink.copy(alpha = 0.65f)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = ink
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ink.copy(alpha = 0.78f)
                )
            }

            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier.size(84.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 8.dp.toPx()
                    drawArc(
                        color = ink.copy(alpha = 0.16f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = ink,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = ink
                )
            }
        }
    }
}

/**
 * Bento metric tile directly modeled on the 2x2 ring-progress metric tiles in the screenshot.
 */
@Composable
fun CircularRingMetricTile(
    title: String,
    value: String,
    subtitle: String,
    progress: Float,
    icon: ImageVector,
    ringColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 24.dp,
        contentPadding = 16.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    drawArc(
                        color = ringColor.copy(alpha = 0.20f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0.08f, 1f),
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ringColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ringColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 28.sp,
                fontFeatureSettings = "tnum"
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Thick frosted glass capsule progress bar (inspired by the video's 15% -> 74% slider bar).
 */
@Composable
fun GlassCapsuleProgressBar(
    progress: Float,
    fillColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp
) {
    val ext = ComplianceTheme.extended
    val shape = CircleShape
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), shape)
            .border(0.8.dp, ext.glassRimEnd, shape)
    ) {
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(fillColor.copy(alpha = 0.85f), fillColor)
                        )
                    )
            )
        }
    }
}

@Composable
fun SchoolCrestBadge(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val ext = ComplianceTheme.extended
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 6.dp,
        border = BorderStroke(1.2.dp, ext.glassRimStart)
    ) {
        Image(
            painter = painterResource(id = R.drawable.jcbest_crest),
            contentDescription = "JC-Best Schools International Crest",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(6.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    }
}

@Composable
fun MicroLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp
        ),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun SubScreenTopRow(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassCircleButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier.testTag("subscreen_back_button")
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun SearchInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = "search_input"
) {
    val ext = ComplianceTheme.extended
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "Search")
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        colors = glassFieldColors(),
        singleLine = true,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun StatusPill(
    label: String,
    textColor: Color,
    containerColor: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(50.dp),
        border = BorderStroke(0.8.dp, textColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(12.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = textColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SeenBadge(isSeen: Boolean, isPending: Boolean = false) {
    val ext = ComplianceTheme.extended
    when {
        isPending -> StatusPill("PENDING", ext.amber, ext.amberContainer, Icons.Default.Schedule)
        isSeen -> StatusPill("REVIEWED", ext.emerald, ext.emeraldContainer, Icons.Default.Visibility)
        else -> StatusPill("UNSEEN", ext.amber, ext.amberContainer, Icons.Default.VisibilityOff)
    }
}

@Composable
fun ResolutionBadge(status: ReportResolutionStatus) {
    val ext = ComplianceTheme.extended
    val (fg, bg) = when (status) {
        ReportResolutionStatus.ACCEPTED -> ext.emerald to ext.emeraldContainer
        ReportResolutionStatus.REJECTED -> ext.rose to ext.roseContainer
        ReportResolutionStatus.ACTION_REQUIRED -> Color(0xFFFF6B2C) to Color(0x28FF6B2C)
        ReportResolutionStatus.PENDING -> ext.amber to ext.amberContainer
        ReportResolutionStatus.OPEN -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
    }
    StatusPill(status.label.uppercase(), fg, bg)
}

@Composable
fun EditedBadgePill(modifier: Modifier = Modifier) {
    val ext = ComplianceTheme.extended
    val fg = MaterialTheme.colorScheme.onSurfaceVariant
    val bg = if (ext.isDark) Color.White.copy(alpha = 0.08f) else Color(0xFF0F172A).copy(alpha = 0.06f)
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(50.dp),
        border = BorderStroke(0.7.dp, fg.copy(alpha = 0.25f))
    ) {
        Text(
            text = "Edited",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontStyle = FontStyle.Italic
            ),
            color = fg,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun SeverityBadge(severity: SeverityLevel) {
    val ext = ComplianceTheme.extended
    val (fg, bg) = when (severity) {
        SeverityLevel.CRITICAL -> ext.rose to ext.roseContainer
        SeverityLevel.HIGH -> Color(0xFFFF6B2C) to Color(0x28FF6B2C)
        SeverityLevel.MEDIUM -> ext.amber to ext.amberContainer
        SeverityLevel.LOW -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
        SeverityLevel.NONE -> ext.emerald to ext.emeraldContainer
    }
    StatusPill(severity.label.uppercase(), fg, bg)
}

@Composable
fun EmptyStatePanel(
    icon: ImageVector = Icons.Default.Info,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassIconHalo(icon = icon, size = 56.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun NativeLoadingBar(visible: Boolean) {
    if (visible) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent
        )
    }
}

@Composable
fun MonoCodeText(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun SupabaseConnectionDialog(
    current: SupabaseConfig,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var url by remember(current.url) { mutableStateOf(current.url) }
    var key by remember(current.anonKey) { mutableStateOf(current.anonKey) }

    val ext = ComplianceTheme.extended
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (ext.isDark) Color(0xFF151923) else Color(0xFFF6F8FC),
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Supabase Backend", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Connect this app to your JCBest Directorate Supabase project URL and publishable/anon key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Project URL") },
                    placeholder = { Text("https://xxxxx.supabase.co") },
                    singleLine = true,
                    colors = glassFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("supabase_url_input")
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Publishable / Anon Key") },
                    placeholder = { Text("sb_publishable_...") },
                    singleLine = true,
                    colors = glassFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("supabase_key_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(url, key) },
                enabled = url.isNotBlank() && key.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_supabase_config_button")
            ) {
                Text("Save Connection")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun glassFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedContainerColor = ComplianceTheme.extended.glassTop,
    unfocusedContainerColor = ComplianceTheme.extended.glassBottom,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = ComplianceTheme.extended.glassRimEnd
)

@Composable
fun GlowingPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val ext = ComplianceTheme.extended
    val p = ComplianceTheme.palette
    val resolvedGradient = if (containerColor == MaterialTheme.colorScheme.primary) {
        Brush.horizontalGradient(listOf(p.tangerine, p.tangerineEnd))
    } else {
        Brush.horizontalGradient(listOf(containerColor, containerColor.copy(alpha = 0.88f)))
    }
    val shape = CircleShape
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = contentColor.copy(alpha = 0.55f)
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (ext.isDark) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.65f)
        ),
        modifier = modifier.height(54.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(resolvedGradient, shape)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content
            )
        }
    }
}

/**
 * Smoked glass checklist / action row modeled on the Hume suggested-setup rows:
 * left tinted circular icon badge, middle title + subtitle, right circular pill/control slot.
 */
@Composable
fun VideoStyleChecklistRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailingContent: @Composable () -> Unit
) {
    val ext = ComplianceTheme.extended
    val p = ComplianceTheme.palette
    val shape = RoundedCornerShape(22.dp)
    val selectedBg = if (selected) {
        Brush.verticalGradient(
            listOf(
                iconTint.copy(alpha = if (ext.isDark) 0.22f else 0.14f),
                p.glassBottom
            )
        )
    } else {
        Brush.verticalGradient(listOf(p.glassTop, p.glassBottom))
    }
    val rimBrush = if (selected) {
        Brush.verticalGradient(listOf(iconTint.copy(alpha = 0.65f), p.borderBottom))
    } else {
        Brush.verticalGradient(listOf(p.borderTop, p.borderBottom))
    }

    CompositionLocalProvider(LocalContentColor provides p.textPrimary) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(selectedBg, shape)
                .border(BorderStroke(1.dp, rimBrush), shape)
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = if (ext.isDark) 0.22f else 0.14f))
                    .border(1.dp, iconTint.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            trailingContent()
        }
    }
}

