package com.example.ui.screens.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ComplianceTaxonomy
import com.example.data.model.ComplianceCheck
import com.example.data.model.ComplianceReport
import com.example.data.model.ReportResolutionStatus
import com.example.data.model.UserRole
import com.example.domain.AnalysisEngine
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowingPrimaryButton
import com.example.ui.components.MicroLabel
import com.example.ui.components.MonoCodeText
import com.example.ui.components.ResolutionBadge
import com.example.ui.components.SeenBadge
import com.example.ui.components.glassFieldColors
import com.example.ui.theme.ComplianceTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailSheet(
    report: ComplianceReport,
    userRole: UserRole,
    customChecks: List<ComplianceCheck>,
    onDismiss: () -> Unit,
    onToggleSeen: (ComplianceReport) -> Unit,
    onUpdateResolution: (ComplianceReport, ReportResolutionStatus, String) -> Unit,
    onRemapCheck: (ComplianceReport, ComplianceCheck) -> Unit,
    onEdit: (ComplianceReport) -> Unit,
    onDelete: (ComplianceReport) -> Unit,
    onResolveSignedUrl: suspend (String) -> Result<String>
) {
    val ext = ComplianceTheme.extended
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showEvidenceViewer by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRemapMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var selectedStatus by remember(report.id) { mutableStateOf(report.status) }
    var resolutionNotes by remember(report.id) { mutableStateOf(report.resolutionNotes.orEmpty()) }

    val div = AnalysisEngine.resolveDivision(report)
    val applicableChecks = remember(div, customChecks) {
        ComplianceTaxonomy.getApplicableChecks(div, report.category, customChecks)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (ext.isDark) Color(0xFF131720) else Color(0xFFF4F7FB),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonoCodeText(report.reportNumber)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SeenBadge(report.isSeen)
                    ResolutionBadge(report.status)
                }
            }

            Text(
                text = (report.staffName ?: report.assignedLocation ?: report.division).ifBlank { "Exception Record" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "${div.label} • ${report.term} • Week ${report.weekNumber} • ${report.incidentDate}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 16.dp) {
                MicroLabel("Compliance Check Cited")
                Spacer(Modifier.height(4.dp))
                Text(report.complianceCheck, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (!report.className.isNullOrBlank() || !report.subject.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = listOfNotNull(report.className, report.subject).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(10.dp))
                MicroLabel("Observed Exception")
                Spacer(Modifier.height(2.dp))
                Text(report.exceptionText.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                MicroLabel("Operational / Student Effect")
                Spacer(Modifier.height(2.dp))
                Text(report.effectText.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Reported by ${report.reporterName.ifBlank { "Officer" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Evidence Viewer Trigger
            if (!report.evidenceUrl.isNullOrBlank() && report.evidenceType != "none") {
                OutlinedButton(
                    onClick = { showEvidenceViewer = true },
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("view_report_evidence_button")
                ) {
                    Icon(Icons.Default.Attachment, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("View ${report.evidenceType.replaceFirstChar { it.uppercase() }} Evidence", fontWeight = FontWeight.Bold)
                }
            }

            // Action Controls
            val canEdit = userRole.isAdminOrAbove || !report.isSeen
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { if (canEdit) onEdit(report) },
                    enabled = canEdit,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("edit_report_button")
                ) {
                    Icon(if (canEdit) Icons.Default.Edit else Icons.Default.Lock, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (canEdit) "Edit" else "Reviewed (Locked)")
                }

                if (userRole.isAdminOrAbove) {
                    Button(
                        onClick = { onToggleSeen(report) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (report.isSeen) ext.amber else ext.emerald,
                            contentColor = if (ext.isDark) Color(0xFF0A0C10) else Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("toggle_seen_button")
                    ) {
                        Icon(if (report.isSeen) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (report.isSeen) "Mark Unseen" else "Mark Seen", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (userRole.isAdminOrAbove) {
                HorizontalDivider(color = ext.glassRimEnd)
                MicroLabel("Admin Moderation & Resolution")

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showStatusMenu = true },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Stage: ${selectedStatus.label}")
                        DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                            ReportResolutionStatus.entries.forEach { st ->
                                DropdownMenuItem(
                                    text = { Text(st.label) },
                                    onClick = {
                                        selectedStatus = st
                                        showStatusMenu = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { showRemapMenu = true },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Remap Standard")
                        DropdownMenu(expanded = showRemapMenu, onDismissRequest = { showRemapMenu = false }) {
                            applicableChecks.forEach { chk ->
                                DropdownMenuItem(
                                    text = { Text(chk.name, maxLines = 1) },
                                    onClick = {
                                        showRemapMenu = false
                                        onRemapCheck(report, chk)
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = resolutionNotes,
                    onValueChange = { resolutionNotes = it },
                    label = { Text("Directorate Resolution Notes") },
                    minLines = 2,
                    colors = glassFieldColors(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                GlowingPrimaryButton(
                    onClick = { onUpdateResolution(report, selectedStatus, resolutionNotes) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_resolution_button")
                ) {
                    Text("Save Resolution Update", fontWeight = FontWeight.ExtraBold)
                }

                if (userRole.isSuperAdmin) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.rose),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("delete_report_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Delete Exception Permanently")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showEvidenceViewer && !report.evidenceUrl.isNullOrBlank()) {
        EvidenceViewerDialog(
            storedPath = report.evidenceUrl,
            evidenceType = report.evidenceType,
            evidenceName = report.evidenceName ?: report.complianceCheck,
            inspectorName = report.reporterName.ifBlank { "Compliance Officer" },
            onResolveSignedUrl = onResolveSignedUrl,
            onVerdict = { _, verdict, note ->
                when (verdict) {
                    EvidenceVerdict.PASS -> {
                        selectedStatus = ReportResolutionStatus.RESOLVED
                        if (userRole.isAdminOrAbove) {
                            onUpdateResolution(report, ReportResolutionStatus.RESOLVED, resolutionNotes)
                        }
                    }
                    EvidenceVerdict.FLAG -> {
                        selectedStatus = ReportResolutionStatus.ACTION_REQUIRED
                        if (userRole.isAdminOrAbove) {
                            onUpdateResolution(report, ReportResolutionStatus.ACTION_REQUIRED, resolutionNotes)
                        }
                    }
                    EvidenceVerdict.NOTE -> {
                        if (!note.isNullOrBlank()) {
                            val updatedNotes = if (resolutionNotes.isBlank()) note else "$resolutionNotes\n$note"
                            resolutionNotes = updatedNotes
                            if (userRole.isAdminOrAbove) {
                                onUpdateResolution(report, selectedStatus, updatedNotes)
                            }
                        }
                    }
                }
            },
            onDismiss = { showEvidenceViewer = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = if (ext.isDark) Color(0xFF151923) else Color(0xFFF6F8FC),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Delete Compliance Exception?") },
            text = { Text("This action permanently removes ${report.reportNumber} from the cloud archive.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(report)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.rose)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
