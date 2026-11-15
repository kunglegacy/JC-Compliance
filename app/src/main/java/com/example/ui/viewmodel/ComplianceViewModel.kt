package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AcademicWeekEngine
import com.example.data.local.AppPreferencesState
import com.example.data.local.ComplianceTaxonomy
import com.example.data.local.DraftRepository
import com.example.data.local.SecurePreferences
import com.example.data.model.AccentPalette
import com.example.data.model.Classroom
import com.example.data.model.ComplianceCheck
import com.example.data.model.ComplianceDivision
import com.example.data.model.ComplianceReport
import com.example.data.model.DirectorateUser
import com.example.data.model.LockPolicy
import com.example.data.model.OfflineDraft
import com.example.data.model.ReportResolutionStatus
import com.example.data.model.ReportScope
import com.example.data.model.StaffMember
import com.example.data.model.SubjectItem
import com.example.data.model.SupabaseConfig
import com.example.data.model.SystemSettings
import com.example.data.model.ThemePreference
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.example.data.remote.ComplianceRepository
import com.example.domain.AnalysisEngine
import com.example.domain.PdfReportExporter
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class AuthScreenMode {
    LOGIN,
    PASSWORD_RESET
}

data class ComplianceUiState(
    val isBootstrapping: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isOnline: Boolean = true,
    val authMode: AuthScreenMode = AuthScreenMode.LOGIN,
    val authError: String? = null,
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false,
    val reports: List<ComplianceReport> = emptyList(),
    val staffList: List<StaffMember> = emptyList(),
    val classes: List<Classroom> = ComplianceTaxonomy.defaultClasses,
    val subjects: List<SubjectItem> = ComplianceTaxonomy.defaultSubjects,
    val serverCustomChecks: List<ComplianceCheck> = emptyList(),
    val directorateUsers: List<DirectorateUser> = emptyList(),
    val systemSettings: SystemSettings = SystemSettings(),
    val selectedTerm: String = "First Term",
    val activeTab: String = "dashboard",
    val selectedReportForDetail: ComplianceReport? = null,
    val showReportForm: Boolean = false,
    val editingReport: ComplianceReport? = null,
    val convertingDraft: OfflineDraft? = null,
    val accountSubScreen: String? = null, // null | "settings" | "change_password" | "password_reset" | "admin"
    val adminSubScreen: String = "hub"    // "hub" | "officer_stats" | "staff_register" | "system_users" | "system_config"
)

class ComplianceViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = SecurePreferences(application)
    private val draftRepo = DraftRepository(application)
    private val remoteRepo = ComplianceRepository(application)

    val preferencesState: StateFlow<AppPreferencesState> = prefs.state.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppPreferencesState(
            supabaseConfig = SupabaseConfig("", ""),
            session = null
        )
    )

    val drafts: StateFlow<List<OfflineDraft>> = draftRepo.drafts.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )

    private val _uiState = MutableStateFlow(ComplianceUiState())
    val uiState: StateFlow<ComplianceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val initial = prefs.state.first()
            _uiState.update { it.copy(isBootstrapping = false) }
            if (initial.session != null && initial.supabaseConfig.isConfigured) {
                refreshAllData(silent = true)
            }
        }
    }

    fun setOnlineStatus(online: Boolean) {
        _uiState.update { it.copy(isOnline = online) }
    }

    fun clearAuthError() {
        _uiState.update { it.copy(authError = null) }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null, isErrorFeedback = false) }
    }

    fun notifyMessage(message: String, isError: Boolean = false) {
        _uiState.update { it.copy(feedbackMessage = message, isErrorFeedback = isError) }
    }

    fun setAuthMode(mode: AuthScreenMode) {
        _uiState.update { it.copy(authMode = mode, authError = null) }
    }

    fun setActiveTab(tab: String) {
        _uiState.update {
            it.copy(
                activeTab = tab,
                accountSubScreen = null,
                adminSubScreen = "hub"
            )
        }
    }

    fun setAccountSubScreen(screen: String?) {
        _uiState.update { it.copy(accountSubScreen = screen, adminSubScreen = "hub") }
    }

    fun setAdminSubScreen(screen: String) {
        _uiState.update { it.copy(adminSubScreen = screen) }
    }

    fun setSelectedTerm(term: String) {
        _uiState.update { it.copy(selectedTerm = term) }
    }

    fun openReportDetail(report: ComplianceReport?) {
        _uiState.update { it.copy(selectedReportForDetail = report) }
    }

    fun openNewReportForm(draft: OfflineDraft? = null) {
        _uiState.update {
            it.copy(
                showReportForm = true,
                editingReport = null,
                convertingDraft = draft
            )
        }
    }

    fun openEditReportForm(report: ComplianceReport) {
        _uiState.update {
            it.copy(
                selectedReportForDetail = null,
                showReportForm = true,
                editingReport = report,
                convertingDraft = null
            )
        }
    }

    fun closeReportForm() {
        _uiState.update {
            it.copy(showReportForm = false, editingReport = null, convertingDraft = null)
        }
    }

    fun saveSupabaseConfig(url: String, anonKey: String) {
        viewModelScope.launch {
            prefs.saveSupabaseConfig(url, anonKey)
            notifyMessage("Supabase connection settings saved.")
        }
    }

    // ---------------------------------------------------------------------
    // AUTH & PASSWORD
    // ---------------------------------------------------------------------
    fun signIn(email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(authError = "Please enter both institutional email and password.") }
            return
        }
        val config = preferencesState.value.supabaseConfig
        if (!config.isConfigured) {
            _uiState.update { it.copy(authError = "Institutional backend connection is not configured.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, authError = null) }
            val result = remoteRepo.signIn(config, cleanEmail, password)
            result.onSuccess { session ->
                prefs.saveSession(session)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authError = null,
                        authMode = AuthScreenMode.LOGIN,
                        activeTab = "dashboard",
                        accountSubScreen = null
                    )
                }
                refreshAllData(silent = true)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authError = err.message ?: "Sign in failed. Please verify your credentials."
                    )
                }
            }
        }
    }

    fun sendPasswordResetEmail(email: String, onSuccess: () -> Unit = {}) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            notifyMessage("Please enter a valid institutional email address.", isError = true)
            return
        }
        val config = preferencesState.value.supabaseConfig
        if (!config.isConfigured) {
            notifyMessage("Supabase connection is not configured.", isError = true)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteRepo.sendPasswordResetEmail(config, cleanEmail)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    notifyMessage("Password reset link dispatched to $cleanEmail.")
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    notifyMessage(err.message ?: "Failed to send reset link.", isError = true)
                }
        }
    }

    fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String,
        onSuccess: () -> Unit
    ) {
        val session = preferencesState.value.session ?: return
        val config = preferencesState.value.supabaseConfig
        if (currentPassword.isBlank()) {
            notifyMessage("Please enter your current password.", isError = true)
            return
        }
        if (newPassword.length < 6) {
            notifyMessage("New password must be at least 6 characters.", isError = true)
            return
        }
        if (newPassword != confirmPassword) {
            notifyMessage("New passwords do not match.", isError = true)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val validCurrent = remoteRepo.verifyPassword(config, session.email, currentPassword)
            if (!validCurrent) {
                _uiState.update { it.copy(isLoading = false) }
                notifyMessage("Current password is incorrect.", isError = true)
                return@launch
            }
            remoteRepo.updatePassword(config, session.accessToken, newPassword)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    notifyMessage("Password updated successfully.")
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    notifyMessage(err.message ?: "Failed to update password.", isError = true)
                }
        }
    }

    suspend fun verifyPasswordForSecurityAction(password: String): Boolean {
        val session = preferencesState.value.session ?: return false
        val config = preferencesState.value.supabaseConfig
        return remoteRepo.verifyPassword(config, session.email, password)
    }

    fun signOut() {
        viewModelScope.launch {
            val state = preferencesState.value
            val session = state.session
            if (session != null && state.supabaseConfig.isConfigured) {
                remoteRepo.signOut(state.supabaseConfig, session.accessToken)
            }
            prefs.clearSession()
            _uiState.value = ComplianceUiState(isBootstrapping = false)
        }
    }

    // ---------------------------------------------------------------------
    // DEVICE SETUP, LOCK POLICY & BIOMETRICS
    // ---------------------------------------------------------------------
    fun shouldShowAppLock(state: AppPreferencesState): Boolean {
        if (state.session == null || !state.deviceSetupCompleted) return false
        if (state.isManuallyLocked) return true
        return when (state.lockPolicy) {
            LockPolicy.ALWAYS -> state.lastActiveMillis == 0L
            LockPolicy.HOURS_24 -> {
                if (state.lastActiveMillis == 0L) true
                else (System.currentTimeMillis() - state.lastActiveMillis) > 24L * 60L * 60L * 1000L
            }
            LockPolicy.NEVER -> false
        }
    }

    fun completeDeviceSetup(lockPolicy: LockPolicy, enableBiometrics: Boolean) {
        val session = preferencesState.value.session ?: return
        viewModelScope.launch {
            prefs.completeDeviceSetup(
                userId = session.userId,
                lockPolicy = lockPolicy,
                biometricsEnabled = enableBiometrics,
                biometricsEmail = if (enableBiometrics) session.email else ""
            )
            notifyMessage("Device security configuration saved.")
        }
    }

    fun updateLockPolicy(policy: LockPolicy) {
        val userId = preferencesState.value.session?.userId ?: return
        viewModelScope.launch {
            prefs.updateLockPolicy(userId, policy)
            notifyMessage("Lock policy updated to ${policy.title}.")
        }
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        val email = preferencesState.value.session?.email.orEmpty()
        viewModelScope.launch {
            prefs.updateBiometrics(enabled, email)
            notifyMessage(if (enabled) "Biometric unlock enabled." else "Biometric unlock disabled.")
        }
    }

    fun unlockApp() {
        viewModelScope.launch {
            prefs.touchLastActive()
        }
    }

    fun lockAppNow() {
        viewModelScope.launch {
            prefs.setManuallyLocked(true)
        }
    }

    // ---------------------------------------------------------------------
    // APPEARANCE & PROFILE
    // ---------------------------------------------------------------------
    fun setThemePreference(pref: ThemePreference) {
        viewModelScope.launch { prefs.saveThemePreference(pref) }
    }

    fun setAccentPalette(palette: AccentPalette) {
        viewModelScope.launch { prefs.saveAccentPalette(palette) }
    }

    fun setAutoFillEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.saveAutoFillEnabled(enabled) }
    }

    fun saveProfileIdentity(displayName: String, avatarUri: String?) {
        val state = preferencesState.value
        val session = state.session
        viewModelScope.launch {
            prefs.saveProfileCustomization(displayName, avatarUri)
            if (session != null && displayName.isNotBlank() && state.supabaseConfig.isConfigured) {
                remoteRepo.updateDisplayNameOnServer(state.supabaseConfig, session, displayName)
                prefs.saveSession(session.copy(displayName = displayName.trim()))
            }
            notifyMessage("Profile settings saved.")
        }
    }

    // ---------------------------------------------------------------------
    // DATA SYNCHRONIZATION
    // ---------------------------------------------------------------------
    fun refreshAllData(silent: Boolean = false) {
        val state = preferencesState.value
        val session = state.session ?: return
        val config = state.supabaseConfig
        if (!config.isConfigured) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, isLoading = !silent) }
            val activeSession = ensureFreshSession(config, session)

            val settingsDeferred = async { remoteRepo.fetchSettings(config, activeSession.accessToken) }
            val reportsDeferred = async { remoteRepo.fetchReports(config, activeSession) }
            val staffDeferred = async { remoteRepo.fetchStaff(config, activeSession.accessToken) }
            val classesDeferred = async { remoteRepo.fetchClasses(config, activeSession.accessToken) }
            val subjectsDeferred = async { remoteRepo.fetchSubjects(config, activeSession.accessToken) }
            val checksDeferred = async { remoteRepo.fetchComplianceChecks(config, activeSession.accessToken) }
            val usersDeferred = async {
                if (activeSession.role.isAdminOrAbove) remoteRepo.fetchDirectorateUsers(config, activeSession.accessToken)
                else emptyList()
            }

            val settings = settingsDeferred.await()
            val reportsRes = reportsDeferred.await()
            val staff = staffDeferred.await()
            val classes = classesDeferred.await()
            val subjects = subjectsDeferred.await()
            val checks = checksDeferred.await()
            val users = usersDeferred.await()

            _uiState.update { current ->
                val fetchedReports = reportsRes.getOrElse { current.reports }
                val scopedReports = if (activeSession.role.isAdminOrAbove) {
                    fetchedReports
                } else {
                    fetchedReports.filter { it.reportedBy.equals(activeSession.userId, ignoreCase = true) }
                }
                val newTerm = if (current.selectedTerm.isBlank()) settings.activeTerm else current.selectedTerm
                current.copy(
                    isRefreshing = false,
                    isLoading = false,
                    systemSettings = settings,
                    selectedTerm = if (current.reports.isEmpty()) settings.activeTerm else newTerm,
                    reports = scopedReports,
                    staffList = staff,
                    classes = classes,
                    subjects = subjects,
                    serverCustomChecks = checks,
                    directorateUsers = users
                )
            }
        }
    }

    private suspend fun ensureFreshSession(config: SupabaseConfig, current: UserSession): UserSession {
        val refreshed = remoteRepo.refreshSession(config, current)
        return refreshed.getOrNull()?.also { prefs.saveSession(it) } ?: current
    }

    // ---------------------------------------------------------------------
    // OFFLINE DRAFTS
    // ---------------------------------------------------------------------
    fun saveOfflineDraftFile(title: String, notes: String, file: File?, mediaType: String, mediaName: String?) {
        viewModelScope.launch {
            draftRepo.saveDraft(title, notes, file, mediaType, mediaName)
            notifyMessage("Saved to Offline Drafts.")
        }
    }

    fun saveOfflineDraftUri(title: String, notes: String, uri: Uri, mediaType: String) {
        viewModelScope.launch {
            draftRepo.saveDraftFromUri(title, notes, uri, mediaType)
            notifyMessage("Captured attachment saved to Offline Drafts.")
        }
    }

    fun deleteDraft(draftId: String) {
        viewModelScope.launch {
            draftRepo.deleteDraft(draftId, deleteMediaFile = true)
            notifyMessage("Draft removed.")
        }
    }

    // ---------------------------------------------------------------------
    // REPORTS CRUD & MODERATION
    // ---------------------------------------------------------------------
    fun submitReport(
        existingReport: ComplianceReport?,
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
        sourceDraft: OfflineDraft?,
        cleanDraftMediaOnComplete: Boolean,
        onComplete: () -> Unit
    ) {
        val state = preferencesState.value
        val session = state.session ?: return
        val config = state.supabaseConfig

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val reportNumber = existingReport?.reportNumber ?: remoteRepo.generateReportNumber()
            val settings = _uiState.value.systemSettings
            val activeWeek = AcademicWeekEngine.calculateAcademicWeek(settings).weekNumber

            var finalEvidenceUrl = existingReport?.evidenceUrl
            var finalEvidenceType = existingReport?.evidenceType ?: "none"
            var finalEvidenceName = existingReport?.evidenceName

            if (localFile != null && localFile.exists()) {
                val mime = when (evidenceType) {
                    "image" -> "image/jpeg"
                    "video" -> "video/mp4"
                    "audio" -> "audio/mp4"
                    else -> "application/octet-stream"
                }
                val upRes = remoteRepo.uploadEvidenceFile(config, session.accessToken, reportNumber, localFile, mime)
                if (upRes.isFailure) {
                    _uiState.update { it.copy(isLoading = false) }
                    notifyMessage(upRes.exceptionOrNull()?.message ?: "Evidence upload failed.", isError = true)
                    return@launch
                }
                finalEvidenceUrl = upRes.getOrNull()
                finalEvidenceType = evidenceType
                finalEvidenceName = evidenceName ?: localFile.name
            } else if (pickedUri != null) {
                val upRes = remoteRepo.uploadEvidenceUri(config, session.accessToken, reportNumber, pickedUri, "application/octet-stream")
                if (upRes.isFailure) {
                    _uiState.update { it.copy(isLoading = false) }
                    notifyMessage(upRes.exceptionOrNull()?.message ?: "Evidence upload failed.", isError = true)
                    return@launch
                }
                val (path, name, mime) = upRes.getOrThrow()
                finalEvidenceUrl = path
                finalEvidenceName = name
                finalEvidenceType = when {
                    mime.startsWith("image/") -> "image"
                    mime.startsWith("video/") -> "video"
                    mime.startsWith("audio/") -> "audio"
                    else -> "document"
                }
            }

            val staffNameVal = when {
                staff != null -> staff.name
                division == ComplianceDivision.OPERATIONS && specificSubjectText.isNotBlank() -> specificSubjectText.trim()
                division == ComplianceDivision.DISCIPLINE && targetType == "division" -> "Student Decorum"
                division == ComplianceDivision.CDS -> "CDS Infrastructure"
                else -> "Staff / Unit"
            }

            val payload = JSONObject().apply {
                put("report_number", reportNumber)
                put("reported_by", session.userId)
                put("reporter_id", session.userId)
                put("reporter_name", state.customDisplayName.ifBlank { session.displayName }.ifBlank { session.email })
                put("reporting_officer", state.customDisplayName.ifBlank { session.displayName }.ifBlank { session.email })
                put("reporter_role", session.email)
                put("incident_date", incidentDate)
                put("date", incidentDate)
                put("week_number", existingReport?.weekNumber ?: activeWeek)
                put("term", existingReport?.term ?: settings.activeTerm)
                put("division", division.dbValue)
                put("department", staff?.department ?: division.dbValue)
                put("category", check?.category ?: operationScope.ifBlank { division.label })
                put("target_type", targetType)
                put("check_id", check?.id ?: "CUSTOM")
                put("compliance_check", check?.name ?: "General Compliance")
                put("exception", exceptionText.trim())
                put("exception_text", exceptionText.trim())
                put("effect", effectText.trim())
                put("effect_text", effectText.trim())
                put("severity", "MEDIUM")
                put("staff_id", staff?.id ?: JSONObject.NULL)
                put("staff_name", staffNameVal)
                put("staff_code", staff?.staffCode ?: JSONObject.NULL)
                put("staff_category", staff?.category ?: division.label)
                put("staff_group", staff?.staffGroup ?: operationScope.ifBlank { division.label })
                put("role", staff?.role ?: JSONObject.NULL)
                put("assigned_location", assignedLocation.ifBlank { classroom?.name ?: "" })
                put("class_id", classroom?.id ?: JSONObject.NULL)
                put("class_name", classroom?.name ?: JSONObject.NULL)
                put("subject_id", subject?.id ?: JSONObject.NULL)
                put("subject", subject?.name ?: JSONObject.NULL)
                put("evidence_url", finalEvidenceUrl ?: JSONObject.NULL)
                put("evidence_type", finalEvidenceType)
                put("evidence_name", finalEvidenceName ?: JSONObject.NULL)
                if (existingReport == null) {
                    put("status", "OPEN")
                    put("review_status", "UNSEEN")
                    put("is_seen", false)
                }
            }

            val result = if (existingReport != null) {
                remoteRepo.updateReport(config, session.accessToken, existingReport.id, payload)
            } else {
                remoteRepo.createReport(config, session, payload)
            }

            result.onSuccess { saved ->
                if (sourceDraft != null) {
                    draftRepo.deleteDraft(sourceDraft.id, deleteMediaFile = cleanDraftMediaOnComplete)
                }
                _uiState.update { current ->
                    val updatedList = if (existingReport != null) {
                        current.reports.map { if (it.id == saved.id) saved else it }
                    } else {
                        listOf(saved) + current.reports
                    }
                    current.copy(
                        isLoading = false,
                        reports = updatedList,
                        showReportForm = false,
                        editingReport = null,
                        convertingDraft = null
                    )
                }
                notifyMessage(if (existingReport != null) "Report updated." else "Compliance exception submitted.")
                onComplete()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false) }
                notifyMessage(err.message ?: "Failed to save report.", isError = true)
            }
        }
    }

    fun toggleSeenStatus(report: ComplianceReport) {
        val state = preferencesState.value
        val session = state.session ?: return
        viewModelScope.launch {
            remoteRepo.setReportSeenState(state.supabaseConfig, session, report.id, !report.isSeen)
                .onSuccess { updated ->
                    _uiState.update { current ->
                        current.copy(
                            reports = current.reports.map { if (it.id == updated.id) updated else it },
                            selectedReportForDetail = if (current.selectedReportForDetail?.id == updated.id) updated else current.selectedReportForDetail
                        )
                    }
                    notifyMessage(if (updated.isSeen) "Marked as Reviewed (Seen)." else "Reverted to Unseen.")
                }
                .onFailure { notifyMessage(it.message ?: "Unable to update review state.", isError = true) }
        }
    }

    fun updateResolution(report: ComplianceReport, status: ReportResolutionStatus, notes: String) {
        val state = preferencesState.value
        val session = state.session ?: return
        viewModelScope.launch {
            remoteRepo.updateReportResolution(state.supabaseConfig, session, report.id, status, notes)
                .onSuccess { updated ->
                    _uiState.update { current ->
                        current.copy(
                            reports = current.reports.map { if (it.id == updated.id) updated else it },
                            selectedReportForDetail = updated
                        )
                    }
                    notifyMessage("Status updated to ${status.label}.")
                }
                .onFailure { notifyMessage(it.message ?: "Failed to update resolution.", isError = true) }
        }
    }

    fun remapReportCheck(report: ComplianceReport, newCheck: ComplianceCheck) {
        val state = preferencesState.value
        val session = state.session ?: return
        viewModelScope.launch {
            val updates = JSONObject().apply {
                put("check_id", newCheck.id)
                put("compliance_check", newCheck.name)
                put("category", newCheck.category)
                put("division", newCheck.division.dbValue)
            }
            remoteRepo.updateReport(state.supabaseConfig, session.accessToken, report.id, updates)
                .onSuccess { updated ->
                    _uiState.update { current ->
                        current.copy(
                            reports = current.reports.map { if (it.id == updated.id) updated else it },
                            selectedReportForDetail = updated
                        )
                    }
                    notifyMessage("Remapped standard to ${newCheck.name}.")
                }
                .onFailure { notifyMessage(it.message ?: "Failed to remap check.", isError = true) }
        }
    }

    fun deleteReport(report: ComplianceReport) {
        val state = preferencesState.value
        val session = state.session ?: return
        viewModelScope.launch {
            remoteRepo.deleteReport(state.supabaseConfig, session.accessToken, report.id)
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            reports = current.reports.filterNot { it.id == report.id },
                            selectedReportForDetail = null
                        )
                    }
                    notifyMessage("Report permanently deleted.")
                }
                .onFailure { notifyMessage(it.message ?: "Unable to delete report.", isError = true) }
        }
    }

    suspend fun resolveSignedEvidenceUrl(storedPath: String): Result<String> {
        val state = preferencesState.value
        val token = state.session?.accessToken.orEmpty()
        return remoteRepo.getSignedEvidenceUrl(state.supabaseConfig, token, storedPath)
    }

    fun addCustomCheck(division: ComplianceDivision, name: String, category: String) {
        viewModelScope.launch {
            val check = ComplianceCheck(
                id = "USR-${UUID.randomUUID().toString().take(6).uppercase()}",
                name = name.trim(),
                category = category.ifBlank { division.label },
                division = division,
                isCustom = true
            )
            prefs.addCustomCheck(check)
            notifyMessage("Added custom compliance check.")
        }
    }

    // ---------------------------------------------------------------------
    // STAFF & ADMIN OPERATIONS
    // ---------------------------------------------------------------------
    fun saveStaffMember(staff: StaffMember, isNew: Boolean, onSuccess: () -> Unit) {
        val state = preferencesState.value
        val session = state.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteRepo.upsertStaff(state.supabaseConfig, session.accessToken, staff, isNew)
                .onSuccess { saved ->
                    _uiState.update { current ->
                        val list = if (isNew) {
                            (current.staffList + saved).sortedBy { it.name }
                        } else {
                            current.staffList.map { if (it.id == saved.id) saved else it }
                        }
                        current.copy(isLoading = false, staffList = list)
                    }
                    notifyMessage(if (isNew) "Staff member added." else "Staff details updated.")
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    notifyMessage(err.message ?: "Failed to save staff member.", isError = true)
                }
        }
    }

    fun toggleStaffArchive(staff: StaffMember) {
        val state = preferencesState.value
        val session = state.session ?: return
        viewModelScope.launch {
            val newActive = !staff.isActive
            remoteRepo.setStaffActiveStatus(state.supabaseConfig, session.accessToken, staff.id, newActive)
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            staffList = current.staffList.map {
                                if (it.id == staff.id) it.copy(isActive = newActive) else it
                            }
                        )
                    }
                    notifyMessage(if (newActive) "${staff.name} restored." else "${staff.name} archived.")
                }
                .onFailure { notifyMessage(it.message ?: "Unable to update staff status.", isError = true) }
        }
    }

    fun reassignConflictingStaffCodeAndSave(
        conflictHolder: StaffMember,
        newCodeForHolder: String,
        newStaff: StaffMember,
        isNew: Boolean,
        onSuccess: () -> Unit
    ) {
        val state = preferencesState.value
        val session = state.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val updatedHolder = conflictHolder.copy(
                staffCode = newCodeForHolder.trim(),
                staffNumber = newCodeForHolder.trim()
            )
            remoteRepo.upsertStaff(state.supabaseConfig, session.accessToken, updatedHolder, isNew = false)
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    notifyMessage(err.message ?: "Failed to reassign conflicting code.", isError = true)
                    return@launch
                }
            remoteRepo.upsertStaff(state.supabaseConfig, session.accessToken, newStaff, isNew = isNew)
                .onSuccess {
                    refreshAllData(silent = true)
                    notifyMessage("Conflict resolved and staff record saved.")
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    notifyMessage(err.message ?: "Failed saving staff record.", isError = true)
                }
        }
    }

    fun archiveConflictingStaffAndSave(
        conflictHolder: StaffMember,
        newStaff: StaffMember,
        isNew: Boolean,
        onSuccess: () -> Unit
    ) {
        val state = preferencesState.value
        val session = state.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val clearedCode = "${conflictHolder.staffCode}-ARCHIVED-${System.currentTimeMillis() % 10000}"
            remoteRepo.upsertStaff(
                state.supabaseConfig,
                session.accessToken,
                conflictHolder.copy(staffCode = clearedCode, staffNumber = clearedCode, isActive = false),
                isNew = false
            )
            remoteRepo.upsertStaff(state.supabaseConfig, session.accessToken, newStaff, isNew = isNew)
                .onSuccess {
                    refreshAllData(silent = true)
                    notifyMessage("Former record archived and new staff record saved.")
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    notifyMessage(err.message ?: "Failed saving staff record.", isError = true)
                }
        }
    }

    fun updateUserRole(user: DirectorateUser, newRole: UserRole) {
        val state = preferencesState.value
        val session = state.session ?: return
        viewModelScope.launch {
            remoteRepo.updateDirectorateUserRole(state.supabaseConfig, session.accessToken, user.userId, newRole)
                .onSuccess {
                    _uiState.update { cur ->
                        cur.copy(
                            directorateUsers = cur.directorateUsers.map {
                                if (it.userId == user.userId) it.copy(role = newRole) else it
                            }
                        )
                    }
                    notifyMessage("Role updated to ${newRole.label}.")
                }
                .onFailure { notifyMessage(it.message ?: "Failed to update role.", isError = true) }
        }
    }

    fun toggleUserStatus(user: DirectorateUser) {
        val state = preferencesState.value
        val session = state.session ?: return
        val nextStatus = if (user.status.equals("active", ignoreCase = true)) "suspended" else "active"
        viewModelScope.launch {
            remoteRepo.updateDirectorateUserStatus(state.supabaseConfig, session.accessToken, user.userId, nextStatus)
                .onSuccess {
                    _uiState.update { cur ->
                        cur.copy(
                            directorateUsers = cur.directorateUsers.map {
                                if (it.userId == user.userId) it.copy(status = nextStatus) else it
                            }
                        )
                    }
                    notifyMessage("User ${if (nextStatus == "active") "activated" else "suspended"}.")
                }
                .onFailure { notifyMessage(it.message ?: "Failed updating user status.", isError = true) }
        }
    }

    fun updateUserEmail(user: DirectorateUser, newEmail: String) {
        val state = preferencesState.value
        val session = state.session ?: return
        viewModelScope.launch {
            remoteRepo.updateDirectorateUserEmail(state.supabaseConfig, session.accessToken, user.userId, newEmail)
                .onSuccess {
                    _uiState.update { cur ->
                        cur.copy(
                            directorateUsers = cur.directorateUsers.map {
                                if (it.userId == user.userId) it.copy(email = newEmail.trim()) else it
                            }
                        )
                    }
                    notifyMessage("User email updated.")
                }
                .onFailure { notifyMessage(it.message ?: "Failed updating user email.", isError = true) }
        }
    }

    fun forceResetUserPassword(user: DirectorateUser) {
        val state = preferencesState.value
        val session = state.session ?: return
        viewModelScope.launch {
            remoteRepo.forceResetDirectoratePassword(state.supabaseConfig, session.accessToken, user.userId, user.email)
                .onSuccess { msg -> notifyMessage(msg) }
                .onFailure { notifyMessage(it.message ?: "Password reset failed.", isError = true) }
        }
    }

    fun provisionUser(
        email: String,
        password: String,
        displayName: String,
        staffNumber: String,
        role: UserRole,
        onSuccess: () -> Unit
    ) {
        val state = preferencesState.value
        val session = state.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteRepo.provisionDirectorateUser(
                state.supabaseConfig,
                session.accessToken,
                email,
                password,
                displayName,
                staffNumber,
                role
            ).onSuccess {
                refreshAllData(silent = true)
                notifyMessage("New officer account provisioned.")
                onSuccess()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false) }
                notifyMessage(err.message ?: "Provisioning failed.", isError = true)
            }
        }
    }

    fun saveSystemSettings(newSettings: SystemSettings) {
        val state = preferencesState.value
        val session = state.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteRepo.updateSettings(state.supabaseConfig, session.accessToken, newSettings)
                .onSuccess { saved ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            systemSettings = saved,
                            selectedTerm = saved.activeTerm
                        )
                    }
                    notifyMessage("System academic settings saved.")
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    notifyMessage(err.message ?: "Failed updating system settings.", isError = true)
                }
        }
    }

    fun purgeEvidenceBucket() {
        val state = preferencesState.value
        val session = state.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteRepo.purgeEvidenceBucket(state.supabaseConfig, session.accessToken)
                .onSuccess { count ->
                    _uiState.update { it.copy(isLoading = false) }
                    notifyMessage("Purged $count evidence media files from cloud storage.")
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    notifyMessage(err.message ?: "Storage purge failed.", isError = true)
                }
        }
    }

    fun exportOfficialPdf(
        scope: ReportScope,
        term: String,
        startDate: String = "",
        endDate: String = ""
    ) {
        viewModelScope.launch {
            val state = _uiState.value
            val session = preferencesState.value.session
            val accountReports = if (session == null || session.role.isAdminOrAbove) {
                state.reports
            } else {
                state.reports.filter { it.reportedBy.equals(session.userId, ignoreCase = true) }
            }
            val staffMap = AnalysisEngine.buildStaffLookup(state.staffList)
            val filtered = AnalysisEngine.filterReports(accountReports, term, startDate, endDate)
                .filter { AnalysisEngine.recordMatchesScope(it, scope, staffMap) }

            val dateLabel = if (startDate.isBlank() && endDate.isBlank()) "All Dates" else "${startDate.ifBlank { "..." }} to ${endDate.ifBlank { "..." }}"
            PdfReportExporter.exportAndShare(getApplication(), filtered, scope, term, dateLabel)
                .onFailure { err -> notifyMessage(err.message ?: "PDF generation failed.", isError = true) }
        }
    }
}
