package com.example.ui.screens.reports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun EvidenceViewerDialog(
    storedPath: String,
    evidenceType: String,
    evidenceName: String?,
    inspectorName: String = "Compliance Officer",
    items: List<EvidencePreviewItem>? = null,
    initialIndex: Int = 0,
    showModerationControls: Boolean = true,
    onResolveSignedUrl: suspend (String) -> Result<String>,
    onResolveHeaders: (String) -> Map<String, String> = { emptyMap() },
    onDownloadEvidence: ((EvidencePreviewItem) -> Unit)? = null,
    onVerdict: ((EvidencePreviewItem, EvidenceVerdict, String?) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val previewItems = remember(storedPath, evidenceType, evidenceName, inspectorName, items) {
        if (!items.isNullOrEmpty()) {
            items
        } else {
            listOf(
                EvidencePreviewItem(
                    id = storedPath,
                    storedPath = storedPath,
                    mediaType = evidenceType,
                    title = evidenceName?.ifBlank { "Compliance Evidence" } ?: "Compliance Evidence",
                    inspectorName = inspectorName.ifBlank { "Compliance Officer" }
                )
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        EvidencePreviewerScreen(
            items = previewItems,
            initialIndex = initialIndex,
            showModerationControls = showModerationControls,
            onResolveSignedUrl = onResolveSignedUrl,
            onResolveHeaders = onResolveHeaders,
            onDownloadEvidence = onDownloadEvidence,
            onVerdict = onVerdict,
            onBack = onDismiss
        )
    }
}
