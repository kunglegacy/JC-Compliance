package com.example.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AcademicWeekEngine
import com.example.data.model.SystemSettings
import com.example.data.model.UserRole
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowingPrimaryButton
import com.example.ui.components.MicroLabel
import com.example.ui.components.SubScreenTopRow
import com.example.ui.components.glassFieldColors
import com.example.ui.theme.ComplianceTheme

@Composable
fun SystemConfigScreen(
    settings: SystemSettings,
    userRole: UserRole,
    isLoading: Boolean,
    onSaveSettings: (SystemSettings) -> Unit,
    onPurgeEvidenceBucket: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val ext = ComplianceTheme.extended
    var sessionStr by remember(settings) { mutableStateOf(settings.academicSession) }
    var activeTerm by remember(settings) { mutableStateOf(settings.activeTerm) }
    var termStartDate by remember(settings) { mutableStateOf(settings.termStartDate) }
    var manualOverride by remember(settings) { mutableStateOf(settings.manualWeekOverride) }
    var showPurgeConfirm by remember { mutableStateOf(false) }

    val previewWeek = remember(sessionStr, activeTerm, termStartDate, manualOverride) {
        AcademicWeekEngine.calculateAcademicWeek(
            SystemSettings(sessionStr, activeTerm, termStartDate, manualOverride)
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
                title = "Academic Configuration",
                subtitle = "Term calendar, week anchoring & maintenance",
                onBack = onBack
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 18.dp) {
                        MicroLabel("Current Status Preview")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$sessionStr • $activeTerm • ${previewWeek.label}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = sessionStr,
                            onValueChange = { sessionStr = it },
                            label = { Text("Academic Session (e.g. 2025/2026)") },
                            singleLine = true,
                            colors = glassFieldColors(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))
                        MicroLabel("Active Term")
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("First Term", "Second Term", "Third Term").forEach { term ->
                                val selected = activeTerm == term
                                Surface(
                                    onClick = { activeTerm = term },
                                    shape = CircleShape,
                                    color = if (selected) MaterialTheme.colorScheme.primary else ext.glassBottom,
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else ext.glassRimEnd)
                                ) {
                                    Text(
                                        text = term,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = termStartDate,
                            onValueChange = { termStartDate = it },
                            label = { Text("Term Resumption Start Date (YYYY-MM-DD)") },
                            singleLine = true,
                            colors = glassFieldColors(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 18.dp) {
                        MicroLabel("1-Click Dynamic Week Anchor (Monday-Locked)")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Tap any week to automatically anchor the Resumption Start Date so today lands in that exact academic week.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (1..12).forEach { w ->
                                OutlinedButton(
                                    onClick = {
                                        termStartDate = AcademicWeekEngine.getAnchorStartDateForWeek(w)
                                        manualOverride = "none"
                                    },
                                    shape = CircleShape
                                ) {
                                    Text("Wk $w")
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        MicroLabel("Manual Week Override")
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val opts = listOf("none" to "Auto Dynamic", "end_of_term" to "End of Term") +
                                (1..12).map { "$it" to "Week $it" }
                            opts.forEach { (key, label) ->
                                val selected = manualOverride == key
                                Surface(
                                    onClick = { manualOverride = key },
                                    shape = CircleShape,
                                    color = if (selected) ext.indigo else ext.glassBottom,
                                    contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    border = BorderStroke(1.dp, if (selected) ext.indigo else ext.glassRimEnd)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        GlowingPrimaryButton(
                            onClick = {
                                onSaveSettings(SystemSettings(sessionStr, activeTerm, termStartDate, manualOverride))
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_system_config_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save Academic Settings", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                if (userRole.isSuperAdmin) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 18.dp) {
                            MicroLabel("End-of-Term Maintenance", color = ext.rose)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Purge Cloud Evidence Bucket",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Clears old evidence media files from cloud storage at the conclusion of a term. Super Admin only.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { showPurgeConfirm = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = ext.roseContainer.copy(alpha = 0.45f),
                                    contentColor = ext.rose
                                ),
                                border = BorderStroke(1.dp, ext.rose.copy(alpha = 0.5f)),
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("purge_evidence_bucket_button")
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Purge Cloud Evidence Media", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPurgeConfirm) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirm = false },
            containerColor = if (ext.isDark) Color(0xFF151923) else Color(0xFFF6F8FC),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Purge Cloud Evidence Media?") },
            text = { Text("This deletes uploaded evidence images/videos/documents from cloud storage. Text report records remain intact.") },
            confirmButton = {
                Button(
                    onClick = {
                        showPurgeConfirm = false
                        onPurgeEvidenceBucket()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.rose)
                ) { Text("Purge Media") }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
