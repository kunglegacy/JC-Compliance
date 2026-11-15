package com.example.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassIconHalo
import com.example.ui.components.GlowingPrimaryButton
import com.example.ui.components.MicroLabel
import com.example.ui.components.SubScreenTopRow
import com.example.ui.components.glassFieldColors
import com.example.ui.theme.ComplianceTheme

@Composable
fun PasswordResetScreen(
    initialEmail: String = "",
    isLoading: Boolean,
    onSendResetEmail: (email: String, onSuccess: () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var email by remember(initialEmail) { mutableStateOf(initialEmail) }
    var emailSent by remember { mutableStateOf(false) }
    val ext = ComplianceTheme.extended

    AtmosphericGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            SubScreenTopRow(
                title = "Password Reset",
                subtitle = "Account recovery options",
                onBack = onBack
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GlassIconHalo(icon = Icons.Default.Key, tint = MaterialTheme.colorScheme.primary)

                    // Option 1: Reset via Email
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, contentPadding = 18.dp) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Column {
                                MicroLabel("Option 1 • Self Service")
                                Text(
                                    text = "Reset via Institutional Email",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (!emailSent) {
                            Text(
                                text = "Enter your registered email address and we will send a secure password recovery link.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Institutional Email") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                colors = glassFieldColors(),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reset_email_input")
                            )
                            Spacer(Modifier.height(16.dp))
                            GlowingPrimaryButton(
                                onClick = { onSendResetEmail(email) { emailSent = true } },
                                enabled = !isLoading && email.contains("@"),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("send_reset_link_button")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Sending Link...")
                                } else {
                                    Icon(Icons.Default.Key, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Send Reset Link", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        } else {
                            Surface(
                                color = ext.emeraldContainer,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, ext.emerald.copy(alpha = 0.40f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ext.emerald)
                                    Column {
                                        Text(
                                            text = "Recovery Email Dispatched",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = ext.emerald
                                        )
                                        Text(
                                            text = "Check your inbox at $email for your reset instructions.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { onSendResetEmail(email) {} },
                                enabled = !isLoading,
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("resend_reset_link_button")
                            ) {
                                Text("Resend Reset Email", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Option 2: Contact Compliance Officer
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, contentPadding = 18.dp) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(ext.amberContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = ext.amber)
                            }
                            Column {
                                MicroLabel("Option 2 • Directorate Support")
                                Text(
                                    text = "Contact Compliance Officer",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "If you no longer have access to your institutional inbox, contact a Directorate Administrator or Super Admin to issue a temporary reset credential from the Directorate User Registry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = ext.glassBottom,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, ext.glassRimEnd),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                MicroLabel("Directorate Helpdesk")
                                Text(
                                    text = "compliance@jcbestschools.com",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
