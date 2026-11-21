package com.example.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ComplianceReport
import com.example.data.model.DirectorateUser
import com.example.data.model.StaffMember
import com.example.data.model.SystemSettings
import com.example.data.model.UserRole
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.CircularRingMetricTile
import com.example.ui.components.MicroLabel
import com.example.ui.components.SubScreenTopRow
import com.example.ui.components.VideoStyleChecklistRow
import com.example.ui.theme.ComplianceTheme

@Composable
fun AdminScreen(
    subScreen: String,
    userRole: UserRole,
    reports: List<ComplianceReport>,
    staffList: List<StaffMember>,
    directorateUsers: List<DirectorateUser>,
    settings: SystemSettings,
    isLoading: Boolean,
    onNavigateSubScreen: (String) -> Unit,
    onBackToAccount: () -> Unit,
    onSaveStaff: (StaffMember, Boolean, () -> Unit) -> Unit,
    onToggleStaffArchive: (StaffMember) -> Unit,
    onReassignStaffConflict: (StaffMember, String, StaffMember, Boolean, () -> Unit) -> Unit,
    onArchiveStaffConflict: (StaffMember, StaffMember, Boolean, () -> Unit) -> Unit,
    onSelectReport: (ComplianceReport) -> Unit,
    onUpdateUserRole: (DirectorateUser, UserRole) -> Unit,
    onToggleUserStatus: (DirectorateUser) -> Unit,
    onUpdateUserEmail: (DirectorateUser, String) -> Unit,
    onForceResetUserPassword: (DirectorateUser) -> Unit,
    onProvisionUser: (String, String, String, String, UserRole, () -> Unit) -> Unit,
    onSaveSystemSettings: (SystemSettings) -> Unit,
    onPurgeEvidenceBucket: () -> Unit,
    onResolveSignedUrl: (suspend (String) -> Result<String>)? = null,
    onGetMediaHeaders: ((String) -> Map<String, String>)? = null
) {
    when (subScreen) {
        "officer_stats" -> OfficerAnalyticsScreen(
            reports = reports,
            onBack = { onNavigateSubScreen("hub") }
        )

        "staff_register" -> StaffRegisterScreen(
            staffList = staffList,
            reports = reports,
            onSaveStaff = onSaveStaff,
            onToggleArchive = onToggleStaffArchive,
            onReassignConflict = onReassignStaffConflict,
            onArchiveConflict = onArchiveStaffConflict,
            onSelectReport = onSelectReport,
            onBack = { onNavigateSubScreen("hub") }
        )

        "system_users" -> SystemUsersScreen(
            users = directorateUsers,
            currentUserRole = userRole,
            onUpdateRole = onUpdateUserRole,
            onToggleStatus = onToggleUserStatus,
            onUpdateEmail = onUpdateUserEmail,
            onForceResetPassword = onForceResetUserPassword,
            onProvisionUser = onProvisionUser,
            onResolveSignedUrl = onResolveSignedUrl,
            onGetMediaHeaders = onGetMediaHeaders,
            onBack = { onNavigateSubScreen("hub") }
        )

        "system_config" -> SystemConfigScreen(
            settings = settings,
            userRole = userRole,
            isLoading = isLoading,
            onSaveSettings = onSaveSystemSettings,
            onPurgeEvidenceBucket = onPurgeEvidenceBucket,
            onBack = { onNavigateSubScreen("hub") }
        )

        else -> {
            BackHandler { onBackToAccount() }
            val ext = ComplianceTheme.extended
            val activeAcademicCount = staffList.count { it.isActive && it.department.contains("academ", true) }
            val totalStaffRatio = if (staffList.isNotEmpty()) activeAcademicCount.toFloat() / staffList.size else 0.7f

            AtmosphericGlassBackground {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    SubScreenTopRow(
                        title = "Admin Panel",
                        subtitle = "Directorate governance & registries",
                        onBack = onBackToAccount
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                CircularRingMetricTile(
                                    title = "Academic Staff",
                                    value = "$activeAcademicCount",
                                    subtitle = "Active faculty",
                                    progress = totalStaffRatio,
                                    icon = Icons.Default.Badge,
                                    ringColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                CircularRingMetricTile(
                                    title = "System Users",
                                    value = "${directorateUsers.size}",
                                    subtitle = "Registered officers",
                                    progress = 0.85f,
                                    icon = Icons.Default.ManageAccounts,
                                    ringColor = ext.emerald,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        item {
                            MicroLabel("Governance Modules", modifier = Modifier.padding(top = 4.dp))
                        }

                        item {
                            AdminHubRow(
                                icon = Icons.Default.Assessment,
                                title = "Officer Reporting Statistics",
                                subtitle = "Track Total, Seen, Unseen & Rejected submissions per officer",
                                tint = ext.indigo,
                                onClick = { onNavigateSubScreen("officer_stats") },
                                testTag = "admin_officer_stats_card"
                            )
                        }

                        item {
                            AdminHubRow(
                                icon = Icons.Default.Badge,
                                title = "Staff Register",
                                subtitle = "Manage Academic & Operational staff, codes, and dossiers",
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = { onNavigateSubScreen("staff_register") },
                                testTag = "admin_staff_register_card"
                            )
                        }

                        item {
                            AdminHubRow(
                                icon = Icons.Default.ManageAccounts,
                                title = "Directorate Users",
                                subtitle = "Roles, account activation, password resets & user provisioning",
                                tint = ext.emerald,
                                onClick = { onNavigateSubScreen("system_users") },
                                testTag = "admin_system_users_card"
                            )
                        }

                        item {
                            AdminHubRow(
                                icon = Icons.Default.CalendarMonth,
                                title = "Academic Configuration",
                                subtitle = "${settings.activeTerm} • ${settings.academicSession} • Week Anchor",
                                tint = ext.amber,
                                onClick = { onNavigateSubScreen("system_config") },
                                testTag = "admin_system_config_card"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminHubRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    val ext = ComplianceTheme.extended
    VideoStyleChecklistRow(
        icon = icon,
        iconTint = tint,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        modifier = Modifier.testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ext.glassTop)
                .border(1.dp, ext.glassRimEnd, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
