package com.example.ui.screens.exceptions

import android.Manifest
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.data.model.ComplianceDivision
import com.example.data.model.ComplianceReport
import com.example.data.model.DraftMediaItem
import com.example.data.model.MediaUploadLimits
import com.example.data.model.OfflineDraft
import com.example.data.model.ReportFormDraft
import com.example.data.model.ReportResolutionStatus
import com.example.domain.AnalysisEngine
import com.example.domain.BackgroundExecutionHelper
import com.example.ui.components.LivingAtmosphericBackground
import com.example.ui.components.StatusGlassChip
import com.example.ui.components.TangerineGlowButton
import com.example.ui.components.VideoFrameThumbnail
import com.example.ui.components.complianceGlassCard
import com.example.ui.screens.dashboard.RecentReportCard
import com.example.ui.screens.reports.EvidencePreviewItem
import com.example.ui.screens.reports.EvidencePreviewerScaffold
import com.example.ui.theme.LocalCompliancePalette
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AdminExceptionFeedback(
    val resolutionNote: String? = null,
    val evidenceNotes: List<String> = emptyList(),
    val isRejected: Boolean = false,
    val isPending: Boolean = false
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
    extraDraftMediaByDraftId: Map<String, List<DraftMediaItem>> = emptyMap(),
    onAddDraftMedia: (draft: OfflineDraft, pathOrUri: String, mediaType: String, capturedAtLabel: String) -> Unit = { _, _, _, _ -> },
    onRemoveDraftMedia: (draft: OfflineDraft, mediaId: String) -> Unit = { _, _ -> },
    onConvertDraftWithMedia: (draft: OfflineDraft, selectedMedia: List<DraftMediaItem>) -> Unit = { d, _ -> onConvertDraft(d) },
    onOpenEditForm: (ComplianceReport) -> Unit = {},
    savedFormDraft: ReportFormDraft? = null,
    onResumeFormDraft: () -> Unit = {},
    onClearFormDraft: () -> Unit = {},
    adminFeedbackByReportId: Map<String, AdminExceptionFeedback> = emptyMap(),
    onShowLimitWarning: (String) -> Unit = {},
    onWillLaunchExternalPicker: () -> Unit = {},
    isOnline: Boolean = true,
    onAddOfflineFormDraft: () -> Unit = {},
    onEditFormDraft: (OfflineDraft) -> Unit = {},
    onCancelQueuedDraft: (OfflineDraft) -> Unit = {},
    onRetryQueuedSubmissions: () -> Unit = {}
) {
    val context = LocalContext.current
    val p = LocalCompliancePalette.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var bgRestrictionStatus by remember { mutableStateOf(BackgroundExecutionHelper.checkStatus(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                bgRestrictionStatus = BackgroundExecutionHelper.checkStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Reset transient UI state whenever logged-in user switches
    var selectedTab by remember(currentUserId) { mutableIntStateOf(0) } // 0 = Offline Drafts, 1 = Submitted
    var search by remember(currentUserId) { mutableStateOf("") }
    var statusFilter by remember(currentUserId) { mutableStateOf("ALL") }

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

    // Preview / Trim Review / Evidence Delete Confirm
    var previewModalState by remember(currentUserId) { mutableStateOf<Pair<OfflineDraft, Int>?>(null) }
    var reviewTrimDraft by remember(currentUserId) { mutableStateOf<OfflineDraft?>(null) }
    var pendingDeleteDraftMedia by remember(currentUserId) { mutableStateOf<Pair<OfflineDraft, DraftMediaItem>?>(null) }

    fun resolveDraftMediaList(draft: OfflineDraft): List<DraftMediaItem> {
        val removed = localRemovedMediaIds[draft.id].orEmpty()
        val base = if (draft.mediaItems.isNotEmpty()) {
            draft.mediaItems
        } else if (!draft.mediaPath.isNullOrBlank()) {
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

    fun canAppendToDraft(draft: OfflineDraft, mediaType: String): Boolean {
        val media = resolveDraftMediaList(draft)
        val imgs = media.count { it.mediaType.equals("image", ignoreCase = true) }
        val vids = media.count { it.mediaType.equals("video", ignoreCase = true) }
        if (mediaType.equals("image", ignoreCase = true) && imgs >= MediaUploadLimits.MAX_IMAGES_PER_REPORT) {
            onShowLimitWarning("Maximum ${MediaUploadLimits.MAX_IMAGES_PER_REPORT} photos allowed per draft.")
            return false
        }
        if (mediaType.equals("video", ignoreCase = true) && vids >= MediaUploadLimits.MAX_VIDEOS_PER_REPORT) {
            onShowLimitWarning("Maximum ${MediaUploadLimits.MAX_VIDEOS_PER_REPORT} videos allowed per draft.")
            return false
        }
        return true
    }

    fun appendMediaToTargetDraft(draft: OfflineDraft, pathOrUri: String, mediaType: String) {
        if (!canAppendToDraft(draft, mediaType)) return
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
        contract = ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        val appendTarget = targetDraftForAppend
        targetDraftForAppend = null
        if (uris.isNotEmpty()) {
            if (appendTarget != null) {
                uris.forEach { uri ->
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    val mediaType = if (mime.startsWith("video/")) "video" else "image"
                    appendMediaToTargetDraft(appendTarget, uri.toString(), mediaType)
                }
            } else {
                // Save first picked item as primary offline draft (respecting 4-photo / 2-video cap)
                var imgs = 0
                var vids = 0
                uris.forEach { uri ->
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    val mediaType = if (mime.startsWith("video/")) "video" else "image"
                    if (mediaType == "image" && imgs < MediaUploadLimits.MAX_IMAGES_PER_REPORT) {
                        onSavePickedMediaDraft(uri, mediaType)
                        imgs++
                    } else if (mediaType == "video" && vids < MediaUploadLimits.MAX_VIDEOS_PER_REPORT) {
                        onSavePickedMediaDraft(uri, mediaType)
                        vids++
                    }
                }
            }
            selectedTab = 0
        }
    }

    fun launchPhotoCapture() {
        val dir = File(context.cacheDir, "cam_capture_${currentUserId.ifBlank { "guest" }}").apply { if (!exists()) mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        pendingPhotoFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        onWillLaunchExternalPicker()
        takePictureLauncher.launch(uri)
    }

    fun launchVideoCapture() {
        val dir = File(context.cacheDir, "cam_capture_${currentUserId.ifBlank { "guest" }}").apply { if (!exists()) mkdirs() }
        val file = File(dir, "video_${System.currentTimeMillis()}.mp4")
        pendingVideoFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        onWillLaunchExternalPicker()
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
        val mediaType = if (mode == "video") "video" else "image"
        if (appendTo != null && !canAppendToDraft(appendTo, mediaType)) {
            return
        }
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

    val allMine = remember(reports, currentUserId) {
        reports.filter { r ->
            currentUserId.isNotBlank() && r.reportedBy.equals(currentUserId, ignoreCase = true)
        }
    }

    val statusCounts = remember(allMine) {
        mapOf(
            "ALL" to allMine.size,
            ReportResolutionStatus.OPEN.key to allMine.count { it.status == ReportResolutionStatus.OPEN },
            ReportResolutionStatus.PENDING.key to allMine.count { it.status == ReportResolutionStatus.PENDING },
            ReportResolutionStatus.ACTION_REQUIRED.key to allMine.count { it.status == ReportResolutionStatus.ACTION_REQUIRED },
            ReportResolutionStatus.ACCEPTED.key to allMine.count { it.status == ReportResolutionStatus.ACCEPTED },
            ReportResolutionStatus.REJECTED.key to allMine.count { it.status == ReportResolutionStatus.REJECTED }
        )
    }

    val actionRequiredCount = statusCounts[ReportResolutionStatus.ACTION_REQUIRED.key] ?: 0

    val mySubmitted = remember(allMine, search, statusFilter) {
        val q = search.trim().lowercase()
        allMine.filter { r ->
            if (statusFilter != "ALL" && !r.status.key.equals(statusFilter, ignoreCase = true)) {
                return@filter false
            }
            if (q.isNotBlank()) {
                val hay = "${r.staffName.orEmpty()} ${r.complianceCheck} ${r.exceptionText} ${r.className.orEmpty()} ${r.reportNumber}".lowercase()
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
                            onWillLaunchExternalPicker()
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_gallery_pick_button")
                    )
                }

                // Compact media allowance guide pill + Urgent Action Required indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Limit: up to ${MediaUploadLimits.MAX_IMAGES_PER_REPORT} photos & ${MediaUploadLimits.MAX_VIDEOS_PER_REPORT} videos per exception",
                        color = p.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (actionRequiredCount > 0) {
                        StatusGlassChip(
                            dotColor = Color(0xFFFF6B2C),
                            text = "$actionRequiredCount Action"
                        )
                    }
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
                        label = "Offline Drafts (${drafts.size + if (savedFormDraft?.hasContent == true) 1 else 0})",
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_offline_drafts")
                    )
                    GlassSegmentItem(
                        label = "Submitted (${allMine.size})",
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_submitted_exceptions")
                    )
                }
            }

            if (selectedTab == 0) {
                // OFFLINE DRAFTS TAB (In-progress Composer Draft + Encrypted Offline Media Drafts, Max 4 Photos & 2 Videos)
                val hasQueuedDrafts = drafts.any { it.isQueuedForSubmit }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (hasQueuedDrafts && bgRestrictionStatus.hasAnyRestriction) {
                        item(key = "queued_bg_restriction_warning") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(p.rose.copy(alpha = 0.14f))
                                    .border(1.dp, p.rose.copy(alpha = 0.48f), RoundedCornerShape(20.dp))
                                    .clickable {
                                        onWillLaunchExternalPicker()
                                        BackgroundExecutionHelper.openRelevantSettings(context, bgRestrictionStatus)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                                    .testTag("queue_background_restricted_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = p.rose,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = BackgroundExecutionHelper.getPrimaryTitle(bgRestrictionStatus),
                                        color = p.rose,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Queued reports will wait until you reopen the app. Tap to allow background activity.",
                                        color = p.textPrimary,
                                        fontSize = 11.sp
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(p.rose)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Enable",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    item(key = "add_to_draft_cta") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .complianceGlassCard(22.dp)
                                .border(
                                    1.2.dp,
                                    Brush.horizontalGradient(
                                        listOf(
                                            p.tangerine.copy(alpha = 0.65f),
                                            p.tangerineEnd.copy(alpha = 0.35f)
                                        )
                                    ),
                                    RoundedCornerShape(22.dp)
                                )
                                .clickable(onClick = onAddOfflineFormDraft)
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("add_to_draft_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .shadow(10.dp, CircleShape, spotColor = p.tangerine.copy(alpha = 0.4f))
                                    .clip(CircleShape)
                                    .background(Brush.horizontalGradient(listOf(p.tangerine, p.tangerineEnd)))
                                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add to Draft",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "+ Add to Draft",
                                    color = p.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Write a full offline exception draft • Evidence optional",
                                    color = p.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.EditNote,
                                contentDescription = null,
                                tint = p.tangerine,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    if (savedFormDraft != null && savedFormDraft.hasContent) {
                        item(key = "in_progress_composer_draft") {
                            val divLabel = ComplianceDivision.fromAny(savedFormDraft.divisionKey)?.label ?: "Academic"
                            val detailSummary = listOfNotNull(
                                divLabel,
                                savedFormDraft.staffName?.takeIf { it.isNotBlank() },
                                savedFormDraft.checkName?.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .complianceGlassCard(24.dp)
                                    .border(1.dp, p.tangerine.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                                    .padding(16.dp)
                                    .testTag("in_progress_composer_draft_card"),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.EditNote,
                                            contentDescription = null,
                                            tint = p.tangerine,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "IN-PROGRESS REPORT DRAFT",
                                            color = p.tangerine,
                                            fontSize = 10.sp,
                                            letterSpacing = 1.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    TextButton(onClick = onClearFormDraft) {
                                        Text("Clear", color = p.rose, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    text = detailSummary.ifBlank { "Saved Exception Draft" },
                                    color = p.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (savedFormDraft.exceptionText.isNotBlank()) {
                                    Text(
                                        text = savedFormDraft.exceptionText,
                                        color = p.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TangerineGlowButton(
                                    text = "Resume Unfinished Report",
                                    onClick = onResumeFormDraft,
                                    modifier = Modifier.testTag("resume_draft_card_button")
                                )
                            }
                        }
                    }

                    if (drafts.isEmpty() && savedFormDraft?.hasContent != true) {
                        item {
                            GlassEmptyStateCard(
                                icon = Icons.Rounded.EditNote,
                                title = "No Offline Drafts Yet",
                                subtitle = "Tap \"+ Add to Draft\" above to draft a full report offline, or capture photos/videos—even without internet."
                            )
                        }
                    } else if (drafts.isNotEmpty()) {
                        items(drafts, key = { it.id }) { draft ->
                            val mediaList = resolveDraftMediaList(draft)
                            val imgCount = mediaList.count { it.mediaType.equals("image", true) }
                            val vidCount = mediaList.count { it.mediaType.equals("video", true) }
                            val exceedsLimit = imgCount > MediaUploadLimits.MAX_IMAGES_PER_REPORT ||
                                vidCount > MediaUploadLimits.MAX_VIDEOS_PER_REPORT
                            val primaryTime = mediaList.firstOrNull()?.capturedAtLabel
                                ?: formatDraftTimestamp(draft.incidentDate, draft.mediaPath)

                            val cardAccentBorder = when {
                                draft.isQueuedForSubmit -> p.tangerine.copy(alpha = 0.55f)
                                draft.isFormDraft && !draft.hasEvidence -> p.rose.copy(alpha = 0.35f)
                                draft.isFormDraft -> p.emerald.copy(alpha = 0.35f)
                                else -> Color.Transparent
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .complianceGlassCard(24.dp)
                                    .then(
                                        if (cardAccentBorder != Color.Transparent) {
                                            Modifier.border(1.dp, cardAccentBorder, RoundedCornerShape(24.dp))
                                        } else Modifier
                                    )
                                    .padding(16.dp)
                                    .testTag("offline_draft_card_${draft.id}"),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Header: Status / Quota Chips + Date+Time + Delete Draft
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        when {
                                            draft.isQueuedForSubmit -> {
                                                StatusGlassChip(
                                                    dotColor = p.tangerine,
                                                    text = "QUEUED • AUTO-SUBMIT"
                                                )
                                            }
                                            draft.isFormDraft && !draft.hasEvidence -> {
                                                StatusGlassChip(
                                                    dotColor = p.rose,
                                                    text = "DRAFT • NO EVIDENCE"
                                                )
                                            }
                                            draft.isFormDraft -> {
                                                StatusGlassChip(
                                                    dotColor = p.emerald,
                                                    text = "FORM • ${imgCount}/4 Img • ${vidCount}/2 Vid"
                                                )
                                            }
                                            else -> {
                                                StatusGlassChip(
                                                    dotColor = if (exceedsLimit) p.rose else p.emerald,
                                                    text = "${imgCount}/4 Img • ${vidCount}/2 Vid"
                                                )
                                            }
                                        }
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

                                // Form Draft Metadata Pills
                                draft.formDraft?.let { fd ->
                                    val divLabel = ComplianceDivision.fromAny(fd.divisionKey)?.label ?: "Academic"
                                    val metaParts = listOfNotNull(
                                        divLabel,
                                        fd.staffName?.takeIf { it.isNotBlank() }
                                            ?: fd.opPersonOrLocation.takeIf { it.isNotBlank() }
                                            ?: fd.studentNamesArm.takeIf { it.isNotBlank() },
                                        fd.className?.takeIf { it.isNotBlank() }
                                    )
                                    if (metaParts.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            metaParts.forEach { chipText ->
                                                Text(
                                                    text = chipText,
                                                    color = p.textPrimary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(p.trackFill)
                                                        .border(1.dp, p.borderBottom, CircleShape)
                                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
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
                                    if (draft.isQueuedForSubmit) {
                                        Text(
                                            text = if (isOnline) {
                                                "Connection detected — ready to transmit automatically."
                                            } else {
                                                "Waiting for internet. Will submit automatically in background once online."
                                            },
                                            color = p.tangerine,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Multi-Evidence Thumbnail Strip
                                if (mediaList.isNotEmpty()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(mediaList, key = { idx, item -> "${item.id}_$idx" }) { idx, item ->
                                            DraftMediaThumbTile(
                                                item = item,
                                                onPreview = { previewModalState = draft to idx },
                                                onDelete = {
                                                    if (!draft.isQueuedForSubmit) {
                                                        pendingDeleteDraftMedia = draft to item
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                if (!draft.isQueuedForSubmit) {
                                    // Add More Media Bar (Enforcing max 4 photos & 2 videos)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SmallGlassActionChip(
                                            icon = Icons.Rounded.CameraAlt,
                                            label = "+ Photo ($imgCount/4)",
                                            onClick = { requestCameraAndRun("photo", appendTo = draft) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        SmallGlassActionChip(
                                            icon = Icons.Rounded.Videocam,
                                            label = "+ Video ($vidCount/2)",
                                            onClick = { requestCameraAndRun("video", appendTo = draft) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        SmallGlassActionChip(
                                            icon = Icons.Rounded.PhotoLibrary,
                                            label = "+ Gallery",
                                            onClick = {
                                                if (imgCount >= MediaUploadLimits.MAX_IMAGES_PER_REPORT && vidCount >= MediaUploadLimits.MAX_VIDEOS_PER_REPORT) {
                                                    onShowLimitWarning("Draft already reached the max 4 photos and 2 videos limit.")
                                                } else {
                                                    targetDraftForAppend = draft
                                                    onWillLaunchExternalPicker()
                                                    photoPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                                    )
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    if (exceedsLimit) {
                                        Text(
                                            text = "Exceeds limit (max 4 photos & 2 videos). Trim extra attachments before submitting.",
                                            color = p.rose,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Primary Actions per Draft Type
                                when {
                                    draft.isQueuedForSubmit -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .clip(CircleShape)
                                                    .background(p.trackFill)
                                                    .border(1.dp, p.borderBottom, CircleShape)
                                                    .clickable { onCancelQueuedDraft(draft) }
                                                    .padding(horizontal = 12.dp)
                                                    .testTag("cancel_queue_draft_${draft.id}"),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Undo,
                                                    contentDescription = null,
                                                    tint = p.textPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "Move to Draft",
                                                    color = p.textPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                            }

                                            if (isOnline) {
                                                Row(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(48.dp)
                                                        .shadow(12.dp, CircleShape, spotColor = p.tangerine.copy(alpha = 0.45f))
                                                        .clip(CircleShape)
                                                        .background(Brush.horizontalGradient(listOf(p.tangerine, p.tangerineEnd)))
                                                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                                        .clickable(onClick = onRetryQueuedSubmissions)
                                                        .padding(horizontal = 12.dp)
                                                        .testTag("retry_queue_draft_${draft.id}"),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CloudUpload,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = "Submit Now",
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    draft.isFormDraft -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .clip(CircleShape)
                                                    .background(p.trackFill)
                                                    .border(1.dp, p.borderBottom, CircleShape)
                                                    .clickable { onEditFormDraft(draft) }
                                                    .padding(horizontal = 12.dp)
                                                    .testTag("edit_form_draft_${draft.id}"),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Edit,
                                                    contentDescription = null,
                                                    tint = p.textPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "Edit Draft",
                                                    color = p.textPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .shadow(12.dp, CircleShape, spotColor = p.tangerine.copy(alpha = 0.45f))
                                                    .clip(CircleShape)
                                                    .background(Brush.horizontalGradient(listOf(p.tangerine, p.tangerineEnd)))
                                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                                    .clickable {
                                                        when {
                                                            exceedsLimit -> reviewTrimDraft = draft
                                                            mediaList.isEmpty() -> onConvertDraft(draft)
                                                            else -> onConvertDraftWithMedia(draft, mediaList)
                                                        }
                                                    }
                                                    .padding(horizontal = 12.dp)
                                                    .testTag("convert_draft_${draft.id}"),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = if (exceedsLimit) "Trim (${mediaList.size})" else "Complete & Submit",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    else -> {
                                        TangerineGlowButton(
                                            text = if (exceedsLimit) "Trim & Convert (${mediaList.size} items)" else "Convert to Exception Report",
                                            onClick = {
                                                when {
                                                    exceedsLimit -> reviewTrimDraft = draft
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
                    }
                }
            } else {
                // SUBMITTED EXCEPTIONS TAB (Dynamic Search, Urgent Action Banner, 5-Status Pills, Full-Form Edit)
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
                            if (actionRequiredCount > 0 && statusFilter != ReportResolutionStatus.ACTION_REQUIRED.key) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFFFF6B2C).copy(alpha = 0.16f))
                                        .border(1.dp, Color(0xFFFF6B2C).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                        .clickable { statusFilter = ReportResolutionStatus.ACTION_REQUIRED.key }
                                        .padding(horizontal = 12.dp, vertical = 9.dp)
                                        .testTag("action_required_banner"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.NotificationsActive,
                                        contentDescription = null,
                                        tint = Color(0xFFFF6B2C),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "$actionRequiredCount exception${if (actionRequiredCount > 1) "s" else ""} require your attention — Tap to filter",
                                        color = p.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            GlassSearchBox(
                                value = search,
                                onValueChange = { search = it },
                                placeholder = "Search submitted exceptions..."
                            )

                            val filterOptions = listOf(
                                "ALL" to "All (${statusCounts["ALL"] ?: 0})",
                                ReportResolutionStatus.OPEN.key to "Open (${statusCounts[ReportResolutionStatus.OPEN.key] ?: 0})",
                                ReportResolutionStatus.PENDING.key to "Pending (${statusCounts[ReportResolutionStatus.PENDING.key] ?: 0})",
                                ReportResolutionStatus.ACTION_REQUIRED.key to "Action Required (${statusCounts[ReportResolutionStatus.ACTION_REQUIRED.key] ?: 0})",
                                ReportResolutionStatus.ACCEPTED.key to "Accepted (${statusCounts[ReportResolutionStatus.ACCEPTED.key] ?: 0})",
                                ReportResolutionStatus.REJECTED.key to "Rejected (${statusCounts[ReportResolutionStatus.REJECTED.key] ?: 0})"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                filterOptions.forEach { (key, label) ->
                                    val selected = statusFilter == key
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
                                            .clickable { statusFilter = key }
                                            .padding(horizontal = 13.dp, vertical = 7.dp)
                                            .testTag("exception_filter_${key.lowercase()}")
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
                                    title = if (allMine.isEmpty()) "No Submitted Exceptions" else "No Matching Exceptions",
                                    subtitle = if (allMine.isEmpty()) {
                                        "Tap 'New Exception' above to log a formal compliance report."
                                    } else {
                                        "Try clearing your search or selecting 'All' above."
                                    }
                                )
                            }
                        } else {
                            items(mySubmitted, key = { it.id }) { report ->
                                val feedback = adminFeedbackByReportId[report.id]
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .complianceGlassCard(24.dp)
                                        .then(
                                            if (report.isActionRequired) {
                                                Modifier.border(
                                                    1.2.dp,
                                                    Color(0xFFFF6B2C).copy(alpha = 0.55f),
                                                    RoundedCornerShape(24.dp)
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RecentReportCard(
                                        report = report,
                                        resolvedDivision = AnalysisEngine.resolveDivision(report),
                                        onClick = { onSelectReport(report) }
                                    )

                                    // Admin Action / Review Note Box
                                    val resNote = feedback?.resolutionNote?.takeIf { it.isNotBlank() } ?: report.resolutionNotes?.takeIf { it.isNotBlank() }
                                    val evNotes = feedback?.evidenceNotes?.ifEmpty { report.evidenceNotes } ?: report.evidenceNotes
                                    if (!resNote.isNullOrBlank() || evNotes.isNotEmpty() || report.isActionRequired) {
                                        val isAction = report.isActionRequired
                                        val isRej = report.isRejected || feedback?.isRejected == true
                                        val isAcc = report.isAccepted
                                        val noteAccent = when {
                                            isAction -> Color(0xFFFF6B2C)
                                            isRej -> p.rose
                                            isAcc -> p.emerald
                                            else -> p.tangerine
                                        }
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
                                                    imageVector = if (isAcc) Icons.Rounded.CheckCircle else Icons.Rounded.Feedback,
                                                    contentDescription = null,
                                                    tint = noteAccent,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = when {
                                                        isAction -> "ACTION REQUIRED • CHECK & UPDATE"
                                                        isRej -> "ADMIN REJECTION NOTE"
                                                        isAcc -> "ACCEPTED • DIRECTORATE NOTE"
                                                        else -> "PENDING • COMPLIANCE NOTE"
                                                    },
                                                    color = noteAccent,
                                                    fontSize = 10.sp,
                                                    letterSpacing = 1.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (!resNote.isNullOrBlank()) {
                                                Text(
                                                    text = resNote,
                                                    color = p.textPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            } else if (isAction) {
                                                Text(
                                                    text = "Please review this exception and update any missing details or evidence.",
                                                    color = p.textPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            evNotes.forEach { evNote ->
                                                Text(
                                                    text = "• $evNote",
                                                    color = p.textSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }

                                    // Action Footer: Full Form Edit (including Evidence) for Open / Pending / Action Required; Locked for Accepted / Rejected
                                    HorizontalDivider(thickness = 0.7.dp, color = p.borderBottom)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val attachCount = report.evidenceUrls.size.takeIf { it > 0 }
                                            ?: if (!report.evidenceUrl.isNullOrBlank() && report.evidenceType != "none") 1 else 0
                                        Text(
                                            text = when {
                                                report.isActionRequired -> "Action needed • Tap Edit to resolve"
                                                report.isPending -> "Pending review • Editable ($attachCount evidence)"
                                                report.isAccepted -> "Accepted • Final & locked"
                                                report.isRejected -> "Rejected • Final & locked"
                                                else -> "Open • Tap Edit to update ($attachCount evidence)"
                                            },
                                            color = if (report.isActionRequired) Color(0xFFFF6B2C) else p.textSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = if (report.isActionRequired) FontWeight.SemiBold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        if (report.canUserEdit) {
                                            val btnTint = if (report.isActionRequired) Color(0xFFFF6B2C) else p.tangerine
                                            Row(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(btnTint.copy(alpha = 0.16f))
                                                    .border(1.dp, btnTint.copy(alpha = 0.4f), CircleShape)
                                                    .clickable { onOpenEditForm(report) }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                                    .testTag("user_edit_exception_${report.id}"),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Edit,
                                                    contentDescription = "Edit Exception",
                                                    tint = btnTint,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = if (report.isActionRequired) "Edit & Resolve" else "Edit Report",
                                                    color = btnTint,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        } else {
                                            val lockTint = if (report.isAccepted) p.emerald else p.rose
                                            Row(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(lockTint.copy(alpha = 0.14f))
                                                    .border(1.dp, lockTint.copy(alpha = 0.35f), CircleShape)
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                                    .testTag("exception_locked_badge_${report.id}"),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Lock,
                                                    contentDescription = "${report.status.label} and Locked",
                                                    tint = lockTint,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = report.status.label,
                                                    color = lockTint,
                                                    fontSize = 11.sp,
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
                    showModerationControls = false,
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

    // Trim Review Modal when a legacy draft exceeds Max 4 Photos & 2 Videos
    reviewTrimDraft?.let { draft ->
        val mediaList = resolveDraftMediaList(draft)
        val imgCount = mediaList.count { it.mediaType.equals("image", true) }
        val vidCount = mediaList.count { it.mediaType.equals("video", true) }
        val validCounts = imgCount <= MediaUploadLimits.MAX_IMAGES_PER_REPORT &&
            vidCount <= MediaUploadLimits.MAX_VIDEOS_PER_REPORT &&
            mediaList.isNotEmpty()
        AlertDialog(
            onDismissRequest = { reviewTrimDraft = null },
            containerColor = if (p.isDark) Color(0xFF151923) else Color(0xFFF6F8FC),
            titleContentColor = p.textPrimary,
            textContentColor = p.textSecondary,
            icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = p.tangerine) },
            title = { Text("Max 4 Photos & 2 Videos", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This draft has $imgCount photos and $vidCount videos. Please trim extras to stay within 4 photos and 2 videos.",
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
                                    onClick = { pendingDeleteDraftMedia = draft to item }
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
                    enabled = validCounts,
                    colors = ButtonDefaults.buttonColors(containerColor = p.tangerine, contentColor = Color.White),
                    onClick = {
                        reviewTrimDraft = null
                        onConvertDraftWithMedia(draft, mediaList)
                    }
                ) {
                    Text("Proceed (${imgCount}/4 Img, ${vidCount}/2 Vid)", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { reviewTrimDraft = null }) {
                    Text("Cancel", color = p.textSecondary)
                }
            }
        )
    }

    // Draft Evidence Delete Confirmation Glass Card (Prevents accidental X taps)
    pendingDeleteDraftMedia?.let { (draft, item) ->
        Dialog(
            onDismissRequest = { pendingDeleteDraftMedia = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .complianceGlassCard(26.dp)
                        .padding(20.dp)
                        .testTag("confirm_delete_draft_evidence_card"),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DraftMediaMiniPreview(
                            item = item,
                            modifier = Modifier
                                .size(54.dp)
                                .border(1.dp, p.rose.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Delete Evidence?",
                                color = p.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${item.mediaType.uppercase()} • ${item.capturedAtLabel}",
                                color = p.tangerine,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = "Do you want to permanently remove this evidence attachment from your draft?",
                        color = p.textSecondary,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(CircleShape)
                                .background(p.trackFill)
                                .border(1.dp, p.borderBottom, CircleShape)
                                .clickable { pendingDeleteDraftMedia = null }
                                .testTag("cancel_delete_evidence_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Keep",
                                color = p.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(CircleShape)
                                .background(p.rose)
                                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                .clickable {
                                    removeDraftMediaItem(draft, item)
                                    pendingDeleteDraftMedia = null
                                }
                                .testTag("confirm_delete_evidence_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Delete",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
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
        } else if (isVideo) {
            VideoFrameThumbnail(
                source = item.pathOrUri,
                contentDescription = "Draft video preview",
                modifier = Modifier.fillMaxSize(),
                badgeSize = 22.dp
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.AttachFile,
                contentDescription = item.mediaType,
                tint = p.textPrimary,
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