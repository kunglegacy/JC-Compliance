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
import com.example.data.model.OfflineDraft
import com.example.data.model.ReportFormDraft
import com.example.data.model.UserRole
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Calendar

object ComplianceNotificationHelper {

    const val CHANNEL_EXCEPTIONS = "jcbest_exception_alerts"
    const val CHANNEL_DRAFTS = "jcbest_draft_reminders"

    const val EXTRA_TARGET_TAB = "extra_open_tab"
    const val EXTRA_REPORT_ID = "extra_open_report_id"

    private const val PREFS_NAME = "jcbest_notif_delivery_prefs"
    private const val KEY_DELIVERED_IDS = "delivered_keys"
    private const val KEY_LAST_DRAFT_REMINDER_AT_PREFIX = "last_draft_reminder_at_"
    private const val KEY_LAST_DRAFT_SIGNATURE_PREFIX = "last_draft_signature_"
    private const val KEY_LAST_EVENING_REMINDER_DATE_PREFIX = "last_evening_reminder_date_"
    private const val DRAFT_GLOBAL_COOLDOWN_MS = 6L * 60L * 60L * 1000L      // Never spam more than once in 6 hours
    private const val DRAFT_SIGNATURE_COOLDOWN_MS = 12L * 60L * 60L * 1000L  // Wait 12 hours before re-notifying same drafts

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
            "Draft & Daily Compliance Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Dynamic draft completion reminders and evening compliance check-ins."
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

    fun notifyOfflineQueuedSubmitted(
        context: Context,
        draftId: String,
        reportId: String?,
        reportNumber: String,
        complianceCheck: String
    ) {
        if (!canPostNew(context, "offline_sent_$draftId")) return
        val label = complianceCheck.ifBlank { reportNumber.ifBlank { "Exception Report" } }
        postNotification(
            context = context,
            channelId = CHANNEL_EXCEPTIONS,
            notificationId = ("offline_sent_$draftId").hashCode(),
            title = "Offline Exception Submitted",
            body = "Your offline report ($label) has been successfully submitted automatically now that your device is back online.",
            targetTab = "my-exceptions",
            reportId = reportId,
            priority = NotificationCompat.PRIORITY_HIGH
        )
        markDelivered(context, "offline_sent_$draftId")
    }

    fun notifyOfflineQueuedBatchSubmitted(
        context: Context,
        submittedCount: Int,
        latestReportId: String?
    ) {
        if (submittedCount <= 0) return
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        postNotification(
            context = context,
            channelId = CHANNEL_EXCEPTIONS,
            notificationId = ("offline_batch_${System.currentTimeMillis() / 10_000L}").hashCode(),
            title = "$submittedCount Offline Exceptions Submitted",
            body = "Your $submittedCount queued compliance reports have been submitted automatically now that your device is back online.",
            targetTab = "my-exceptions",
            reportId = latestReportId,
            priority = NotificationCompat.PRIORITY_HIGH
        )
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
        drafts: List<OfflineDraft>,
        unfinishedFormDraft: ReportFormDraft?
    ) {
        if (userId.isBlank()) return
        val unqueuedDrafts = drafts.filterNot { it.isQueuedForSubmit }
        val composerHasContent = unfinishedFormDraft?.hasContent == true
        if (unqueuedDrafts.isEmpty() && !composerHasContent) return
        if (!hasNotificationPermission(context)) return

        // Quiet hours guard (do not buzz users late at night or very early morning)
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (hour < 7 || hour >= 22) return

        var withEvidence = unqueuedDrafts.count { it.hasEvidence }
        var withoutEvidence = unqueuedDrafts.count { !it.hasEvidence }
        if (composerHasContent && unfinishedFormDraft != null) {
            val composerHasEvidence = unfinishedFormDraft.selectedDraftMedia.isNotEmpty() ||
                !unfinishedFormDraft.localMediaPath.isNullOrBlank() ||
                (unfinishedFormDraft.evidenceType != "none" && !unfinishedFormDraft.evidenceName.isNullOrBlank())
            if (composerHasEvidence) withEvidence++ else withoutEvidence++
        }

        val signature = "w$withEvidence:wo$withoutEvidence"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastAtKey = "${KEY_LAST_DRAFT_REMINDER_AT_PREFIX}$userId"
        val lastSigKey = "${KEY_LAST_DRAFT_SIGNATURE_PREFIX}$userId"
        val lastAt = prefs.getLong(lastAtKey, 0L)
        val lastSig = prefs.getString(lastSigKey, "").orEmpty()
        val now = System.currentTimeMillis()

        val elapsed = now - lastAt
        if (elapsed < DRAFT_GLOBAL_COOLDOWN_MS) return
        if (lastSig == signature && elapsed < DRAFT_SIGNATURE_COOLDOWN_MS) return

        ensureChannels(context)
        val (title, body) = when {
            withoutEvidence > 0 && withEvidence == 0 -> {
                if (withoutEvidence == 1) {
                    "Unfinished Draft Without Evidence" to
                        "You have a saved exception report draft that does not have evidence yet. Please attach evidence and complete it."
                } else {
                    "Unfinished Drafts Without Evidence" to
                        "You have $withoutEvidence unfinished exception report drafts without evidence. Please attach evidence and complete them."
                }
            }
            withEvidence > 0 && withoutEvidence == 0 -> {
                if (withEvidence == 1) {
                    "Unfinished Draft With Evidence" to
                        "You have an unfinished exception draft with evidence attached. Please review and finish submitting it."
                } else {
                    "Unfinished Drafts With Evidence" to
                        "You have $withEvidence unfinished exception drafts with evidence attached. Please review and finish submitting them."
                }
            }
            else -> {
                val total = withEvidence + withoutEvidence
                "Unfinished Exception Drafts ($total)" to
                    "You have $withEvidence unfinished draft${if (withEvidence > 1) "s" else ""} with evidence and $withoutEvidence without evidence. Please review and complete your pending reports."
            }
        }

        postNotification(
            context = context,
            channelId = CHANNEL_DRAFTS,
            notificationId = ("draft_$userId").hashCode(),
            title = title,
            body = body,
            targetTab = "my-exceptions",
            reportId = null,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        prefs.edit()
            .putLong(lastAtKey, now)
            .putString(lastSigKey, signature)
            .apply()
    }

    fun notifyEveningStandardReminderIfDue(
        context: Context,
        userId: String,
        role: UserRole,
        hasAnyDraft: Boolean,
        lastSubmittedDateIso: String?,
        firstTrackingDateIso: String,
        forceHourCheck: Boolean = true
    ) {
        if (userId.isBlank()) return
        if (role != UserRole.STANDARD) return
        if (hasAnyDraft) return
        if (!hasNotificationPermission(context)) return

        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        // Strictly Monday through Friday only; exclude Saturdays and Sundays
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) return

        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (forceHourCheck && (hour < 18 || hour >= 23)) return

        val today = LocalDate.now()
        if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) return

