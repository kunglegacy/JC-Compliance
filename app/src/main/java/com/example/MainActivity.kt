package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ComplianceDivision
import com.example.data.model.ReportScope
import com.example.ui.components.LivingAtmosphericBackground
import com.example.ui.components.NativeLoadingBar
import com.example.ui.components.complianceGlassCard
import com.example.ui.screens.account.AccountScreen
import com.example.ui.screens.account.ChangePasswordScreen
import com.example.ui.screens.account.SettingsScreen
import com.example.ui.screens.admin.AdminScreen
import com.example.ui.screens.analytics.AnalyticsScreen
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.PasswordResetScreen
import com.example.ui.screens.dashboard.OverviewScreen
import com.example.ui.screens.exceptions.ExceptionsScreen
import com.example.ui.screens.reports.ReportDetailSheet
import com.example.ui.screens.reports.ReportFormSheet
import com.example.ui.screens.reports.ReportsScreen
import com.example.ui.screens.security.AppLockScreen
import com.example.ui.screens.security.DeviceSetupScreen
import com.example.ui.theme.JCBestTheme
import com.example.ui.theme.LocalCompliancePalette
import com.example.ui.viewmodel.AuthScreenMode
import com.example.ui.viewmodel.ComplianceViewModel

class MainActivity : FragmentActivity() {

    private val viewModel: ComplianceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefsState by viewModel.preferencesState.collectAsStateWithLifecycle()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val drafts by viewModel.drafts.collectAsStateWithLifecycle()

            JCBestTheme(
                themePreference = prefsState.themePreference,
                accentPalette = prefsState.accentPalette
            ) {
                JCBestComplianceRoot(
                    activity = this,
                    viewModel = viewModel,
                    prefsState = prefsState,
                    uiState = uiState,
                    drafts = drafts
                )
            }
        }
    }
}

private data class FooterNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val FooterItems = listOf(
    FooterNavItem("dashboard", "Overview", Icons.Rounded.Dashboard),
    FooterNavItem("my-exceptions", "Exception", Icons.Rounded.ReportProblem),
    FooterNavItem("reports", "Reports", Icons.Rounded.Assignment),
    FooterNavItem("analytics", "Analysis", Icons.Rounded.Analytics),
    FooterNavItem("account", "Account", Icons.Rounded.Person)
)

