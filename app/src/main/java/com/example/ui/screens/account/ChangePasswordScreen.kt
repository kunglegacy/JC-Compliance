package com.example.ui.screens.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassIconHalo
import com.example.ui.components.GlowingPrimaryButton
import com.example.ui.components.MicroLabel
import com.example.ui.components.SubScreenTopRow
import com.example.ui.components.glassFieldColors

@Composable
fun ChangePasswordScreen(
    isLoading: Boolean,
    onChangePassword: (current: String, newPass: String, confirm: String, onSuccess: () -> Unit) -> Unit,
    onForgotPassword: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCurrent by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }

    AtmosphericGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            SubScreenTopRow(
                title = "Change Password",
                subtitle = "Update account credentials",
                onBack = onBack
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GlassIconHalo(icon = Icons.Default.Key, tint = MaterialTheme.colorScheme.primary)

                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, contentPadding = 18.dp) {
                        MicroLabel("Verify & Update")
                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            label = { Text("Current / Old Password") },
                            trailingIcon = {
                                IconButton(onClick = { showCurrent = !showCurrent }) {
                                    Icon(
                                        if (showCurrent) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle visibility"
                                    )
                                }
                            },
                            visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            colors = glassFieldColors(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("change_pw_current_input")
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("New Password") },
                            trailingIcon = {
                                IconButton(onClick = { showNew = !showNew }) {
                                    Icon(
                                        if (showNew) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle visibility"
                                    )
                                }
                            },
                            visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            colors = glassFieldColors(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("change_pw_new_input")
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm New Password") },
                            visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            colors = glassFieldColors(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("change_pw_confirm_input")
                        )

                        Spacer(Modifier.height(18.dp))

                        GlowingPrimaryButton(
                            onClick = {
                                onChangePassword(currentPassword, newPassword, confirmPassword) {
                                    currentPassword = ""
                                    newPassword = ""
                                    confirmPassword = ""
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("change_pw_submit_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Updating...")
                            } else {
                                Icon(Icons.Default.Key, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Update Password", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, contentPadding = 18.dp) {
                        MicroLabel("Account Recovery")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Forgot your current password?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Proceed to Password Reset to request an email recovery link or contact a Compliance Officer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = onForgotPassword,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("change_pw_forgot_button")
                        ) {
                            Text("Forgot Password? Reset Options", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
