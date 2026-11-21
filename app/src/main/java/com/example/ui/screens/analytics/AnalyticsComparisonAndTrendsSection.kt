package com.example.ui.screens.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DeltaMetric
import com.example.data.model.OverallTrendData
import com.example.data.model.SegmentTermStats
import com.example.data.model.TermComparisonData
import com.example.data.model.TermTrendPoint
import com.example.domain.AuthoritativeAnalysisBuilder
import com.example.ui.components.GlassCard
import com.example.ui.components.MicroLabel
import com.example.ui.theme.ComplianceTheme
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TermComparisonSection(
    comparisons: TermComparisonData
) {
    val ext = ComplianceTheme.extended
    val p = ComplianceTheme.palette
    val primary = MaterialTheme.colorScheme.primary
    var scopeFilter by remember { mutableStateOf("ALL") } // PRIMARY | SECONDARY | ALL
    var metricView by remember { mutableStateOf("exceptions") } // exceptions | staff | perHead | detailed
    var selectedSegIndex by remember { mutableIntStateOf(0) }

    val terms = comparisons.availableTerms.ifEmpty { listOf(comparisons.activeTerm) }
    val termPalette = listOf(ext.indigo, primary, ext.emerald)

    val visibleSegments = remember(scopeFilter) {
        AuthoritativeAnalysisBuilder.SEGMENT_CONFIGS.filter { cfg ->
            when (scopeFilter) {
                "PRIMARY" -> cfg.category == "PRIMARY" || cfg.category == "NURSERY"
                "SECONDARY" -> cfg.category == "SECONDARY"
                else -> true
            }
        }
    }

    LaunchedEffect(visibleSegments.size) {
        if (selectedSegIndex >= visibleSegments.size) selectedSegIndex = 0
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, contentPadding = 16.dp) {
        AnalyticsSectionHeader(
            icon = Icons.AutoMirrored.Filled.CompareArrows,
            tint = ext.indigo,
            eyebrow = "Cross-Term Comparison Matrix",
            title = "Multi-Term Segment Trajectory",
            subtitle = if (comparisons.hasComparativeHistory && comparisons.priorTerm != null) {
                "Authoritative delta shift from ${comparisons.priorTerm} to ${comparisons.activeTerm}."
            } else {
                "Segment comparison across recorded terms."
            }
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DeltaSummaryChip("Exceptions Δ", comparisons.totalExceptionsChange, Modifier.weight(1f))
            DeltaSummaryChip("Staff Δ", comparisons.affectedStaffChange, Modifier.weight(1f))
            DeltaSummaryChip("Per-Head Δ", comparisons.perHeadChange, Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("ALL" to "All Segments", "PRIMARY" to "Primary & Nursery", "SECONDARY" to "Secondary").forEach { (key, label) ->
                val selected = scopeFilter == key
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) primary else p.trackFill)
                        .border(1.dp, if (selected) primary else p.borderBottom, RoundedCornerShape(12.dp))
                        .clickable { scopeFilter = key }
                        .testTag("comparison_scope_${key.lowercase(Locale.US)}")
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "exceptions" to "Exceptions",
                "staff" to "Staff Flagged",
                "perHead" to "Per-Head",
                "detailed" to "Detailed Matrix"
            ).forEach { (key, label) ->
                val selected = metricView == key
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) ext.indigo.copy(alpha = 0.16f) else Color.Transparent)
                        .border(1.dp, if (selected) ext.indigo.copy(alpha = 0.6f) else ext.glassRimEnd, RoundedCornerShape(50))
                        .clickable { metricView = key }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) ext.indigo else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        val chartSegments = remember(visibleSegments) { visibleSegments.filter { !it.isTotal } }
        if (chartSegments.isNotEmpty()) {
            var animTrigger by remember { mutableStateOf(false) }
            LaunchedEffect(metricView, scopeFilter, comparisons.activeTerm) { animTrigger = true }
            val barProgress by animateFloatAsState(
                targetValue = if (animTrigger) 1f else 0f,
                animationSpec = tween(680, easing = FastOutSlowInEasing),
                label = "grouped_bars"
            )

            val maxChartValue = remember(chartSegments, terms, metricView, comparisons) {
                var mx = 1f
                chartSegments.forEach { seg ->
                    terms.forEach { tName ->
                        val st = comparisons.segmentStatsByTerm[tName]?.get(seg.key) ?: SegmentTermStats()
                        val v = when (metricView) {
                            "staff" -> st.affectedStaffCount.toFloat()
                            "perHead" -> st.perHead
                            else -> st.exceptionCount.toFloat()
                        }
                        if (v > mx) mx = v
                    }
                }
                mx
            }

            val activeSeg = chartSegments.getOrNull(selectedSegIndex.coerceIn(0, chartSegments.lastIndex))
            if (activeSeg != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.trackFill)
                        .border(1.dp, ext.glassRimEnd, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeSeg.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = activeSeg.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        terms.forEachIndexed { idx, tName ->
                            val st = comparisons.segmentStatsByTerm[tName]?.get(activeSeg.key) ?: SegmentTermStats()
                            val valueStr = when (metricView) {
                                "staff" -> "${st.affectedStaffCount}"
                                "perHead" -> String.format(Locale.US, "%.2f", st.perHead)
                                else -> "${st.exceptionCount}"
                            }
                            val c = termPalette[idx % termPalette.size]
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = tName.replace(" Term", ""),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = c,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = valueStr,
                                    style = MaterialTheme.typography.titleSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            val gridLineColor = p.borderBottom
            val highlightBg = primary.copy(alpha = 0.08f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
                    .pointerInput(chartSegments) {
                        detectTapGestures { offset ->
                            val groupWidth = size.width / chartSegments.size.toFloat().coerceAtLeast(1f)
                            val tapped = (offset.x / groupWidth).toInt().coerceIn(0, chartSegments.lastIndex)
                            selectedSegIndex = tapped
                        }
                    }
            ) {
                val padBottom = 18.dp.toPx()
                val padTop = 10.dp.toPx()
                val chartH = (size.height - padBottom - padTop).coerceAtLeast(20f)
                val groupCount = chartSegments.size
                val groupWidth = size.width / groupCount.toFloat()

                for (i in 0..3) {
                    val y = padTop + chartH * (i / 3f)
                    drawLine(
                        color = gridLineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    )
                }

                chartSegments.forEachIndexed { segIdx, seg ->
                    val groupLeft = segIdx * groupWidth
                    if (segIdx == selectedSegIndex) {
                        drawRoundRect(
                            color = highlightBg,
                            topLeft = Offset(groupLeft + 2.dp.toPx(), 2.dp.toPx()),
                            size = Size((groupWidth - 4.dp.toPx()).coerceAtLeast(8f), size.height - 4.dp.toPx()),
                            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                        )
                    }

                    val barCount = terms.size.coerceAtLeast(1)
                    val innerPad = groupWidth * 0.16f
                    val availW = (groupWidth - innerPad * 2f).coerceAtLeast(8f)
                    val gap = 3.dp.toPx()
                    val singleBarW = ((availW - gap * (barCount - 1)) / barCount).coerceAtLeast(5.dp.toPx())

                    terms.forEachIndexed { tIdx, tName ->
                        val st = comparisons.segmentStatsByTerm[tName]?.get(seg.key) ?: SegmentTermStats()
                        val rawVal = when (metricView) {
                            "staff" -> st.affectedStaffCount.toFloat()
                            "perHead" -> st.perHead
                            else -> st.exceptionCount.toFloat()
                        }
                        val ratio = (rawVal / maxChartValue).coerceIn(0f, 1f)
                        val barH = (chartH * ratio * barProgress).coerceAtLeast(if (rawVal > 0f) 4.dp.toPx() else 2.dp.toPx())
                        val bx = groupLeft + innerPad + tIdx * (singleBarW + gap)
                        val by = padTop + chartH - barH
                        val barColor = termPalette[tIdx % termPalette.size]
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(barColor, barColor.copy(alpha = 0.6f))),
                            topLeft = Offset(bx, by),
                            size = Size(singleBarW, barH),
                            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                chartSegments.forEachIndexed { idx, seg ->
                    val short = seg.label
                        .replace("Junior/Senior Primary", "J/S Pri")
                        .replace("Junior Primary", "Jnr Pri")
                        .replace("Senior Primary", "Snr Pri")
                        .replace("Junior Secondary", "Jnr Sec")
                        .replace("Senior Secondary", "Snr Sec")
                        .replace("Sec. Combined", "Sec All")
                    Text(
                        text = short,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        fontWeight = if (idx == selectedSegIndex) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (idx == selectedSegIndex) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedSegIndex = idx }
                            .padding(top = 2.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                terms.forEachIndexed { idx, tName ->
                    val c = termPalette[idx % termPalette.size]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                        Text(
                            text = tName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        MicroLabel("Authoritative Segment Comparison Table")
        Spacer(Modifier.height(8.dp))
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
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SEGMENT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(140.dp)
                    )
                    terms.forEach { tName ->
                        Text(
                            text = tName.replace(" Term", "").uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(86.dp),
                            textAlign = TextAlign.End
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "SHIFT Δ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(110.dp),
                        textAlign = TextAlign.End
                    )
                }

                visibleSegments.forEach { seg ->
                    val compRow = comparisons.segmentComparisons[seg.key]
                    val deltaMetric = when (metricView) {
                        "staff" -> compRow?.affectedStaffComparison
                        "perHead" -> compRow?.perHeadComparison
                        else -> compRow?.exceptionComparison
                    }
                    Row(
                        modifier = Modifier
                            .background(if (seg.isTotal) primary.copy(alpha = 0.08f) else Color.Transparent)
                            .border(0.5.dp, ext.glassRimEnd.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.width(140.dp)) {
                            Text(
                                text = seg.label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (seg.isTotal || seg.isCombined) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = seg.description,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        terms.forEach { tName ->
                            val st = comparisons.segmentStatsByTerm[tName]?.get(seg.key) ?: SegmentTermStats()
                            val cellText = when (metricView) {
                                "detailed" -> "${st.exceptionCount} (${st.affectedStaffCount})"
                                "staff" -> "${st.affectedStaffCount}"
                                "perHead" -> String.format(Locale.US, "%.2f", st.perHead)
                                else -> "${st.exceptionCount}"
                            }
                            Text(
                                text = cellText,
                                style = MaterialTheme.typography.bodySmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(86.dp),
                                textAlign = TextAlign.End
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(modifier = Modifier.width(110.dp), contentAlignment = Alignment.CenterEnd) {
                            DeltaBadgePill(comparison = deltaMetric, compact = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeltaSummaryChip(
    label: String,
    delta: DeltaMetric?,
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
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DeltaBadgePill(comparison = delta, compact = true)
    }
}

@Composable
fun OverallTrendSection(trends: OverallTrendData) {
    val ext = ComplianceTheme.extended
    val p = ComplianceTheme.palette
    val primary = MaterialTheme.colorScheme.primary
    var selectedMetric by remember { mutableStateOf("total") }
    var selectedPointIdx by remember { mutableIntStateOf((trends.points.size - 1).coerceAtLeast(0)) }

    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, contentPadding = 16.dp) {
        AnalyticsSectionHeader(
            icon = Icons.AutoMirrored.Filled.ShowChart,
            tint = ext.emerald,
            eyebrow = "Longitudinal Trajectory",
            title = "Multi-Term Progression Curve",
            subtitle = "Unified exception trajectory blending live logs & verified snapshots.",
            badge = trends.netChangePct?.let { pct ->
                {
                    val isDown = pct <= 0
                    val c = if (isDown) ext.emerald else ext.rose
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(c.copy(alpha = 0.14f))
                            .border(1.dp, c.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${if (pct > 0) "+" else ""}$pct% Net",
                            style = MaterialTheme.typography.labelSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                            fontWeight = FontWeight.ExtraBold,
                            color = c
                        )
                    }
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        if (trends.points.isEmpty()) {
            Text(
                text = "Insufficient term points to plot trajectory.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@GlassCard
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "total" to "Total",
                "academic" to "Academic",
                "operations" to "Operations",
                "discipline" to "Discipline",
                "perHead" to "Per-Head"
            ).forEach { (key, label) ->
                val active = selectedMetric == key
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (active) primary else p.trackFill)
                        .border(1.dp, if (active) primary else p.borderBottom, RoundedCornerShape(50))
                        .clickable { selectedMetric = key }
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

        val points = trends.points
        val activePoint = points.getOrNull(selectedPointIdx.coerceIn(0, points.lastIndex)) ?: points.last()

        fun metricValue(pt: TermTrendPoint): Float = when (selectedMetric) {
            "academic" -> pt.academicExceptions.toFloat()
            "operations" -> pt.operationsExceptions.toFloat()
            "discipline" -> pt.disciplineExceptions.toFloat()
            "perHead" -> pt.perHead
            else -> pt.totalExceptions.toFloat()
        }

        AnimatedContent(targetState = activePoint, label = "trend_inspector") { pt ->
            val valStr = if (selectedMetric == "perHead") {
                String.format(Locale.US, "%.2f", metricValue(pt))
            } else {
                "${metricValue(pt).roundToInt()}"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.trackFill)
                    .border(1.dp, ext.glassRimEnd, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = pt.termName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${if (pt.isFromSnapshot) "Verified Snapshot" else "Live Records"} • ${pt.affectedStaffCount} flagged staff",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pt.isFromSnapshot) ext.indigo else ext.emerald
                    )
                }
                Text(
                    text = valStr,
                    style = MaterialTheme.typography.headlineSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                    fontWeight = FontWeight.ExtraBold,
                    color = primary
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        var curveArmed by remember { mutableStateOf(false) }
        LaunchedEffect(selectedMetric, trends.points.size) { curveArmed = true }
        val drawProgress by animateFloatAsState(
            targetValue = if (curveArmed) 1f else 0f,
            animationSpec = tween(750, easing = FastOutSlowInEasing),
            label = "trend_curve_reveal"
        )

        val lineColor = when (selectedMetric) {
            "operations" -> ext.amber
            "discipline" -> ext.rose
            "academic" -> ext.indigo
            "perHead" -> ext.emerald
            else -> primary
        }
        val gridColor = p.borderBottom
        val nodeSurface = MaterialTheme.colorScheme.surface

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (points.size <= 1) {
                            selectedPointIdx = 0
                        } else {
                            val padX = 24.dp.toPx()
                            val usableW = (size.width - padX * 2).coerceAtLeast(1f)
                            val rel = ((offset.x - padX) / usableW).coerceIn(0f, 1f)
                            selectedPointIdx = (rel * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
                        }
                    }
                }
        ) {
            val padX = 22.dp.toPx()
            val padY = 16.dp.toPx()
            val w = (size.width - padX * 2).coerceAtLeast(1f)
            val h = (size.height - padY * 2).coerceAtLeast(1f)

            for (i in 0..3) {
                val y = padY + h * (i / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(padX, y),
                    end = Offset(padX + w, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
            }

            val values = points.map(::metricValue)
            val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
            val coords = points.mapIndexed { idx, _ ->
                val x = if (points.size == 1) padX + w / 2f else padX + w * (idx.toFloat() / (points.size - 1))
                val normY = (values[idx] / maxV).coerceIn(0f, 1f)
                val y = padY + h * (1f - normY * drawProgress)
                Offset(x, y)
            }

            if (coords.size >= 2) {
                val strokePath = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    for (i in 0 until coords.lastIndex) {
                        val p0 = coords[i]
                        val p1 = coords[i + 1]
                        val cx = (p0.x + p1.x) / 2f
                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }
                }
                val areaPath = Path().apply {
                    addPath(strokePath)
                    lineTo(coords.last().x, padY + h)
                    lineTo(coords.first().x, padY + h)
                    close()
                }
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
                        startY = padY,
                        endY = padY + h
                    )
                )
                drawPath(
                    path = strokePath,
                    color = lineColor,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            coords.forEachIndexed { idx, pt ->
                val selected = idx == selectedPointIdx
                if (selected) {
                    drawCircle(color = lineColor.copy(alpha = 0.24f), radius = 13.dp.toPx(), center = pt)
                }
                drawCircle(color = lineColor, radius = if (selected) 7.dp.toPx() else 5.dp.toPx(), center = pt)
                drawCircle(color = nodeSurface, radius = if (selected) 3.5.dp.toPx() else 2.5.dp.toPx(), center = pt)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEachIndexed { idx, pt ->
                Text(
                    text = pt.termName.replace(" Term", ""),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (idx == selectedPointIdx) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (idx == selectedPointIdx) lineColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { selectedPointIdx = idx }
                )
            }
        }
    }
}
