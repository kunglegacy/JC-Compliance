package com.example.ui.screens.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.data.model.CategoryPatternData
import com.example.data.model.ClassAnalysisData
import com.example.ui.components.GlassCard
import com.example.ui.components.MicroLabel
import com.example.ui.components.SearchInputBar
import com.example.ui.theme.ComplianceTheme
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassAnalysisPanel(classes: ClassAnalysisData) {
    val ext = ComplianceTheme.extended
    val p = ComplianceTheme.palette
    val primary = MaterialTheme.colorScheme.primary
    var viewMode by remember { mutableStateOf("GROUPED") }
    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }

    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, contentPadding = 16.dp) {
        AnalyticsSectionHeader(
            icon = Icons.Default.Class,
            tint = ext.indigo,
            eyebrow = "Classroom Attribution",
            title = "Class-by-Class Analysis",
            subtitle = "Ranked class exception concentration grouped by school tier."
        )

        Spacer(Modifier.height(12.dp))

        if (classes.allClassesRanked.isEmpty()) {
            Text(
                text = "No class-level exceptions recorded for this filter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@GlassCard
        }

        val topClass = classes.allClassesRanked.first()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HighlightGlassMiniCard("Active Classes", "${classes.totalClassesCount}", "With exceptions", ext.indigo, Modifier.weight(1f))
            HighlightGlassMiniCard("Top Class", topClass.className, "${topClass.exceptionCount} flags", ext.rose, Modifier.weight(1f))
            HighlightGlassMiniCard("Unassigned", "${classes.unspecifiedCount}", "Non-class items", ext.amber, Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(p.trackFill)
                .border(1.dp, ext.glassRimEnd, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("GROUPED" to "By Section Tier", "RANKED" to "All Ranked").forEach { (key, label) ->
                val active = viewMode == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) primary else Color.Transparent)
                        .clickable { viewMode = key }
                        .testTag("class_view_${key.lowercase(Locale.US)}")
                        .padding(horizontal = 8.dp),
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

        Spacer(Modifier.height(14.dp))

        val top8 = classes.allClassesRanked.take(8)
        val maxCls = (top8.maxOfOrNull { it.exceptionCount } ?: 1).coerceAtLeast(1)
        MicroLabel("Top Class Exceptions")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            top8.forEach { cls ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${cls.rank} ${cls.className} (${cls.sectionLabel})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${cls.exceptionCount}",
                            style = MaterialTheme.typography.labelMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
                            fontWeight = FontWeight.ExtraBold,
                            color = if (cls.rank == 1) ext.rose else primary
                        )
                    }
                    AnimatedGlassProgressBar(
                        progress = cls.exceptionCount.toFloat() / maxCls.toFloat(),
                        fillColor = if (cls.rank == 1) ext.rose else primary,
                        height = 6.dp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (viewMode == "GROUPED") {
            MicroLabel("Section Accordions (${classes.bySection.size} sections)")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                classes.bySection.forEachIndexed { idx, group ->
                    val expanded = expandedSections[group.sectionKey] ?: (idx == 0)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(analyticsSubCardFill())
                            .border(1.dp, ext.glassRimEnd, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedSections[group.sectionKey] = !expanded }
                                .testTag("class_accordion_${group.sectionKey}")
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.sectionLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${group.classes.size} classes • ${group.affectedStaffCount} staff • ${String.format(Locale.US, "%.2f", group.perHead)}/head",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(primary.copy(alpha = 0.14f))
                                        .padding(horizontal = 9.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${group.totalExceptions}",
                                        style = MaterialTheme.typography.labelMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = primary
                                    )
                                }
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (expanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = expanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                group.classes.forEach { cItem ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(ext.glassBottom)
                                            .border(0.75.dp, ext.glassRimEnd, RoundedCornerShape(12.dp))
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "#${cItem.rank} ${cItem.className}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${cItem.exceptionCount} (${cItem.shareOfSection}%)",
                                                style = MaterialTheme.typography.labelSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                                                fontWeight = FontWeight.ExtraBold,
                                                color = primary
                                            )
                                        }
                                        if (cItem.subcategories.isNotEmpty()) {
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                cItem.subcategories.take(3).forEach { sub ->
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(p.trackFill)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "${sub.category} (${sub.count})",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
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
                    }
                }
            }
        } else {
            MicroLabel("All Ranked Classes (${classes.allClassesRanked.size})")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                classes.allClassesRanked.forEach { cItem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(analyticsSubCardFill())
                            .border(0.75.dp, ext.glassRimEnd, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            RankBadge(rank = cItem.rank, tint = if (cItem.rank <= 3) ext.rose else ext.indigo)
                            Column {
                                Text(cItem.className, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "${cItem.sectionLabel} • ${cItem.affectedStaffCount} staff • Top: ${cItem.topCategory}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = "${cItem.exceptionCount}",
                            style = MaterialTheme.typography.titleSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                            fontWeight = FontWeight.ExtraBold,
                            color = primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExceptionCategoryPatternPanel(
    categoryPatterns: CategoryPatternData,
    onDrillCategory: (String) -> Unit
) {
    val ext = ComplianceTheme.extended
    val p = ComplianceTheme.palette
    val primary = MaterialTheme.colorScheme.primary
    var searchQuery by remember { mutableStateOf("") }

    val filteredCategories = remember(categoryPatterns.rankedCategories, searchQuery) {
        if (searchQuery.isBlank()) categoryPatterns.rankedCategories
        else categoryPatterns.rankedCategories.filter {
            it.category.contains(searchQuery, ignoreCase = true) ||
                it.affectedSectionsSummary.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, contentPadding = 16.dp) {
            AnalyticsSectionHeader(
                icon = Icons.Default.Category,
                tint = primary,
                eyebrow = "Exception Drivers & Pareto",
                title = "Exception Category & Pattern Analysis",
                subtitle = "Root compliance check drivers and structural concentration across divisions."
            )

            Spacer(Modifier.height(14.dp))

            if (categoryPatterns.rankedCategories.isEmpty()) {
                Text(
                    text = "No staff compliance exception categories recorded for this filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@GlassCard
            }

            MicroLabel("Top ${categoryPatterns.topDrivers.size} Primary Drivers (tap to inspect)")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categoryPatterns.topDrivers.forEach { driver ->
                    val tint = when (driver.rank) {
                        1 -> ext.rose
                        2 -> primary
                        else -> ext.amber
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(tint.copy(alpha = 0.11f))
                            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .clickable { onDrillCategory(driver.category) }
                            .testTag("top_driver_${driver.rank}")
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            RankBadge(rank = driver.rank, tint = tint)
                            Column {
                                Text(
                                    text = driver.category,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = driver.affectedSectionsSummary.ifBlank { "Multiple divisions" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${driver.occurrences}",
                                style = MaterialTheme.typography.titleMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
                                fontWeight = FontWeight.ExtraBold,
                                color = tint
                            )
                            Text(
                                text = "${driver.percentage}% share",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            val conc = categoryPatterns.concentration
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(ext.indigo.copy(alpha = 0.11f))
                    .border(1.dp, ext.indigo.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = ext.indigo, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Pareto Concentration: ${conc.top3Share}% in Top ${conc.top3Count}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "80% of exception volume is concentrated across ${conc.categoriesFor80PercentVolume} out of ${conc.totalCategories} compliance checks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                categoryPatterns.rankedCategories.take(5).forEach { cat ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = cat.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${cat.percentage}% (Cum ${cat.cumulativePercentage}%)",
                                style = MaterialTheme.typography.labelSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                                fontWeight = FontWeight.Bold,
                                color = ext.indigo
                            )
                        }
                        AnimatedGlassProgressBar(
                            progress = cat.cumulativePercentage / 100f,
                            fillColor = ext.indigo,
                            height = 5.dp
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            MicroLabel("Section & Division Volume Contribution")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categoryPatterns.sectionContributions.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (item.isInstitutionLevel) ext.amber else primary)
                            )
                            Text(
                                text = item.section,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (item.isInstitutionLevel) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ext.amber.copy(alpha = 0.14f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Institution-Level",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = ext.amber
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${item.occurrences} (${item.percentage}%)",
                            style = MaterialTheme.typography.labelMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (categoryPatterns.observations.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 16.dp) {
                AnalyticsSectionHeader(
                    icon = Icons.Default.Lightbulb,
                    tint = ext.emerald,
                    eyebrow = "Automated Insights",
                    title = "Data-Grounded Pattern Observations"
                )
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    categoryPatterns.observations.forEach { obs ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(p.trackFill)
                                .border(1.dp, ext.glassRimEnd, RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = obs.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ext.emerald
                                )
                                Text(
                                    text = obs.highlight,
                                    style = MaterialTheme.typography.labelSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                                    fontWeight = FontWeight.Bold,
                                    color = primary
                                )
                            }
                            Text(
                                text = obs.statement,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 16.dp) {
            MicroLabel("Complete Category Frequency Registry (${filteredCategories.size})")
            Spacer(Modifier.height(8.dp))
            SearchInputBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search check or affected section...",
                testTag = "analytics_category_search"
            )
            Spacer(Modifier.height(10.dp))

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
                        Text("RANK", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(42.dp))
                        Text("EXCEPTION CATEGORY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(180.dp))
                        Text("COUNT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                        Text("SHARE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                        Spacer(Modifier.width(10.dp))
                        Text("TERM Δ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(106.dp), textAlign = TextAlign.End)
                    }
                    filteredCategories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .border(0.5.dp, ext.glassRimEnd.copy(alpha = 0.45f))
                                .clickable { onDrillCategory(cat.category) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("#${cat.rank}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = primary, modifier = Modifier.width(42.dp))
                            Column(modifier = Modifier.width(180.dp)) {
                                Text(cat.category, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (cat.affectedSectionsSummary.isNotBlank()) {
                                    Text(cat.affectedSectionsSummary, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text("${cat.occurrences}", style = MaterialTheme.typography.bodySmall.merge(TextStyle(fontFeatureSettings = "tnum")), fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                            Text("${cat.percentage}%", style = MaterialTheme.typography.bodySmall.merge(TextStyle(fontFeatureSettings = "tnum")), modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                            Spacer(Modifier.width(10.dp))
                            Box(modifier = Modifier.width(106.dp), contentAlignment = Alignment.CenterEnd) {
                                DeltaBadgePill(comparison = cat.historicalDelta, compact = true)
                            }
                        }
                    }
                }
            }
        }
    }
}