@Composable
private fun JCBestComplianceRoot(
    activity: FragmentActivity,
    viewModel: ComplianceViewModel,
    prefsState: com.example.data.local.AppPreferencesState,
    uiState: com.example.ui.viewmodel.ComplianceUiState,
    drafts: List<com.example.data.model.OfflineDraft>
) {
    val context = LocalContext.current
    val p = LocalCompliancePalette.current
    val snackbarHostState = remember { SnackbarHostState() }
    var resetPrefillEmail by remember { mutableStateOf("") }
    var initialReportsScope by remember { mutableStateOf(ReportScope.ALL) }

    // System Connectivity Observer
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeCaps = cm.getNetworkCapabilities(cm.activeNetwork)
        viewModel.setOnlineStatus(
            activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        )
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                viewModel.setOnlineStatus(true)
            }

            override fun onLost(network: Network) {
                viewModel.setOnlineStatus(false)
            }
        }
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(req, callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }

    LaunchedEffect(uiState.feedbackMessage) {
        val msg = uiState.feedbackMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    // Ultra-Fast, Zero-Jank Branded Splash Loader
    if (uiState.isBootstrapping) {
        ComplianceSplashLoader()
        return
    }

    val session = prefsState.session

    // Unauthenticated Auth Flow
    if (session == null) {
        Scaffold(
            containerColor = p.bgBase,
            contentColor = p.textPrimary,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    GlassSnackbar(
                        data = data,
                        isError = uiState.isErrorFeedback
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (uiState.authMode) {
                    AuthScreenMode.LOGIN -> LoginScreen(
                        isLoading = uiState.isLoading,
                        authError = uiState.authError,
                        onClearError = viewModel::clearAuthError,
                        onSignIn = viewModel::signIn,
                        onForgotPassword = { typedEmail ->
                            resetPrefillEmail = typedEmail
                            viewModel.setAuthMode(AuthScreenMode.PASSWORD_RESET)
                        },
                        supabaseConfig = prefsState.supabaseConfig,
                        rememberedEmail = prefsState.rememberedEmail,
                        rememberedDisplayName = prefsState.rememberedDisplayName,
                        rememberedAvatarUri = prefsState.rememberedAvatarUri,
                        onSaveSupabaseConfig = viewModel::saveSupabaseConfig
                    )

                    AuthScreenMode.PASSWORD_RESET -> PasswordResetScreen(
                        initialEmail = resetPrefillEmail,
                        isLoading = uiState.isLoading,
                        onSendResetEmail = viewModel::sendPasswordResetEmail,
                        onBack = { viewModel.setAuthMode(AuthScreenMode.LOGIN) }
                    )
                }
            }
        }
        return
    }

    // First-time Device Security Setup
    if (!prefsState.deviceSetupCompleted) {
        Scaffold(
            containerColor = p.bgBase,
            contentColor = p.textPrimary,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    GlassSnackbar(
                        data = data,
                        isError = uiState.isErrorFeedback
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                DeviceSetupScreen(
                    activity = activity,
                    onVerifyPassword = viewModel::verifyPasswordForSecurityAction,
                    onCompleteSetup = viewModel::completeDeviceSetup
                )
            }
        }
        return
    }

    // App Lock Security Gate
    if (viewModel.shouldShowAppLock(prefsState)) {
        Scaffold(
            containerColor = p.bgBase,
            contentColor = p.textPrimary,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    GlassSnackbar(
                        data = data,
                        isError = uiState.isErrorFeedback
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                AppLockScreen(
                    activity = activity,
                    displayName = prefsState.customDisplayName.ifBlank { session.displayName },
                    email = session.email,
                    biometricsEnabled = prefsState.biometricsEnabled,
                    onVerifyPassword = viewModel::verifyPasswordForSecurityAction,
                    onUnlocked = viewModel::unlockApp,
                    onSignOut = viewModel::signOut
                )
            }
        }
        return
    }

    // Back handler returns from secondary tabs to Overview
    BackHandler(enabled = uiState.activeTab != "dashboard" && uiState.accountSubScreen == null) {
        viewModel.setActiveTab("dashboard")
    }

    val showDock = uiState.accountSubScreen == null
    val visibleFooterItems = remember(session.role) {
        if (session.role.isAdminOrAbove) FooterItems
        else FooterItems.filterNot { it.route == "reports" }
    }
    val scopedReports = remember(uiState.reports, session.role, session.userId) {
        if (session.role.isAdminOrAbove) uiState.reports
        else uiState.reports.filter { it.reportedBy.equals(session.userId, ignoreCase = true) }
    }

    LaunchedEffect(session.role, uiState.activeTab, uiState.accountSubScreen) {
        if (!session.role.isAdminOrAbove) {
            if (uiState.accountSubScreen == "admin") {
                viewModel.setAccountSubScreen(null)
            }
            if (uiState.activeTab == "reports") {
                viewModel.setActiveTab("dashboard")
            }
        }
    }

    // Adaptive Edge-to-Edge Shell: Frosted Glass Bottom Dock on phone, Glass NavigationRail on tablet
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 640.dp

        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (useRail && showDock) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .border(0.8.dp, p.borderBottom),
                        containerColor = if (p.isDark) Color(0xFF0E1118) else Color(0xFFF1F5FB),
                        contentColor = p.textPrimary
                    ) {
                        Spacer(Modifier.height(12.dp))
                        visibleFooterItems.forEach { item ->
                            NavigationRailItem(
                                selected = uiState.activeTab == item.route,
                                onClick = {
                                    initialReportsScope = ReportScope.ALL
                                    viewModel.setActiveTab(item.route)
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, maxLines = 1, fontSize = 11.sp) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = p.tangerine,
                                    selectedTextColor = p.tangerine,
                                    indicatorColor = p.tangerine.copy(alpha = 0.16f),
                                    unselectedIconColor = p.textSecondary,
                                    unselectedTextColor = p.textSecondary
                                ),
                                modifier = Modifier.testTag("rail_nav_${item.route}")
                            )
                        }
                    }
                }

                val allCustomChecks = remember(uiState.serverCustomChecks, prefsState.customChecks) {
                    uiState.serverCustomChecks + prefsState.customChecks
                }

                // Active Route Content
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when {
                        uiState.accountSubScreen == "settings" -> SettingsScreen(
                            activity = activity,
                            prefsState = prefsState,
                            onSaveIdentity = viewModel::saveProfileIdentity,
                            onSetAutoFill = viewModel::setAutoFillEnabled,
                            onSetTheme = viewModel::setThemePreference,
                            onSetAccent = viewModel::setAccentPalette,
                            onUpdateLockPolicy = viewModel::updateLockPolicy,
                            onLockAppNow = viewModel::lockAppNow,
                            onVerifyPassword = viewModel::verifyPasswordForSecurityAction,
                            onSetBiometricsEnabled = viewModel::setBiometricsEnabled,
                            onSaveSupabaseConfig = viewModel::saveSupabaseConfig,
                            onNotify = viewModel::notifyMessage,
                            onBack = { viewModel.setAccountSubScreen(null) }
                        )

                        uiState.accountSubScreen == "change_password" -> ChangePasswordScreen(
                            isLoading = uiState.isLoading,
                            onChangePassword = viewModel::changePassword,
                            onForgotPassword = { viewModel.setAccountSubScreen("password_reset") },
                            onBack = { viewModel.setAccountSubScreen(null) }
                        )

                        uiState.accountSubScreen == "password_reset" -> PasswordResetScreen(
                            initialEmail = session.email,
                            isLoading = uiState.isLoading,
                            onSendResetEmail = viewModel::sendPasswordResetEmail,
                            onBack = { viewModel.setAccountSubScreen("change_password") }
                        )

                        uiState.accountSubScreen == "admin" && session.role.isAdminOrAbove -> AdminScreen(
                            subScreen = uiState.adminSubScreen,
                            userRole = session.role,
                            reports = uiState.reports,
                            staffList = uiState.staffList,
                            directorateUsers = uiState.directorateUsers,
                            settings = uiState.systemSettings,
                            isLoading = uiState.isLoading,
                            onNavigateSubScreen = viewModel::setAdminSubScreen,
                            onBackToAccount = { viewModel.setAccountSubScreen(null) },
                            onSaveStaff = viewModel::saveStaffMember,
                            onToggleStaffArchive = viewModel::toggleStaffArchive,
                            onReassignStaffConflict = viewModel::reassignConflictingStaffCodeAndSave,
                            onArchiveStaffConflict = viewModel::archiveConflictingStaffAndSave,
                            onSelectReport = viewModel::openReportDetail,
                            onUpdateUserRole = viewModel::updateUserRole,
                            onToggleUserStatus = viewModel::toggleUserStatus,
                            onUpdateUserEmail = viewModel::updateUserEmail,
                            onForceResetUserPassword = viewModel::forceResetUserPassword,
                            onProvisionUser = viewModel::provisionUser,
                            onSaveSystemSettings = viewModel::saveSystemSettings,
                            onPurgeEvidenceBucket = viewModel::purgeEvidenceBucket
                        )

                        uiState.activeTab == "dashboard" -> OverviewScreen(
                            displayName = prefsState.customDisplayName.ifBlank { session.displayName },
                            reports = scopedReports,
                            staffList = uiState.staffList,
                            settings = uiState.systemSettings,
                            selectedTerm = uiState.selectedTerm,
                            isRefreshing = uiState.isRefreshing,
                            onSelectTerm = viewModel::setSelectedTerm,
                            onRefresh = { viewModel.refreshAllData(silent = false) },
                            onNewReport = { viewModel.openNewReportForm() },
                            onSelectReport = viewModel::openReportDetail,
                            onNavigateToDivision = { div ->
                                if (session.role.isAdminOrAbove) {
                                    initialReportsScope = when (div) {
                                        ComplianceDivision.ACADEMIC -> ReportScope.ACADEMIC
                                        ComplianceDivision.OPERATIONS -> ReportScope.MAINTENANCE
                                        ComplianceDivision.CDS -> ReportScope.CDS
                                        ComplianceDivision.DISCIPLINE -> ReportScope.DISCIPLINE
                                    }
                                    viewModel.setActiveTab("reports")
                                } else {
                                    viewModel.setActiveTab("my-exceptions")
                                }
                            },
                            userRole = session.role,
                            currentUserId = session.userId,
                            onResolveSignedUrl = viewModel::resolveSignedEvidenceUrl
                        )

                        uiState.activeTab == "my-exceptions" -> ExceptionsScreen(
                            currentUserId = session.userId,
                            reports = scopedReports,
                            drafts = drafts,
                            onNewException = { viewModel.openNewReportForm() },
                            onConvertDraft = { draft -> viewModel.openNewReportForm(draft) },
                            onDeleteDraft = viewModel::deleteDraft,
                            onSaveCameraPhotoDraft = { file ->
                                viewModel.saveOfflineDraftFile(
                                    title = "Captured Photo Evidence",
                                    notes = "",
                                    file = file,
                                    mediaType = "image",
                                    mediaName = file.name
                                )
                            },
                            onSaveCameraVideoDraft = { file ->
                                viewModel.saveOfflineDraftFile(
                                    title = "Captured Video Evidence",
                                    notes = "",
                                    file = file,
                                    mediaType = "video",
                                    mediaName = file.name
                                )
                            },
                            onSavePickedMediaDraft = { uri, type ->
                                viewModel.saveOfflineDraftUri(
                                    title = "Picked ${type.replaceFirstChar { it.uppercase() }} Evidence",
                                    notes = "",
                                    uri = uri,
                                    mediaType = type
                                )
                            },
                            onSelectReport = viewModel::openReportDetail
                        )

                        uiState.activeTab == "reports" && session.role.isAdminOrAbove -> ReportsScreen(
                            reports = uiState.reports,
                            staffList = uiState.staffList,
                            selectedTerm = uiState.selectedTerm,
                            initialScope = initialReportsScope,
                            onSelectTerm = viewModel::setSelectedTerm,
                            onExportPdf = { scope, term ->
                                viewModel.exportOfficialPdf(scope, term)
                            },
                            onSelectReport = viewModel::openReportDetail
                        )

                        uiState.activeTab == "analytics" -> AnalyticsScreen(
                            reports = scopedReports,
                            staffList = uiState.staffList,
                            selectedTerm = uiState.selectedTerm,
                            onSelectTerm = viewModel::setSelectedTerm,
                            onExportPdf = { scope, term, start, end ->
                                viewModel.exportOfficialPdf(scope, term, start, end)
                            },
                            userRole = session.role,
                            currentUserId = session.userId
                        )

                        uiState.activeTab == "account" -> AccountScreen(
                            session = session,
                            displayName = prefsState.customDisplayName.ifBlank { session.displayName },
                            avatarUri = prefsState.avatarUri,
                            settings = uiState.systemSettings,
                            onOpenSettings = { viewModel.setAccountSubScreen("settings") },
                            onOpenChangePassword = { viewModel.setAccountSubScreen("change_password") },
                            onOpenAdminPanel = { viewModel.setAccountSubScreen("admin") },
                            onLockAppNow = viewModel::lockAppNow,
                            onSignOut = viewModel::signOut
                        )

                        else -> OverviewScreen(
                            displayName = prefsState.customDisplayName.ifBlank { session.displayName },
                            reports = scopedReports,
                            staffList = uiState.staffList,
                            settings = uiState.systemSettings,
                            selectedTerm = uiState.selectedTerm,
                            isRefreshing = uiState.isRefreshing,
                            onSelectTerm = viewModel::setSelectedTerm,
                            onRefresh = { viewModel.refreshAllData(silent = false) },
                            onNewReport = { viewModel.openNewReportForm() },
                            onSelectReport = viewModel::openReportDetail,
                            onNavigateToDivision = { div ->
                                if (session.role.isAdminOrAbove) {
                                    initialReportsScope = when (div) {
                                        ComplianceDivision.ACADEMIC -> ReportScope.ACADEMIC
                                        ComplianceDivision.OPERATIONS -> ReportScope.MAINTENANCE
                                        ComplianceDivision.CDS -> ReportScope.CDS
                                        ComplianceDivision.DISCIPLINE -> ReportScope.DISCIPLINE
                                    }
                                    viewModel.setActiveTab("reports")
                                } else {
                                    viewModel.setActiveTab("my-exceptions")
                                }
                            },
                            userRole = session.role,
                            currentUserId = session.userId,
                            onResolveSignedUrl = viewModel::resolveSignedEvidenceUrl
                        )
                    }

                    // Top Loading / Offline Telemetry Overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        NativeLoadingBar(visible = uiState.isLoading || uiState.isRefreshing)

                        if (!uiState.isOnline) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(p.tangerine.copy(alpha = if (p.isDark) 0.22f else 0.14f))
                                    .border(1.dp, p.tangerine.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudOff,
                                    contentDescription = null,
                                    tint = p.tangerine,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Offline Mode — Capture drafts locally and sync later.",
                                    color = p.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Frosted Non-Transparent Glass Bottom Dock (with soft dissolve scrim beneath)
                    if (!useRail && showDock) {
                        FloatingGlassBottomDock(
                            items = visibleFooterItems,
                            activeTab = uiState.activeTab,
                            onSelectTab = { route ->
                                initialReportsScope = ReportScope.ALL
                                viewModel.setActiveTab(route)
                            },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }

                    // Snackbar Host floating cleanly above dock
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(
                                horizontal = 16.dp,
                                vertical = if (!useRail && showDock) 94.dp else 16.dp
                            )
                    ) { data ->
                        GlassSnackbar(
                            data = data,
                            isError = uiState.isErrorFeedback
                        )
                    }

                    // Global Report Detail Sheet
                    uiState.selectedReportForDetail?.let { detailReport ->
                        ReportDetailSheet(
                            report = detailReport,
                            userRole = session.role,
                            customChecks = allCustomChecks,
                            onDismiss = { viewModel.openReportDetail(null) },
                            onToggleSeen = viewModel::toggleSeenStatus,
                            onUpdateResolution = viewModel::updateResolution,
                            onRemapCheck = viewModel::remapReportCheck,
                            onEdit = viewModel::openEditReportForm,
                            onDelete = viewModel::deleteReport,
                            onResolveSignedUrl = viewModel::resolveSignedEvidenceUrl
                        )
                    }

                    // Global New/Edit Report Sheet
                    if (uiState.showReportForm) {
                        ReportFormSheet(
                            editingReport = uiState.editingReport,
                            sourceDraft = uiState.convertingDraft,
                            staffList = uiState.staffList,
                            classes = uiState.classes,
                            subjects = uiState.subjects,
                            customChecks = allCustomChecks,
                            autoFillEnabled = prefsState.autoFillEnabled,
                            isLoading = uiState.isLoading,
                            onAddCustomCheck = viewModel::addCustomCheck,
                            onDismiss = viewModel::closeReportForm,
                            onSubmit = { division, targetType, staff, classroom, subject, opScope, specificSubjectText, assignedLocation, check, incDate, exText, effText, localFile, pickedUri, evType, evName, cleanDraft ->
                                viewModel.submitReport(
                                    existingReport = uiState.editingReport,
                                    division = division,
                                    targetType = targetType,
                                    staff = staff,
                                    classroom = classroom,
                                    subject = subject,
                                    operationScope = opScope,
                                    specificSubjectText = specificSubjectText,
                                    assignedLocation = assignedLocation,
                                    check = check,
                                    incidentDate = incDate,
                                    exceptionText = exText,
                                    effectText = effText,
                                    localFile = localFile,
                                    pickedUri = pickedUri,
                                    evidenceType = evType,
                                    evidenceName = evName,
                                    sourceDraft = uiState.convertingDraft,
                                    cleanDraftMediaOnComplete = cleanDraft,
                                    onComplete = {}
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComplianceSplashLoader() {
    val p = LocalCompliancePalette.current
    val infinite = rememberInfiniteTransition(label = "splash_loader")

    val arcRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing)
        ),
        label = "arc_rotation"
    )
    val emblemPulse by infinite.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emblem_pulse"
    )
    val shimmerSlide by infinite.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
        ),
        label = "shimmer_slide"
    )

    LivingAtmosphericBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                // Sculpted Dual-Ring Halo + Frosted Glass Core
                Box(
                    modifier = Modifier.size(112.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokePx = 3.5.dp.toPx()
                        // Subtle static glass ring
                        drawCircle(
                            color = p.trackFill,
                            style = Stroke(width = strokePx)
                        )
                        // Fast rotating Electric Tangerine -> Emerald energy arc
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    p.tangerine,
                                    p.emerald,
                                    Color.Transparent
                                )
                            ),
                            startAngle = arcRotation,
                            sweepAngle = 220f,
                            useCenter = false,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .graphicsLayer {
                                scaleX = emblemPulse
                                scaleY = emblemPulse
                            }
                            .complianceGlassCard(50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = "JC-Best Compliance",
                            tint = p.tangerine,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "JC-BEST",
                        color = p.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp
                    )
                    Text(
                        text = "COMPLIANCE & EVIDENCE OS",
                        color = p.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp
                    )
                }

                // Sleek High-Speed Tangerine Shimmer Capsule
                Box(
                    modifier = Modifier
                        .width(176.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(p.trackFill)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val barWidth = size.width * 0.44f
                        val startX = size.width * shimmerSlide
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, p.tangerine, p.emerald, Color.Transparent),
                                startX = startX,
                                endX = startX + barWidth
                            ),
                            start = Offset(startX, size.height / 2f),
                            end = Offset(startX + barWidth, size.height / 2f),
                            strokeWidth = size.height,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            Text(
                text = "Securing session & loading workspace...",
                color = p.textSecondary.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            )
        }
    }
}

