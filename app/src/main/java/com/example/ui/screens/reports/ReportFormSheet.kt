package com.example.ui.screens.reports

import android.Manifest
import androidx.compose.ui.Modifier
import android.content.pm.PackageManager
import android.app.DatePickerDialog
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.util.Calendar
import java.util.Locale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.local.AcademicWeekEngine
import com.example.data.local.ComplianceAutoFillEngine
import com.example.data.local.ComplianceTaxonomy
import com.example.data.model.Classroom
import com.example.data.model.ComplianceCheck
import com.example.data.model.ComplianceDivision
import com.example.data.model.ComplianceReport
import com.example.data.model.OfflineDraft
import com.example.data.model.StaffMember
import com.example.data.model.SubjectItem
import com.example.ui.components.GlowingPrimaryButton
import com.example.ui.components.MicroLabel
import com.example.ui.components.PreSubmitAuditSheet
import com.example.ui.components.glassFieldColors
import com.example.ui.theme.ComplianceTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportFormSheet(
    editingReport: ComplianceReport?,
    sourceDraft: OfflineDraft?,
    staffList: List<StaffMember>,
    classes: List<Classroom>,
    subjects: List<SubjectItem>,
    customChecks: List<ComplianceCheck>,
    autoFillEnabled: Boolean,
    isLoading: Boolean,
    onAddCustomCheck: (ComplianceDivision, String, String) -> Unit,
    onDismiss: () -> Unit,
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
        localFile: File?,
        pickedUri: Uri?,
        evidenceType: String,
        evidenceName: String?,
        cleanDraftMedia: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val ext = ComplianceTheme.extended
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var division by remember {
        mutableStateOf(
            ComplianceDivision.fromAny(editingReport?.division) ?: ComplianceDivision.ACADEMIC
        )
    }
    var targetType by remember { mutableStateOf(editingReport?.targetType ?: "staff") }
    var classTierFilter by remember { mutableStateOf("All Tiers") }
    var staffSearch by remember { mutableStateOf("") }
    var selectedStaff by remember {
        mutableStateOf(
            editingReport?.staffId?.let { id -> staffList.firstOrNull { it.id == id } }
                ?: editingReport?.staffName?.let { n -> staffList.firstOrNull { it.name.equals(n, true) } }
        )
    }
    var selectedClass by remember {
        mutableStateOf(
            editingReport?.classId?.let { id -> classes.firstOrNull { it.id == id } }
                ?: editingReport?.className?.let { n -> classes.firstOrNull { it.name.equals(n, true) } }
        )
    }
    var selectedSubject by remember {
        mutableStateOf(
            editingReport?.subjectId?.let { id -> subjects.firstOrNull { it.id == id } }
                ?: editingReport?.subject?.let { n -> subjects.firstOrNull { it.name.equals(n, true) } }
        )
    }

    var operationScope by remember { mutableStateOf(editingReport?.category ?: "Maintenance") }
    var selectedBus by remember { mutableStateOf("Bus 1") }
    var opPersonOrLocation by remember { mutableStateOf(editingReport?.assignedLocation.orEmpty()) }
    var studentNamesArm by remember { mutableStateOf("") }
    var incidentDate by remember {
        mutableStateOf(editingReport?.incidentDate ?: sourceDraft?.incidentDate ?: AcademicWeekEngine.todayIso())
    }
    var exceptionText by remember {
        mutableStateOf(editingReport?.exceptionText ?: sourceDraft?.notes.orEmpty())
    }
    var effectText by remember { mutableStateOf(editingReport?.effectText.orEmpty()) }

    val checks = remember(division, operationScope, customChecks) {
        ComplianceTaxonomy.getApplicableChecks(division, operationScope, customChecks)
    }
    var selectedCheck by remember(division, operationScope) {
        mutableStateOf(
            checks.firstOrNull { it.name.equals(editingReport?.complianceCheck, true) || it.id == editingReport?.checkId }
                ?: checks.firstOrNull()
        )
    }

    var showStaffMenu by remember { mutableStateOf(false) }
    var showClassMenu by remember { mutableStateOf(false) }
    var showSubjectMenu by remember { mutableStateOf(false) }
    var showCheckMenu by remember { mutableStateOf(false) }
    var showBusMenu by remember { mutableStateOf(false) }
    var showCustomCheckDialog by remember { mutableStateOf(false) }
    var showNoEvidenceWarning by remember { mutableStateOf(false) }
    var cleanDraftMedia by remember { mutableStateOf(true) }

    // Media attachments
    var localMediaFile by remember {
        mutableStateOf(sourceDraft?.mediaPath?.let { File(it) }?.takeIf { it.exists() })
    }
    var pickedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var evidenceType by remember {
        mutableStateOf(editingReport?.evidenceType ?: sourceDraft?.mediaType ?: "none")
    }
    var evidenceName by remember {
        mutableStateOf(editingReport?.evidenceName ?: sourceDraft?.mediaName)
    }

    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    val takePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingPhotoFile
        pendingPhotoFile = null
        if (ok && f != null && f.exists()) {
            localMediaFile = f
            pickedMediaUri = null
            evidenceType = "image"
            evidenceName = f.name
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            pickedMediaUri = uri
            localMediaFile = null
            evidenceType = if (mime.startsWith("video/")) "video" else "image"
            evidenceName = uri.lastPathSegment ?: "Picked media"
        }
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pickedMediaUri = uri
            localMediaFile = null
            evidenceType = "document"
            evidenceName = uri.lastPathSegment ?: "Attached document"
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
            localMediaFile = f
            pickedMediaUri = null
            evidenceType = "audio"
            evidenceName = f.name
        }
    }

    val audioPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startAudioRecording()
    }

    val camPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val f = File(context.cacheDir, "report_photo_${System.currentTimeMillis()}.jpg")
            pendingPhotoFile = f
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            takePhotoLauncher.launch(uri)
        }
    }

    fun runAutoFill(force: Boolean = true) {
        if (!force && !autoFillEnabled) return
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
    }

    val filteredClasses = remember(classes, classTierFilter) {
        if (classTierFilter == "All Tiers") classes else classes.filter { it.section.equals(classTierFilter, true) }
    }
    val activeStaff = remember(staffList, division, staffSearch) {
        val q = staffSearch.trim().lowercase()
        staffList.filter { it.isActive }.filter { s ->
            val deptMatch = when (division) {
                ComplianceDivision.ACADEMIC -> s.department.contains("academ", true)
                ComplianceDivision.OPERATIONS -> s.department.contains("oper", true) || s.category.contains("non", true)
                else -> true
            }
            val qMatch = q.isBlank() || s.name.lowercase().contains(q) || s.staffCode.lowercase().contains(q)
            deptMatch && qMatch
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (editingReport != null) "Edit Exception" else "Log Compliance Exception",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            MicroLabel("Division")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComplianceDivision.entries.forEach { div ->
                    FilterChip(
                        selected = division == div,
                        onClick = {
                            division = div
                            targetType = if (div == ComplianceDivision.CDS) "division" else "staff"
                        },
                        label = { Text(div.label) },
                        modifier = Modifier.testTag("form_div_${div.key}")
                    )
                }
            }

            if (division == ComplianceDivision.ACADEMIC) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ComplianceTaxonomy.academicClassTiers.forEach { tier ->
                        FilterChip(selected = classTierFilter == tier, onClick = { classTierFilter = tier }, label = { Text(tier) })
                    }
                }
                OutlinedTextField(
                    value = staffSearch,
                    onValueChange = { staffSearch = it },
                    label = { Text("Search Staff Roster") },
                    singleLine = true,
                    colors = glassFieldColors(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(onClick = { showStaffMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedStaff?.let { "${it.name} (${it.staffCode})" } ?: "Select Staff Member")
                    }
                    DropdownMenu(expanded = showStaffMenu, onDismissRequest = { showStaffMenu = false }) {
                        activeStaff.forEach { s ->
                            DropdownMenuItem(text = { Text("${s.name} • ${s.staffCode}") }, onClick = { selectedStaff = s; showStaffMenu = false })
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
                                DropdownMenuItem(text = { Text(c.name) }, onClick = { selectedClass = c; showClassMenu = false })
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { showSubjectMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedSubject?.name ?: "Select Subject")
                        }
                        DropdownMenu(expanded = showSubjectMenu, onDismissRequest = { showSubjectMenu = false }) {
                            subjects.forEach { subj ->
                                DropdownMenuItem(text = { Text(subj.name) }, onClick = { selectedSubject = subj; showSubjectMenu = false })
                            }
                        }
                    }
                }
            }

            if (division == ComplianceDivision.OPERATIONS) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComplianceTaxonomy.operationSubScopes.forEach { sc ->
                        FilterChip(selected = operationScope == sc, onClick = { operationScope = sc }, label = { Text(sc) })
                    }
                }
                if (operationScope == "Transport") {
                    Box {
                        OutlinedButton(onClick = { showBusMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Fleet Vehicle: $selectedBus")
                        }
                        DropdownMenu(expanded = showBusMenu, onDismissRequest = { showBusMenu = false }) {
                            ComplianceTaxonomy.fleetBuses.forEach { b ->
                                DropdownMenuItem(text = { Text(b) }, onClick = { selectedBus = b; showBusMenu = false })
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = opPersonOrLocation,
                    onValueChange = { opPersonOrLocation = it },
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
                    FilterChip(selected = targetType == "division", onClick = { targetType = "division" }, label = { Text("Student Decorum") })
                    FilterChip(selected = targetType == "staff", onClick = { targetType = "staff" }, label = { Text("Staff Monitor") })
                }
                if (targetType == "division") {
                    OutlinedTextField(
                        value = studentNamesArm,
                        onValueChange = { studentNamesArm = it },
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
                                DropdownMenuItem(text = { Text(s.name) }, onClick = { selectedStaff = s; showStaffMenu = false })
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
                                    if (autoFillEnabled && exceptionText.isBlank()) runAutoFill(true)
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
                        onClick = { runAutoFill(true) },
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
                onValueChange = { exceptionText = it },
                label = { Text("Exception Observation") },
                minLines = 3,
                colors = glassFieldColors(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("form_exception_text")
            )

            OutlinedTextField(
                value = effectText,
                onValueChange = { effectText = it },
                label = { Text("Operational / Academic Effect") },
                minLines = 2,
                colors = glassFieldColors(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("form_effect_text")
            )

            MicroLabel("Evidence Attachment (Max 1 for Direct Report)")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        val f = File(context.cacheDir, "report_photo_${System.currentTimeMillis()}.jpg")
                        pendingPhotoFile = f
                        takePhotoLauncher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f))
                    } else camPermLauncher.launch(Manifest.permission.CAMERA)
                }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Camera")
                }
                OutlinedButton(onClick = {
                    mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Gallery")
                }
                OutlinedButton(onClick = {
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

            // Small Instant Upload Preview Thumbnail (Image preview / Video frame + Play badge)
            if (evidenceType != "none" && (!evidenceName.isNullOrBlank() || localMediaFile != null || pickedMediaUri != null)) {
                Surface(
                    color = ext.emeraldContainer,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ext.emerald.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().testTag("evidence_preview_card")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Visual thumbnail box
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (evidenceType == "image" || evidenceType == "video") {
                                AsyncImage(
                                    model = localMediaFile ?: pickedMediaUri ?: editingReport?.evidenceUrl,
                                    contentDescription = "Evidence Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (evidenceType == "video") {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.70f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play badge",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else if (evidenceType == "audio") {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Audio Evidence",
                                    tint = ext.emerald,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else if (evidenceType == "document") {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Document Evidence",
                                    tint = ext.emerald,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = evidenceName ?: "Attached Evidence",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = ext.emerald,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${evidenceType.uppercase()} • 1 evidence attached (Strict limit)",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.emerald.copy(alpha = 0.85f)
                            )
                        }

                        IconButton(
                            onClick = {
                                localMediaFile = null
                                pickedMediaUri = null
                                evidenceType = "none"
                                evidenceName = null
                            },
                            modifier = Modifier.size(32.dp).testTag("remove_attached_evidence_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove evidence",
                                tint = ext.rose,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (sourceDraft != null && !sourceDraft.mediaPath.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = cleanDraftMedia, onCheckedChange = { cleanDraftMedia = it })
                    Text(
                        text = "Clean local draft media after submission",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            fun performSubmit() {
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
                    localMediaFile,
                    pickedMediaUri,
                    evidenceType,
                    evidenceName,
                    cleanDraftMedia
                )
            }

            val hasEvidence = localMediaFile != null || pickedMediaUri != null || !editingReport?.evidenceUrl.isNullOrBlank()

            GlowingPrimaryButton(
                onClick = { showNoEvidenceWarning = true },
                enabled = !isLoading && exceptionText.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("submit_report_button")
            ) {
                Text(if (isLoading) "Submitting..." else "Submit Exception Report", fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(14.dp))

            if (showNoEvidenceWarning) {
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
                                (if (hasEvidence) "Evidence Attached (${evidenceType.uppercase()})" else "No Media Attached (Optional — tap X to attach)") to hasEvidence
                            ),
                            isLoading = isLoading,
                            onDismiss = { showNoEvidenceWarning = false },
                            onSubmit = {
                                showNoEvidenceWarning = false
                                performSubmit()
                            }
                        )
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
