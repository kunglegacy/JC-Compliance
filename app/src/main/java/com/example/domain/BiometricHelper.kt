package com.example.domain

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.model.BiometricStatus

object BiometricHelper {

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun checkSupport(activity: FragmentActivity): BiometricStatus {
        val manager = BiometricManager.from(activity)
        return when (manager.canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus(
                available = true,
                enrolled = true,
                status = "SUCCESS",
                message = "Biometric sensor is ready."
            )
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus(
                available = true,
                enrolled = false,
                status = "NONE_ENROLLED",
                message = "No fingerprint or face biometric is enrolled on this device yet."
            )
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus(
                available = false,
                enrolled = false,
                status = "NO_HARDWARE",
                message = "This device does not have a biometric sensor."
            )
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus(
                available = false,
                enrolled = false,
                status = "HW_UNAVAILABLE",
                message = "Biometric hardware is currently busy or unavailable."
            )
            else -> BiometricStatus(
                available = false,
                enrolled = false,
                status = "UNSUPPORTED",
                message = "Biometric authentication is unavailable on this device."
            )
        }
    }

    fun openEnrollmentSettings(activity: FragmentActivity) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                    putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, AUTHENTICATORS)
                }
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }
            activity.startActivity(intent)
        } catch (_: Exception) {
            runCatching { activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String = "Cancel",
        onSuccess: () -> Unit,
        onError: (message: String, needsEnrollment: Boolean, isCancelled: Boolean) -> Unit
    ) {
        val status = checkSupport(activity)
        if (!status.enrolled) {
            onError(status.message, status.status == "NONE_ENROLLED", false)
            return
        }

        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                        val needsEnroll = errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS
                        onError(errString.toString(), needsEnroll, cancelled)
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .setNegativeButtonText(negativeButtonText)
                .setConfirmationRequired(true)
                .build()

            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(e.message ?: "Unable to launch biometric scanner.", false, false)
        }
    }
}
