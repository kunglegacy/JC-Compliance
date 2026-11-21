package com.example.domain

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

data class BackgroundRestrictionStatus(
    val isAppBackgroundRestricted: Boolean,
    val isBackgroundDataRestricted: Boolean,
    val isSystemPowerSaveMode: Boolean
) {
    val hasAnyRestriction: Boolean
        get() = isAppBackgroundRestricted || isBackgroundDataRestricted || isSystemPowerSaveMode

    val hasAppLevelRestriction: Boolean
        get() = isAppBackgroundRestricted || isBackgroundDataRestricted
}

object BackgroundExecutionHelper {

    fun checkStatus(context: Context): BackgroundRestrictionStatus {
        val appContext = context.applicationContext

        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isAppBgRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { am?.isBackgroundRestricted == true }.getOrDefault(false)
        } else {
            false
        }

        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isBgDataRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                cm?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            }.getOrDefault(false)
        } else {
            false
        }

        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = runCatching { pm?.isPowerSaveMode == true }.getOrDefault(false)

        return BackgroundRestrictionStatus(
            isAppBackgroundRestricted = isAppBgRestricted,
            isBackgroundDataRestricted = isBgDataRestricted,
            isSystemPowerSaveMode = isPowerSave
        )
    }

    fun getPrimaryTitle(status: BackgroundRestrictionStatus): String = when {
        status.isAppBackgroundRestricted -> "Background Activity Is Turned Off"
        status.isBackgroundDataRestricted -> "Background Data Is Restricted"
        status.isSystemPowerSaveMode -> "Battery Saver Mode Is Active"
        else -> "Background Activity Enabled"
    }

    fun getPrimaryDescription(status: BackgroundRestrictionStatus): String = when {
        status.isAppBackgroundRestricted ->
            "Background activity is disabled for this app in Android settings. Queued offline reports will not send automatically until you re-open the app unless you turn background activity back on."
        status.isBackgroundDataRestricted ->
            "Data Saver is blocking background network uploads for this app. Allow background data usage, otherwise your queued report will wait until you open the app."
        status.isSystemPowerSaveMode ->
            "Your device Battery Saver is active and may pause background sync while the screen is off. Disable Battery Saver or open the app once back online."
        else ->
            "Background activity and network sync are active. Your report will upload automatically once internet returns—even if you leave the app."
    }

    fun getActionButtonLabel(status: BackgroundRestrictionStatus): String = when {
        status.isAppBackgroundRestricted -> "Turn On Background Activity"
        status.isBackgroundDataRestricted -> "Allow Background Data"
        status.isSystemPowerSaveMode -> "Battery Saver Settings"
        else -> "App Settings"
    }

    fun openRelevantSettings(context: Context, status: BackgroundRestrictionStatus) {
        when {
            status.isAppBackgroundRestricted -> openAppInfoSettings(context)
            status.isBackgroundDataRestricted -> openBackgroundDataSettings(context)
            status.isSystemPowerSaveMode -> openBatterySaverSettings(context)
            else -> openAppInfoSettings(context)
        }
    }

    fun openAppInfoSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }

    fun openBackgroundDataSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val intent = Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            if (launched) return
        }
        openAppInfoSettings(context)
    }

    fun openBatterySaverSettings(context: Context) {
        val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            openAppInfoSettings(context)
        }
    }
}
