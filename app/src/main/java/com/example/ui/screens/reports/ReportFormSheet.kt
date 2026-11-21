package com.example.ui.screens.reports

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.data.local.AcademicWeekEngine
import com.example.data.local.ComplianceAutoFillEngine
import com.example.data.local.ComplianceTaxonomy
import com.example.data.model.Classroom
import com.example.data.model.ComplianceCheck
import com.example.data.model.ComplianceDivision
import com.example.data.model.ComplianceReport
import com.example.data.model.DraftMediaItem
import com.example.data.model.MediaUploadLimits
import com.example.data.model.OfflineDraft
import com.example.data.model.ReportEvidenceAttachment
import com.example.data.model.ReportFormDraft
import com.example.data.model.ReportFormMode
import com.example.data.model.StaffMember
import com.example.data.model.SubjectItem
import com.example.domain.BackgroundExecutionHelper
import com.example.ui.components.GlowingPrimaryButton
import com.example.ui.components.MicroLabel
import com.example.ui.components.PreSubmitAuditSheet
import com.example.ui.components.VideoFrameThumbnail
import com.example.ui.components.complianceGlassCard
import com.example.ui.components.glassFieldColors
import com.example.ui.theme.ComplianceTheme
import com.example.ui.theme.LocalCompliancePalette
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportFormSheet(
    editingReport: ComplianceReport?,
    sourceDraft: OfflineDraft?,
    formMode: ReportFormMode = ReportFormMode.STANDARD,
    isOnline: Boolean = true,
    staffList: List<StaffMember>,
    classes: List<Classroom>,
    subjects: List<SubjectItem>,
    customChecks: List<ComplianceCheck>,
    autoFillEnabled: Boolean,
    isLoading: Boolean,
    onAddCustomCheck: (ComplianceDivision, String, String) -> Unit,
    onDismiss: () -> Unit,
    preselectedDraftMedia: List<DraftMediaItem> = emptyList(),
    compressionProgress: Float? = null,
    compressionLabel: String? = null,
    onWillLaunchExternalPicker: () -> Unit = {},
    initialDraft: ReportFormDraft? = null,
    onDraftChanged: (ReportFormDraft) -> Unit = {},
    onClearDraft: () -> Unit = {},
    onSaveToOfflineDraft: (draft: ReportFormDraft, attachments: List<ReportEvidenceAttachment>) -> Unit = { _, _ -> },
    onStageMediaFile: suspend (File) -> String? = { it.absolutePath },
    onStageMediaUri: suspend (Uri) -> Pair<String, String>? = { null },
    onResolveSignedUrl: suspend (String) -> Result<String> = { Result.failure(Exception("Unavailable")) },
    onMediaLimitError: (String) -> Unit = {},
    feedbackMessage: String? = null,
    isErrorFeedback: Boolean = false,
    onClearFeedback: () -> Unit = {},
    onQueueOfflineAutoSubmit: (
        division: ComplianceDivision,
        targetType: String,
        staff: StaffMember?,
        classroom: Classroom?,
        subject: SubjectItem?,
        operationScope: String,
        specificSubjectText: String,
        assignedLocation: String,
        check: ComplianceCheck?,
        incidentDate: String,
        exceptionText: String,
        effectText: String,
        attachments: List<ReportEvidenceAttachment>,
        cleanDraftMedia: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onSubmit: (
        division: ComplianceDivision,
        targetType: String,
        staff: StaffMember?,
        classroom: Classroom?,
        subject: SubjectItem?,
        operationScope: String,
        specificSubjectText: String,
        assignedLocation: String,
        check: ComplianceCheck?,
        incidentDate: String,
        exceptionText: String,
        effectText: String,
        attachments: List<ReportEvidenceAttachment>,
        cleanDraftMedia: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val ext = ComplianceTheme.extended
    val p = LocalCompliancePalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    val isDraftFormMode = formMode == ReportFormMode.OFFLINE_DRAFT && editingReport == null
    val draftToRestore = when {
        editingReport != null -> null
        sourceDraft?.formDraft != null -> sourceDraft.formDraft
        sourceDraft != null -> null
        formMode == ReportFormMode.STANDARD -> initialDraft
        else -> null
    }
    var userTouched by remember { mutableStateOf(false) }

    var division by remember {
        mutableStateOf(
            ComplianceDivision.fromAny(editingReport?.division ?: draftToRestore?.divisionKey)
                ?: ComplianceDivision.ACADEMIC
        )
    }
    var targetType by remember {
        mutableStateOf(
            editingReport?.targetType
                ?: draftToRestore?.targetType
                ?: if (division == ComplianceDivision.CDS || division == ComplianceDivision.OPERATIONS) "division" else "staff"
        )
    }
    var classTierFilter by remember {
        mutableStateOf(draftToRestore?.classTierFilter ?: "All Tiers")
    }
    var staffSearch by remember {
        mutableStateOf(draftToRestore?.staffSearch ?: "")
    }
    var selectedStaff by remember {
        mutableStateOf(
            editingReport?.staffId?.let { id -> staffList.firstOrNull { it.id == id } }
                ?: editingReport?.staffName?.let { n -> staffList.firstOrNull { it.name.equals(n, true) } }
                ?: draftToRestore?.staffId?.let { id -> staffList.firstOrNull { it.id == id } }
                ?: draftToRestore?.staffCode?.let { code -> staffList.firstOrNull { it.staffCode.equals(code, true) } }
                ?: draftToRestore?.staffName?.let { n -> staffList.firstOrNull { it.name.equals(n, true) } }
        )
    }
    var selectedClass by remember {
        mutableStateOf(
            editingReport?.classId?.let { id -> classes.firstOrNull { it.id == id } }
                ?: editingReport?.className?.let { n -> classes.firstOrNull { it.name.equals(n, true) } }
                ?: draftToRestore?.classId?.let { id -> classes.firstOrNull { it.id == id } }
                ?: draftToRestore?.className?.let { n -> classes.firstOrNull { it.name.equals(n, true) } }
        )
    }
    var selectedSubject by remember {
        mutableStateOf(
            editingReport?.subjectId?.let { id -> subjects.firstOrNull { it.id == id } }
                ?: editingReport?.subject?.let { n -> subjects.firstOrNull { it.name.equals(n, true) } }
                ?: draftToRestore?.subjectId?.let { id -> subjects.firstOrNull { it.id == id } }
                ?: draftToRestore?.subjectName?.let { n -> subjects.firstOrNull { it.name.equals(n, true) } }
        )
    }

    var operationScope by remember {
        mutableStateOf(editingReport?.category ?: draftToRestore?.operationScope ?: "Maintenance")
    }
    var selectedBus by remember {
        mutableStateOf(draftToRestore?.selectedBus ?: "Bus 1")
    }
    var opPersonOrLocation by remember {
        mutableStateOf(editingReport?.assignedLocation ?: draftToRestore?.opPersonOrLocation.orEmpty())
    }
    var studentNamesArm by remember {
        mutableStateOf(draftToRestore?.studentNamesArm.orEmpty())
    }
    var incidentDate by remember {
        mutableStateOf(
            editingReport?.incidentDate
                ?: sourceDraft?.incidentDate?.takeIf { it.isNotBlank() }
                ?: draftToRestore?.incidentDate?.takeIf { it.isNotBlank() }
                ?: AcademicWeekEngine.todayIso()
        )
    }
    var exceptionText by remember {
        mutableStateOf(
            editingReport?.exceptionText
                ?: sourceDraft?.notes?.takeIf { it.isNotBlank() }
                ?: draftToRestore?.exceptionText.orEmpty()
        )
    }
    var effectText by remember {
        mutableStateOf(editingReport?.effectText ?: draftToRestore?.effectText.orEmpty())
    }

    val checks = remember(division, operationScope, customChecks) {
        ComplianceTaxonomy.getApplicableChecks(division, operationScope, customChecks)
    }
    var selectedCheck by remember(division, operationScope) {
        mutableStateOf(
            checks.firstOrNull {
                it.name.equals(editingReport?.complianceCheck, true) ||
                    it.id == editingReport?.checkId ||
                    (draftToRestore != null && (it.id == draftToRestore.checkId || it.name.equals(draftToRestore.checkName, true)))
            } ?: checks.firstOrNull()
        )
    }

    var showStaffMenu by remember { mutableStateOf(false) }
    var showClassMenu by remember { mutableStateOf(false) }
    var showSubjectMenu by remember { mutableStateOf(false) }
    var showCheckMenu by remember { mutableStateOf(false) }
    var showBusMenu by remember { mutableStateOf(false) }
    var showCustomCheckDialog by remember { mutableStateOf(false) }
    var showNoEvidenceWarning by remember { mutableStateOf(false) }
    var showOfflineAutoSubmitConsent by remember { mutableStateOf(false) }
    var cleanDraftMedia by remember { mutableStateOf(true) }
    var mediaLimitBanner by remember { mutableStateOf<String?>(null) }
    var localValidationError by remember { mutableStateOf<String?>(null) }

    // Unified evidence attachments for BOTH Create and Edit modes (Max 4 Images, Max 2 Videos)
    var attachedEvidence by remember(
        editingReport?.id,
        sourceDraft?.id,
        preselectedDraftMedia,
        draftToRestore?.updatedAtMillis
    ) {
        val initial = buildList {
            if (editingReport != null) {
                val urls = editingReport.evidenceUrls.ifEmpty {
                    listOfNotNull(editingReport.evidenceUrl?.takeIf { it.isNotBlank() && editingReport.evidenceType != "none" })
                }
                urls.forEachIndexed { idx, path ->
                    val type = editingReport.evidenceTypes.getOrNull(idx)?.takeIf { it.isNotBlank() && it != "none" }
                        ?: editingReport.evidenceType.takeIf { it.isNotBlank() && it != "none" }
                        ?: "image"
                    add(
                        ReportEvidenceAttachment(
                            id = "cloud_${editingReport.id}_$idx",
                            pathOrUri = path,
                            mediaType = type,
                            displayName = path.substringAfterLast('/').ifBlank { "Cloud ${type.replaceFirstChar { c -> c.uppercase() }} #${idx + 1}" },
                            isExistingCloud = true
                        )
                    )
                }
            } else {
                val draftItems = when {
                    preselectedDraftMedia.isNotEmpty() -> preselectedDraftMedia
                    sourceDraft != null && sourceDraft.mediaItems.isNotEmpty() -> sourceDraft.mediaItems
                    !sourceDraft?.mediaPath.isNullOrBlank() -> listOf(
                        DraftMediaItem(
                            id = "src_${sourceDraft!!.id}",
                            pathOrUri = sourceDraft.mediaPath!!,
                            mediaType = sourceDraft.mediaType.ifBlank { "image" },
                            capturedAtLabel = sourceDraft.mediaName ?: "Draft media"
                        )
                    )
                    draftToRestore?.selectedDraftMedia?.isNotEmpty() == true -> draftToRestore.selectedDraftMedia
                    !draftToRestore?.localMediaPath.isNullOrBlank() -> listOf(
                        DraftMediaItem(
                            id = "restored_${draftToRestore!!.updatedAtMillis}",
                            pathOrUri = draftToRestore.localMediaPath!!,
                            mediaType = draftToRestore.evidenceType.takeIf { it != "none" } ?: "image",
                            capturedAtLabel = draftToRestore.evidenceName ?: "Restored media"
                        )
                    )
                    else -> emptyList()
                }
                draftItems.forEachIndexed { idx, dm ->
                    val name = if (dm.pathOrUri.startsWith("/")) {
                        File(dm.pathOrUri).name.ifBlank { dm.capturedAtLabel }
                    } else {
                        dm.capturedAtLabel.ifBlank { "Attachment #${idx + 1}" }
                    }
                    add(
                        ReportEvidenceAttachment(
                            id = dm.id,
                            pathOrUri = dm.pathOrUri,
                            mediaType = dm.mediaType.ifBlank { "image" },
                            displayName = name,
                            isExistingCloud = false
                        )
                    )
                }
            }
        }
        mutableStateOf(initial)
    }

    val currentImageCount = remember(attachedEvidence) {
        attachedEvidence.count { it.mediaType.equals("image", ignoreCase = true) }
    }
    val currentVideoCount = remember(attachedEvidence) {
        attachedEvidence.count { it.mediaType.equals("video", ignoreCase = true) }
    }

    fun tryAppendAttachment(item: ReportEvidenceAttachment): Boolean {
        if (item.mediaType.equals("image", ignoreCase = true) && currentImageCount >= MediaUploadLimits.MAX_IMAGES_PER_REPORT) {
            val msg = "Limit reached: maximum ${MediaUploadLimits.MAX_IMAGES_PER_REPORT} images allowed per exception."
            mediaLimitBanner = msg
            onMediaLimitError(msg)
            return false
        }
        if (item.mediaType.equals("video", ignoreCase = true) && currentVideoCount >= MediaUploadLimits.MAX_VIDEOS_PER_REPORT) {
            val msg = "Limit reached: maximum ${MediaUploadLimits.MAX_VIDEOS_PER_REPORT} videos allowed per exception."
            mediaLimitBanner = msg
            onMediaLimitError(msg)
            return false
        }
        if (attachedEvidence.any { it.pathOrUri == item.pathOrUri }) {
            return true
        }
        mediaLimitBanner = null
        attachedEvidence = attachedEvidence + item
        userTouched = true
        return true
    }

    fun buildSnapshotDraft(): ReportFormDraft {
        val syncedDraftMedia = attachedEvidence
            .filterNot { it.isExistingCloud }
            .map {
                DraftMediaItem(
                    id = it.id,
                    pathOrUri = it.pathOrUri,
                    mediaType = it.mediaType,
                    capturedAtLabel = it.displayName
                )
            }
        return ReportFormDraft(
            divisionKey = division.key,
            targetType = targetType,
            classTierFilter = classTierFilter,
            staffSearch = staffSearch,
            staffId = selectedStaff?.id,
            staffCode = selectedStaff?.staffCode,
            staffName = selectedStaff?.name,
            classId = selectedClass?.id,
            className = selectedClass?.name,
            subjectId = selectedSubject?.id,
            subjectName = selectedSubject?.name,
            operationScope = operationScope,
            selectedBus = selectedBus,
            opPersonOrLocation = opPersonOrLocation,
            studentNamesArm = studentNamesArm,
            checkId = selectedCheck?.id,
            checkName = selectedCheck?.name,
            incidentDate = incidentDate,
            exceptionText = exceptionText,
            effectText = effectText,
            sourceDraftId = sourceDraft?.id ?: draftToRestore?.sourceDraftId,
            selectedDraftMedia = syncedDraftMedia,
            localMediaPath = syncedDraftMedia.firstOrNull()?.pathOrUri,
            evidenceType = syncedDraftMedia.firstOrNull()?.mediaType ?: "none",
            evidenceName = when {
                syncedDraftMedia.isEmpty() -> null
                syncedDraftMedia.size == 1 -> syncedDraftMedia.first().capturedAtLabel
                else -> "${syncedDraftMedia.size} items"
            },
            hasUserEdited = true,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    // Live auto-save composer draft ONLY in standard report composer mode
    LaunchedEffect(
        isDraftFormMode,
        userTouched,
        division, targetType, classTierFilter, staffSearch,
        selectedStaff, selectedClass, selectedSubject,
        operationScope, selectedBus, opPersonOrLocation, studentNamesArm,
        selectedCheck, incidentDate, exceptionText, effectText,
        attachedEvidence
    ) {
        if (editingReport == null && !isDraftFormMode && userTouched) {
            onDraftChanged(buildSnapshotDraft())
        }
    }

    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    var pendingVideoFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraAction by remember { mutableStateOf<String?>(null) }

    val takePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingPhotoFile
        pendingPhotoFile = null
        if (ok && f != null && f.exists()) {
            coroutineScope.launch {
                val staged = onStageMediaFile(f)
                val finalPath = staged?.let(::File)?.takeIf { it.exists() }?.absolutePath ?: f.absolutePath
                tryAppendAttachment(
                    ReportEvidenceAttachment(
                        id = "photo_${System.currentTimeMillis()}",
                        pathOrUri = finalPath,
                        mediaType = "image",
                        displayName = f.name,
                        isExistingCloud = false
                    )
                )
            }
        }
    }

    val captureVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val f = pendingVideoFile
        pendingVideoFile = null
        if (ok && f != null && f.exists()) {
            coroutineScope.launch {
                val staged = onStageMediaFile(f)
                val finalPath = staged?.let(::File)?.takeIf { it.exists() }?.absolutePath ?: f.absolutePath
                tryAppendAttachment(
                    ReportEvidenceAttachment(
                        id = "video_${System.currentTimeMillis()}",
                        pathOrUri = finalPath,
                        mediaType = "video",
                        displayName = f.name,
                        isExistingCloud = false
                    )
                )
            }
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                var imgTotal = attachedEvidence.count { it.mediaType.equals("image", true) }
                var vidTotal = attachedEvidence.count { it.mediaType.equals("video", true) }
                val added = mutableListOf<ReportEvidenceAttachment>()
                var hitLimitMsg: String? = null

                for (uri in uris) {
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    val detectedType = if (mime.startsWith("video/")) "video" else "image"
                    if (detectedType == "image" && imgTotal >= MediaUploadLimits.MAX_IMAGES_PER_REPORT) {
                        hitLimitMsg = "Max ${MediaUploadLimits.MAX_IMAGES_PER_REPORT} images allowed per exception (extra photos skipped)."
                        continue
                    }
                    if (detectedType == "video" && vidTotal >= MediaUploadLimits.MAX_VIDEOS_PER_REPORT) {
                        hitLimitMsg = "Max ${MediaUploadLimits.MAX_VIDEOS_PER_REPORT} videos allowed per exception (extra videos skipped)."
                        continue
                    }
                    val stagedPair = onStageMediaUri(uri)
                    val pathOrUri = stagedPair?.first ?: uri.toString()
                    val name = stagedPair?.second ?: uri.lastPathSegment ?: "Picked $detectedType"
                    if (attachedEvidence.none { it.pathOrUri == pathOrUri } && added.none { it.pathOrUri == pathOrUri }) {
                        added.add(
                            ReportEvidenceAttachment(
                                id = "pick_${System.currentTimeMillis()}_${uri.hashCode()}",
                                pathOrUri = pathOrUri,
                                mediaType = detectedType,
                                displayName = name,
                                isExistingCloud = false
                            )
                        )
                        if (detectedType == "image") imgTotal++ else vidTotal++
                    }
                }
                if (added.isNotEmpty()) {
                    attachedEvidence = attachedEvidence + added
                    userTouched = true
                }
                if (hitLimitMsg != null) {
                    mediaLimitBanner = hitLimitMsg
                    onMediaLimitError(hitLimitMsg)
                } else if (added.isNotEmpty()) {
                    mediaLimitBanner = null
                }
            }
        }
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val stagedPair = onStageMediaUri(uri)
                val pathOrUri = stagedPair?.first ?: uri.toString()
                val name = stagedPair?.second ?: uri.lastPathSegment ?: "Attached document"
                tryAppendAttachment(
                    ReportEvidenceAttachment(
                        id = "doc_${System.currentTimeMillis()}",
                        pathOrUri = pathOrUri,
                        mediaType = "document",
                        displayName = name,
                        isExistingCloud = false
                    )
                )
            }
        }
    }

    // Audio memo recording
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                recorder?.stop()
                recorder?.release()
            }
        }
    }

    fun startAudioRecording() {
        val out = File(context.cacheDir, "audio_memo_${System.currentTimeMillis()}.m4a")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        mr.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(out.absolutePath)
            prepare()
            start()
        }
        recordingFile = out
        recorder = mr
        isRecordingAudio = true
    }

    fun stopAudioRecording() {
        runCatching {
            recorder?.stop()
            recorder?.release()
        }
        recorder = null
        isRecordingAudio = false
        val f = recordingFile
        if (f != null && f.exists()) {
            coroutineScope.launch {
                val staged = onStageMediaFile(f)
                val finalPath = staged?.let(::File)?.takeIf { it.exists() }?.absolutePath ?: f.absolutePath
                tryAppendAttachment(
                    ReportEvidenceAttachment(
                        id = "audio_${System.currentTimeMillis()}",
                        pathOrUri = finalPath,
                        mediaType = "audio",
                        displayName = f.name,
                        isExistingCloud = false
                    )
                )
            }
        }
    }

    val audioPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startAudioRecording()
    }

    fun launchCameraCapture(mode: String) {
        if (mode == "image" && currentImageCount >= MediaUploadLimits.MAX_IMAGES_PER_REPORT) {
            val msg = "Maximum ${MediaUploadLimits.MAX_IMAGES_PER_REPORT} images reached."
            mediaLimitBanner = msg
            onMediaLimitError(msg)
            return
        }
        if (mode == "video" && currentVideoCount >= MediaUploadLimits.MAX_VIDEOS_PER_REPORT) {
            val msg = "Maximum ${MediaUploadLimits.MAX_VIDEOS_PER_REPORT} videos reached."
            mediaLimitBanner = msg
            onMediaLimitError(msg)
            return
        }
        if (mode == "video") {
            val f = File(context.cacheDir, "report_video_${System.currentTimeMillis()}.mp4")
            pendingVideoFile = f
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            onWillLaunchExternalPicker()
            captureVideoLauncher.launch(uri)
        } else {
            val f = File(context.cacheDir, "report_photo_${System.currentTimeMillis()}.jpg")
            pendingPhotoFile = f
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            onWillLaunchExternalPicker()
            takePhotoLauncher.launch(uri)
        }
    }

    val camPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingCameraAction ?: "image"
        pendingCameraAction = null
        if (granted) {
            launchCameraCapture(action)
        }
    }

    fun runAutoFill() {
        if (!autoFillEnabled) return
        val res = ComplianceAutoFillEngine.generate(
            division = division,
            targetType = targetType,
            checkId = selectedCheck?.id.orEmpty(),
            checkName = selectedCheck?.name.orEmpty(),
            staffName = selectedStaff?.name.orEmpty(),
            className = selectedClass?.name.orEmpty(),
            subjectName = selectedSubject?.name.orEmpty(),
            driverName = opPersonOrLocation,
            selectedBus = selectedBus,
            minderName = opPersonOrLocation,
            minderLocation = opPersonOrLocation,
            securityName = opPersonOrLocation,
            maintenanceLocation = opPersonOrLocation,
            studentNamesArm = studentNamesArm,
            operationScope = operationScope
        )
        exceptionText = res.exceptionText
        effectText = res.effectText
        userTouched = true
    }

    val filteredClasses = remember(classes, classTierFilter) {
        if (classTierFilter == "All Tiers") classes else classes.filter { it.section.equals(classTierFilter, true) }
    }
    val activeStaff = remember(staffList, division, staffSearch) {
        val q = staffSearch.trim().lowercase()
        val onlyActive = staffList.filter { it.isActive }
        val byDept = onlyActive.filter { s ->
            when (division) {
                ComplianceDivision.ACADEMIC ->
                    s.department.contains("academ", true) ||
                        s.category.contains("academ", true) ||
                        s.department.isBlank()
                ComplianceDivision.OPERATIONS ->
                    s.department.contains("oper", true) || s.category.contains("non", true)
                else -> true
            }
        }
        val basePool = if (byDept.isNotEmpty()) byDept else onlyActive
        basePool.filter { s ->
            q.isBlank() ||
                s.name.lowercase().contains(q) ||
                s.staffCode.lowercase().contains(q) ||
                s.staffNumber.lowercase().contains(q) ||
                s.role.lowercase().contains(q)
        }
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
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (isDraftFormMode) {
                        Text(
                            text = "OFFLINE DRAFT FORM",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = p.tangerine
                        )
                    }
                    Text(
                        text = when {
                            editingReport != null -> "Edit Exception"
                            isDraftFormMode && sourceDraft != null -> "Edit Offline Draft"
                            isDraftFormMode -> "Add Offline Draft"
                            else -> "Log Compliance Exception"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (editingReport == null) {
                        TextButton(
                            onClick = {
                                userTouched = false
                                division = ComplianceDivision.ACADEMIC
                                targetType = "staff"
                                classTierFilter = "All Tiers"
                                staffSearch = ""
                                selectedStaff = null
                                selectedClass = null
                                selectedSubject = null
                                operationScope = "Maintenance"
                                selectedBus = "Bus 1"
                                opPersonOrLocation = ""
                                studentNamesArm = ""
                                incidentDate = AcademicWeekEngine.todayIso()
                                exceptionText = ""
                                effectText = ""
                                selectedCheck = ComplianceTaxonomy.getApplicableChecks(
                                    ComplianceDivision.ACADEMIC,
                                    "Maintenance",
                                    customChecks
                                ).firstOrNull()
                                attachedEvidence = emptyList()
                                mediaLimitBanner = null
                                localValidationError = null
                                onClearFeedback()
                                onClearDraft()
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_report_form_button")
                        ) {
                            Text(
                                text = "Clear",
                                color = ext.rose,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            if (isDraftFormMode) {
                Surface(
                    color = p.tangerine.copy(alpha = if (ext.isDark) 0.16f else 0.10f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, p.tangerine.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Saved locally on your device—even without media or internet. Edit anytime or complete & submit when ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
            }

            MicroLabel("Division")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComplianceDivision.entries.forEach { div ->
                    FilterChip(
                        selected = division == div,
                        onClick = {
                            division = div
                            targetType = if (div == ComplianceDivision.CDS || div == ComplianceDivision.OPERATIONS) "division" else "staff"
                            localValidationError = null
                            onClearFeedback()
                            userTouched = true
                        },
                        label = { Text(div.label) },
                        modifier = Modifier.testTag("form_div_${div.key}")
                    )
                }
            }

            if (division == ComplianceDivision.ACADEMIC) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ComplianceTaxonomy.academicClassTiers.forEach { tier ->
                        FilterChip(
                            selected = classTierFilter == tier,
                            onClick = {
                                classTierFilter = tier
                                userTouched = true
                            },
                            label = { Text(tier) }
                        )
                    }
                }
                OutlinedTextField(
                    value = staffSearch,
                    onValueChange = {
                        staffSearch = it
                        localValidationError = null
                        userTouched = true
                    },
                    label = { Text("Search Staff Roster") },
                    trailingIcon = if (staffSearch.isNotBlank()) {
                        {
                            IconButton(onClick = { staffSearch = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear staff search")
                            }
                        }
                    } else null,
                    singleLine = true,
                    colors = glassFieldColors(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("form_staff_search")
                )
                if (staffSearch.isNotBlank() && (selectedStaff == null || !selectedStaff!!.name.equals(staffSearch.trim(), true))) {
                    Surface(
                        color = ext.glassBottom,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ext.glassRimEnd),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            if (activeStaff.isEmpty()) {
                                Text(
                                    text = "No matching staff found for \"${staffSearch.trim()}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            } else {
                                activeStaff.take(6).forEach { candidate ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedStaff = candidate
                                                staffSearch = candidate.name
                                                localValidationError = null
                                                userTouched = true
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = candidate.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (candidate.staffCode.isNotBlank()) {
                                            Text(
                                                text = candidate.staffCode,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Box {
                    OutlinedButton(
                        onClick = { showStaffMenu = true },
                        modifier = Modifier.fillMaxWidth().testTag("form_select_staff_button")
                    ) {
                        Text(
                            selectedStaff?.let { "${it.name} (${it.staffCode})" }
                                ?: "Select Staff Member (${activeStaff.size})"
                        )
                    }
                    DropdownMenu(expanded = showStaffMenu, onDismissRequest = { showStaffMenu = false }) {
                        if (activeStaff.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No matching active staff found") },
                                onClick = { showStaffMenu = false }
                            )
                        } else {
                            activeStaff.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s.name} • ${s.staffCode}") },
                                    onClick = {
                                        selectedStaff = s
                                        showStaffMenu = false
                                        localValidationError = null
                                        userTouched = true
                                    }
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { showClassMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedClass?.name ?: "Select Class")
                        }
                        DropdownMenu(expanded = showClassMenu, onDismissRequest = { showClassMenu = false }) {
                            filteredClasses.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.name) },
                                    onClick = {
                                        selectedClass = c
                                        showClassMenu = false
                                        userTouched = true
                                    }
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { showSubjectMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedSubject?.name ?: "Select Subject")
                        }
                        DropdownMenu(expanded = showSubjectMenu, onDismissRequest = { showSubjectMenu = false }) {
                            subjects.forEach { subj ->
                                DropdownMenuItem(
                                    text = { Text(subj.name) },
                                    onClick = {
                                        selectedSubject = subj
                                        showSubjectMenu = false
                                        userTouched = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (division == ComplianceDivision.OPERATIONS) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComplianceTaxonomy.operationSubScopes.forEach { sc ->
                        FilterChip(
                            selected = operationScope == sc,
                            onClick = {
                                operationScope = sc
                                userTouched = true
                            },
                            label = { Text(sc) }
                        )
                    }
                }
                if (operationScope == "Transport") {
                    Box {
                        OutlinedButton(onClick = { showBusMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Fleet Vehicle: $selectedBus")
                        }
                        DropdownMenu(expanded = showBusMenu, onDismissRequest = { showBusMenu = false }) {
                            ComplianceTaxonomy.fleetBuses.forEach { b ->
                                DropdownMenuItem(
                                    text = { Text(b) },
                                    onClick = {
                                        selectedBus = b
                                        showBusMenu = false
                                        userTouched = true
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = opPersonOrLocation,
                    onValueChange = {
                        opPersonOrLocation = it
                        userTouched = true
                    },
                    label = {
                        Text(
                            when (operationScope) {
                                "Transport" -> "Driver Name"
                                "Minders" -> "Minder Name & Wing"
                                "Security" -> "Security Officer / Post"
                                else -> "Facility Location / Unit"
                            }
                        )
                    },
                    singleLine = true,
                    colors = glassFieldColors(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (division == ComplianceDivision.DISCIPLINE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = targetType == "division",
                        onClick = {
                            targetType = "division"
                            userTouched = true
                        },
                        label = { Text("Student Decorum") }
                    )
                    FilterChip(
                        selected = targetType == "staff",
                        onClick = {
                            targetType = "staff"
                            userTouched = true
                        },
                        label = { Text("Staff Monitor") }
                    )
                }
                if (targetType == "division") {
                    OutlinedTextField(
                        value = studentNamesArm,
                        onValueChange = {
                            studentNamesArm = it
                            userTouched = true
                        },
                        label = { Text("Student Name(s) & Class Arm") },
                        singleLine = true,
                        colors = glassFieldColors(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Box {
                        OutlinedButton(onClick = { showStaffMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedStaff?.name ?: "Select Monitoring Staff")
                        }
                        DropdownMenu(expanded = showStaffMenu, onDismissRequest = { showStaffMenu = false }) {
                            staffList.filter { it.isActive }.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.name) },
                                    onClick = {
                                        selectedStaff = s
                                        showStaffMenu = false
                                        userTouched = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Compliance Check Picker
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(onClick = { showCheckMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedCheck?.name ?: "Select Compliance Check", maxLines = 1)
                    }
                    DropdownMenu(expanded = showCheckMenu, onDismissRequest = { showCheckMenu = false }) {
                        checks.forEach { chk ->
                            DropdownMenuItem(
                                text = { Text("${chk.id} • ${chk.name}") },
                                onClick = {
                                    selectedCheck = chk
                                    showCheckMenu = false
                                    userTouched = true
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = { showCustomCheckDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Custom Check")
                }
            }

            fun openIncidentDatePicker() {
                val cal = Calendar.getInstance()
                try {
                    if (incidentDate.length == 10) {
                        val parts = incidentDate.split("-")
                        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                    }
                } catch (_: Exception) {}
                DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        incidentDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                        userTouched = true
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ext.glassBottom)
                        .border(1.dp, ext.glassRimEnd, RoundedCornerShape(16.dp))
                        .clickable { openIncidentDatePicker() }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("form_incident_date_picker"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "INCIDENT DATE (CALENDAR)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = incidentDate,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Pick Incident Date",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (autoFillEnabled) {
                    OutlinedButton(
                        onClick = { runAutoFill() },
                        modifier = Modifier.testTag("autofill_narrative_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Auto-Fill")
                    }
                }
            }

            OutlinedTextField(
                value = exceptionText,
                onValueChange = {
                    exceptionText = it
                    userTouched = true
                },
                label = { Text("Exception Observation") },
                minLines = 3,
                colors = glassFieldColors(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("form_exception_text")
            )

            OutlinedTextField(
                value = effectText,
                onValueChange = {
                    effectText = it
                    userTouched = true
                },
                label = { Text("Operational / Academic Effect") },
                minLines = 2,
                colors = glassFieldColors(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("form_effect_text")
            )

            // Unified Evidence Attachments (Supports Create AND Edit; Max 4 Photos, Max 2 Videos)
            MicroLabel("Evidence Attachments • Photos $currentImageCount/${MediaUploadLimits.MAX_IMAGES_PER_REPORT} • Videos $currentVideoCount/${MediaUploadLimits.MAX_VIDEOS_PER_REPORT}")

            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = currentImageCount < MediaUploadLimits.MAX_IMAGES_PER_REPORT,
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            launchCameraCapture("image")
                        } else {
                            pendingCameraAction = "image"
                            camPermLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Photo (${currentImageCount}/4)")
                }
                OutlinedButton(
                    enabled = currentVideoCount < MediaUploadLimits.MAX_VIDEOS_PER_REPORT,
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            launchCameraCapture("video")
                        } else {
                            pendingCameraAction = "video"
                            camPermLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Video (${currentVideoCount}/2)")
                }
                OutlinedButton(
                    enabled = currentImageCount < MediaUploadLimits.MAX_IMAGES_PER_REPORT || currentVideoCount < MediaUploadLimits.MAX_VIDEOS_PER_REPORT,
                    onClick = {
                        onWillLaunchExternalPicker()
                        mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Gallery")
                }
                OutlinedButton(onClick = {
                    onWillLaunchExternalPicker()
                    docPicker.launch(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                }) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Document")
                }
                OutlinedButton(onClick = {
                    if (isRecordingAudio) stopAudioRecording()
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startAudioRecording()
                    else audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    Icon(if (isRecordingAudio) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isRecordingAudio) "Stop Audio" else "Voice Memo")
                }
            }

            mediaLimitBanner?.let { warn ->
                Surface(
                    color = ext.rose.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ext.rose.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = warn,
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.rose,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (attachedEvidence.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    attachedEvidence.forEachIndexed { idx, item ->
                        val isVideo = item.mediaType.equals("video", ignoreCase = true)
                        val isImage = item.mediaType.equals("image", ignoreCase = true)
                        var signedThumbUrl by remember(item.id, item.pathOrUri) { mutableStateOf<String?>(null) }
                        if (item.isExistingCloud && (isImage || isVideo)) {
                            LaunchedEffect(item.id, item.pathOrUri) {
                                onResolveSignedUrl(item.pathOrUri).onSuccess { signedThumbUrl = it }
                            }
                        }
                        val previewModel: Any? = when {
                            item.isExistingCloud -> signedThumbUrl
                            item.pathOrUri.startsWith("/") -> File(item.pathOrUri)
                            else -> Uri.parse(item.pathOrUri)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.width(84.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .border(
                                        1.dp,
                                        if (item.isExistingCloud) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else ext.emerald.copy(alpha = 0.45f),
                                        RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    isVideo && previewModel != null -> VideoFrameThumbnail(
                                        source = previewModel,
                                        contentDescription = "Evidence Video Thumbnail",
                                        modifier = Modifier.fillMaxSize(),
                                        badgeSize = 22.dp
                                    )
                                    isImage && previewModel != null -> AsyncImage(
                                        model = previewModel,
                                        contentDescription = "Evidence Image Thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    item.mediaType.equals("audio", true) -> Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Audio Evidence",
                                        tint = ext.emerald,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    else -> Icon(
                                        imageVector = if (item.isExistingCloud) Icons.Default.CloudDone else Icons.Default.Description,
                                        contentDescription = "Attachment",
                                        tint = ext.emerald,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.78f))
                                        .clickable {
                                            attachedEvidence = attachedEvidence.filterNot { it.id == item.id }
                                            mediaLimitBanner = null
                                            userTouched = true
                                        }
                                        .testTag("remove_form_evidence_${idx}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove evidence item",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (item.isExistingCloud) "Saved • ${item.mediaType.uppercase()}" else item.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (sourceDraft != null && attachedEvidence.any { !it.isExistingCloud }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = cleanDraftMedia, onCheckedChange = { cleanDraftMedia = it })
                    Text(
                        text = "Clean local draft media after submission",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (compressionProgress != null && !compressionLabel.isNullOrBlank()) {
                Surface(
                    color = ext.glassBottom,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth().testTag("compression_progress_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = compressionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${(compressionProgress.coerceIn(0f, 1f) * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { compressionProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                    }
                }
            }

            fun performSubmit() {
                localValidationError = null
                onClearFeedback()
                onSubmit(
                    division,
                    targetType,
                    selectedStaff,
                    selectedClass,
                    selectedSubject,
                    operationScope,
                    if (division == ComplianceDivision.OPERATIONS) {
                        if (operationScope == "Transport") "$selectedBus ($opPersonOrLocation)" else opPersonOrLocation
                    } else studentNamesArm,
                    opPersonOrLocation,
                    selectedCheck,
                    incidentDate,
                    exceptionText,
                    effectText,
                    attachedEvidence,
                    cleanDraftMedia
                )
            }

            fun validateSubmissionInputs(): String? = when {
                division == ComplianceDivision.ACADEMIC && selectedStaff == null ->
                    "Please select a staff member from the Staff Roster."
                division == ComplianceDivision.DISCIPLINE && targetType == "staff" && selectedStaff == null ->
                    "Please select a monitoring staff member or switch to Student Decorum."
                selectedCheck == null ->
                    "Please select a compliance check."
                exceptionText.isBlank() ->
                    "Please enter the observed compliance exception."
                else -> null
            }

            val activeSheetError = localValidationError ?: feedbackMessage?.takeIf { isErrorFeedback && it.isNotBlank() }
            activeSheetError?.let { errText ->
                Surface(
                    color = ext.rose.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ext.rose.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth().testTag("report_form_error_banner")
                ) {
                    Text(
                        text = errText,
                        style = MaterialTheme.typography.labelMedium,
                        color = ext.rose,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            val hasEvidence = attachedEvidence.isNotEmpty()

            if (isDraftFormMode) {
                GlowingPrimaryButton(
                    onClick = {
                        onClearFeedback()
                        val snapshot = buildSnapshotDraft()
                        if (!snapshot.hasAnyDraftContent) {
                            localValidationError = "Enter an observation, check, staff member, or attachment before saving to Offline Drafts."
                        } else {
                            localValidationError = null
                            onSaveToOfflineDraft(snapshot, attachedEvidence)
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().testTag("save_to_drafts_button")
                ) {
                    Text(
                        text = if (isLoading) "Saving Draft..." else if (sourceDraft != null) "Update Offline Draft" else "Save to Offline Drafts",
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                OutlinedButton(
                    onClick = {
                        onClearFeedback()
                        localValidationError = validateSubmissionInputs()
                        if (localValidationError == null) {
                            showNoEvidenceWarning = true
                        }
                    },
                    enabled = !isLoading,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("submit_from_draft_form_button")
                ) {
                    Text(
                        text = if (isOnline) "Complete & Submit Now" else "Complete & Submit (Offline)",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                GlowingPrimaryButton(
                    onClick = {
                        onClearFeedback()
                        localValidationError = validateSubmissionInputs()
                        if (localValidationError == null) {
                            showNoEvidenceWarning = true
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().testTag("submit_report_button")
                ) {
                    Text(
                        text = if (isLoading) {
                            compressionLabel ?: "Submitting..."
                        } else if (editingReport != null) {
                            "Save Updated Exception"
                        } else {
                            "Submit Exception Report"
                        },
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            if (showNoEvidenceWarning) {
                val evidenceAuditLabel = if (hasEvidence) {
                    "Evidence: ${attachedEvidence.size} attached (${currentImageCount} photos, ${currentVideoCount} videos)"
                } else {
                    "No Media Attached (Optional — tap X to attach)"
                }
                Dialog(
                    onDismissRequest = { showNoEvidenceWarning = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        PreSubmitAuditSheet(
                            checks = listOf(
                                "${division.label} • ${selectedCheck?.name ?: "Standard Check"}" to (selectedCheck != null),
                                "Observation Narrative Entered" to exceptionText.isNotBlank(),
                                "Date Anchored ($incidentDate)" to incidentDate.isNotBlank(),
                                evidenceAuditLabel to hasEvidence
                            ),
                            isLoading = isLoading,
                            onDismiss = { showNoEvidenceWarning = false },
                            onSubmit = {
                                showNoEvidenceWarning = false
                                if (!isOnline && editingReport == null) {
                                    showOfflineAutoSubmitConsent = true
                                } else {
                                    performSubmit()
                                }
                            }
                        )
                    }
                }
            }

            if (showOfflineAutoSubmitConsent) {
                val consentLifecycleOwner = LocalLifecycleOwner.current
                var bgStatus by remember { mutableStateOf(BackgroundExecutionHelper.checkStatus(context)) }
                DisposableEffect(consentLifecycleOwner, showOfflineAutoSubmitConsent) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            bgStatus = BackgroundExecutionHelper.checkStatus(context)
                        }
                    }
                    consentLifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { consentLifecycleOwner.lifecycle.removeObserver(observer) }
                }

                Dialog(
                    onDismissRequest = { showOfflineAutoSubmitConsent = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .complianceGlassCard(28.dp)
                                .padding(20.dp)
                                .testTag("offline_autosubmit_consent_dialog"),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "OFFLINE SUBMISSION",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = p.tangerine
                            )
                            Text(
                                text = "You Are Currently Offline",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = p.textPrimary
                            )
                            Text(
                                text = "Would you like to queue this compliance exception so it automatically submits once your device reconnects to the internet?",
                                style = MaterialTheme.typography.bodySmall,
                                color = p.textSecondary
                            )

                            // Live Background Activity & System Restriction Diagnostic Card
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        if (bgStatus.hasAnyRestriction) p.rose.copy(alpha = 0.13f)
                                        else p.emerald.copy(alpha = 0.12f)
                                    )
                                    .border(
                                        1.dp,
                                        if (bgStatus.hasAnyRestriction) p.rose.copy(alpha = 0.45f)
                                        else p.emerald.copy(alpha = 0.4f),
                                        RoundedCornerShape(18.dp)
                                    )
                                    .padding(12.dp)
                                    .testTag("background_activity_status_card"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (bgStatus.hasAnyRestriction) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = if (bgStatus.hasAnyRestriction) p.rose else p.emerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = BackgroundExecutionHelper.getPrimaryTitle(bgStatus),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (bgStatus.hasAnyRestriction) p.rose else p.emerald
                                    )
                                }
                                Text(
                                    text = BackgroundExecutionHelper.getPrimaryDescription(bgStatus),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = p.textPrimary
                                )
                                if (bgStatus.hasAnyRestriction) {
                                    OutlinedButton(
                                        onClick = {
                                            onWillLaunchExternalPicker()
                                            BackgroundExecutionHelper.openRelevantSettings(context, bgStatus)
                                        },
                                        shape = CircleShape,
                                        border = BorderStroke(1.dp, p.rose.copy(alpha = 0.65f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .testTag("open_background_settings_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Settings,
                                            contentDescription = null,
                                            tint = p.rose,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = BackgroundExecutionHelper.getActionButtonLabel(bgStatus),
                                            color = p.rose,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(p.trackFill)
                                    .border(1.dp, p.borderBottom, RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${division.label} • ${selectedCheck?.name ?: "Standard Check"}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = p.textPrimary
                                )
                                Text(
                                    text = "Date: $incidentDate • ${attachedEvidence.size} attachment${if (attachedEvidence.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = p.textSecondary
                                )
                            }
                            GlowingPrimaryButton(
                                onClick = {
                                    showOfflineAutoSubmitConsent = false
                                    onQueueOfflineAutoSubmit(
                                        division,
                                        targetType,
                                        selectedStaff,
                                        selectedClass,
                                        selectedSubject,
                                        operationScope,
                                        if (division == ComplianceDivision.OPERATIONS) {
                                            if (operationScope == "Transport") "$selectedBus ($opPersonOrLocation)" else opPersonOrLocation
                                        } else studentNamesArm,
                                        opPersonOrLocation,
                                        selectedCheck,
                                        incidentDate,
                                        exceptionText,
                                        effectText,
                                        attachedEvidence,
                                        cleanDraftMedia
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("confirm_offline_autosubmit_button")
                            ) {
                                Text(
                                    text = if (bgStatus.hasAnyRestriction) {
                                        "Queue Anyway (Sends When Reopened)"
                                    } else {
                                        "Yes, Auto-Submit When Online"
                                    },
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    showOfflineAutoSubmitConsent = false
                                    onSaveToOfflineDraft(buildSnapshotDraft(), attachedEvidence)
                                },
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("save_draft_instead_button")
                            ) {
                                Text("Save to Offline Drafts Instead", fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(
                                onClick = { showOfflineAutoSubmitConsent = false },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Keep Editing", color = p.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomCheckDialog) {
        var newCheckName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCustomCheckDialog = false },
            containerColor = if (ext.isDark) Color(0xFF151923) else Color(0xFFF6F8FC),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Add Custom Compliance Check") },
            text = {
                OutlinedTextField(
                    value = newCheckName,
                    onValueChange = { newCheckName = it },
                    label = { Text("Standard Name") },
                    singleLine = true,
                    colors = glassFieldColors(),
                    shape = RoundedCornerShape(14.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCheckName.isNotBlank()) {
                            onAddCustomCheck(division, newCheckName, division.label)
                            showCustomCheckDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCustomCheckDialog = false }) { Text("Cancel") } }
        )
    }
}
