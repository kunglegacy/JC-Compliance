package com.example.data.local

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.example.data.remote.ComplianceRepository
import com.example.domain.ComplianceNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ComplianceNotificationJobService : JobService() {

    private val serviceJob = Job()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartJob(params: JobParameters?): Boolean {
        val jobId = params?.jobId ?: JOB_ID_PERIODIC_SYNC
        scope.launch {
            runCatching {
                val prefs = SecurePreferences(applicationContext)
                val state = prefs.state.first()
                val session = state.session
                if (session != null) {
                    if (jobId == JOB_ID_DRAFT_REMINDER) {
                        val draftRepo = DraftRepository(applicationContext)
                        val drafts = draftRepo.observeDraftsForUser(session.userId).first()
                        val formDraft = draftRepo.loadComposerFormDraft(session.userId)?.takeIf { it.hasContent }
                        ComplianceNotificationHelper.notifyDraftReminderIfDue(
                            context = applicationContext,
                            userId = session.userId,
                            offlineDraftCount = drafts.size,
                            hasUnfinishedFormDraft = formDraft != null
                        )
                    } else if (state.supabaseConfig.isConfigured) {
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
                                "NEW_SUBMISSION" -> if (activeSession.role.isAdminOrAbove) {
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

                        // Also remind if unattended drafts remain
                        val draftRepo = DraftRepository(applicationContext)
                        val drafts = draftRepo.observeDraftsForUser(activeSession.userId).first()
                        val formDraft = draftRepo.loadComposerFormDraft(activeSession.userId)?.takeIf { it.hasContent }
                        ComplianceNotificationHelper.notifyDraftReminderIfDue(
                            context = applicationContext,
                            userId = activeSession.userId,
                            offlineDraftCount = drafts.size,
                            hasUnfinishedFormDraft = formDraft != null
                        )
                    }
                }
            }
            jobFinished(params, false)
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

        fun schedulePeriodicSync(context: Context) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val component = ComponentName(context, ComplianceNotificationJobService::class.java)
            val info = JobInfo.Builder(JOB_ID_PERIODIC_SYNC, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15L * 60L * 1000L)
                .build()
            runCatching { js.schedule(info) }
        }

        fun scheduleDraftReminder(context: Context, delayMs: Long = 90_000L) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val component = ComponentName(context, ComplianceNotificationJobService::class.java)
            val info = JobInfo.Builder(JOB_ID_DRAFT_REMINDER, component)
                .setMinimumLatency(delayMs)
                .setOverrideDeadline(delayMs + 120_000L)
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
            }
        }
    }
}
