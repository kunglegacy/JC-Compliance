package com.example.ui.screens.reports

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Feedback
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
import androidx.compose.material3.Surface
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
import com.example.ui.components.EditedBadgePill
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
    onResolveSignedUrl: suspend (String) -> Result<String>,
    onSaveEvidenceFeedback: (ComplianceReport, Int, EvidenceVerdict, String?) -> Unit = { _, _, _, _ -> }
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (report.isEdited) {
                        EditedBadgePill()
                    }
                    SeenBadge(report.isSeen, report.isPending)
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

            val evidenceUrls = remember(report.id, report.evidenceUrl, report.evidenceUrls, report.evidenceType) {
                report.evidenceUrls.ifEmpty {
                    listOfNotNull(report.evidenceUrl?.takeIf { it.isNotBlank() && report.evidenceType != "none" })
                }
            }
            val evidenceItems = remember(report.id, evidenceUrls, report.evidenceTypes, report.evidenceType, report.evidenceName, report.complianceCheck, report.reporterName) {
                evidenceUrls.mapIndexed { idx, path ->
                    val type = report.evidenceTypes.getOrNull(idx)?.takeIf { it.isNotBlank() && it != "none" }
                        ?: report.evidenceType.takeIf { it.isNotBlank() && it != "none" }
                        ?: "image"
                    EvidencePreviewItem(
                        id = "${report.id}_ev_$idx",
                        storedPath = path,
                        mediaType = type,
                        title = if (evidenceUrls.size > 1) "${report.evidenceName ?: report.complianceCheck} (${idx + 1}/${evidenceUrls.size})"
                        else (report.evidenceName ?: report.complianceCheck),
                        inspectorName = report.reporterName.ifBlank { "Compliance Officer" }
                    )
                }
            }

            // Visible Directorate Review / Rejection Notes for Reporters & Admins
            if (!report.resolutionNotes.isNullOrBlank() || report.evidenceNotes.isNotEmpty()) {
                val isRejectedOrFlagged = report.status == ReportResolutionStatus.REJECTED ||
                    report.status == ReportResolutionStatus.ACTION_REQUIRED
                val accent = if (isRejectedOrFlagged) ext.rose else ext.amber
                Surface(
                    color = accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
                    modifier = Modifier.fillMaxWidth().testTag("report_detail_admin_feedback")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Feedback, contentDescription = null, tint = accent)
                            MicroLabel(
                                text = when {
                                    isRejectedOrFlagged -> "Directorate Rejection / Action Feedback"
                                    report.isPending -> "Pending • Admin / Media Note"
                                    else -> "Directorate Review Notes"
                                },
                                color = accent
                            )
                        }
                        if (!report.resolutionNotes.isNullOrBlank()) {
                            Text(
                                text = report.resolutionNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        report.evidenceNotes.forEach { evNote ->
                            Text(
                                text = "• $evNote",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Evidence Viewer Trigger
            if (evidenceItems.isNotEmpty()) {
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
                    Text(
                        text = if (evidenceItems.size > 1) "View ${evidenceItems.size} Evidence Items"
                        else "View ${evidenceItems.first().mediaType.replaceFirstChar { it.uppercase() }} Evidence",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Action Controls
            val canEdit = userRole.isAdminOrAbove || report.canUserEdit
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
                    Text(
                        when {
                            canEdit && report.isActionRequired -> "Update (Action Required)"
                            canEdit && report.isPending -> "Update (Pending)"
                            canEdit -> "Edit"
                            report.isAccepted -> "Accepted (Locked)"
                            else -> "Rejected (Locked)"
                        },
                        maxLines = 1
                    )
                }

                if (userRole.isAdminOrAbove) {
                    val markAsReviewed = !report.isSeen || report.isPending
                    Button(
                        onClick = { onToggleSeen(report) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (markAsReviewed) ext.emerald else ext.amber,
                            contentColor = if (ext.isDark) Color(0xFF0A0C10) else Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("toggle_seen_button")
                    ) {
                        Icon(if (markAsReviewed) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when {
                                report.isPending -> "Accept & Mark Seen"
                                report.isSeen -> "Mark Unseen"
                                else -> "Mark Seen"
                            },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            if (userRole.isAdminOrAbove) {
                HorizontalDivider(color = ext.glassRimEnd)
                MicroLabel("Admin Moderation & Status")

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showStatusMenu = true },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Status: ${selectedStatus.label}")
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
                    label = { Text("Directorate Review / Action Notes") },
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
                    Text("Save Status Update", fontWeight = FontWeight.ExtraBold)
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

    val evidenceUrlsForViewer = report.evidenceUrls.ifEmpty {
        listOfNotNull(report.evidenceUrl?.takeIf { it.isNotBlank() && report.evidenceType != "none" })
    }
    val viewerItems = remember(
        report.id,
        evidenceUrlsForViewer,
        report.evidenceTypes,
        report.evidenceType,
        report.evidenceName,
        report.complianceCheck,
        report.reporterName,
        report.evidenceNotes
    ) {
        evidenceUrlsForViewer.mapIndexed { idx, path ->
            val type = report.evidenceTypes.getOrNull(idx)?.takeIf { it.isNotBlank() && it != "none" }
                ?: report.evidenceType.takeIf { it.isNotBlank() && it != "none" }
                ?: "image"
            val itemNote = report.evidenceNotes
                .filter { it.startsWith("[Evidence #${idx + 1}") }
                .joinToString("\n")
                .takeIf { it.isNotBlank() }
            EvidencePreviewItem(
                id = "${report.id}_ev_$idx",
                storedPath = path,
                mediaType = type,
                title = if (evidenceUrlsForViewer.size > 1) "${report.evidenceName ?: report.complianceCheck} (${idx + 1}/${evidenceUrlsForViewer.size})"
                else (report.evidenceName ?: report.complianceCheck),
                inspectorName = report.reporterName.ifBlank { "Compliance Officer" },
                existingNote = itemNote
            )
        }
    }

    if (showEvidenceViewer && viewerItems.isNotEmpty()) {
        EvidenceViewerDialog(
            storedPath = viewerItems.first().storedPath,
            evidenceType = viewerItems.first().mediaType,
            evidenceName = report.evidenceName ?: report.complianceCheck,
            inspectorName = report.reporterName.ifBlank { "Compliance Officer" },
            items = viewerItems,
            showModerationControls = userRole.isAdminOrAbove,
            onResolveSignedUrl = onResolveSignedUrl,
            onVerdict = { item, verdict, note ->
                val idx = viewerItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                when (verdict) {
                    EvidenceVerdict.PASS -> selectedStatus = ReportResolutionStatus.ACCEPTED
                    EvidenceVerdict.FLAG -> selectedStatus = ReportResolutionStatus.ACTION_REQUIRED
                    EvidenceVerdict.NOTE -> {
                        selectedStatus = ReportResolutionStatus.PENDING
                        if (!note.isNullOrBlank()) {
                            val tag = "[Evidence #${idx + 1} • ${verdict.name}] ${note.trim()}"
                            resolutionNotes = if (resolutionNotes.isBlank()) tag else "$resolutionNotes\n$tag"
                        }
                    }
                }
                if (userRole.isAdminOrAbove) {
                    onSaveEvidenceFeedback(report, idx, verdict, note)
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
