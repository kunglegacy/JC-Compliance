package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AcademicWeekEngine
import com.example.data.local.AppPreferencesState
import com.example.data.local.ComplianceNotificationJobService
import com.example.data.local.ComplianceTaxonomy
import com.example.data.local.DraftRepository
import com.example.data.local.SecurePreferences
import com.example.data.model.AccentPalette
import com.example.data.model.AppNotificationRecord
import com.example.data.model.Classroom
import com.example.data.model.ComplianceCheck
import com.example.data.model.ComplianceDivision
import com.example.data.model.ComplianceReport
import com.example.data.model.DirectorateUser
import com.example.data.model.DraftMediaItem
import com.example.data.model.LockPolicy
import com.example.data.model.MediaUploadLimits
import com.example.data.model.OfflineDraft
import com.example.data.model.ReportEvidenceAttachment
import com.example.data.model.ReportFormDraft
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
import com.example.domain.ComplianceNotificationHelper
import com.example.domain.MediaCompressor
import com.example.domain.PdfReportExporter
import com.example.ui.screens.reports.EvidenceVerdict
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
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
    val compressionProgress: Float? = null,
    val compressionLabel: String? = null,
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
    val convertingDraftMedia: List<DraftMediaItem> = emptyList(),
    val savedFormDraft: ReportFormDraft? = null,
    val showResumeReportPrompt: Boolean = false,
    val accountSubScreen: String? = null, // null | "settings" | "change_password" | "password_reset" | "admin"
    val adminSubScreen: String = "hub",    // "hub" | "officer_stats" | "staff_register" | "system_users" | "system_config"
    val lockCheckNonce: Long = 0L,
    val pendingNavigationReportId: String? = null
)

class ComplianceViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = SecurePreferences(application)
    private val draftRepo = DraftRepository(application)
    private val remoteRepo = ComplianceRepository(application)

    private var unlockedInCurrentProcess = false
    private var isAwaitingExternalActivity = false
    private var wasBackgrounded = false
    private var lastVerifiedPassword: Pair<String, String>? = null
    private var knownReportSnapshots: Map<String, ReportResolutionStatus>? = null
    private var initialSyncDone = false
    private var pollingJob: Job? = null

    fun setAwaitingExternalActivity(awaiting: Boolean) {
        isAwaitingExternalActivity = awaiting
    }

    val preferencesState: StateFlow<AppPreferencesState> = prefs.state.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppPreferencesState(
            supabaseConfig = SupabaseConfig("", ""),
            session = null
        )
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val drafts: StateFlow<List<OfflineDraft>> = preferencesState
        .map { it.session?.userId.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { userId ->
            if (userId.isBlank()) flowOf(emptyList())
            else draftRepo.observeDraftsForUser(userId)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList()
        )

    private val _uiState = MutableStateFlow(ComplianceUiState())
    val uiState: StateFlow<ComplianceUiState> = _uiState.asStateFlow()

    init {
        ComplianceNotificationHelper.ensureChannels(application)
        viewModelScope.launch {
            val initial = prefs.state.first()
            val locked = shouldShowAppLock(initial)
            if (locked) {
                draftRepo.wipeActiveSessionMediaCache()
            } else if (initial.session != null) {
                draftRepo.warmActiveSessionMediaCache(initial.session.userId)
                refreshSavedFormDraftState(initial.session.userId, triggerPrompt = true)
            }
            _uiState.update { it.copy(isBootstrapping = false) }
            if (initial.session != null && initial.supabaseConfig.isConfigured) {
                ComplianceNotificationJobService.schedulePeriodicSync(application)
                startForegroundNotificationLoop()
                refreshAllData(silent = true)
            }
        }
    }

    private fun startForegroundNotificationLoop() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(45_000L)
                val state = preferencesState.value
                if (state.session != null && state.supabaseConfig.isConfigured && !shouldShowAppLock(state)) {
                    refreshAllData(silent = true)
                }
            }
        }
    }

    fun handleNotificationNavigation(tab: String?, reportId: String?) {
        val targetTab = tab?.takeIf { it.isNotBlank() } ?: return
        val session = preferencesState.value.session
        val resolvedTab = if (targetTab == "reports" && session?.role?.isAdminOrAbove != true) {
            "my-exceptions"
        } else {
            targetTab
        }
        val matched = reportId?.let { id -> _uiState.value.reports.firstOrNull { it.id == id } }
        _uiState.update {
            it.copy(
                activeTab = resolvedTab,
                accountSubScreen = null,
                selectedReportForDetail = matched ?: it.selectedReportForDetail,
                pendingNavigationReportId = if (matched == null) reportId else null
            )
        }
    }

    private suspend fun refreshSavedFormDraftState(userId: String, triggerPrompt: Boolean) {
        if (userId.isBlank()) return
        val loaded = draftRepo.loadComposerFormDraft(userId)?.takeIf { it.hasContent }
        _uiState.update { current ->
            val shouldPrompt = triggerPrompt && loaded != null && !current.showReportForm
            current.copy(
                savedFormDraft = loaded,
                showResumeReportPrompt = if (shouldPrompt) true else current.showResumeReportPrompt
            )
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

    fun openNewReportForm(draft: OfflineDraft? = null, selectedMedia: List<DraftMediaItem> = emptyList()) {
        val resolvedMedia = if (selectedMedia.isNotEmpty()) selectedMedia else draft?.mediaItems.orEmpty()
        _uiState.update {
            it.copy(
                showReportForm = true,
                showResumeReportPrompt = false,
                editingReport = null,
                convertingDraft = draft,
                convertingDraftMedia = resolvedMedia
            )
        }
    }

    fun openEditReportForm(report: ComplianceReport) {
        val session = preferencesState.value.session
        if (report.isReviewedFinal && session?.role?.isAdminOrAbove != true) {
            notifyMessage("Accepted or rejected exceptions are locked and cannot be edited.", isError = true)
            return
        }
        _uiState.update {
            it.copy(
                selectedReportForDetail = null,
                showReportForm = true,
                editingReport = report,
                convertingDraft = null,
                convertingDraftMedia = emptyList()
            )
        }
    }

    fun closeReportForm() {
        _uiState.update {
            it.copy(
                showReportForm = false,
                editingReport = null,
                convertingDraft = null,
                convertingDraftMedia = emptyList()
            )
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
                unlockedInCurrentProcess = true
                lastVerifiedPassword = session.email.lowercase() to password
                val currentPrefs = prefs.state.first()
                if (currentPrefs.biometricsEnabled &&
                    (currentPrefs.biometricsUserEmail.equals(session.email, true) || currentPrefs.biometricsUserEmail.isBlank())
                ) {
                    prefs.saveBiometricCredential(session.email, password)
                }
                prefs.saveSession(session)
                draftRepo.warmActiveSessionMediaCache(session.userId)
                refreshSavedFormDraftState(session.userId, triggerPrompt = true)
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
                ComplianceNotificationJobService.schedulePeriodicSync(getApplication())
                startForegroundNotificationLoop()
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

    fun hasBiometricQuickLogin(email: String): Boolean {
        val clean = email.trim()
        if (clean.isBlank()) return false
        return preferencesState.value.biometricsEnabled && prefs.hasBiometricCredentialFor(clean)
    }

    fun signInWithBiometrics(email: String) {
        val clean = email.trim()
        val savedPassword = prefs.decryptBiometricCredential(clean)
        if (savedPassword.isNullOrBlank()) {
            _uiState.update {
                it.copy(authError = "Saved biometric session expired. Please sign in once with your password.")
            }
            return
        }
        signIn(clean, savedPassword)
    }

    fun switchRememberedAccount() {
        lastVerifiedPassword = null
        viewModelScope.launch {
            prefs.clearRememberedAccount()
            _uiState.update { it.copy(authError = null) }
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
                    val curPrefs = preferencesState.value
                    if (curPrefs.biometricsEnabled && curPrefs.biometricsUserEmail.equals(session.email, true)) {
                        prefs.saveBiometricCredential(session.email, newPassword)
                    }
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
        val ok = remoteRepo.verifyPassword(config, session.email, password)
        if (ok) {
            lastVerifiedPassword = session.email.lowercase() to password
        }
        return ok
    }

    fun signOut() {
        unlockedInCurrentProcess = false
        lastVerifiedPassword = null
        knownReportSnapshots = null
        initialSyncDone = false
        pollingJob?.cancel()
        pollingJob = null
        ComplianceNotificationJobService.cancelAll(getApplication())
        prefs.setAppLockedSync(false)
        draftRepo.wipeActiveSessionMediaCache()
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
        if (state.isManuallyLocked || prefs.isAppLockedSync()) return true
        return when (state.lockPolicy) {
            LockPolicy.ALWAYS -> !unlockedInCurrentProcess || state.lastActiveMillis == 0L
            LockPolicy.HOURS_24 -> {
                if (state.lastActiveMillis == 0L) true
                else (System.currentTimeMillis() - state.lastActiveMillis) >= 24L * 60L * 60L * 1000L
            }
            LockPolicy.NEVER -> false
        }
    }

    fun onAppBackgrounded(isChangingConfigs: Boolean) {
        if (isChangingConfigs || isAwaitingExternalActivity) return
        val state = preferencesState.value
        if (state.session == null || !state.deviceSetupCompleted) return
        wasBackgrounded = true
        val hasPendingDrafts = drafts.value.isNotEmpty() || _uiState.value.savedFormDraft?.hasContent == true
        if (hasPendingDrafts) {
            ComplianceNotificationJobService.scheduleDraftReminder(getApplication(), 90_000L)
        }
        when (state.lockPolicy) {
            LockPolicy.ALWAYS -> {
                unlockedInCurrentProcess = false
                prefs.setAppLockedSync(true)
                draftRepo.wipeActiveSessionMediaCache()
                _uiState.update { it.copy(savedFormDraft = null, showResumeReportPrompt = false) }
                viewModelScope.launch { prefs.setManuallyLocked(true) }
            }
            LockPolicy.HOURS_24 -> {
                if (unlockedInCurrentProcess) {
                    viewModelScope.launch { prefs.recordLastActiveTimestamp() }
                }
            }
            LockPolicy.NEVER -> Unit
        }
    }

    fun onAppForegrounded() {
        if (isAwaitingExternalActivity) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(900)
                isAwaitingExternalActivity = false
            }
            return
        }
        val state = preferencesState.value
        val returningFromBackground = wasBackgrounded
        wasBackgrounded = false
        if (state.session != null && state.deviceSetupCompleted) {
            var lockedNow = false
            when (state.lockPolicy) {
                LockPolicy.ALWAYS -> {
                    if (!unlockedInCurrentProcess || prefs.isAppLockedSync()) {
                        unlockedInCurrentProcess = false
                        lockedNow = true
                        prefs.setAppLockedSync(true)
                        draftRepo.wipeActiveSessionMediaCache()
                        _uiState.update { it.copy(savedFormDraft = null, showResumeReportPrompt = false) }
                        viewModelScope.launch { prefs.setManuallyLocked(true) }
                    }
                }
                LockPolicy.HOURS_24 -> {
                    val elapsed = System.currentTimeMillis() - state.lastActiveMillis
                    if (state.lastActiveMillis == 0L || elapsed >= 24L * 60L * 60L * 1000L) {
                        unlockedInCurrentProcess = false
                        lockedNow = true
                        prefs.setAppLockedSync(true)
                        draftRepo.wipeActiveSessionMediaCache()
                        _uiState.update { it.copy(savedFormDraft = null, showResumeReportPrompt = false) }
                        viewModelScope.launch { prefs.setManuallyLocked(true) }
                    }
                }
                LockPolicy.NEVER -> Unit
            }
            if (!lockedNow && returningFromBackground) {
                viewModelScope.launch {
                    refreshSavedFormDraftState(state.session.userId, triggerPrompt = true)
                }
            }
        }
        _uiState.update { it.copy(lockCheckNonce = System.currentTimeMillis()) }
    }

    fun completeDeviceSetup(lockPolicy: LockPolicy, enableBiometrics: Boolean) {
        val session = preferencesState.value.session ?: return
        unlockedInCurrentProcess = true
        viewModelScope.launch {
            if (enableBiometrics) {
                val cached = lastVerifiedPassword
                if (cached != null && cached.first == session.email.lowercase()) {
                    prefs.saveBiometricCredential(session.email, cached.second)
                }
            } else {
                prefs.clearBiometricCredential()
            }
            lastVerifiedPassword = null
            prefs.completeDeviceSetup(
                userId = session.userId,
                lockPolicy = lockPolicy,
                biometricsEnabled = enableBiometrics,
                biometricsEmail = if (enableBiometrics) session.email else ""
            )
            draftRepo.warmActiveSessionMediaCache(session.userId)
            refreshSavedFormDraftState(session.userId, triggerPrompt = true)
            notifyMessage("Device security configuration saved.")
        }
    }

    fun updateLockPolicy(policy: LockPolicy) {
        val userId = preferencesState.value.session?.userId ?: return
        unlockedInCurrentProcess = true
        prefs.setAppLockedSync(false)
        viewModelScope.launch {
            prefs.updateLockPolicy(userId, policy)
            prefs.touchLastActive()
            notifyMessage("Lock policy updated to ${policy.title}.")
        }
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        val email = preferencesState.value.session?.email.orEmpty()
        viewModelScope.launch {
            if (enabled) {
                val cached = lastVerifiedPassword
                if (cached != null && cached.first == email.lowercase()) {
                    prefs.saveBiometricCredential(email, cached.second)
                }
            } else {
                prefs.clearBiometricCredential()
            }
            lastVerifiedPassword = null
            prefs.updateBiometrics(enabled, email)
            notifyMessage(if (enabled) "Biometric unlock enabled." else "Biometric unlock disabled.")
        }
    }

    fun unlockApp() {
        unlockedInCurrentProcess = true
        wasBackgrounded = false
        prefs.setAppLockedSync(false)
        val userId = preferencesState.value.session?.userId.orEmpty()
        viewModelScope.launch {
            prefs.touchLastActive()
            if (userId.isNotBlank()) {
                draftRepo.warmActiveSessionMediaCache(userId)
                refreshSavedFormDraftState(userId, triggerPrompt = true)
            }
            ComplianceNotificationJobService.schedulePeriodicSync(getApplication())
            startForegroundNotificationLoop()
            _uiState.update { it.copy(lockCheckNonce = System.currentTimeMillis()) }
        }
    }

    fun lockAppNow() {
        unlockedInCurrentProcess = false
        prefs.setAppLockedSync(true)
        draftRepo.wipeActiveSessionMediaCache()
        _uiState.update { it.copy(savedFormDraft = null, showResumeReportPrompt = false) }
        viewModelScope.launch {
            prefs.setManuallyLocked(true)
            _uiState.update { it.copy(lockCheckNonce = System.currentTimeMillis()) }
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
        val cleanName = displayName.trim()
        viewModelScope.launch {
            var resolvedAvatar = avatarUri
            val needsUpload = !avatarUri.isNullOrBlank() &&
                !avatarUri.startsWith("http://") &&
                !avatarUri.startsWith("https://") &&
                session != null &&
                state.supabaseConfig.isConfigured

            if (needsUpload && session != null) {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        compressionLabel = "Optimizing profile photo...",
                        compressionProgress = 0.1f
                    )
                }
                val activeSession = ensureFreshSession(state.supabaseConfig, session)
                val compRes = if (avatarUri!!.startsWith("/")) {
                    MediaCompressor.compressImageFile(
                        context = getApplication(),
                        sourceFile = File(avatarUri),
                        maxDimension = 512,
                        targetMaxBytes = 140 * 1024,
                        onProgress = { p -> _uiState.update { it.copy(compressionProgress = p) } }
                    )
                } else {
                    MediaCompressor.compressAvatarUri(
                        context = getApplication(),
                        uri = Uri.parse(avatarUri),
                        onProgress = { p -> _uiState.update { it.copy(compressionProgress = p) } }
                    )
                }
                val compressedFile = compRes.getOrNull()?.file
                if (compressedFile != null && compressedFile.exists()) {
                    _uiState.update {
                        it.copy(compressionLabel = "Uploading profile photo...", compressionProgress = 0.9f)
                    }
                    val upRes = remoteRepo.uploadProfileAvatar(state.supabaseConfig, activeSession, compressedFile)
                    upRes.onSuccess { cloudUrl ->
                        resolvedAvatar = cloudUrl
                    }.onFailure { err ->
                        _uiState.update { it.copy(isLoading = false, compressionLabel = null, compressionProgress = null) }
                        notifyMessage(err.message ?: "Unable to sync profile photo to cloud.", isError = true)
                        return@launch
                    }
                }
            }

            prefs.saveProfileCustomization(cleanName, resolvedAvatar)
            if (session != null && state.supabaseConfig.isConfigured) {
                val activeSession = ensureFreshSession(state.supabaseConfig, session)
                if (cleanName.isNotBlank()) {
                    remoteRepo.updateDisplayNameOnServer(state.supabaseConfig, activeSession, cleanName)
                }
                prefs.saveSession(
                    activeSession.copy(
                        displayName = cleanName.ifBlank { activeSession.displayName },
                        avatarUrl = resolvedAvatar ?: activeSession.avatarUrl
                    )
                )
            }
            _uiState.update { it.copy(isLoading = false, compressionLabel = null, compressionProgress = null) }
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
            val avatarDeferred = async { remoteRepo.fetchProfileAvatarUrl(config, activeSession) }
            val usersDeferred = async {
                if (activeSession.role.isAdminOrAbove) {
                    remoteRepo.fetchDirectorateUsers(config, activeSession.accessToken, activeSession)
                } else {
                    emptyList()
                }
            }
            val notificationsDeferred = async {
                remoteRepo.fetchUnreadNotifications(config, activeSession)
            }

            val settings = settingsDeferred.await()
            val reportsRes = reportsDeferred.await()
            val staff = staffDeferred.await()
            val classes = classesDeferred.await()
            val subjects = subjectsDeferred.await()
            val checks = checksDeferred.await()
            val fetchedAvatar = avatarDeferred.await()
            val users = usersDeferred.await()
            val unreadNotifications = notificationsDeferred.await()

            if (fetchedAvatar.isNotBlank() && fetchedAvatar != state.avatarUri) {
                prefs.saveProfileCustomization(null, fetchedAvatar)
            }

            dispatchServerNotifications(config, activeSession, unreadNotifications)

            val fetchedReports = reportsRes.getOrElse { _uiState.value.reports }
            val scopedReports = if (activeSession.role.isAdminOrAbove) {
                fetchedReports
            } else {
                fetchedReports.filter { it.reportedBy.equals(activeSession.userId, ignoreCase = true) }
            }

            processReportDiffNotifications(activeSession, scopedReports)

            _uiState.update { current ->
                val newTerm = if (current.selectedTerm.isBlank()) settings.activeTerm else current.selectedTerm
                val pendingId = current.pendingNavigationReportId
                val targetDetail = if (!pendingId.isNullOrBlank()) {
                    scopedReports.firstOrNull { it.id == pendingId } ?: current.selectedReportForDetail
                } else {
                    current.selectedReportForDetail
                }
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
                    directorateUsers = users,
                    selectedReportForDetail = targetDetail,
                    pendingNavigationReportId = if (targetDetail?.id == pendingId) null else pendingId
                )
            }
        }
    }

    private suspend fun dispatchServerNotifications(
        config: SupabaseConfig,
        session: UserSession,
        notifications: List<AppNotificationRecord>
    ) {
        if (notifications.isEmpty()) return
        val ctx = getApplication<Application>()
        notifications.forEach { n ->
            when (n.type.uppercase()) {
                "ACTION_REQUIRED" -> ComplianceNotificationHelper.notifyActionRequired(
                    context = ctx,
                    dedupKey = n.reportId ?: n.id,
                    reportId = n.reportId,
                    reportNumber = n.reportNumber.orEmpty(),
                    complianceCheck = n.body
                )
                "ACCEPTED" -> ComplianceNotificationHelper.notifyAccepted(
                    context = ctx,
                    dedupKey = n.reportId ?: n.id,
                    reportId = n.reportId,
                    reportNumber = n.reportNumber.orEmpty(),
                    complianceCheck = n.body
                )
                "NEW_SUBMISSION" -> if (session.role.isAdminOrAbove) {
                    ComplianceNotificationHelper.notifyNewSubmissionForAdmin(
                        context = ctx,
                        dedupKey = n.reportId ?: n.id,
                        reportId = n.reportId,
                        reporterName = n.actorName
                    )
                }
                else -> ComplianceNotificationHelper.notifyCustomServerAlert(
                    context = ctx,
                    dedupKey = n.id,
                    title = n.title,
                    body = n.body,
                    targetTab = if (session.role.isAdminOrAbove) "reports" else "my-exceptions",
                    reportId = n.reportId
                )
            }
            remoteRepo.markNotificationRead(config, session.accessToken, n.id)
        }
    }

    private fun processReportDiffNotifications(session: UserSession, reports: List<ComplianceReport>) {
        val ctx = getApplication<Application>()
        val previous = knownReportSnapshots
        if (!initialSyncDone || previous == null) {
            // Seed existing reports so initial app boot doesn't flood user with historical alerts
            val seedKeys = buildList {
                reports.forEach { r ->
                    add("new_sub_${r.id}")
                    if (r.status == ReportResolutionStatus.ACTION_REQUIRED) add("action_req_${r.id}_${r.status.key}")
                    if (r.status == ReportResolutionStatus.ACCEPTED) add("accepted_${r.id}_${r.status.key}")
                }
            }
            ComplianceNotificationHelper.seedSeenKeys(ctx, seedKeys)
            knownReportSnapshots = reports.associate { it.id to it.status }
            initialSyncDone = true
            return
        }

        reports.forEach { report ->
            val prevStatus = previous[report.id]
            val isMine = report.reportedBy.equals(session.userId, ignoreCase = true)

            if (session.role.isAdminOrAbove && prevStatus == null && !isMine) {
                ComplianceNotificationHelper.notifyNewSubmissionForAdmin(
                    context = ctx,
                    dedupKey = report.id,
                    reportId = report.id,
                    reporterName = report.reporterName,
                    complianceCheck = report.complianceCheck
                )
            }

            if (isMine && prevStatus != null && prevStatus != report.status) {
                when (report.status) {
                    ReportResolutionStatus.ACTION_REQUIRED -> {
                        ComplianceNotificationHelper.notifyActionRequired(
                            context = ctx,
                            dedupKey = "${report.id}_${report.status.key}",
                            reportId = report.id,
                            reportNumber = report.reportNumber,
                            complianceCheck = report.complianceCheck
                        )
                    }
                    ReportResolutionStatus.ACCEPTED -> {
                        ComplianceNotificationHelper.notifyAccepted(
                            context = ctx,
                            dedupKey = "${report.id}_${report.status.key}",
                            reportId = report.id,
                            reportNumber = report.reportNumber,
                            complianceCheck = report.complianceCheck
                        )
                    }
                    else -> Unit
                }
            }
        }
        knownReportSnapshots = reports.associate { it.id to it.status }
    }

    private suspend fun ensureFreshSession(config: SupabaseConfig, current: UserSession): UserSession {
        val refreshed = remoteRepo.refreshSession(config, current)
        return refreshed.getOrNull()?.also { prefs.saveSession(it) } ?: current
    }

    // ---------------------------------------------------------------------
    // OFFLINE DRAFTS (USER-ISOLATED MULTI-MEDIA)
    // ---------------------------------------------------------------------
    fun saveOfflineDraftFile(title: String, notes: String, file: File?, mediaType: String, mediaName: String?) {
        val userId = preferencesState.value.session?.userId.orEmpty()
        if (userId.isBlank()) return
        viewModelScope.launch {
            draftRepo.saveDraft(userId, title, notes, file, mediaType, mediaName)
            notifyMessage("Saved to Offline Drafts.")
        }
    }

    fun saveOfflineDraftUri(title: String, notes: String, uri: Uri, mediaType: String) {
        val userId = preferencesState.value.session?.userId.orEmpty()
        if (userId.isBlank()) return
        viewModelScope.launch {
            draftRepo.saveDraftFromUri(userId, title, notes, uri, mediaType)
            notifyMessage("Captured attachment saved to Offline Drafts.")
        }
    }

    private fun canAppendDraftMedia(draftId: String, mediaType: String): Boolean {
        val draft = drafts.value.firstOrNull { it.id == draftId } ?: return true
        val existing = if (draft.mediaItems.isNotEmpty()) {
            draft.mediaItems
        } else if (!draft.mediaPath.isNullOrBlank()) {
            listOf(DraftMediaItem("primary", draft.mediaPath, draft.mediaType, ""))
        } else {
            emptyList()
        }
        val imageCount = existing.count { it.mediaType.equals("image", true) }
        val videoCount = existing.count { it.mediaType.equals("video", true) }
        if (mediaType.equals("image", true) && imageCount >= MediaUploadLimits.MAX_IMAGES_PER_REPORT) {
            notifyMessage("Maximum ${MediaUploadLimits.MAX_IMAGES_PER_REPORT} images allowed per exception draft.", isError = true)
            return false
        }
        if (mediaType.equals("video", true) && videoCount >= MediaUploadLimits.MAX_VIDEOS_PER_REPORT) {
            notifyMessage("Maximum ${MediaUploadLimits.MAX_VIDEOS_PER_REPORT} videos allowed per exception draft.", isError = true)
            return false
        }
        return true
    }

    fun addMediaFileToDraft(draftId: String, file: File, mediaType: String) {
        val userId = preferencesState.value.session?.userId.orEmpty()
        if (userId.isBlank()) return
        if (!canAppendDraftMedia(draftId, mediaType)) return
        viewModelScope.launch {
            draftRepo.addMediaFileToDraft(userId, draftId, file, mediaType)
            notifyMessage("Evidence added to draft.")
        }
    }

    fun addMediaUriToDraft(draftId: String, uri: Uri, mediaType: String) {
        val userId = preferencesState.value.session?.userId.orEmpty()
        if (userId.isBlank()) return
        if (!canAppendDraftMedia(draftId, mediaType)) return
        viewModelScope.launch {
            draftRepo.addMediaUriToDraft(userId, draftId, uri, mediaType)
            notifyMessage("Evidence added to draft.")
        }
    }

    fun removeMediaItemFromDraft(draftId: String, mediaId: String) {
        val userId = preferencesState.value.session?.userId.orEmpty()
        if (userId.isBlank()) return
        viewModelScope.launch {
            draftRepo.removeMediaItemFromDraft(userId, draftId, mediaId)
        }
    }

    fun deleteDraft(draftId: String) {
        val userId = preferencesState.value.session?.userId.orEmpty()
        if (userId.isBlank()) return
        viewModelScope.launch {
            draftRepo.deleteDraft(userId, draftId, deleteMediaFile = true)
            notifyMessage("Draft removed.")
        }
    }

    // ---------------------------------------------------------------------
    // IN-PROGRESS REPORT COMPOSER AUTO-DRAFT & RESUME PROMPT
    // ---------------------------------------------------------------------
    fun saveInProgressReportDraft(draft: ReportFormDraft) {
        val userId = preferencesState.value.session?.userId.orEmpty()
        if (userId.isBlank()) return
        val normalized = draft.copy(updatedAtMillis = System.currentTimeMillis())
        _uiState.update { it.copy(savedFormDraft = if (normalized.hasContent) normalized else null) }
        viewModelScope.launch {
            if (normalized.hasContent) {
                draftRepo.saveComposerFormDraft(userId, normalized)
            } else {
                draftRepo.clearComposerFormDraft(userId)
            }
        }
    }

    fun dismissResumeReportPrompt() {
        // Hide ONLY the popup card; preserve savedFormDraft & encrypted disk backup.
        _uiState.update { it.copy(showResumeReportPrompt = false) }
    }

    fun resumeSavedReportDraft() {
        _uiState.update {
            it.copy(
                showResumeReportPrompt = false,
                showReportForm = true,
                editingReport = null,
                convertingDraft = null,
                convertingDraftMedia = emptyList()
            )
        }
    }

    fun clearInProgressReportDraft() {
        val userId = preferencesState.value.session?.userId.orEmpty()
        _uiState.update { it.copy(savedFormDraft = null, showResumeReportPrompt = false) }
        if (userId.isBlank()) return
        viewModelScope.launch {
            draftRepo.clearComposerFormDraft(userId)
            notifyMessage("Report entries cleared.")
        }
    }

    suspend fun stageReportMediaFile(file: File): String? {
        val userId = preferencesState.value.session?.userId.orEmpty()
        if (userId.isBlank()) return file.absolutePath
        return draftRepo.stageComposerMediaFile(userId, file) ?: file.absolutePath
    }

    suspend fun stageReportMediaUri(uri: Uri): Pair<String, String>? {
        val userId = preferencesState.value.session?.userId.orEmpty()
        if (userId.isBlank()) return null
        return draftRepo.stageComposerMediaUri(userId, uri)
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
        attachments: List<ReportEvidenceAttachment> = emptyList(),
        localFile: File? = null,
        pickedUri: Uri? = null,
        evidenceType: String = "none",
        evidenceName: String? = null,
        sourceDraft: OfflineDraft? = null,
        cleanDraftMediaOnComplete: Boolean = true,
        draftMediaItems: List<DraftMediaItem> = emptyList(),
        onComplete: () -> Unit = {}
    ) {
        val state = preferencesState.value
        val session = state.session ?: return
        val config = state.supabaseConfig

        if (existingReport != null && existingReport.isReviewedFinal && !session.role.isAdminOrAbove) {
            notifyMessage("Accepted or rejected exceptions are locked and cannot be edited.", isError = true)
            return
        }

        // Normalize into unified attachment list so create & edit share the same exact validation + upload pipeline
        val resolvedAttachments: List<ReportEvidenceAttachment> = when {
            attachments.isNotEmpty() -> attachments
            draftMediaItems.isNotEmpty() -> draftMediaItems.map {
                ReportEvidenceAttachment(
                    id = it.id,
                    pathOrUri = it.pathOrUri,
                    mediaType = it.mediaType.ifBlank { "image" },
                    displayName = File(it.pathOrUri).name.ifBlank { it.mediaType },
                    isExistingCloud = false
                )
            }
            localFile != null && localFile.exists() -> listOf(
                ReportEvidenceAttachment(
                    id = "att_local_${localFile.name}",
                    pathOrUri = localFile.absolutePath,
                    mediaType = evidenceType.ifBlank { "image" },
                    displayName = evidenceName ?: localFile.name,
                    isExistingCloud = false
                )
            )
            pickedUri != null -> listOf(
                ReportEvidenceAttachment(
                    id = "att_uri_${pickedUri.hashCode()}",
                    pathOrUri = pickedUri.toString(),
                    mediaType = evidenceType.ifBlank { "image" },
                    displayName = evidenceName ?: "Picked evidence",
                    isExistingCloud = false
                )
            )
            else -> emptyList()
        }

        val imageCount = resolvedAttachments.count { it.mediaType.equals("image", ignoreCase = true) }
        val videoCount = resolvedAttachments.count { it.mediaType.equals("video", ignoreCase = true) }
        if (imageCount > MediaUploadLimits.MAX_IMAGES_PER_REPORT) {
            notifyMessage("Maximum ${MediaUploadLimits.MAX_IMAGES_PER_REPORT} images allowed per exception.", isError = true)
            return
        }
        if (videoCount > MediaUploadLimits.MAX_VIDEOS_PER_REPORT) {
            notifyMessage("Maximum ${MediaUploadLimits.MAX_VIDEOS_PER_REPORT} videos allowed per exception.", isError = true)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, compressionProgress = null, compressionLabel = null) }
            val activeSession = ensureFreshSession(config, session)
            val reportNumber = existingReport?.reportNumber ?: remoteRepo.generateReportNumber()
            val settings = _uiState.value.systemSettings
            val activeWeek = AcademicWeekEngine.calculateAcademicWeek(settings).weekNumber

            val finalUrls = mutableListOf<String>()
            val finalTypes = mutableListOf<String>()

            for ((index, att) in resolvedAttachments.withIndex()) {
                if (att.isExistingCloud) {
                    if (att.pathOrUri.isNotBlank()) {
                        finalUrls.add(att.pathOrUri)
                        finalTypes.add(att.mediaType.ifBlank { "image" })
                    }
                    continue
                }

                val isImage = att.mediaType.equals("image", ignoreCase = true)
                val isVideo = att.mediaType.equals("video", ignoreCase = true)
                val stepLabel = "Optimizing ${att.mediaType} ${index + 1}/${resolvedAttachments.size}..."
                _uiState.update { it.copy(compressionLabel = stepLabel, compressionProgress = 0.05f) }

                val compressedRes = when {
                    isImage && att.pathOrUri.startsWith("/") ->
                        MediaCompressor.compressImageFile(getApplication(), File(att.pathOrUri)) { p ->
                            _uiState.update { it.copy(compressionProgress = p) }
                        }
                    isImage ->
                        MediaCompressor.compressImageUri(getApplication(), Uri.parse(att.pathOrUri)) { p ->
                            _uiState.update { it.copy(compressionProgress = p) }
                        }
                    isVideo && att.pathOrUri.startsWith("/") ->
                        MediaCompressor.compressVideoFile(getApplication(), File(att.pathOrUri)) { p ->
                            _uiState.update { it.copy(compressionProgress = p) }
                        }
                    isVideo ->
                        MediaCompressor.compressVideoUri(getApplication(), Uri.parse(att.pathOrUri)) { p ->
                            _uiState.update { it.copy(compressionProgress = p) }
                        }
                    else -> null
                }

                _uiState.update {
                    it.copy(
                        compressionLabel = "Uploading evidence ${index + 1}/${resolvedAttachments.size}...",
                        compressionProgress = 0.92f
                    )
                }

                val compressedFile = compressedRes?.getOrNull()?.file
                val compressedMime = compressedRes?.getOrNull()?.mimeType
                val defaultMime = when (att.mediaType.lowercase()) {
                    "image" -> "image/jpeg"
                    "video" -> "video/mp4"
                    "audio" -> "audio/mp4"
                    else -> "application/octet-stream"
                }

                val upResult = when {
                    compressedFile != null && compressedFile.exists() ->
                        remoteRepo.uploadEvidenceFile(
                            config = config,
                            token = activeSession.accessToken,
                            reportNumber = reportNumber,
                            file = compressedFile,
                            mimeType = compressedMime ?: defaultMime
                        )
                    att.pathOrUri.startsWith("/") -> {
                        val rawFile = File(att.pathOrUri)
                        remoteRepo.uploadEvidenceFile(config, activeSession.accessToken, reportNumber, rawFile, defaultMime)
                    }
                    else ->
                        remoteRepo.uploadEvidenceUri(
                            config = config,
                            token = activeSession.accessToken,
                            reportNumber = reportNumber,
                            uri = Uri.parse(att.pathOrUri),
                            fallbackMime = defaultMime
                        ).map { it.first }
                }

                if (upResult.isFailure) {
                    _uiState.update { it.copy(isLoading = false, compressionLabel = null, compressionProgress = null) }
                    notifyMessage(upResult.exceptionOrNull()?.message ?: "Evidence upload failed.", isError = true)
                    return@launch
                }
                finalUrls.add(upResult.getOrThrow())
                finalTypes.add(att.mediaType.ifBlank { "image" })
            }

            val finalEvidenceUrl = finalUrls.firstOrNull()
            val finalEvidenceType = finalTypes.firstOrNull() ?: "none"
            val finalEvidenceName = when {
                finalUrls.isEmpty() -> null
                finalUrls.size == 1 -> resolvedAttachments.firstOrNull()?.displayName ?: "Attached evidence"
                else -> "${finalUrls.size} attached items"
            }

            _uiState.update { it.copy(compressionLabel = "Saving compliance report...", compressionProgress = 0.98f) }

            val staffNameVal = when {
                staff != null -> staff.name
                division == ComplianceDivision.OPERATIONS && specificSubjectText.isNotBlank() -> specificSubjectText.trim()
                division == ComplianceDivision.DISCIPLINE && targetType == "division" -> "Student Decorum"
                division == ComplianceDivision.CDS -> "CDS Infrastructure"
                else -> "Staff / Unit"
            }
            val actorDisplayName = state.customDisplayName.ifBlank { activeSession.displayName }.ifBlank { activeSession.email }

            val payload = JSONObject().apply {
                put("report_number", reportNumber)
                put("reported_by", activeSession.userId)
                put("reporter_id", activeSession.userId)
                put("reporter_name", actorDisplayName)
                put("reporting_officer", actorDisplayName)
                put("reporter_role", activeSession.email)
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
                put("evidence_urls", JSONArray(finalUrls))
                put("evidence_types", JSONArray(finalTypes))
                if (existingReport == null) {
                    put("status", ReportResolutionStatus.OPEN.key)
                    put("review_status", "UNSEEN")
                    put("is_seen", false)
                    put("is_edited", false)
                } else {
                    put("is_edited", true)
                    put("edited_at", java.time.Instant.now().toString())
                }
            }

            val result = if (existingReport != null) {
                remoteRepo.updateReport(config, activeSession.accessToken, existingReport.id, payload)
            } else {
                remoteRepo.createReport(config, activeSession, payload)
            }

            result.onSuccess { saved ->
                if (sourceDraft != null) {
                    draftRepo.deleteDraft(activeSession.userId, sourceDraft.id, deleteMediaFile = cleanDraftMediaOnComplete)
                }
                if (existingReport == null) {
                    draftRepo.clearComposerFormDraft(activeSession.userId)
                    remoteRepo.insertNotificationBestEffort(
                        config = config,
                        token = activeSession.accessToken,
                        recipientUserId = null,
                        recipientRole = "admin",
                        type = "NEW_SUBMISSION",
                        title = "New Compliance Exception",
                        body = "$actorDisplayName has submitted a new exception (${saved.complianceCheck.ifBlank { saved.reportNumber }}). Please review.",
                        reportId = saved.id,
                        reportNumber = saved.reportNumber,
                        actorName = actorDisplayName
                    )
                } else {
                    knownReportSnapshots = (knownReportSnapshots.orEmpty()) + (saved.id to saved.status)
                }
                _uiState.update { current ->
                    val updatedList = if (existingReport != null) {
                        current.reports.map { if (it.id == saved.id) saved else it }
                    } else {
                        listOf(saved) + current.reports
                    }
                    current.copy(
                        isLoading = false,
                        compressionLabel = null,
                        compressionProgress = null,
                        reports = updatedList,
                        showReportForm = false,
                        editingReport = null,
                        convertingDraft = null,
                        convertingDraftMedia = emptyList(),
                        savedFormDraft = if (existingReport == null) null else current.savedFormDraft,
                        showResumeReportPrompt = if (existingReport == null) false else current.showResumeReportPrompt
                    )
                }
                notifyMessage(if (existingReport != null) "Exception report updated." else "Compliance exception submitted.")
                onComplete()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, compressionLabel = null, compressionProgress = null) }
                notifyMessage(err.message ?: "Failed to save report.", isError = true)
            }
        }
    }

    fun editSubmittedExceptionByUser(
        report: ComplianceReport,
        updatedCheck: String,
        updatedDetails: String,
        updatedDate: String
    ) {
        val state = preferencesState.value
        val session = state.session ?: return
        if (report.isReviewedFinal && !session.role.isAdminOrAbove) {
            notifyMessage("Accepted or rejected exceptions are locked and cannot be modified.", isError = true)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val activeSession = ensureFreshSession(state.supabaseConfig, session)
            val updates = JSONObject().apply {
                put("compliance_check", updatedCheck.trim())
                put("exception", updatedDetails.trim())
                put("exception_text", updatedDetails.trim())
                put("incident_date", updatedDate.trim())
                put("date", updatedDate.trim())
                put("is_edited", true)
                put("edited_at", java.time.Instant.now().toString())
            }
            remoteRepo.updateReport(state.supabaseConfig, activeSession.accessToken, report.id, updates)
                .onSuccess { updated ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            reports = current.reports.map { if (it.id == updated.id) updated else it },
                            selectedReportForDetail = if (current.selectedReportForDetail?.id == updated.id) updated else current.selectedReportForDetail
                        )
                    }
                    notifyMessage("Exception updated.")
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    notifyMessage(err.message ?: "Unable to update exception.", isError = true)
                }
        }
    }

    fun saveEvidenceFeedback(
        report: ComplianceReport,
        evidenceIndex: Int,
        verdict: EvidenceVerdict,
        note: String?
    ) {
        val state = preferencesState.value
        val session = state.session ?: return
        if (!session.role.isAdminOrAbove) {
            notifyMessage("Only Directorate reviewers can submit evidence feedback.", isError = true)
            return
        }
        viewModelScope.launch {
            val activeSession = ensureFreshSession(state.supabaseConfig, session)
            val tag = "[Evidence #${evidenceIndex + 1} • ${verdict.name}]"
            val trimmedNote = note?.trim().orEmpty()
            val entry = when {
                trimmedNote.isNotBlank() -> "$tag $trimmedNote"
                verdict == EvidenceVerdict.FLAG -> "$tag Flagged by Directorate Reviewer"
                else -> null
            }

            val updatedEvidenceNotes = if (entry != null) {
                report.evidenceNotes + entry
            } else {
                report.evidenceNotes
            }

            val nextStatus = when (verdict) {
                EvidenceVerdict.PASS -> ReportResolutionStatus.ACCEPTED
                EvidenceVerdict.FLAG -> ReportResolutionStatus.ACTION_REQUIRED
                EvidenceVerdict.NOTE -> ReportResolutionStatus.PENDING
            }

            val baseResolution = report.resolutionNotes.orEmpty().trim()
            val nextResolution = when {
                entry == null -> baseResolution
                baseResolution.isBlank() -> entry
                else -> "$baseResolution\n$entry"
            }

            val adminActor = activeSession.displayName.ifBlank { activeSession.email }
            val nowIso = java.time.Instant.now().toString()
            val updates = JSONObject().apply {
                put("status", nextStatus.key)
                put("review_status", if (nextStatus == ReportResolutionStatus.PENDING) "PENDING" else "SEEN")
                put("is_seen", true)
                put("seen_at", nowIso)
                put("seen_by", adminActor)
                if (nextResolution.isNotBlank()) put("resolution_notes", nextResolution)
                put("evidence_notes", JSONArray(updatedEvidenceNotes))
                if (nextStatus == ReportResolutionStatus.PENDING) {
                    put("pending_at", nowIso)
                    put("pending_by", adminActor)
                }
                if (nextStatus == ReportResolutionStatus.ACCEPTED) {
                    put("resolved_by", adminActor)
                    put("resolved_at", nowIso)
                }
            }

            remoteRepo.updateReport(state.supabaseConfig, activeSession.accessToken, report.id, updates)
                .onSuccess { updated ->
                    knownReportSnapshots = (knownReportSnapshots.orEmpty()) + (updated.id to updated.status)
                    if (report.reportedBy.isNotBlank() &&
                        (nextStatus == ReportResolutionStatus.ACTION_REQUIRED || nextStatus == ReportResolutionStatus.ACCEPTED)
                    ) {
                        remoteRepo.insertNotificationBestEffort(
                            config = state.supabaseConfig,
                            token = activeSession.accessToken,
                            recipientUserId = report.reportedBy,
                            recipientRole = "user",
                            type = nextStatus.key,
                            title = if (nextStatus == ReportResolutionStatus.ACTION_REQUIRED) "Action Required on Exception" else "Exception Accepted",
                            body = if (nextStatus == ReportResolutionStatus.ACTION_REQUIRED) {
                                "Your exception (${updated.complianceCheck.ifBlank { updated.reportNumber }}) requires your attention. Please check and resolve it."
                            } else {
                                "Your exception (${updated.complianceCheck.ifBlank { updated.reportNumber }}) has been accepted."
                            },
                            reportId = updated.id,
                            reportNumber = updated.reportNumber,
                            actorName = adminActor
                        )
                    }
                    _uiState.update { current ->
                        current.copy(
                            reports = current.reports.map { if (it.id == updated.id) updated else it },
                            selectedReportForDetail = if (current.selectedReportForDetail?.id == updated.id) updated else current.selectedReportForDetail
                        )
                    }
                    notifyMessage(
                        if (verdict == EvidenceVerdict.NOTE) "Evidence note saved & marked Pending."
                        else "Evidence feedback saved (${nextStatus.label})."
                    )
                }
                .onFailure { err ->
                    notifyMessage(err.message ?: "Failed to save evidence feedback.", isError = true)
                }
        }
    }

    fun toggleSeenStatus(report: ComplianceReport) {
        val state = preferencesState.value
        val session = state.session ?: return
        val targetSeen = !report.isSeen || report.isPending
        val clearPending = report.isPending
        viewModelScope.launch {
            remoteRepo.setReportSeenState(
                config = state.supabaseConfig,
                session = session,
                reportId = report.id,
                seen = targetSeen,
                clearPendingToInReview = clearPending
            )
                .onSuccess { updated ->
                    knownReportSnapshots = (knownReportSnapshots.orEmpty()) + (updated.id to updated.status)
                    _uiState.update { current ->
                        current.copy(
                            reports = current.reports.map { if (it.id == updated.id) updated else it },
                            selectedReportForDetail = if (current.selectedReportForDetail?.id == updated.id) updated else current.selectedReportForDetail
                        )
                    }
                    notifyMessage(if (updated.isSeen) "Marked as Reviewed (Seen)." else "Reverted to Open (Unseen).")
                }
                .onFailure { notifyMessage(it.message ?: "Unable to update review state.", isError = true) }
        }
    }

    fun updateResolution(report: ComplianceReport, status: ReportResolutionStatus, notes: String) {
        val state = preferencesState.value
        val session = state.session ?: return
        viewModelScope.launch {
            val activeSession = ensureFreshSession(state.supabaseConfig, session)
            val adminActor = activeSession.displayName.ifBlank { activeSession.email }
            remoteRepo.updateReportResolution(state.supabaseConfig, activeSession, report.id, status, notes)
                .onSuccess { updated ->
                    knownReportSnapshots = (knownReportSnapshots.orEmpty()) + (updated.id to updated.status)
                    if (report.reportedBy.isNotBlank() &&
                        (status == ReportResolutionStatus.ACTION_REQUIRED || status == ReportResolutionStatus.ACCEPTED)
                    ) {
                        remoteRepo.insertNotificationBestEffort(
                            config = state.supabaseConfig,
                            token = activeSession.accessToken,
                            recipientUserId = report.reportedBy,
                            recipientRole = "user",
                            type = status.key,
                            title = if (status == ReportResolutionStatus.ACTION_REQUIRED) "Action Required on Exception" else "Exception Accepted",
                            body = if (status == ReportResolutionStatus.ACTION_REQUIRED) {
                                "Your exception (${updated.complianceCheck.ifBlank { updated.reportNumber }}) requires your attention. Please check and resolve it."
                            } else {
                                "Your exception (${updated.complianceCheck.ifBlank { updated.reportNumber }}) has been accepted."
                            },
                            reportId = updated.id,
                            reportNumber = updated.reportNumber,
                            actorName = adminActor
                        )
                    }
                    _uiState.update { current ->
                        current.copy(
                            reports = current.reports.map { if (it.id == updated.id) updated else it },
                            selectedReportForDetail = updated
                        )
                    }
                    notifyMessage("Status updated to ${status.label}.")
                }
                .onFailure { notifyMessage(it.message ?: "Failed to update status.", isError = true) }
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
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val state = preferencesState.value
        val session = state.session
        viewModelScope.launch {
            if (session != null && state.supabaseConfig.isConfigured && session.role.isAdminOrAbove) {
                val activeSession = ensureFreshSession(state.supabaseConfig, session)
                val remoteRes = remoteRepo.createComplianceCheck(
                    state.supabaseConfig,
                    activeSession.accessToken,
                    division,
                    cleanName,
                    category
                )
                if (remoteRes.isSuccess) {
                    val created = remoteRes.getOrThrow()
                    _uiState.update { current ->
                        current.copy(serverCustomChecks = current.serverCustomChecks + created)
                    }
                    notifyMessage("Custom compliance check saved to registry.")
                    return@launch
                }
            }
            val check = ComplianceCheck(
                id = "USR-${UUID.randomUUID().toString().take(6).uppercase()}",
                name = cleanName,
                category = category.ifBlank { division.label },
                division = division,
                isCustom = true
            )
            prefs.addCustomCheck(check)
            notifyMessage("Added custom compliance check locally.")
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
            val activeSession = ensureFreshSession(state.supabaseConfig, session)
            remoteRepo.provisionDirectorateUser(
                state.supabaseConfig,
                activeSession.accessToken,
                email,
                password,
                displayName,
                staffNumber,
                role
            ).onSuccess { newUser ->
                _uiState.update { current ->
                    val merged = (listOf(newUser) + current.directorateUsers.filterNot {
                        it.userId.equals(newUser.userId, true) || it.email.equals(newUser.email, true)
                    })
                    current.copy(isLoading = false, directorateUsers = merged)
                }
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
