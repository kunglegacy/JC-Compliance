package com.example.ui.screens.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.domain.BiometricHelper
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowingPrimaryButton
import com.example.ui.components.MicroLabel
import com.example.ui.components.SchoolCrestBadge
import com.example.ui.components.glassFieldColors
import com.example.ui.theme.ComplianceTheme
import kotlinx.coroutines.launch

@Composable
fun AppLockScreen(
    activity: FragmentActivity,
    displayName: String,
    email: String,
    biometricsEnabled: Boolean,
    onVerifyPassword: suspend (String) -> Boolean,
    onUnlocked: () -> Unit,
    onSignOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val ext = ComplianceTheme.extended
    val glowColor = MaterialTheme.colorScheme.primary
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun triggerBiometricScan() {
        BiometricHelper.authenticate(
            activity = activity,
            title = "Unlock JCBest Compliance",
            subtitle = "Verify your fingerprint or face to resume your session",
            negativeButtonText = "Use Password",
            onSuccess = { onUnlocked() },
            onError = { msg, _, isCancelled ->
                if (!isCancelled) {
                    errorMsg = msg
                }
            }
        )
    }

    LaunchedEffect(biometricsEnabled) {
        if (biometricsEnabled) {
            triggerBiometricScan()
        }
    }

    AtmosphericGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(glowColor.copy(alpha = 0.34f), Color.Transparent),
                                    center = center,
                                    radius = size.minDimension / 2f
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SchoolCrestBadge(size = 76.dp)
                }

                Spacer(Modifier.height(10.dp))
                MicroLabel("Hardware Locked", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Session Locked",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = displayName.ifBlank { email },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(22.dp))

                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp, contentPadding = 20.dp) {
                    if (biometricsEnabled) {
                        GlowingPrimaryButton(
                            onClick = { triggerBiometricScan() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("app_lock_biometric_button")
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Unlock with Fingerprint / Face", fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMsg = null
                        },
                        label = { Text("Account Password") },
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
                            .testTag("app_lock_password_input")
                    )

                    if (!errorMsg.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = errorMsg.orEmpty(),
                            color = ext.rose,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            if (password.isBlank()) {
                                errorMsg = "Enter your account password."
                                return@OutlinedButton
                            }
                            scope.launch {
                                isChecking = true
                                val ok = onVerifyPassword(password)
                                isChecking = false
                                if (ok) onUnlocked() else errorMsg = "Incorrect password."
                            }
                        },
                        enabled = !isChecking,
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("app_lock_password_button")
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.LockOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Unlock with Password", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                TextButton(onClick = onSignOut, modifier = Modifier.testTag("app_lock_sign_out")) {
                    Text("Switch Account / Sign Out", color = ext.rose, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
