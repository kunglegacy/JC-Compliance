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
    onResolveSignedUrl: suspend (String) -> Result<String>,
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
            onResolveSignedUrl = onResolveSignedUrl,
            onVerdict = onVerdict,
            onBack = onDismiss
        )
    }
}
