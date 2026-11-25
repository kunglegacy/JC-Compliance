package com.example.domain

import android.content.Context
import android.net.Uri
import com.example.data.local.AcademicWeekEngine
import com.example.data.local.DraftRepository
import com.example.data.local.SecurePreferences
import com.example.data.model.ComplianceDivision
import com.example.data.model.ComplianceReport
import com.example.data.model.ReportResolutionStatus
import com.example.data.remote.ComplianceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AutoSubmitBatchResult(
    val submittedReports: List<ComplianceReport> = emptyList(),
    val failedCount: Int = 0
) {
    val hasAnySubmitted: Boolean get() = submittedReports.isNotEmpty()
    val hasRemainingFailures: Boolean get() = failedCount > 0
}

object OfflineAutoSubmitEngine {

    private val processMutex = Mutex()
    private val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    private fun isUuid(value: String?): Boolean =
        !value.isNullOrBlank() && uuidRegex.matches(value.trim())

    suspend fun processQueue(context: Context): AutoSubmitBatchResult = processMutex.withLock {
        val appContext = context.applicationContext
        val prefs = SecurePreferences(appContext)
        val draftRepo = DraftRepository(appContext)
        val remoteRepo = ComplianceRepository(appContext)

        val state = prefs.state.first()
        val initialSession = state.session ?: return@withLock AutoSubmitBatchResult()
        val config = state.supabaseConfig
        if (!config.isConfigured) return@withLock AutoSubmitBatchResult()

        val queuedDrafts = draftRepo.listQueuedForAutoSubmit(initialSession.userId)
        if (queuedDrafts.isEmpty()) return@withLock AutoSubmitBatchResult()

        val activeSession = remoteRepo.refreshSession(config, initialSession).getOrNull()?.also {
            prefs.saveSession(it)
        } ?: initialSession

        val cacheRepo = com.example.data.local.OfflineCacheRepository(appContext)
        val cachedPayload = cacheRepo.loadWorkspaceCache(activeSession.userId)
        val settings = cachedPayload?.systemSettings ?: runCatching {
            remoteRepo.fetchSettings(config, activeSession.accessToken)
        }.getOrDefault(com.example.data.model.SystemSettings())
        val activeWeek = AcademicWeekEngine.calculateAcademicWeek(settings).weekNumber

        val submitted = mutableListOf<ComplianceReport>()
        var firstSubmittedDraftId: String? = null
        var failures = 0

        for (queuedDraft in queuedDrafts) {
            val form = queuedDraft.formDraft
            if (form == null) {
                failures++
                continue
            }
            val division = ComplianceDivision.fromAny(form.divisionKey) ?: ComplianceDivision.ACADEMIC
            val reportNumber = remoteRepo.generateReportNumber()
            val finalUrls = mutableListOf<String>()
            val finalTypes = mutableListOf<String>()
            var uploadFailed = false

            val mediaItems = queuedDraft.mediaItems.ifEmpty {
                form.selectedDraftMedia
            }

            for (item in mediaItems) {
                val mediaType = item.mediaType.ifBlank { "image" }
                val isImage = mediaType.equals("image", ignoreCase = true)
                val isVideo = mediaType.equals("video", ignoreCase = true)
                val defaultMime = when (mediaType.lowercase()) {
                    "image" -> "image/jpeg"
                    "video" -> "video/mp4"
                    "audio" -> "audio/mp4"
                    else -> "application/octet-stream"
                }

                val pathOrUri = item.pathOrUri
                val compressedRes = when {
                    isImage && pathOrUri.startsWith("/") ->
                        MediaCompressor.compressImageFile(appContext, File(pathOrUri)) {}
                    isImage ->
                        MediaCompressor.compressImageUri(appContext, Uri.parse(pathOrUri)) {}
                    isVideo && pathOrUri.startsWith("/") ->
                        MediaCompressor.compressVideoFile(appContext, File(pathOrUri)) {}
                    isVideo ->
                        MediaCompressor.compressVideoUri(appContext, Uri.parse(pathOrUri)) {}
                    else -> null
                }
                val compressedFile = compressedRes?.getOrNull()?.file
                val compressedMime = compressedRes?.getOrNull()?.mimeType

                val upRes = when {
                    compressedFile != null && compressedFile.exists() ->
                        remoteRepo.uploadEvidenceFile(
                            config = config,
                            token = activeSession.accessToken,
                            reportNumber = reportNumber,
                            file = compressedFile,
                            mimeType = compressedMime ?: defaultMime,
                            reportId = reportNumber
                        )
                    pathOrUri.startsWith("/") -> {
                        val rawFile = File(pathOrUri)
                        if (!rawFile.exists()) {
                            Result.failure(Exception("Local queued evidence file missing."))
                        } else {
                            remoteRepo.uploadEvidenceFile(
                                config = config,
                                token = activeSession.accessToken,
                                reportNumber = reportNumber,
                                file = rawFile,
                                mimeType = defaultMime,
                                reportId = reportNumber
                            )
                        }
                    }
                    else ->
                        remoteRepo.uploadEvidenceUri(
                            config = config,
                            token = activeSession.accessToken,
                            reportNumber = reportNumber,
                            uri = Uri.parse(pathOrUri),
                            fallbackMime = defaultMime,
                            reportId = reportNumber
                        ).map { it.first }
                }

                if (compressedFile != null && compressedFile.exists() && compressedFile.absolutePath.contains("cache")) {
                    runCatching { compressedFile.delete() }
                }

                if (upRes.isFailure) {
                    uploadFailed = true
                    break
                }
                finalUrls.add(upRes.getOrThrow())
                finalTypes.add(mediaType)
            }

            if (uploadFailed) {
                failures++
                continue
            }

            val finalEvidenceUrl = finalUrls.firstOrNull()
            val finalEvidenceType = finalTypes.firstOrNull() ?: "none"
            val finalEvidenceName = when {
                finalUrls.isEmpty() -> null
                finalUrls.size == 1 -> mediaItems.firstOrNull()?.capturedAtLabel ?: "Attached evidence"
                else -> "${finalUrls.size} attached items"
            }

            val isGroupSubmission = division == ComplianceDivision.ACADEMIC &&
                form.isGroupReport &&
                form.groupMembers.isNotEmpty()
            val normalizedGroupMembers = if (isGroupSubmission) {
                GroupReportHelpers.normalizeMembers(
                    members = form.groupMembers,
                    staffRoster = cachedPayload?.staffList.orEmpty(),
                    classes = cachedPayload?.classes.orEmpty(),
                    subjects = cachedPayload?.subjects.orEmpty()
                )
            } else {
                emptyList()
            }

            val resolvedStaffId = if (isGroupSubmission) null else form.staffId?.takeIf { division != ComplianceDivision.CDS && isUuid(it) }
            val effectiveTargetType = when {
                isGroupSubmission -> "division"
                division == ComplianceDivision.CDS -> "division"
                division == ComplianceDivision.OPERATIONS && resolvedStaffId == null -> "division"
                division == ComplianceDivision.DISCIPLINE && (form.targetType == "division" || resolvedStaffId == null) -> "division"
                else -> "staff"
            }
            val resolvedClassId = if (isGroupSubmission || form.classId.equals("NONE", true)) null else form.classId?.takeIf { isUuid(it) }
            val resolvedSubjectId = if (isGroupSubmission) null else form.subjectId?.takeIf { isUuid(it) }
            val resolvedCheckId = form.checkId?.trim()?.takeIf { it.isNotBlank() && !it.equals("CUSTOM", ignoreCase = true) }

            val specificText = when {
                division == ComplianceDivision.OPERATIONS && form.operationScope == "Transport" ->
                    "${form.selectedBus} (${form.opPersonOrLocation})"
                division == ComplianceDivision.OPERATIONS -> form.opPersonOrLocation
                else -> form.studentNamesArm
            }.trim()

            val staffNameVal = when {
                isGroupSubmission -> normalizedGroupMembers.joinToString(", ") { it.staffName }
                !form.staffName.isNullOrBlank() && division != ComplianceDivision.CDS -> form.staffName
                division == ComplianceDivision.OPERATIONS && specificText.isNotBlank() -> specificText
                division == ComplianceDivision.OPERATIONS -> form.operationScope.ifBlank { "Operation" }
                division == ComplianceDivision.DISCIPLINE && effectiveTargetType == "division" ->
                    specificText.ifBlank { "Student Decorum" }
                division == ComplianceDivision.CDS -> "CDS Infrastructure"
                else -> "Staff / Unit"
            }
            val actorDisplayName = state.customDisplayName.ifBlank { activeSession.displayName }.ifBlank { activeSession.email }
            val safeEffectText = form.effectText.trim().ifBlank { "Operational or academic compliance impact noted." }
            val incDate = form.incidentDate.ifBlank { AcademicWeekEngine.todayIso() }
            val checkLabel = form.checkName?.takeIf { it.isNotBlank() } ?: "General Compliance"

            val payload = JSONObject().apply {
                put("report_number", reportNumber)
                put("reported_by", activeSession.userId)
                put("reporter_id", activeSession.userId)
                put("reporter_name", actorDisplayName)
                put("reporting_officer", actorDisplayName)
                put("reporter_role", activeSession.email)
                put("incident_date", incDate)
                put("date", incDate)
                put("week_number", activeWeek)
                put("term", settings.activeTerm)
                put("division", division.dbValue)
                put("department", division.dbValue)
                put("category", form.checkName?.takeIf { it.isNotBlank() } ?: form.operationScope.ifBlank { division.label })
                put("target_type", effectiveTargetType)
                if (resolvedCheckId != null) put("check_id", resolvedCheckId) else put("check_id", JSONObject.NULL)
                put("compliance_check", checkLabel)
                put("exception", form.exceptionText.trim())
                put("exception_text", form.exceptionText.trim())
                put("effect", safeEffectText)
                put("effect_text", safeEffectText)
                put("severity", "MEDIUM")
                put("staff_id", resolvedStaffId ?: JSONObject.NULL)
                put("staff_name", staffNameVal)
                put("staff_code", form.staffCode?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                put("staff_category", division.label)
                put("staff_group", form.operationScope.ifBlank { division.label })
                put("role", JSONObject.NULL)
                put("assigned_location", form.opPersonOrLocation.ifBlank { form.className.orEmpty() })
                put("class_id", resolvedClassId ?: JSONObject.NULL)
                put("class_name", form.className?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                put("subject_id", resolvedSubjectId ?: JSONObject.NULL)
                put("subject", form.subjectName?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                put("evidence_url", finalEvidenceUrl ?: JSONObject.NULL)
                put("evidence_type", finalEvidenceType)
                put("evidence_name", finalEvidenceName ?: JSONObject.NULL)
                put("evidence_urls", JSONArray(finalUrls))
                put("evidence_types", JSONArray(finalTypes))
                put("status", ReportResolutionStatus.OPEN.key)
                put("review_status", "UNSEEN")
                put("is_seen", false)
                put("is_edited", false)
                if (isGroupSubmission) {
                    GroupReportHelpers.applyGroupFieldsToPayload(this, normalizedGroupMembers)
                }
            }

            val createRes = remoteRepo.createReport(config, activeSession, payload)
            if (createRes.isSuccess) {
                val saved = createRes.getOrThrow()
                if (firstSubmittedDraftId == null) firstSubmittedDraftId = queuedDraft.id
                submitted.add(saved)
                draftRepo.deleteDraft(activeSession.userId, queuedDraft.id, deleteMediaFile = true)
                prefs.saveLastSubmittedReportDate(activeSession.userId, AcademicWeekEngine.todayIso())
                remoteRepo.insertNotificationBestEffort(
                    config = config,
                    token = activeSession.accessToken,
                    recipientUserId = null,
                    recipientRole = "super_admin",
                    type = "NEW_SUBMISSION",
                    title = "New Compliance Exception",
                    body = "$actorDisplayName has submitted a new exception (${saved.complianceCheck.ifBlank { saved.reportNumber }}). Please review.",
                    reportId = saved.id,
                    reportNumber = saved.reportNumber,
                    actorName = actorDisplayName
                )
            } else {
                failures++
            }
        }

        if (submitted.isNotEmpty()) {
            val existingReports = cachedPayload?.reports.orEmpty()
            val submittedIds = submitted.map { it.id }.toSet()
            val merged = submitted + existingReports.filterNot { it.id in submittedIds }
            cacheRepo.saveReportsOnly(
                userId = activeSession.userId,
                reports = merged,
                syncedAtMs = System.currentTimeMillis()
            )
        }

        if (submitted.size == 1 && firstSubmittedDraftId != null) {
            val saved = submitted.first()
            ComplianceNotificationHelper.notifyOfflineQueuedSubmitted(
                context = appContext,
                draftId = firstSubmittedDraftId,
                reportId = saved.id,
                reportNumber = saved.reportNumber,
                complianceCheck = saved.complianceCheck
            )
        } else if (submitted.size > 1) {
            ComplianceNotificationHelper.notifyOfflineQueuedBatchSubmitted(
                context = appContext,
                submittedCount = submitted.size,
                latestReportId = submitted.lastOrNull()?.id
            )
        }

        if (prefs.isAppLockedSync()) {
            draftRepo.wipeActiveSessionMediaCache()
        }

        AutoSubmitBatchResult(submittedReports = submitted, failedCount = failures)
    }
}
