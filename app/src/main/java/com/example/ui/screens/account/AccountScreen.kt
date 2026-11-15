package com.example.ui.screens.account

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.SystemSettings
import com.example.data.model.UserSession
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.MicroLabel
import com.example.ui.components.StatusPill
import com.example.ui.components.VideoStyleChecklistRow
import com.example.ui.theme.ComplianceTheme

@Composable
fun AccountScreen(
    session: UserSession,
    displayName: String,
    avatarUri: String,
    settings: SystemSettings,
    onOpenSettings: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onOpenAdminPanel: () -> Unit,
    onLockAppNow: () -> Unit,
    onSignOut: () -> Unit
) {
    val ext = ComplianceTheme.extended
    val resolvedName = displayName.ifBlank { session.displayName }.ifBlank { session.email.substringBefore("@") }
    val glowColor = MaterialTheme.colorScheme.primary

    AtmosphericGlassBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                MicroLabel("Directorate Profile")
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Frosted Profile Hero Card with glowing avatar ring
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, contentPadding = 18.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .drawBehind {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            listOf(glowColor.copy(alpha = 0.36f), Color.Transparent),
                                            center = center,
                                            radius = size.minDimension / 2f
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarUri.isNotBlank()) {
                                AsyncImage(
                                    model = Uri.parse(avatarUri),
                                    contentDescription = "Profile Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, glowColor.copy(alpha = 0.7f), CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .border(1.5.dp, glowColor.copy(alpha = 0.6f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = resolvedName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = session.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(
                                    label = session.role.label.uppercase(),
                                    textColor = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                                StatusPill(
                                    label = settings.activeTerm.uppercase(),
                                    textColor = ext.emerald,
                                    containerColor = ext.emeraldContainer
                                )
                            }
                        }
                    }
                }
            }

            item {
                MicroLabel("Suggested Controls")
            }

            // Video-style smoked glass checklist navigation rows
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccountNavGlassRow(
                        icon = Icons.Default.Settings,
                        title = "Settings",
                        subtitle = "Profile identity, appearance, lock policy & biometrics",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = onOpenSettings,
                        testTag = "account_settings_button"
                    )

                    AccountNavGlassRow(
                        icon = Icons.Default.Key,
                        title = "Change Password",
                        subtitle = "Update password with current credentials or recover access",
                        tint = ext.indigo,
                        onClick = onOpenChangePassword,
                        testTag = "account_change_password_button"
                    )

                    if (session.role.isAdminOrAbove) {
                        AccountNavGlassRow(
                            icon = Icons.Default.AdminPanelSettings,
                            title = "Admin Panel",
                            subtitle = "Officer analytics, staff register, directorate users & academic calendar",
                            tint = ext.amber,
                            onClick = onOpenAdminPanel,
                            testTag = "account_admin_panel_button"
                        )
                    }

                    AccountNavGlassRow(
                        icon = Icons.Default.Lock,
                        title = "Lock App Now",
                        subtitle = "Immediately engage the security lock gate",
                        tint = ext.emerald,
                        onClick = onLockAppNow,
                        testTag = "account_lock_now_button"
                    )
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ext.roseContainer.copy(alpha = 0.5f),
                        contentColor = ext.rose
                    ),
                    border = BorderStroke(1.dp, ext.rose.copy(alpha = 0.45f)),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("account_sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun AccountNavGlassRow(
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
