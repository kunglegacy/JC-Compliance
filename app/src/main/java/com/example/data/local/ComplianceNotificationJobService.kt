package com.example.data.local

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.example.data.remote.ComplianceRepository
import com.example.domain.ComplianceNotificationHelper
import com.example.domain.OfflineAutoSubmitEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class ComplianceNotificationJobService : JobService() {

    private val serviceJob = Job()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartJob(params: JobParameters?): Boolean {
        val jobId = params?.jobId ?: JOB_ID_PERIODIC_SYNC
        scope.launch {
            var rescheduleNeeded = false
            runCatching {
                val prefs = SecurePreferences(applicationContext)
                val state = prefs.state.first()
                val session = state.session
                if (session != null) {
                    val draftRepo = DraftRepository(applicationContext)

                    when (jobId) {
                        JOB_ID_AUTO_SUBMIT -> {
                            val result = OfflineAutoSubmitEngine.processQueue(applicationContext)
                            rescheduleNeeded = result.hasRemainingFailures
                        }

                        JOB_ID_DRAFT_REMINDER -> {
                            val drafts = draftRepo.listAllDraftsOnce(session.userId, materializeMedia = false)
                            val formDraft = draftRepo.loadComposerFormDraft(session.userId)?.takeIf { it.hasContent }
                            ComplianceNotificationHelper.notifyDraftReminderIfDue(
                                context = applicationContext,
                                userId = session.userId,
                                drafts = drafts,
                                unfinishedFormDraft = formDraft
                            )
                        }

                        JOB_ID_EVENING_REMINDER -> {
                            val drafts = draftRepo.listAllDraftsOnce(session.userId, materializeMedia = false)
                            val formDraft = draftRepo.loadComposerFormDraft(session.userId)?.takeIf { it.hasContent }
                            val hasAnyDraft = drafts.isNotEmpty() || formDraft != null
                            val todayIso = AcademicWeekEngine.todayIso()
                            val (lastDate, firstDate) = prefs.getReportTrackingSnapshot(session.userId, todayIso)
                            ComplianceNotificationHelper.notifyEveningStandardReminderIfDue(
                                context = applicationContext,
                                userId = session.userId,
                                role = session.role,
                                hasAnyDraft = hasAnyDraft,
                                lastSubmittedDateIso = lastDate,
                                firstTrackingDateIso = firstDate,
                                forceHourCheck = true
                            )
                            scheduleEveningReminder(applicationContext, forceNextDay = true)
                        }

                        else -> {
                            // Periodic sync: flush queued offline submissions, pull server alerts, check drafts & 6 PM reminder
                            if (state.supabaseConfig.isConfigured) {
                                val queueRes = OfflineAutoSubmitEngine.processQueue(applicationContext)
                                if (queueRes.hasRemainingFailures) {
                                    scheduleAutoSubmitJob(applicationContext)
                                }
                                val remoteRepo = ComplianceRepository(applicationContext)
                                val activeSession = remoteRepo.refreshSession(state.supabaseConfig, session).getOrNull() ?: session
                                val notifications = remoteRepo.fetchUnreadNotifications(state.supabaseConfig, activeSession)
                                notifications.forEach { n ->
                                    when (n.type.uppercase()) {
                                        "ACTION_REQUIRED" -> ComplianceNotificationHelper.notifyActionRequired(
                                            context = applicationContext,
                                            dedupKey = n.reportId ?: n.id,
                                            reportId = n.reportId,
                                            reportNumber = n.reportNumber.orEmpty(),
                                            complianceCheck = n.body
                                        )
                                        "ACCEPTED" -> ComplianceNotificationHelper.notifyAccepted(
                                            context = applicationContext,
                                            dedupKey = n.reportId ?: n.id,
                                            reportId = n.reportId,
                                            reportNumber = n.reportNumber.orEmpty(),
                                            complianceCheck = n.body
                                        )
                                        "NEW_SUBMISSION" -> if (activeSession.role.isSuperAdmin) {
                                            ComplianceNotificationHelper.notifyNewSubmissionForAdmin(
                                                context = applicationContext,
                                                dedupKey = n.reportId ?: n.id,
                                                reportId = n.reportId,
                                                reporterName = n.actorName
                                            )
                                        }
                                        else -> ComplianceNotificationHelper.notifyCustomServerAlert(
                                            context = applicationContext,
                                            dedupKey = n.id,
                                            title = n.title,
                                            body = n.body,
                                            targetTab = if (activeSession.role.isAdminOrAbove) "reports" else "my-exceptions",
                                            reportId = n.reportId
                                        )
                                    }
                                }

                                val drafts = draftRepo.listAllDraftsOnce(activeSession.userId, materializeMedia = false)
                                val formDraft = draftRepo.loadComposerFormDraft(activeSession.userId)?.takeIf { it.hasContent }
                                ComplianceNotificationHelper.notifyDraftReminderIfDue(
                                    context = applicationContext,
                                    userId = activeSession.userId,
                                    drafts = drafts,
                                    unfinishedFormDraft = formDraft
                                )

                                val hasAnyDraft = drafts.isNotEmpty() || formDraft != null
                                val todayIso = AcademicWeekEngine.todayIso()
                                val (lastDate, firstDate) = prefs.getReportTrackingSnapshot(activeSession.userId, todayIso)
                                ComplianceNotificationHelper.notifyEveningStandardReminderIfDue(
                                    context = applicationContext,
                                    userId = activeSession.userId,
                                    role = activeSession.role,
                                    hasAnyDraft = hasAnyDraft,
                                    lastSubmittedDateIso = lastDate,
                                    firstTrackingDateIso = firstDate,
                                    forceHourCheck = true
                                )
                            }
                        }
                    }
                }
            }
            jobFinished(params, rescheduleNeeded)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        private const val JOB_ID_PERIODIC_SYNC = 4101
        private const val JOB_ID_DRAFT_REMINDER = 4102
        private const val JOB_ID_AUTO_SUBMIT = 4103
        private const val JOB_ID_EVENING_REMINDER = 4104

        fun schedulePeriodicSync(context: Context) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val component = ComponentName(context, ComplianceNotificationJobService::class.java)
            val info = JobInfo.Builder(JOB_ID_PERIODIC_SYNC, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15L * 60L * 1000L)
                .setPersisted(true)
                .build()
            runCatching { js.schedule(info) }
        }

        fun scheduleAutoSubmitJob(context: Context) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val component = ComponentName(context, ComplianceNotificationJobService::class.java)
            val info = JobInfo.Builder(JOB_ID_AUTO_SUBMIT, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(0L)
                .setPersisted(true)
                .build()
            runCatching { js.schedule(info) }
        }

        fun scheduleEveningReminder(context: Context, forceNextDay: Boolean = false) {
            val now = Calendar.getInstance()
            val target = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 18)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (forceNextDay || !after(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
                // Strictly Monday through Friday; skip Saturdays and Sundays
                while (
                    get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                    get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                ) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val delayMs = (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val component = ComponentName(context, ComplianceNotificationJobService::class.java)
            val info = JobInfo.Builder(JOB_ID_EVENING_REMINDER, component)
                .setMinimumLatency(delayMs)
                .setOverrideDeadline(delayMs + 30L * 60L * 1000L)
                .setPersisted(true)
                .build()
            runCatching { js.schedule(info) }
        }

        fun scheduleDraftReminder(context: Context, delayMs: Long = 30L * 60L * 1000L) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val component = ComponentName(context, ComplianceNotificationJobService::class.java)
            val info = JobInfo.Builder(JOB_ID_DRAFT_REMINDER, component)
                .setMinimumLatency(delayMs)
                .setOverrideDeadline(delayMs + 15L * 60L * 1000L)
                .build()
            runCatching { js.schedule(info) }
        }

        fun cancelDraftReminder(context: Context) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            runCatching { js.cancel(JOB_ID_DRAFT_REMINDER) }
        }

        fun cancelAll(context: Context) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            runCatching {
                js.cancel(JOB_ID_PERIODIC_SYNC)
                js.cancel(JOB_ID_DRAFT_REMINDER)
                js.cancel(JOB_ID_AUTO_SUBMIT)
                js.cancel(JOB_ID_EVENING_REMINDER)
            }
        }
    }
}
