package com.example.ui.screens.security

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.model.LockPolicy
import com.example.domain.BiometricHelper
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassIconHalo
import com.example.ui.components.GlowingPrimaryButton
import com.example.ui.components.MicroLabel
import com.example.ui.components.SchoolCrestBadge
import com.example.ui.components.VideoStyleChecklistRow
import com.example.ui.components.glassFieldColors
import com.example.ui.theme.ComplianceTheme
import kotlinx.coroutines.launch

@Composable
fun DeviceSetupScreen(
    activity: FragmentActivity,
    onVerifyPassword: suspend (String) -> Boolean,
    onCompleteSetup: (LockPolicy, Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val ext = ComplianceTheme.extended

    var step by remember { mutableIntStateOf(1) }
    var selectedPolicy by remember { mutableStateOf(LockPolicy.HOURS_24) }
    var enableBiometrics by remember { mutableStateOf(true) }
    var verifyPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var needsBiometricEnrollment by remember { mutableStateOf(false) }

    var biometricSupport by remember { mutableStateOf(BiometricHelper.checkSupport(activity)) }

    LaunchedEffect(step) {
        if (step == 2) {
            biometricSupport = BiometricHelper.checkSupport(activity)
            if (!biometricSupport.available) {
                enableBiometrics = false
            }
        }
    }

    val deviceModelName = remember {
        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim()
    }

    AtmosphericGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header bar (Strictly NO "ENCRYPTED" badge/word below "Device Setup")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SchoolCrestBadge(size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Device Setup",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (step == 1) 26.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (step == 1) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (step == 2) 26.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (step == 2) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Video-style centered halo hero emblem
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GlassIconHalo(
                            icon = if (step == 1) Icons.Default.Security else Icons.Default.Fingerprint,
                            tint = if (step == 1) MaterialTheme.colorScheme.primary else ext.emerald,
                            size = 68.dp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (step == 1) "Configure Device Security" else "Biometric Sensor Link",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (step == 1) "Select how this terminal locks between sessions" else "Verify your identity with fingerprint or face scan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Device Binding Summary Card
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, contentPadding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = deviceModelName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Hardware bound session",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (step == 1) {
                        MicroLabel("Suggested Security Policies")

                        LockPolicy.entries.forEach { policy ->
                            val selected = selectedPolicy == policy
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
                                onClick = { selectedPolicy = policy },
                                modifier = Modifier.testTag("setup_policy_${policy.key}")
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
                    } else {
                        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, contentPadding = 18.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(ext.emerald.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Fingerprint,
                                        contentDescription = null,
                                        tint = ext.emerald
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Enable Fingerprint / Face Unlock",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = biometricSupport.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = enableBiometrics,
                                    onCheckedChange = {
                                        enableBiometrics = it
                                        errorMsg = null
                                    },
                                    enabled = biometricSupport.available,
                                    modifier = Modifier.testTag("setup_biometrics_switch")
                                )
                            }

                            if (enableBiometrics) {
                                Spacer(Modifier.height(16.dp))
                                MicroLabel("Verify Password")
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = verifyPassword,
                                    onValueChange = {
                                        verifyPassword = it
                                        errorMsg = null
                                    },
                                    placeholder = { Text("Enter password") },
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle password"
                                            )
                                        }
                                    },
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    colors = glassFieldColors(),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("setup_verify_password_input")
                                )
                            }
                        }

                        if (!errorMsg.isNullOrBlank()) {
                            Surface(
                                color = ext.roseContainer,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, ext.rose.copy(alpha = 0.40f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ext.rose)
                                        Text(errorMsg.orEmpty(), color = ext.rose, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (needsBiometricEnrollment) {
                                        OutlinedButton(
                                            onClick = { BiometricHelper.openEnrollmentSettings(activity) },
                                            shape = CircleShape,
                                            modifier = Modifier.testTag("open_biometric_enroll_settings")
                                        ) {
                                            Text("Open Device Biometric Settings")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            // Frosted floating glass bottom CTA bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, Brush.linearGradient(listOf(ext.glassRimStart, ext.glassRimEnd)))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(ext.glassTop, ext.glassBottom)))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (step == 1) {
                            GlowingPrimaryButton(
                                onClick = { step = 2 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("device_setup_continue_button")
                            ) {
                                Text("Continue", fontWeight = FontWeight.ExtraBold)
                            }
                            TextButton(
                                onClick = { onCompleteSetup(selectedPolicy, false) },
                                modifier = Modifier.testTag("device_setup_skip_button")
                            ) {
                                Text("Skip for now")
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        step = 1
                                        errorMsg = null
                                    },
                                    enabled = !isVerifying,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .height(54.dp)
                                        .testTag("device_setup_back_button")
                                ) {
                                    Text("Back", fontWeight = FontWeight.SemiBold)
                                }

                                GlowingPrimaryButton(
                                    onClick = {
                                        errorMsg = null
                                        needsBiometricEnrollment = false
                                        if (!enableBiometrics) {
                                            onCompleteSetup(selectedPolicy, false)
                                            return@GlowingPrimaryButton
                                        }
                                        if (verifyPassword.isBlank()) {
                                            errorMsg = "Enter your account password before scanning your fingerprint."
                                            return@GlowingPrimaryButton
                                        }
                                        scope.launch {
                                            isVerifying = true
                                            val ok = onVerifyPassword(verifyPassword)
                                            isVerifying = false
                                            if (!ok) {
                                                errorMsg = "Account password verification failed."
                                                return@launch
                                            }
                                            // Real native Android BiometricPrompt hardware sensor verification
                                            BiometricHelper.authenticate(
                                                activity = activity,
                                                title = "Enable Biometric Unlock",
                                                subtitle = "Place your finger on the sensor or verify face unlock to complete setup",
                                                negativeButtonText = "Cancel",
                                                onSuccess = {
                                                    onCompleteSetup(selectedPolicy, true)
                                                },
                                                onError = { msg, needsEnroll, isCancelled ->
                                                    needsBiometricEnrollment = needsEnroll
                                                    errorMsg = if (isCancelled) {
                                                        "Biometric verification cancelled. Scan your fingerprint to enable biometrics, or turn the switch off."
                                                    } else {
                                                        msg
                                                    }
                                                }
                                            )
                                        }
                                    },
                                    enabled = !isVerifying,
                                    containerColor = ext.emerald,
                                    contentColor = if (ext.isDark) Color(0xFF0A0C10) else Color.White,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("device_setup_complete_button")
                                ) {
                                    if (isVerifying) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = if (ext.isDark) Color(0xFF0A0C10) else Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Verifying...", fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Complete Setup", fontWeight = FontWeight.ExtraBold)
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
