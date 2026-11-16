package com.example.ui.screens.exceptions

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.model.ComplianceReport
import com.example.data.model.OfflineDraft
import com.example.domain.AnalysisEngine
import com.example.ui.components.LivingAtmosphericBackground
import com.example.ui.components.StatusGlassChip
import com.example.ui.components.TangerineGlowButton
import com.example.ui.components.complianceGlassCard
import com.example.ui.components.glassFieldColors
import com.example.ui.screens.dashboard.RecentReportCard
import com.example.ui.screens.reports.EvidencePreviewItem
import com.example.ui.screens.reports.EvidencePreviewerScaffold
import com.example.ui.theme.LocalCompliancePalette
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DraftMediaItem(
    val id: String,
    val pathOrUri: String,
    val mediaType: String,
    val capturedAtLabel: String
)

data class AdminExceptionFeedback(
    val resolutionNote: String? = null,
    val evidenceNotes: List<String> = emptyList(),
    val isRejected: Boolean = false
)

@Composable
fun ExceptionsScreen(
    currentUserId: String,
    reports: List<ComplianceReport>,
    drafts: List<OfflineDraft>,
    onNewException: () -> Unit,
    onConvertDraft: (OfflineDraft) -> Unit,
    onDeleteDraft: (String) -> Unit,
    onSaveCameraPhotoDraft: (File) -> Unit,
    onSaveCameraVideoDraft: (File) -> Unit,
    onSavePickedMediaDraft: (Uri, String) -> Unit,
    onSelectReport: (ComplianceReport) -> Unit,
    // Optional hooks for multi-media drafts, user self-edit & admin feedback display
    extraDraftMediaByDraftId: Map<String, List<DraftMediaItem>> = emptyMap(),
    onAddDraftMedia: (draft: OfflineDraft, pathOrUri: String, mediaType: String, capturedAtLabel: String) -> Unit = { _, _, _, _ -> },
    onRemoveDraftMedia: (draft: OfflineDraft, mediaId: String) -> Unit = { _, _ -> },
    onConvertDraftWithMedia: (draft: OfflineDraft, selectedMedia: List<DraftMediaItem>) -> Unit = { d, _ -> onConvertDraft(d) },
    onEditSubmittedException: (report: ComplianceReport, updatedCheck: String, updatedDetails: String, updatedDate: String) -> Unit = { _, _, _, _ -> },
    adminFeedbackByReportId: Map<String, AdminExceptionFeedback> = emptyMap()
) {
    val context = LocalContext.current
    val p = LocalCompliancePalette.current

    // Reset transient UI state whenever logged-in user switches
    var selectedTab by remember(currentUserId) { mutableIntStateOf(0) } // 0 = Offline Drafts, 1 = Submitted
    var search by remember(currentUserId) { mutableStateOf("") }
    var seenFilter by remember(currentUserId) { mutableStateOf("ALL") }

    val submittedListState = rememberLazyListState()
    val showSubmittedFilters by remember {
        derivedStateOf {
            submittedListState.firstVisibleItemIndex == 0 && submittedListState.firstVisibleItemScrollOffset < 36
        }
    }

    // Track per-user local multi-media additions/deletions on drafts seamlessly
    var localAddedMedia by remember(currentUserId) { mutableStateOf<Map<String, List<DraftMediaItem>>>(emptyMap()) }
    var localRemovedMediaIds by remember(currentUserId) { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }

    // Camera / Gallery target (null = new standalone draft, non-null = append into existing draft)
    var targetDraftForAppend by remember(currentUserId) { mutableStateOf<OfflineDraft?>(null) }
    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    var pendingVideoFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraAction by remember { mutableStateOf<String?>(null) }

    // Preview / Trim Review / User Edit modals
    var previewModalState by remember(currentUserId) { mutableStateOf<Pair<OfflineDraft, Int>?>(null) }
    var reviewTrimDraft by remember(currentUserId) { mutableStateOf<OfflineDraft?>(null) }
    var editingReport by remember(currentUserId) { mutableStateOf<ComplianceReport?>(null) }

    fun appendMediaToTargetDraft(draft: OfflineDraft, pathOrUri: String, mediaType: String) {
        val stamp = formatNowDateTime()
        val item = DraftMediaItem(
            id = "dm_${System.currentTimeMillis()}_${pathOrUri.hashCode()}",
            pathOrUri = pathOrUri,
            mediaType = mediaType,
            capturedAtLabel = stamp
        )
        val current = localAddedMedia[draft.id].orEmpty()
        localAddedMedia = localAddedMedia + (draft.id to (current + item))
        onAddDraftMedia(draft, pathOrUri, mediaType, stamp)
    }

    fun resolveDraftMediaList(draft: OfflineDraft): List<DraftMediaItem> {
        val removed = localRemovedMediaIds[draft.id].orEmpty()
        val base = if (!draft.mediaPath.isNullOrBlank()) {
            listOf(
                DraftMediaItem(
                    id = "primary_${draft.id}",
                    pathOrUri = draft.mediaPath,
                    mediaType = draft.mediaType,
                    capturedAtLabel = formatDraftTimestamp(draft.incidentDate, draft.mediaPath)
                )
            )
        } else {
            emptyList()
        }
        val extraFromRepo = extraDraftMediaByDraftId[draft.id].orEmpty()
        val extraLocal = localAddedMedia[draft.id].orEmpty()
        return (base + extraFromRepo + extraLocal)
            .distinctBy { it.pathOrUri }
            .filterNot { it.id in removed }
    }

    fun removeDraftMediaItem(draft: OfflineDraft, item: DraftMediaItem) {
        val updatedRemoved = localRemovedMediaIds[draft.id].orEmpty() + item.id
        localRemovedMediaIds = localRemovedMediaIds + (draft.id to updatedRemoved)
        val remainingLocal = localAddedMedia[draft.id].orEmpty().filterNot { it.id == item.id }
        localAddedMedia = localAddedMedia + (draft.id to remainingLocal)
        onRemoveDraftMedia(draft, item.id)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { ok ->
        val file = pendingPhotoFile
        val appendTarget = targetDraftForAppend
        pendingPhotoFile = null
        targetDraftForAppend = null
        if (ok && file != null && file.exists()) {
            if (appendTarget != null) {
                appendMediaToTargetDraft(appendTarget, file.absolutePath, "image")
            } else {
                onSaveCameraPhotoDraft(file)
            }
            selectedTab = 0
        }
    }

    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { ok ->
        val file = pendingVideoFile
        val appendTarget = targetDraftForAppend
        pendingVideoFile = null
        targetDraftForAppend = null
        if (ok && file != null && file.exists()) {
            if (appendTarget != null) {
                appendMediaToTargetDraft(appendTarget, file.absolutePath, "video")
            } else {
                onSaveCameraVideoDraft(file)
            }
            selectedTab = 0
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val appendTarget = targetDraftForAppend
        targetDraftForAppend = null
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val mediaType = if (mime.startsWith("video/")) "video" else "image"
            if (appendTarget != null) {
                appendMediaToTargetDraft(appendTarget, uri.toString(), mediaType)
            } else {
                onSavePickedMediaDraft(uri, mediaType)
            }
            selectedTab = 0
        }
    }

    fun launchPhotoCapture() {
        val dir = File(context.cacheDir, "cam_capture_${currentUserId.ifBlank { "guest" }}").apply { if (!exists()) mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        pendingPhotoFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        takePictureLauncher.launch(uri)
    }

    fun launchVideoCapture() {
        val dir = File(context.cacheDir, "cam_capture_${currentUserId.ifBlank { "guest" }}").apply { if (!exists()) mkdirs() }
        val file = File(dir, "video_${System.currentTimeMillis()}.mp4")
        pendingVideoFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        captureVideoLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingCameraAction
        pendingCameraAction = null
        if (granted) {
            if (action == "video") launchVideoCapture() else launchPhotoCapture()
        } else {
            targetDraftForAppend = null
        }
    }

    fun requestCameraAndRun(mode: String, appendTo: OfflineDraft? = null) {
        targetDraftForAppend = appendTo
        val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            if (mode == "video") launchVideoCapture() else launchPhotoCapture()
        } else {
            pendingCameraAction = mode
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val mySubmitted = remember(reports, currentUserId, search, seenFilter) {
        val q = search.trim().lowercase()
        reports.filter { r ->
            val mine = currentUserId.isNotBlank() && r.reportedBy.equals(currentUserId, ignoreCase = true)
            if (!mine) return@filter false
            if (seenFilter == "SEEN" && !r.isSeen) return@filter false
            if (seenFilter == "UNSEEN" && r.isSeen) return@filter false
            if (q.isNotBlank()) {
                val hay = "${r.staffName.orEmpty()} ${r.complianceCheck} ${r.exceptionText} ${r.className.orEmpty()}".lowercase()
                if (!hay.contains(q)) return@filter false
            }
            true
        }
    }

    LivingAtmosphericBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. Header + Quick Capture + Glass Segmented Switcher
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FIELD REPORTING",
                            color = p.textSecondary,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Exceptions & Drafts",
                            color = p.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .shadow(14.dp, CircleShape, spotColor = p.tangerine.copy(alpha = 0.45f))
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(p.tangerine, p.tangerineEnd)))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            .clickable(onClick = onNewException)
                            .padding(horizontal = 14.dp)
                            .testTag("new_exception_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("New Exception", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }

                // 3 Frosted Glass Quick-Capture Tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CaptureGlassTile(
                        icon = Icons.Rounded.CameraAlt,
                        label = "Photo",
                        tint = p.tangerine,
                        onClick = { requestCameraAndRun("photo") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_camera_photo_button")
                    )
                    CaptureGlassTile(
                        icon = Icons.Rounded.Videocam,
                        label = "Video",
                        tint = p.emerald,
                        onClick = { requestCameraAndRun("video") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_camera_video_button")
                    )
                    CaptureGlassTile(
                        icon = Icons.Rounded.PhotoLibrary,
                        label = "Gallery",
                        tint = p.textPrimary,
                        onClick = {
                            targetDraftForAppend = null
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_gallery_pick_button")
                    )
                }

                // Frosted Glass Segmented Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .complianceGlassCard(50.dp)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlassSegmentItem(
                        label = "Offline Drafts (${drafts.size})",
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_offline_drafts")
                    )
                    GlassSegmentItem(
                        label = "Submitted (${mySubmitted.size})",
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_submitted_exceptions")
                    )
                }
            }

            if (selectedTab == 0) {
                // OFFLINE DRAFTS TAB (Multi-evidence, Date+Time stamp, Preview/Delete/Add, 1-3 Gate)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (drafts.isEmpty()) {
                        item {
                            GlassEmptyStateCard(
                                icon = Icons.Rounded.CameraAlt,
                                title = "No Offline Drafts",
                                subtitle = "Capture photos or videos above—even offline. Add multiple items to a draft, then select 1 to 3 when submitting."
                            )
                        }
                    } else {
                        items(drafts, key = { it.id }) { draft ->
                            val mediaList = resolveDraftMediaList(draft)
                            val primaryTime = mediaList.firstOrNull()?.capturedAtLabel
                                ?: formatDraftTimestamp(draft.incidentDate, draft.mediaPath)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .complianceGlassCard(24.dp)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Header: Date + Exact Time + Delete Draft
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        StatusGlassChip(
                                            dotColor = if (mediaList.size > 3) p.tangerine else p.emerald,
                                            text = "${mediaList.size} Evidence"
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Schedule,
                                                contentDescription = null,
                                                tint = p.textSecondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = primaryTime,
                                                color = p.textSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = TextStyle(fontFeatureSettings = "tnum")
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { onDeleteDraft(draft.id) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("delete_draft_${draft.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Delete Draft",
                                            tint = p.rose,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = draft.title.ifBlank { "Offline Field Draft" },
                                        color = p.textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (draft.notes.isNotBlank()) {
                                        Text(
                                            text = draft.notes,
                                            color = p.textSecondary,
                                            fontSize = 13.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Multi-Evidence Thumbnail Strip (Tap to Preview, X to Delete)
                                if (mediaList.isNotEmpty()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(mediaList, key = { idx, item -> "${item.id}_$idx" }) { idx, item ->
                                            DraftMediaThumbTile(
                                                item = item,
                                                onPreview = { previewModalState = draft to idx },
                                                onDelete = { removeDraftMediaItem(draft, item) }
                                            )
                                        }
                                    }
                                }

                                // Add More Media Bar (Camera Photo / Camera Video / Gallery)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SmallGlassActionChip(
                                        icon = Icons.Rounded.CameraAlt,
                                        label = "+ Photo",
                                        onClick = { requestCameraAndRun("photo", appendTo = draft) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    SmallGlassActionChip(
                                        icon = Icons.Rounded.Videocam,
                                        label = "+ Video",
                                        onClick = { requestCameraAndRun("video", appendTo = draft) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    SmallGlassActionChip(
                                        icon = Icons.Rounded.PhotoLibrary,
                                        label = "+ Gallery",
                                        onClick = {
                                            targetDraftForAppend = draft
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (mediaList.size > 3) {
                                    Text(
                                        text = "Review required: select 1–3 evidence items before submitting.",
                                        color = p.tangerine,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                TangerineGlowButton(
                                    text = if (mediaList.size > 3) "Review & Submit (${mediaList.size} items)" else "Convert to Exception Report",
                                    onClick = {
                                        when {
                                            mediaList.size > 3 -> reviewTrimDraft = draft
                                            mediaList.isEmpty() -> onConvertDraft(draft)
                                            else -> onConvertDraftWithMedia(draft, mediaList)
                                        }
                                    },
                                    modifier = Modifier.testTag("convert_draft_${draft.id}")
                                )
                            }
                        }
                    }
                }
            } else {
                // SUBMITTED EXCEPTIONS TAB (Dynamic collapsible Search/Seen + User Edit + Admin Notes)
                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = showSubmittedFilters,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassSearchBox(
                                value = search,
                                onValueChange = { search = it },
                                placeholder = "Search submitted exceptions..."
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("ALL" to "All", "UNSEEN" to "Unseen", "SEEN" to "Seen").forEach { (key, label) ->
                                    val selected = seenFilter == key
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .then(
                                                if (selected) {
                                                    Modifier
                                                        .background(Brush.horizontalGradient(listOf(p.tangerine, p.tangerineEnd)))
                                                        .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                                                } else {
                                                    Modifier
                                                        .background(p.trackFill)
                                                        .border(1.dp, p.borderBottom, CircleShape)
                                                }
                                            )
                                            .clickable { seenFilter = key }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) Color.White else p.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    LazyColumn(
                        state = submittedListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (mySubmitted.isEmpty()) {
                            item {
                                GlassEmptyStateCard(
                                    icon = Icons.AutoMirrored.Rounded.Send,
                                    title = "No Submitted Exceptions",
                                    subtitle = "Tap 'New Exception' above to log a formal compliance report."
                                )
                            }
                        } else {
                            items(mySubmitted, key = { it.id }) { report ->
                                val feedback = adminFeedbackByReportId[report.id]
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .complianceGlassCard(24.dp)
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RecentReportCard(
                                        report = report,
                                        resolvedDivision = AnalysisEngine.resolveDivision(report),
                                        onClick = { onSelectReport(report) }
                                    )

                                    // Admin Rejection / Evidence Notes (Visible to Reporting User)
                                    if (feedback != null && (!feedback.resolutionNote.isNullOrBlank() || feedback.evidenceNotes.isNotEmpty())) {
                                        val noteAccent = if (feedback.isRejected) p.rose else p.tangerine
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(noteAccent.copy(alpha = 0.12f))
                                                .border(1.dp, noteAccent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Feedback,
                                                    contentDescription = null,
                                                    tint = noteAccent,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = if (feedback.isRejected) "ADMIN REJECTION NOTE" else "ADMIN REVIEW NOTE",
                                                    color = noteAccent,
                                                    fontSize = 10.sp,
                                                    letterSpacing = 1.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (!feedback.resolutionNote.isNullOrBlank()) {
                                                Text(
                                                    text = feedback.resolutionNote,
                                                    color = p.textPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            feedback.evidenceNotes.forEach { evNote ->
                                                Text(
                                                    text = "• $evNote",
                                                    color = p.textSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }

                                    // Unified User-Only Edit Trigger (Zero Admin Controls)
                                    HorizontalDivider(thickness = 0.7.dp, color = p.borderBottom)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (report.isSeen) "Reviewed by Directorate • Changes locked" else "Tap card to inspect • Edit before review",
                                            color = p.textSecondary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (report.isSeen) {
                                            Row(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(p.emerald.copy(alpha = 0.14f))
                                                    .border(1.dp, p.emerald.copy(alpha = 0.35f), CircleShape)
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                                    .testTag("exception_locked_badge_${report.id}"),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Lock,
                                                    contentDescription = "Reviewed and Locked",
                                                    tint = p.emerald,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = "Reviewed",
                                                    color = p.emerald,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(p.tangerine.copy(alpha = 0.15f))
                                                    .border(1.dp, p.tangerine.copy(alpha = 0.35f), CircleShape)
                                                    .clickable { editingReport = report }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                                    .testTag("user_edit_exception_${report.id}"),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Edit,
                                                    contentDescription = null,
                                                    tint = p.tangerine,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "Edit Exception",
                                                    color = p.tangerine,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
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

    // Full-Screen Draft Evidence Previewer Dialog (Image Zoom + Video Scrubbing + Delete)
    previewModalState?.let { (draft, initialIdx) ->
        val mediaList = resolveDraftMediaList(draft)
        if (mediaList.isNotEmpty()) {
            val previewItems = mediaList.map {
                EvidencePreviewItem(
                    id = it.id,
                    storedPath = it.pathOrUri,
                    mediaType = it.mediaType,
                    title = "${draft.title.ifBlank { "Draft Evidence" }} • ${it.capturedAtLabel}",
                    inspectorName = "Offline Draft"
                )
            }
            Dialog(
                onDismissRequest = { previewModalState = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                EvidencePreviewerScaffold(
                    items = previewItems,
                    activeIndex = initialIdx.coerceIn(0, previewItems.lastIndex),
                    activeUrl = previewItems.getOrNull(initialIdx.coerceIn(0, previewItems.lastIndex))?.storedPath,
                    isLoading = false,
                    errorMessage = null,
                    onSelectIndex = { nextIdx -> previewModalState = draft to nextIdx },
                    onOpenExternal = {},
                    onVerdict = { _, _ -> },
                    onBack = { previewModalState = null }
                )
            }
        } else {
            previewModalState = null
        }
    }

    // Mandatory 1-to-3 Evidence Review & Trim Modal (Draft -> Report Gate)
    reviewTrimDraft?.let { draft ->
        val mediaList = resolveDraftMediaList(draft)
        AlertDialog(
            onDismissRequest = { reviewTrimDraft = null },
            containerColor = if (p.isDark) Color(0xFF151923) else Color(0xFFF6F8FC),
            titleContentColor = p.textPrimary,
            textContentColor = p.textSecondary,
            icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = p.tangerine) },
            title = { Text("Select 1–3 Evidence Items", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Your draft has ${mediaList.size} attachments. Delete extras until 1 to 3 evidence items remain before submitting.",
                        fontSize = 13.sp,
                        color = p.textSecondary
                    )
                    LazyColumn(
                        modifier = Modifier.height(230.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(mediaList, key = { idx, it -> "${it.id}_$idx" }) { idx, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(p.trackFill)
                                    .clickable { previewModalState = draft to idx }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DraftMediaMiniPreview(item)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.mediaType.uppercase(), color = p.tangerine, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(item.capturedAtLabel, color = p.textPrimary, fontSize = 12.sp, maxLines = 1)
                                }
                                IconButton(
                                    enabled = mediaList.size > 1,
                                    onClick = { removeDraftMediaItem(draft, item) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Remove item",
                                        tint = if (mediaList.size > 1) p.rose else p.textSecondary.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = mediaList.size in 1..3,
                    colors = ButtonDefaults.buttonColors(containerColor = p.tangerine, contentColor = Color.White),
                    onClick = {
                        reviewTrimDraft = null
                        onConvertDraftWithMedia(draft, mediaList)
                    }
                ) {
                    Text("Proceed (${mediaList.size}/3)", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { reviewTrimDraft = null }) {
                    Text("Cancel", color = p.textSecondary)
                }
            }
        )
    }

    // Unified User-Only Exception Edit Dialog (Calendar Date Picker, Zero Typed Dates, Zero Admin Controls)
    editingReport?.let { target ->
        UserExceptionEditDialog(
            report = target,
            onDismiss = { editingReport = null },
            onSave = { updatedCheck, updatedDetails, updatedDate ->
                onEditSubmittedException(target, updatedCheck, updatedDetails, updatedDate)
                editingReport = null
            }
        )
    }
}

@Composable
private fun UserExceptionEditDialog(
    report: ComplianceReport,
    onDismiss: () -> Unit,
    onSave: (updatedCheck: String, updatedDetails: String, updatedDate: String) -> Unit
) {
    val context = LocalContext.current
    val p = LocalCompliancePalette.current

    var checkTitle by remember(report.id) { mutableStateOf(report.complianceCheck) }
    var detailsText by remember(report.id) { mutableStateOf(report.exceptionText) }
    var selectedDate by remember(report.id) {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }

    fun openSystemCalendar() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (p.isDark) Color(0xFF151923) else Color(0xFFF6F8FC),
        titleContentColor = p.textPrimary,
        textContentColor = p.textSecondary,
        title = {
            Text("Edit Your Exception", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Update your compliance check title, observation notes, or incident date.",
                    fontSize = 12.sp,
                    color = p.textSecondary
                )
                OutlinedTextField(
                    value = checkTitle,
                    onValueChange = { checkTitle = it },
                    label = { Text("Compliance Check") },
                    singleLine = true,
                    colors = glassFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = detailsText,
                    onValueChange = { detailsText = it },
                    label = { Text("Exception Details") },
                    minLines = 3,
                    colors = glassFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                // Read-only System Calendar Date Field (Cannot be typed)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.trackFill)
                        .border(1.dp, p.borderBottom, RoundedCornerShape(14.dp))
                        .clickable { openSystemCalendar() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("INCIDENT DATE (CALENDAR)", color = p.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(selectedDate, color = p.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = "Pick Date", tint = p.tangerine)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = checkTitle.isNotBlank() && detailsText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = p.tangerine, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                onClick = { onSave(checkTitle.trim(), detailsText.trim(), selectedDate) }
            ) {
                Text("Save Changes", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = p.textSecondary)
            }
        }
    )
}

@Composable
private fun DraftMediaThumbTile(
    item: DraftMediaItem,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    val p = LocalCompliancePalette.current
    Box(
        modifier = Modifier
            .size(width = 88.dp, height = 74.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(p.trackFill)
            .border(1.dp, p.borderTop.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable(onClick = onPreview)
    ) {
        DraftMediaMiniPreview(item = item, modifier = Modifier.fillMaxSize())

        // Delete badge on top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Remove preview", tint = Color.White, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
private fun DraftMediaMiniPreview(
    item: DraftMediaItem,
    modifier: Modifier = Modifier.size(48.dp)
) {
    val p = LocalCompliancePalette.current
    val isVideo = item.mediaType.equals("video", ignoreCase = true)
    val isImage = item.mediaType.equals("image", ignoreCase = true)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(p.trackFill),
        contentAlignment = Alignment.Center
    ) {
        if (isImage) {
            val model: Any = if (item.pathOrUri.startsWith("/")) File(item.pathOrUri) else item.pathOrUri
            AsyncImage(
                model = model,
                contentDescription = "Draft preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = if (isVideo) Icons.Rounded.PlayCircle else Icons.Rounded.AttachFile,
                contentDescription = item.mediaType,
                tint = if (isVideo) p.tangerine else p.textPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun SmallGlassActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.trackFill)
            .border(1.dp, p.borderBottom, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = p.tangerine, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = p.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun CaptureGlassTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    Row(
        modifier = modifier
            .complianceGlassCard(18.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.textPrimary, maxLines = 1)
    }
}

@Composable
private fun GlassSegmentItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    Box(
        modifier = modifier
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(Brush.horizontalGradient(listOf(p.tangerine, p.tangerineEnd)))
                else Modifier.background(Color.Transparent)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else p.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GlassSearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    val p = LocalCompliancePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .complianceGlassCard(18.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = p.textSecondary, modifier = Modifier.size(18.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, color = p.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = p.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(p.tangerine),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Clear",
                tint = p.textSecondary,
                modifier = Modifier.size(18.dp).clickable { onValueChange("") }
            )
        }
    }
}

@Composable
private fun GlassEmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val p = LocalCompliancePalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .complianceGlassCard(26.dp)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(p.trackFill)
                .border(1.dp, p.borderTop, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = p.tangerine, modifier = Modifier.size(26.dp))
        }
        Text(title, color = p.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = p.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

private fun formatNowDateTime(): String =
    SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US).format(Date())

private fun formatDraftTimestamp(incidentDate: String, mediaPath: String?): String {
    val file = mediaPath?.takeIf { it.startsWith("/") }?.let(::File)?.takeIf { it.exists() }
    return if (file != null) {
        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US).format(Date(file.lastModified()))
    } else {
        incidentDate.ifBlank { formatNowDateTime() }
    }
}