@Composable
private fun GlassSnackbar(
    data: androidx.compose.material3.SnackbarData,
    isError: Boolean
) {
    val p = LocalCompliancePalette.current
    Snackbar(
        snackbarData = data,
        shape = RoundedCornerShape(18.dp),
        containerColor = if (isError) p.rose else (if (p.isDark) Color(0xFF1C212C) else Color(0xFF0F172A)),
        contentColor = Color.White
    )
}

@Composable
private fun FloatingGlassBottomDock(
    items: List<FooterNavItem>,
    activeTab: String,
    onSelectTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    val dockFillTop = if (p.isDark) Color(0xFF1A1F2B).copy(alpha = 0.94f) else Color(0xFFFFFFFF).copy(alpha = 0.94f)
    val dockFillBottom = if (p.isDark) Color(0xFF10131C).copy(alpha = 0.96f) else Color(0xFFF1F5FA).copy(alpha = 0.96f)

    // Soft atmospheric fade scrim behind the dock so scrolling text never clashes
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        p.bgBase.copy(alpha = 0.72f),
                        p.bgEnd.copy(alpha = 0.96f)
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = if (p.isDark) 22.dp else 16.dp,
                    shape = RoundedCornerShape(30.dp),
                    ambientColor = p.shadow,
                    spotColor = p.shadow
                )
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(dockFillTop, dockFillBottom)))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(listOf(p.borderTop, p.borderBottom)),
                    shape = RoundedCornerShape(30.dp)
                )
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = activeTab == item.route
                val tint by animateColorAsState(
                    targetValue = if (selected) p.tangerine else p.textSecondary,
                    animationSpec = tween(180),
                    label = "nav_tint"
                )
                val pillWidth by animateDpAsState(
                    targetValue = if (selected) 48.dp else 36.dp,
                    animationSpec = spring(),
                    label = "nav_pill_w"
                )
                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true),
                            onClick = { onSelectTab(item.route) }
                        )
                        .padding(vertical = 4.dp)
                        .testTag("footer_nav_${item.route}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(pillWidth)
                            .height(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) p.tangerine.copy(alpha = if (p.isDark) 0.22f else 0.14f)
                                else Color.Transparent
                            )
                            .border(
                                width = if (selected) 0.9.dp else 0.dp,
                                color = if (selected) p.tangerine.copy(alpha = 0.50f) else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = tint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = item.label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}