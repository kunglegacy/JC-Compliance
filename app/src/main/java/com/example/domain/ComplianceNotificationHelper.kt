package com.example.domain

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object ComplianceNotificationHelper {

    const val CHANNEL_EXCEPTIONS = "jcbest_exception_alerts"
    const val CHANNEL_DRAFTS = "jcbest_draft_reminders"

    const val EXTRA_TARGET_TAB = "extra_open_tab"
    const val EXTRA_REPORT_ID = "extra_open_report_id"

    private const val PREFS_NAME = "jcbest_notif_delivery_prefs"
    private const val KEY_DELIVERED_IDS = "delivered_keys"
    private const val KEY_LAST_DRAFT_REMINDER_AT = "last_draft_reminder_at"
    private const val KEY_LAST_DRAFT_SIGNATURE = "last_draft_signature"
    private const val DRAFT_COOLDOWN_MS = 4L * 60L * 60L * 1000L // 4 hours per unchanged draft set

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alertsChannel = NotificationChannel(
            CHANNEL_EXCEPTIONS,
            "Exception Status & Review Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when exceptions are submitted, accepted, or require action."
            enableVibration(true)
        }

        val draftChannel = NotificationChannel(
            CHANNEL_DRAFTS,
            "Unattended Draft Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminders when saved drafts or unfinished exception reports are waiting to be submitted."
        }

        nm.createNotificationChannel(alertsChannel)
        nm.createNotificationChannel(draftChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notifyActionRequired(
        context: Context,
        dedupKey: String,
        reportId: String?,
        reportNumber: String,
        complianceCheck: String
    ) {
        if (!canPostNew(context, "action_req_$dedupKey")) return
        val label = complianceCheck.ifBlank { reportNumber.ifBlank { "Exception" } }
        postNotification(
            context = context,
            channelId = CHANNEL_EXCEPTIONS,
            notificationId = ("ar_$dedupKey").hashCode(),
            title = "Action Required on Exception",
            body = "Your exception ($label) requires your attention. Please check and resolve it.",
            targetTab = "my-exceptions",
            reportId = reportId,
            priority = NotificationCompat.PRIORITY_HIGH
        )
        markDelivered(context, "action_req_$dedupKey")
    }

    fun notifyAccepted(
        context: Context,
        dedupKey: String,
        reportId: String?,
        reportNumber: String,
        complianceCheck: String
    ) {
        if (!canPostNew(context, "accepted_$dedupKey")) return
        val label = complianceCheck.ifBlank { reportNumber.ifBlank { "Exception" } }
        postNotification(
            context = context,
            channelId = CHANNEL_EXCEPTIONS,
            notificationId = ("acc_$dedupKey").hashCode(),
            title = "Exception Accepted",
            body = "Your exception ($label) has been accepted.",
            targetTab = "my-exceptions",
            reportId = reportId,
            priority = NotificationCompat.PRIORITY_HIGH
        )
        markDelivered(context, "accepted_$dedupKey")
    }

    fun notifyNewSubmissionForAdmin(
        context: Context,
        dedupKey: String,
        reportId: String?,
        reporterName: String,
        complianceCheck: String? = null
    ) {
        if (!canPostNew(context, "new_sub_$dedupKey")) return
        val actor = reporterName.ifBlank { "A reporting officer" }
        val suffix = complianceCheck?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        postNotification(
            context = context,
            channelId = CHANNEL_EXCEPTIONS,
            notificationId = ("sub_$dedupKey").hashCode(),
            title = "New Compliance Exception",
            body = "$actor has submitted a new exception$suffix. Please review.",
            targetTab = "reports",
            reportId = reportId,
            priority = NotificationCompat.PRIORITY_HIGH
        )
        markDelivered(context, "new_sub_$dedupKey")
    }

    fun notifyCustomServerAlert(
        context: Context,
        dedupKey: String,
        title: String,
        body: String,
        targetTab: String,
        reportId: String?
    ) {
        if (!canPostNew(context, "srv_$dedupKey")) return
        postNotification(
            context = context,
            channelId = CHANNEL_EXCEPTIONS,
            notificationId = ("srv_$dedupKey").hashCode(),
            title = title.ifBlank { "Compliance Update" },
            body = body,
            targetTab = targetTab,
            reportId = reportId,
            priority = NotificationCompat.PRIORITY_HIGH
        )
        markDelivered(context, "srv_$dedupKey")
    }

    fun notifyDraftReminderIfDue(
        context: Context,
        userId: String,
        offlineDraftCount: Int,
        hasUnfinishedFormDraft: Boolean
    ) {
        if (userId.isBlank()) return
        if (offlineDraftCount <= 0 && !hasUnfinishedFormDraft) return
        if (!hasNotificationPermission(context)) return

        val signature = "$userId:$offlineDraftCount:$hasUnfinishedFormDraft"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastAt = prefs.getLong(KEY_LAST_DRAFT_REMINDER_AT, 0L)
        val lastSig = prefs.getString(KEY_LAST_DRAFT_SIGNATURE, "").orEmpty()
        val now = System.currentTimeMillis()
        if (lastSig == signature && (now - lastAt) < DRAFT_COOLDOWN_MS) return

        ensureChannels(context)
        val summary = when {
            offlineDraftCount > 0 && hasUnfinishedFormDraft ->
                "$offlineDraftCount offline draft${if (offlineDraftCount > 1) "s" else ""} and an unfinished exception report"
            offlineDraftCount > 1 ->
                "$offlineDraftCount unattended offline drafts"
            offlineDraftCount == 1 ->
                "1 unattended offline draft"
            else ->
                "an unfinished exception report"
        }

        postNotification(
            context = context,
            channelId = CHANNEL_DRAFTS,
            notificationId = ("draft_$userId").hashCode(),
            title = "Unfinished Exception Draft",
            body = "You have $summary waiting to be completed and submitted.",
            targetTab = "my-exceptions",
            reportId = null,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        prefs.edit()
            .putLong(KEY_LAST_DRAFT_REMINDER_AT, now)
            .putString(KEY_LAST_DRAFT_SIGNATURE, signature)
            .apply()
    }

    fun seedSeenKeys(context: Context, keys: Collection<String>) {
        if (keys.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_DELIVERED_IDS, emptySet()).orEmpty().toMutableSet()
        existing.addAll(keys)
        // Trim oldest if huge
        val trimmed = if (existing.size > 400) existing.toList().takeLast(300).toSet() else existing
        prefs.edit().putStringSet(KEY_DELIVERED_IDS, trimmed).apply()
    }

    private fun canPostNew(context: Context, key: String): Boolean {
        if (!hasNotificationPermission(context)) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val delivered = prefs.getStringSet(KEY_DELIVERED_IDS, emptySet()).orEmpty()
        return key !in delivered
    }

    private fun markDelivered(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_DELIVERED_IDS, emptySet()).orEmpty().toMutableSet()
        existing.add(key)
        val trimmed = if (existing.size > 400) existing.toList().takeLast(300).toSet() else existing
        prefs.edit().putStringSet(KEY_DELIVERED_IDS, trimmed).apply()
    }

    private fun postNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        targetTab: String,
        reportId: String?,
        priority: Int
    ) {
        ensureChannels(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_TAB, targetTab)
            if (!reportId.isNullOrBlank()) {
                putExtra(EXTRA_REPORT_ID, reportId)
            }
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, notificationId, launchIntent, piFlags)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }
}
