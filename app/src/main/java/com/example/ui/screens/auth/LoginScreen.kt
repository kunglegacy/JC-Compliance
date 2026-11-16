package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.SupabaseConfig
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowingPrimaryButton
import com.example.ui.components.MicroLabel
import com.example.ui.components.SchoolCrestBadge
import com.example.ui.components.SupabaseConnectionDialog
import com.example.ui.components.glassFieldColors
import com.example.ui.theme.ComplianceTheme

@Composable
fun LoginScreen(
    isLoading: Boolean,
    authError: String?,
    onClearError: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onForgotPassword: (String) -> Unit,
    supabaseConfig: SupabaseConfig = SupabaseConfig("", ""),
    rememberedEmail: String = "",
    rememberedDisplayName: String = "",
    rememberedAvatarUri: String = "",
    onSaveSupabaseConfig: (String, String) -> Unit = { _, _ -> }
) {
    var email by remember(rememberedEmail) { mutableStateOf(rememberedEmail) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }

    val ext = ComplianceTheme.extended
    val haloColor = MaterialTheme.colorScheme.primary

    AtmosphericGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Action: Backend Connection Config Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { showConfigDialog = true },
                        modifier = Modifier.testTag("login_configure_backend_button")
                    ) {
                        Icon(
                            imageVector = if (supabaseConfig.isConfigured) Icons.Default.CloudSync else Icons.Default.Settings,
                            contentDescription = "Backend Settings",
                            tint = if (supabaseConfig.isConfigured) ext.emerald else ext.amber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (supabaseConfig.isConfigured) "Backend Connected" else "Configure Backend",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (supabaseConfig.isConfigured) ext.emerald else ext.amber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Glowing ambient crest halo emblem
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(haloColor.copy(alpha = 0.36f), Color.Transparent),
                                    center = center,
                                    radius = size.minDimension / 2f
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SchoolCrestBadge(size = 84.dp)
                }

                Spacer(Modifier.height(10.dp))
                MicroLabel("Directorate Access", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Directorate Login",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "JC-Best Schools Compliance Portal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (rememberedDisplayName.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Welcome back, $rememberedDisplayName",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Prominent banner if backend is not configured yet
                if (!supabaseConfig.isConfigured) {
                    Surface(
                        color = ext.amberContainer,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, ext.amber.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .clickable { showConfigDialog = true }
                            .testTag("backend_not_configured_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = ext.amber
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Institutional Data Not Configured",
                                    fontWeight = FontWeight.Bold,
                                    color = ext.amber,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Tap here to connect this APK to your Directorate Supabase project URL & key.",
                                    color = ext.amber.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Button(
                                onClick = { showConfigDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ext.amber,
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Setup", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Single unified error banner (prevents duplicated banners)
                AnimatedVisibility(visible = !authError.isNullOrBlank()) {
                    Surface(
                        color = ext.roseContainer,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, ext.rose.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clickable {
                                if (authError?.contains("configured", ignoreCase = true) == true) {
                                    showConfigDialog = true
                                }
                            }
                            .testTag("login_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = ext.rose
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = authError.orEmpty(),
                                    color = ext.rose,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (authError?.contains("configured", ignoreCase = true) == true) {
                                    Text(
                                        text = "Tap here to open Connection Settings",
                                        color = ext.rose,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 28.dp,
                    contentPadding = 20.dp
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            if (authError != null) onClearError()
                        },
                        label = { Text("Institutional Email") },
                        trailingIcon = {
                            Icon(Icons.Default.PersonOutline, contentDescription = null)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        colors = glassFieldColors(),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_email_input")
                    )

                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            if (authError != null) onClearError()
                        },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { if (!isLoading) onSignIn(email, password) }
                        ),
                        colors = glassFieldColors(),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_password_input")
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = ext.solarGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Biometric login can be enabled after signing in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    GlowingPrimaryButton(
                        onClick = { onSignIn(email, password) },
                        enabled = !isLoading,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("secure_login_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Verifying...", fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                text = "Secure Login",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(
                            onClick = { onForgotPassword(email) },
                            modifier = Modifier.testTag("forgot_password_button")
                        ) {
                            Text(
                                text = "Forgot your password?",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfigDialog) {
        SupabaseConnectionDialog(
            current = supabaseConfig,
            onDismiss = { showConfigDialog = false },
            onSave = { url, key ->
                onSaveSupabaseConfig(url, key)
                showConfigDialog = false
            }
        )
    }
}