        val todayIso = today.toString()
        if (lastSubmittedDateIso == todayIso) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dedupPrefKey = "${KEY_LAST_EVENING_REMINDER_DATE_PREFIX}$userId"
        if (prefs.getString(dedupPrefKey, "") == todayIso) return

        val missedWeekdays = countMissedWeekdays(
            today = today,
            lastSubmittedDateIso = lastSubmittedDateIso,
            firstTrackingDateIso = firstTrackingDateIso
        )

        val (title, body) = if (missedWeekdays <= 1) {
            "Daily Compliance Check-In" to
                "You have not reported any compliance exceptions today. Please submit an exception report if any observations are available."
        } else {
            val daysText = numberToWords(missedWeekdays)
            "Compliance Reporting Reminder" to
                "You have not submitted any compliance exceptions for $daysText consecutive weekdays. Please submit an exception report if any issues are available."
        }

        ensureChannels(context)
        postNotification(
            context = context,
            channelId = CHANNEL_DRAFTS,
            notificationId = ("evening_std_$userId").hashCode(),
            title = title,
            body = body,
            targetTab = "my-exceptions",
            reportId = null,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        prefs.edit().putString(dedupPrefKey, todayIso).apply()
    }

    private fun countMissedWeekdays(
        today: LocalDate,
        lastSubmittedDateIso: String?,
        firstTrackingDateIso: String
    ): Int = runCatching {
        val startDate = if (!lastSubmittedDateIso.isNullOrBlank()) {
            LocalDate.parse(lastSubmittedDateIso).plusDays(1)
        } else {
            LocalDate.parse(firstTrackingDateIso.ifBlank { today.toString() })
        }
        if (startDate.isAfter(today)) return@runCatching 1

        // Clamp scan window to 30 days max
        val boundedStart = if (startDate.isBefore(today.minusDays(30))) today.minusDays(30) else startDate
        var cursor = boundedStart
        var count = 0
        while (!cursor.isAfter(today)) {
            if (cursor.dayOfWeek != DayOfWeek.SATURDAY && cursor.dayOfWeek != DayOfWeek.SUNDAY) {
                count++
            }
            cursor = cursor.plusDays(1)
        }
        count.coerceAtLeast(1)
    }.getOrDefault(1)

    private fun numberToWords(n: Int): String = when (n) {
        1 -> "one"
        2 -> "two"
        3 -> "three"
        4 -> "four"
        5 -> "five"
        6 -> "six"
        7 -> "seven"
        8 -> "eight"
        9 -> "nine"
        10 -> "ten"
        else -> n.toString()
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
