package com.example.ui.screens.account

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.local.AppPreferencesState
import com.example.data.model.AccentPalette
import com.example.data.model.LockPolicy
import com.example.data.model.MobileAppConfig
import com.example.data.model.StorageProviderConfig
import com.example.data.model.ThemePreference
import com.example.domain.BiometricHelper
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.AvatarFallbackMode
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassIconHalo
import com.example.ui.components.GlowingPrimaryButton
import com.example.ui.components.MicroLabel
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.SubScreenTopRow
import com.example.ui.components.VideoStyleChecklistRow
import com.example.ui.components.glassFieldColors
import com.example.ui.theme.ComplianceTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    activity: FragmentActivity,
    prefsState: AppPreferencesState,
    isLoading: Boolean = false,
    onUploadAvatar: (Uri) -> Unit,
    onRemoveAvatar: () -> Unit,
    onSaveDisplayName: (String) -> Unit,
    onResolveSignedUrl: (suspend (String) -> Result<String>)? = null,
    onGetMediaHeaders: ((String) -> Map<String, String>)? = null,
    onSetAutoFill: (Boolean) -> Unit,
    onSetTheme: (ThemePreference) -> Unit,
    onSetAccent: (AccentPalette) -> Unit,
    onUpdateLockPolicy: (LockPolicy) -> Unit,
    onLockAppNow: () -> Unit,
    onVerifyPassword: suspend (String) -> Boolean,
    onSetBiometricsEnabled: (Boolean) -> Unit,
    onNotify: (String, Boolean) -> Unit,
    onWillLaunchExternalPicker: () -> Unit = {},
    mobileAppConfig: MobileAppConfig? = null,
    storageProviderConfig: StorageProviderConfig? = null,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()
    val ext = ComplianceTheme.extended
    val session = prefsState.session
    val currentAvatarPath = session?.avatarUrl?.takeIf { it.isNotBlank() } ?: prefsState.avatarUri

    var displayName by remember(prefsState.customDisplayName, session?.displayName) {
        mutableStateOf(
            prefsState.customDisplayName.ifBlank { session?.displayName.orEmpty() }
        )
    }

    var showBiometricPasswordDialog by remember { mutableStateOf(false) }
    var bioPasswordInput by remember { mutableStateOf("") }
    var bioPasswordError by remember { mutableStateOf<String?>(null) }
    var isVerifyingBio by remember { mutableStateOf(false) }
    var needsBioEnrollment by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onUploadAvatar(uri)
        }
    }

    AtmosphericGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            SubScreenTopRow(
                title = "Settings",
                subtitle = "Profile, appearance & device security",
                onBack = onBack
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Video-replica Hero Halo Header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GlassIconHalo(icon = Icons.Default.Tune, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Optimize Your Compliance",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Configure your device look, automation & biometric lock",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 1. Frosted Profile Identity & Single Unified Avatar Uploader
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 16.dp) {
                        MicroLabel("Profile Identity")
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ProfileAvatar(
                                avatarUrl = currentAvatarPath,
                                displayName = displayName.ifBlank { session?.displayName.orEmpty() },
                                size = 64.dp,
                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                fallbackMode = AvatarFallbackMode.INITIALS,
                                onResolveSignedUrl = onResolveSignedUrl,
                                getAuthHeaders = onGetMediaHeaders,
                                testTag = "settings_profile_avatar"
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onWillLaunchExternalPicker()
                                        avatarPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = !isLoading,
                                    shape = CircleShape,
                                    modifier = Modifier.testTag("settings_change_avatar_button")
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (currentAvatarPath.isBlank()) "Choose Photo" else "Change Photo")
                                }
                                if (currentAvatarPath.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = onRemoveAvatar,
                                        enabled = !isLoading,
                                        shape = CircleShape,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = ext.rose
                                        ),
                                        border = BorderStroke(1.dp, ext.rose.copy(alpha = 0.45f)),
                                        modifier = Modifier.testTag("settings_remove_avatar_button")
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Remove Photo")
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            colors = glassFieldColors(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_display_name_input")
                        )
                        Spacer(Modifier.height(12.dp))
                        GlowingPrimaryButton(
                            onClick = { onSaveDisplayName(displayName) },
                            enabled = !isLoading && displayName.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_save_identity_button")
                        ) {
                            Text("Save Display Name", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                // 2. Appearance & Theme (Video Night/Light Mode + Solar + All 5 Accents)
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 16.dp) {
                        MicroLabel("Appearance")
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemePreference.entries.forEach { pref ->
                                val selected = prefsState.themePreference == pref
                                Surface(
                                    onClick = { onSetTheme(pref) },
                                    shape = CircleShape,
                                    color = if (selected) MaterialTheme.colorScheme.primary else ext.glassBottom,
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else ext.glassRimEnd
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = pref.label.replace(" Mode", "").replace(" Default", ""),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        MicroLabel("Accent Palette")
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AccentPalette.entries.forEach { palette ->
                                val swatchColor = when (palette) {
                                    AccentPalette.SOLAR -> Color(0xFFFFCA28)
                                    AccentPalette.SAPPHIRE -> Color(0xFF3B82F6)
                                    AccentPalette.ROSE -> Color(0xFFF43F5E)
                                    AccentPalette.AMETHYST -> Color(0xFF8B5CF6)
                                    AccentPalette.TITANIUM -> Color(0xFF94A3B8)
                                }
                                val selected = prefsState.accentPalette == palette
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onSetAccent(palette) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) swatchColor.copy(alpha = 0.22f) else ext.glassBottom,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) swatchColor else ext.glassRimEnd)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(swatchColor))
                                        Text(
                                            text = palette.label.substringBefore(" "),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Suggested Setup Checklist Rows (Direct Video Replica)
                item {
                    MicroLabel("Suggested Setup")
                }

                item {
                    VideoStyleChecklistRow(
                        icon = Icons.Default.AutoAwesome,
                        iconTint = ext.solarGold,
                        title = "Auto-Fill Templates",
                        subtitle = "Show Auto-Fill button in report form (tap button to fill manually)",
                        selected = prefsState.autoFillEnabled,
                        onClick = { onSetAutoFill(!prefsState.autoFillEnabled) }
                    ) {
                        Switch(
                            checked = prefsState.autoFillEnabled,
                            onCheckedChange = onSetAutoFill,
                            modifier = Modifier.testTag("settings_autofill_switch")
                        )
                    }
                }

                item {
                    VideoStyleChecklistRow(
                        icon = Icons.Default.Fingerprint,
                        iconTint = ext.emerald,
                        title = "Biometric Unlock",
                        subtitle = if (prefsState.biometricsEnabled) "Fingerprint / Face unlock active" else "Tap switch to enroll sensor",
                        selected = prefsState.biometricsEnabled
                    ) {
                        Switch(
                            checked = prefsState.biometricsEnabled,
                            onCheckedChange = { wantEnable ->
                                if (!wantEnable) {
                                    onSetBiometricsEnabled(false)
                                } else {
                                    val support = BiometricHelper.checkSupport(activity)
                                    if (!support.enrolled) {
                                        needsBioEnrollment = support.status == "NONE_ENROLLED"
                                        bioPasswordError = support.message
                                        showBiometricPasswordDialog = true
                                    } else {
                                        bioPasswordInput = ""
                                        bioPasswordError = null
                                        needsBioEnrollment = false
                                        showBiometricPasswordDialog = true
                                    }
                                }
                            },
                            modifier = Modifier.testTag("settings_biometrics_switch")
                        )
                    }
                }

                // 4. App Access Lock Policy Checklist Rows (NO "Update Password" block!)
                item {
                    MicroLabel("App Access Lock Policy")
                }

                LockPolicy.entries.forEach { policy ->
                    item {
                        val selected = prefsState.lockPolicy == policy
                        val icon = when (policy) {
                            LockPolicy.ALWAYS -> Icons.Default.Lock
                            LockPolicy.HOURS_24 -> Icons.Default.AccessTime
                            LockPolicy.NEVER -> Icons.Default.Bolt
                        }
                        val tint = when (policy) {
                            LockPolicy.ALWAYS -> ext.emerald
                            LockPolicy.HOURS_24 -> MaterialTheme.colorScheme.primary
                            LockPolicy.NEVER -> ext.indigo
                        }
                        VideoStyleChecklistRow(
                            icon = icon,
                            iconTint = tint,
                            title = policy.title,
                            subtitle = policy.subtitle,
                            selected = selected,
                            onClick = { onUpdateLockPolicy(policy) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) tint else Color.Transparent)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (selected) tint else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = if (ext.isDark) Color(0xFF0A0C10) else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Cloud Storage & Dynamic APK Update Info (Server-Managed)
                if (mobileAppConfig != null && mobileAppConfig.downloadUrl.isNotBlank()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 16.dp) {
                            MicroLabel("Mobile Release Channel")
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (mobileAppConfig.latestVersion.isNotBlank()) {
                                    "JC-Best Compliance APK v${mobileAppConfig.latestVersion}"
                                } else {
                                    "Official Directorate APK Download"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (mobileAppConfig.releaseNotes.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = mobileAppConfig.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        activity.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(mobileAppConfig.downloadUrl))
                                        )
                                    }.onFailure {
                                        onNotify("Unable to open release download link.", true)
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_download_apk_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Check / Download APK Release", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (storageProviderConfig != null) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, contentPadding = 14.dp) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = ext.emerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    MicroLabel("Unified Evidence Storage")
                                    Text(
                                        text = "${storageProviderConfig.bucketName} • Private B2 Proxy Sync",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Video-style full-width bottom glowing CTA
                item {
                    Spacer(Modifier.height(4.dp))
                    GlowingPrimaryButton(
                        onClick = onLockAppNow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_lock_app_now_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Lock App Now", fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showBiometricPasswordDialog) {
        AlertDialog(
            onDismissRequest = { if (!isVerifyingBio) showBiometricPasswordDialog = false },
            containerColor = if (ext.isDark) Color(0xFF151923) else Color(0xFFF6F8FC),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Enable Biometric Unlock", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Verify your password, then scan your fingerprint or face on the device sensor.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = bioPasswordInput,
                        onValueChange = {
                            bioPasswordInput = it
                            bioPasswordError = null
                        },
                        label = { Text("Current Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = glassFieldColors(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("settings_bio_password_input")
                    )
                    if (!bioPasswordError.isNullOrBlank()) {
                        Text(bioPasswordError.orEmpty(), color = ext.rose, style = MaterialTheme.typography.bodySmall)
                    }
                    if (needsBioEnrollment) {
                        OutlinedButton(onClick = { BiometricHelper.openEnrollmentSettings(activity) }) {
                            Text("Open Device Biometric Settings")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isVerifyingBio && bioPasswordInput.isNotBlank(),
                    onClick = {
                        scope.launch {
                            isVerifyingBio = true
                            val ok = onVerifyPassword(bioPasswordInput)
                            isVerifyingBio = false
                            if (!ok) {
                                bioPasswordError = "Incorrect account password."
                                return@launch
                            }
                            showBiometricPasswordDialog = false
                            BiometricHelper.authenticate(
                                activity = activity,
                                title = "Register Biometric Sensor",
                                subtitle = "Scan your fingerprint or face to enable biometric unlock",
                                negativeButtonText = "Cancel",
                                onSuccess = {
                                    onSetBiometricsEnabled(true)
                                },
                                onError = { msg, needsEnroll, _ ->
                                    if (needsEnroll) BiometricHelper.openEnrollmentSettings(activity)
                                    onNotify(msg, true)
                                }
                            )
                        }
                    },
                    modifier = Modifier.testTag("settings_bio_confirm_button")
                ) {
                    Text(if (isVerifyingBio) "Verifying..." else "Verify & Scan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBiometricPasswordDialog = false }) { Text("Cancel") }
            }
        )
    }
}
