package com.example.data.local

import android.content.Context
import com.example.data.model.Classroom
import com.example.data.model.ComplianceCheck
import com.example.data.model.ComplianceDivision
import com.example.data.model.ComplianceReport
import com.example.data.model.DirectorateUser
import com.example.data.model.GroupReportMember
import com.example.data.model.MobileAppConfig
import com.example.data.model.ReportResolutionStatus
import com.example.data.model.StaffMember
import com.example.data.model.StorageProviderConfig
import com.example.data.model.SubjectItem
import com.example.data.model.SystemSettings
import com.example.data.model.TermAnalysisSnapshot
import com.example.data.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class CachedWorkspacePayload(
    val reports: List<ComplianceReport>,
    val staffList: List<StaffMember>,
    val classes: List<Classroom>,
    val subjects: List<SubjectItem>,
    val serverCustomChecks: List<ComplianceCheck>,
    val directorateUsers: List<DirectorateUser>,
    val systemSettings: SystemSettings?,
    val termSnapshots: List<TermAnalysisSnapshot>,
    val storageProviderConfig: StorageProviderConfig?,
    val mobileAppConfig: MobileAppConfig?,
    val lastFullSyncAtMs: Long,
    val lastReportsSyncAtMs: Long,
    val lastCatalogSyncAtMs: Long,
    val lastReportFingerprint: String
)

/**
 * Encrypted, user-isolated local workspace cache stored in Room (`workspace_cache`).
 * Ensures full offline viewing of reports, analytics, roster, directory, and settings
 * while drastically reducing Supabase egress by eliminating redundant network downloads.
 */
class OfflineCacheRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).workspaceCacheDao()
    private val vault = EncryptedAuthStore(context)

    suspend fun loadWorkspaceCache(userId: String): CachedWorkspacePayload? = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext null
        val entity = dao.getForUser(userId) ?: return@withContext null

        val reports = decodeReports(decryptOrBlank(entity.reportsJson))
        val staff = decodeStaff(decryptOrBlank(entity.staffJson))
        val classes = decodeClasses(decryptOrBlank(entity.classesJson))
        val subjects = decodeSubjects(decryptOrBlank(entity.subjectsJson))
        val checks = decodeChecks(decryptOrBlank(entity.checksJson))
        val users = decodeUsers(decryptOrBlank(entity.usersJson))
        val settings = decodeSettings(decryptOrBlank(entity.settingsJson))
        val snapshots = decodeSnapshots(decryptOrBlank(entity.snapshotsJson))
        val storageCfg = decodeStorageConfig(decryptOrBlank(entity.storageConfigJson))
        val mobileCfg = decodeMobileConfig(decryptOrBlank(entity.mobileConfigJson))

        CachedWorkspacePayload(
            reports = reports,
            staffList = staff,
            classes = classes,
            subjects = subjects,
            serverCustomChecks = checks,
            directorateUsers = users,
            systemSettings = settings,
            termSnapshots = snapshots,
            storageProviderConfig = storageCfg,
            mobileAppConfig = mobileCfg,
            lastFullSyncAtMs = entity.lastFullSyncAtMs,
            lastReportsSyncAtMs = entity.lastReportsSyncAtMs,
            lastCatalogSyncAtMs = entity.lastCatalogSyncAtMs,
            lastReportFingerprint = entity.lastReportFingerprint
        )
    }

    suspend fun saveFullWorkspace(
        userId: String,
        reports: List<ComplianceReport>,
        staffList: List<StaffMember>,
        classes: List<Classroom>,
        subjects: List<SubjectItem>,
        serverCustomChecks: List<ComplianceCheck>,
        directorateUsers: List<DirectorateUser>,
        systemSettings: SystemSettings,
        termSnapshots: List<TermAnalysisSnapshot>,
        storageProviderConfig: StorageProviderConfig?,
        mobileAppConfig: MobileAppConfig?,
        lastFullSyncAtMs: Long,
        lastReportsSyncAtMs: Long,
        lastCatalogSyncAtMs: Long,
        lastReportFingerprint: String
    ) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        val entity = WorkspaceCacheEntity(
            userId = userId,
            reportsJson = vault.encryptDraftText(encodeReports(reports)),
            staffJson = vault.encryptDraftText(encodeStaff(staffList)),
            classesJson = vault.encryptDraftText(encodeClasses(classes)),
            subjectsJson = vault.encryptDraftText(encodeSubjects(subjects)),
            checksJson = vault.encryptDraftText(encodeChecks(serverCustomChecks)),
            usersJson = vault.encryptDraftText(encodeUsers(directorateUsers)),
            settingsJson = vault.encryptDraftText(encodeSettings(systemSettings)),
            snapshotsJson = vault.encryptDraftText(encodeSnapshots(termSnapshots)),
            storageConfigJson = storageProviderConfig?.let { vault.encryptDraftText(encodeStorageConfig(it)) }.orEmpty(),
            mobileConfigJson = mobileAppConfig?.let { vault.encryptDraftText(encodeMobileConfig(it)) }.orEmpty(),
            lastFullSyncAtMs = lastFullSyncAtMs,
            lastReportsSyncAtMs = lastReportsSyncAtMs,
            lastCatalogSyncAtMs = lastCatalogSyncAtMs,
            lastReportFingerprint = lastReportFingerprint
        )
        dao.upsert(entity)
    }

    suspend fun saveReportsOnly(
        userId: String,
        reports: List<ComplianceReport>,
        syncedAtMs: Long? = null,
        fingerprint: String? = null
    ) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        val existing = dao.getForUser(userId) ?: WorkspaceCacheEntity(userId = userId)
        val updated = existing.copy(
            reportsJson = vault.encryptDraftText(encodeReports(reports)),
            lastReportsSyncAtMs = syncedAtMs ?: existing.lastReportsSyncAtMs,
            lastReportFingerprint = fingerprint ?: existing.lastReportFingerprint
        )
        dao.upsert(updated)
    }

    suspend fun saveStaffOnly(userId: String, staffList: List<StaffMember>) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        val existing = dao.getForUser(userId) ?: WorkspaceCacheEntity(userId = userId)
        dao.upsert(existing.copy(staffJson = vault.encryptDraftText(encodeStaff(staffList))))
    }

    suspend fun saveUsersOnly(userId: String, users: List<DirectorateUser>) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        val existing = dao.getForUser(userId) ?: WorkspaceCacheEntity(userId = userId)
        dao.upsert(existing.copy(usersJson = vault.encryptDraftText(encodeUsers(users))))
    }

    suspend fun saveSettingsOnly(userId: String, settings: SystemSettings) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        val existing = dao.getForUser(userId) ?: WorkspaceCacheEntity(userId = userId)
        dao.upsert(existing.copy(settingsJson = vault.encryptDraftText(encodeSettings(settings))))
    }

    suspend fun saveChecksOnly(userId: String, checks: List<ComplianceCheck>) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        val existing = dao.getForUser(userId) ?: WorkspaceCacheEntity(userId = userId)
        dao.upsert(existing.copy(checksJson = vault.encryptDraftText(encodeChecks(checks))))
    }

    suspend fun clearForUser(userId: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        dao.deleteForUser(userId)
    }

    /**
     * Lightweight offline helper for notifications to verify recent report dates from cached reports
     * even when the device is completely offline.
     */
    suspend fun getLatestUserReportDatesOffline(userId: String): List<String> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext emptyList()
        val entity = dao.getForUser(userId) ?: return@withContext emptyList()
        val reports = decodeReports(decryptOrBlank(entity.reportsJson))
        reports
            .filter { it.reportedBy.equals(userId, ignoreCase = true) }
            .flatMap { r -> listOf(r.createdAt.take(10), r.incidentDate.take(10)) }
            .filter { it.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) }
    }

    private fun decryptOrBlank(cipher: String): String {
        if (cipher.isBlank()) return ""
        return vault.decryptDraftText(cipher)
    }

    private fun JSONObject.putOptString(key: String, value: String?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.optNullString(key: String): String? {
        if (isNull(key)) return null
        val v = optString(key, "")
        return if (v.isBlank() || v == "null") null else v
    }

    private fun encodeStringArray(list: List<String>): JSONArray = JSONArray().apply {
        list.forEach { put(it) }
    }

    private fun decodeStringArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "")
                if (s.isNotBlank() && s != "null") add(s)
            }
        }
    }

    // ---------------------------------------------------------------------
    // REPORTS
    // ---------------------------------------------------------------------
    private fun encodeReports(list: List<ComplianceReport>): String {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("reportNumber", r.reportNumber)
                put("reportedBy", r.reportedBy)
                put("reporterName", r.reporterName)
                put("reporterRole", r.reporterRole)
                put("incidentDate", r.incidentDate)
                put("weekNumber", r.weekNumber)
                put("term", r.term)
                putOptString("staffId", r.staffId)
                putOptString("staffName", r.staffName)
                putOptString("staffCode", r.staffCode)
                putOptString("staffCategory", r.staffCategory)
                putOptString("staffGroup", r.staffGroup)
                putOptString("role", r.role)
                putOptString("department", r.department)
                putOptString("assignedLocation", r.assignedLocation)
                putOptString("checkId", r.checkId)
                putOptString("classId", r.classId)
                putOptString("className", r.className)
                putOptString("subjectId", r.subjectId)
                putOptString("subject", r.subject)
                put("exceptionText", r.exceptionText)
                put("effectText", r.effectText)
                put("category", r.category)
                put("division", r.division)
                put("targetType", r.targetType)
                put("complianceCheck", r.complianceCheck)
                putOptString("evidenceUrl", r.evidenceUrl)
                put("evidenceType", r.evidenceType)
                putOptString("evidenceName", r.evidenceName)
                put("evidenceUrls", encodeStringArray(r.evidenceUrls))
                put("evidenceTypes", encodeStringArray(r.evidenceTypes))
                put("evidenceNotes", encodeStringArray(r.evidenceNotes))
                put("status", r.status.key)
                put("reviewStatus", r.reviewStatus)
                put("isSeen", r.isSeen)
                putOptString("seenAt", r.seenAt)
                putOptString("seenBy", r.seenBy)
                putOptString("resolutionNotes", r.resolutionNotes)
                putOptString("resolvedBy", r.resolvedBy)
                putOptString("resolvedAt", r.resolvedAt)
                put("isEdited", r.isEdited)
                putOptString("editedAt", r.editedAt)
                put("createdAt", r.createdAt)
                putOptString("schoolSection", r.schoolSection)
                putOptString("disciplineGroup", r.disciplineGroup)
                putOptString("session", r.session)
                put("isGroupReport", r.isGroupReport)
                put("groupMembers", encodeGroupMembers(r.groupMembers))
                put("exceptionTags", encodeStringArray(r.exceptionTags))
            })
        }
        return arr.toString()
    }

    private fun encodeGroupMembers(members: List<GroupReportMember>): JSONArray = JSONArray().apply {
        members.forEach { m ->
            put(JSONObject().apply {
                put("staff_id", m.staffId)
                put("staff_name", m.staffName)
                put("staff_code", m.staffCode)
                put("staff_group", m.staffGroup)
                putOptString("class_id", m.classId)
                put("class_name", m.className)
                putOptString("subject_id", m.subjectId)
                put("subject_name", m.subjectName)
            })
        }
    }

    private fun decodeGroupMembers(arr: JSONArray?): List<GroupReportMember> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val staffId = o.optString("staff_id", "").trim()
                val staffName = o.optString("staff_name", "").trim()
                if (staffId.isBlank() && staffName.isBlank()) continue
                add(
                    GroupReportMember(
                        staffId = staffId,
                        staffName = staffName,
                        staffCode = o.optString("staff_code", ""),
                        staffGroup = o.optString("staff_group", ""),
                        classId = o.optNullString("class_id") ?: "NONE",
                        className = o.optString("class_name", "None").ifBlank { "None" },
                        subjectId = o.optNullString("subject_id"),
                        subjectName = o.optString("subject_name", "")
                    )
                )
            }
        }
    }

    private fun decodeReports(raw: String): List<ComplianceReport> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val decodedMembers = decodeGroupMembers(o.optJSONArray("groupMembers"))
                    add(
                        ComplianceReport(
                            id = o.optString("id"),
                            reportNumber = o.optString("reportNumber"),
                            reportedBy = o.optString("reportedBy"),
                            reporterName = o.optString("reporterName"),
                            reporterRole = o.optString("reporterRole", "standard"),
                            incidentDate = o.optString("incidentDate"),
                            weekNumber = o.optInt("weekNumber", 1),
                            term = o.optString("term", "First Term"),
                            staffId = o.optNullString("staffId"),
                            staffName = o.optNullString("staffName"),
                            staffCode = o.optNullString("staffCode"),
                            staffCategory = o.optNullString("staffCategory"),
                            staffGroup = o.optNullString("staffGroup"),
                            role = o.optNullString("role"),
                            department = o.optNullString("department"),
                            assignedLocation = o.optNullString("assignedLocation"),
                            checkId = o.optNullString("checkId"),
                            classId = o.optNullString("classId"),
                            className = o.optNullString("className"),
                            subjectId = o.optNullString("subjectId"),
                            subject = o.optNullString("subject"),
                            exceptionText = o.optString("exceptionText"),
                            effectText = o.optString("effectText"),
                            category = o.optString("category"),
                            division = o.optString("division"),
                            targetType = o.optString("targetType", "staff"),
                            complianceCheck = o.optString("complianceCheck"),
                            evidenceUrl = o.optNullString("evidenceUrl"),
                            evidenceType = o.optString("evidenceType", "none"),
                            evidenceName = o.optNullString("evidenceName"),
                            evidenceUrls = decodeStringArray(o.optJSONArray("evidenceUrls")),
                            evidenceTypes = decodeStringArray(o.optJSONArray("evidenceTypes")),
                            evidenceNotes = decodeStringArray(o.optJSONArray("evidenceNotes")),
                            status = ReportResolutionStatus.fromKey(o.optString("status")),
                            reviewStatus = o.optString("reviewStatus", "UNSEEN"),
                            isSeen = o.optBoolean("isSeen", false),
                            seenAt = o.optNullString("seenAt"),
                            seenBy = o.optNullString("seenBy"),
                            resolutionNotes = o.optNullString("resolutionNotes"),
                            resolvedBy = o.optNullString("resolvedBy"),
                            resolvedAt = o.optNullString("resolvedAt"),
                            isEdited = o.optBoolean("isEdited", false),
                            editedAt = o.optNullString("editedAt"),
                            createdAt = o.optString("createdAt"),
                            schoolSection = o.optNullString("schoolSection"),
                            disciplineGroup = o.optNullString("disciplineGroup"),
                            session = o.optNullString("session"),
                            isGroupReport = o.optBoolean("isGroupReport", false) || decodedMembers.isNotEmpty(),
                            groupMembers = decodedMembers,
                            exceptionTags = decodeStringArray(o.optJSONArray("exceptionTags"))
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    // ---------------------------------------------------------------------
    // STAFF
    // ---------------------------------------------------------------------
    private fun encodeStaff(list: List<StaffMember>): String {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("staffNumber", s.staffNumber)
                put("staffCode", s.staffCode)
                put("name", s.name)
                put("category", s.category)
                put("staffGroup", s.staffGroup)
                put("role", s.role)
                put("department", s.department)
                put("assignedLocation", s.assignedLocation)
                put("isActive", s.isActive)
                put("schoolSection", s.schoolSection)
                put("subjects", s.subjects)
            })
        }
        return arr.toString()
    }

    private fun decodeStaff(raw: String): List<StaffMember> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        StaffMember(
                            id = o.optString("id"),
                            staffNumber = o.optString("staffNumber"),
                            staffCode = o.optString("staffCode"),
                            name = o.optString("name"),
                            category = o.optString("category"),
                            staffGroup = o.optString("staffGroup"),
                            role = o.optString("role"),
                            department = o.optString("department"),
                            assignedLocation = o.optString("assignedLocation"),
                            isActive = o.optBoolean("isActive", true),
                            schoolSection = o.optString("schoolSection"),
                            subjects = o.optString("subjects")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    // ---------------------------------------------------------------------
    // CLASSES, SUBJECTS, CHECKS
    // ---------------------------------------------------------------------
    private fun encodeClasses(list: List<Classroom>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("section", c.section)
            })
        }
        return arr.toString()
    }

    private fun decodeClasses(raw: String): List<Classroom> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(Classroom(o.optString("id"), o.optString("name"), o.optString("section")))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeSubjects(list: List<SubjectItem>): String {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("department", s.department)
            })
        }
        return arr.toString()
    }

    private fun decodeSubjects(raw: String): List<SubjectItem> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(SubjectItem(o.optString("id"), o.optString("name"), o.optString("department")))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeChecks(list: List<ComplianceCheck>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("category", c.category)
                put("division", c.division.dbValue)
                put("subSection", c.subSection)
                put("defaultEffect", c.defaultEffect)
                put("severityWeight", c.severityWeight)
                put("isCustom", c.isCustom)
            })
        }
        return arr.toString()
    }

    private fun decodeChecks(raw: String): List<ComplianceCheck> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        ComplianceCheck(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            category = o.optString("category"),
                            division = ComplianceDivision.fromAny(o.optString("division")) ?: ComplianceDivision.ACADEMIC,
                            subSection = o.optString("subSection"),
                            defaultEffect = o.optString("defaultEffect"),
                            severityWeight = o.optInt("severityWeight", 2),
                            isCustom = o.optBoolean("isCustom", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    // ---------------------------------------------------------------------
    // DIRECTORATE USERS
    // ---------------------------------------------------------------------
    private fun encodeUsers(list: List<DirectorateUser>): String {
        val arr = JSONArray()
        list.forEach { u ->
            arr.put(JSONObject().apply {
                put("userId", u.userId)
                put("email", u.email)
                put("displayName", u.displayName)
                put("staffNumber", u.staffNumber)
                put("role", u.role.key)
                put("status", u.status)
                put("isVerified", u.isVerified)
                put("isOwner", u.isOwner)
                put("isFounder", u.isFounder)
                putOptString("createdAt", u.createdAt)
                put("avatarUrl", u.avatarUrl)
            })
        }
        return arr.toString()
    }

    private fun decodeUsers(raw: String): List<DirectorateUser> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        DirectorateUser(
                            userId = o.optString("userId"),
                            email = o.optString("email"),
                            displayName = o.optString("displayName"),
                            staffNumber = o.optString("staffNumber"),
                            role = UserRole.fromKey(o.optString("role")),
                            status = o.optString("status", "Active"),
                            isVerified = o.optBoolean("isVerified", true),
                            isOwner = o.optBoolean("isOwner", false),
                            isFounder = o.optBoolean("isFounder", false),
                            createdAt = o.optNullString("createdAt"),
                            avatarUrl = o.optString("avatarUrl")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    // ---------------------------------------------------------------------
    // SYSTEM SETTINGS & CONFIGS
    // ---------------------------------------------------------------------
    private fun encodeSettings(s: SystemSettings): String = JSONObject().apply {
        put("academicSession", s.academicSession)
        put("activeTerm", s.activeTerm)
        put("termStartDate", s.termStartDate)
        put("manualWeekOverride", s.manualWeekOverride)
    }.toString()

    private fun decodeSettings(raw: String): SystemSettings? {
        if (raw.isBlank()) return null
        return runCatching {
            val o = JSONObject(raw)
            SystemSettings(
                academicSession = o.optString("academicSession", "2025/2026"),
                activeTerm = o.optString("activeTerm", "First Term"),
                termStartDate = o.optString("termStartDate", "2025-09-08"),
                manualWeekOverride = o.optString("manualWeekOverride", "none")
            )
        }.getOrNull()
    }

    private fun encodeStorageConfig(cfg: StorageProviderConfig): String = JSONObject().apply {
        put("provider", cfg.provider)
        put("bucketName", cfg.bucketName)
        put("isPrivate", cfg.isPrivate)
    }.toString()

    private fun decodeStorageConfig(raw: String): StorageProviderConfig? {
        if (raw.isBlank()) return null
        return runCatching {
            val o = JSONObject(raw)
            StorageProviderConfig(
                provider = o.optString("provider", "backblaze_b2"),
                bucketName = o.optString("bucketName", "JC-Best-Compliance"),
                isPrivate = o.optBoolean("isPrivate", true)
            )
        }.getOrNull()
    }

    private fun encodeMobileConfig(cfg: MobileAppConfig): String = JSONObject().apply {
        put("latestVersion", cfg.latestVersion)
        put("downloadUrl", cfg.downloadUrl)
        put("releaseNotes", cfg.releaseNotes)
        put("minVersion", cfg.minVersion)
    }.toString()

    private fun decodeMobileConfig(raw: String): MobileAppConfig? {
        if (raw.isBlank()) return null
        return runCatching {
            val o = JSONObject(raw)
            MobileAppConfig(
                latestVersion = o.optString("latestVersion"),
                downloadUrl = o.optString("downloadUrl"),
                releaseNotes = o.optString("releaseNotes"),
                minVersion = o.optString("minVersion")
            )
        }.getOrNull()
    }

    // ---------------------------------------------------------------------
    // TERM ANALYSIS SNAPSHOTS
    // ---------------------------------------------------------------------
    private fun encodeSnapshots(list: List<TermAnalysisSnapshot>): String {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("session", s.session)
                put("term", s.term)
                put("snapshotVersion", s.snapshotVersion)
                putOptString("officialTermEndDate", s.officialTermEndDate)
                put("status", s.status)
                put("reconciliationStatus", s.reconciliationStatus)
                put("totalQualifyingExceptions", s.totalQualifyingExceptions)
                put("academicExceptionsCount", s.academicExceptionsCount)
                put("operationsExceptionsCount", s.operationsExceptionsCount)
                put("cdsExceptionsCount", s.cdsExceptionsCount)
                put("disciplineExceptionsCount", s.disciplineExceptionsCount)
                put("staffPopulationDenominator", s.staffPopulationDenominator)
                put("affectedStaffCount", s.affectedStaffCount)
                put("cleanStaffCount", s.cleanStaffCount)
                put("overallPerHead", s.overallPerHead.toDouble())
                put("rawFullSnapshotJson", s.rawFullSnapshotJson)
                put("rawSectionMetricsJson", s.rawSectionMetricsJson)
                put("rawDisciplineMetricsJson", s.rawDisciplineMetricsJson)
                put("rawClassMetricsJson", s.rawClassMetricsJson)
                put("rawCategoryMetricsJson", s.rawCategoryMetricsJson)
                put("rawCategoryPatternsJson", s.rawCategoryPatternsJson)
                put("rawSeverityDistributionJson", s.rawSeverityDistributionJson)
                put("rawExecutiveSummaryJson", s.rawExecutiveSummaryJson)
                put("rawComparisonsJson", s.rawComparisonsJson)
                put("rawTrendsJson", s.rawTrendsJson)
                put("createdAt", s.createdAt)
            })
        }
        return arr.toString()
    }

    private fun decodeSnapshots(raw: String): List<TermAnalysisSnapshot> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        TermAnalysisSnapshot(
                            id = o.optString("id"),
                            session = o.optString("session"),
                            term = o.optString("term"),
                            snapshotVersion = o.optInt("snapshotVersion", 1),
                            officialTermEndDate = o.optNullString("officialTermEndDate"),
                            status = o.optString("status", "DRAFT"),
                            reconciliationStatus = o.optString("reconciliationStatus", "PENDING"),
                            totalQualifyingExceptions = o.optInt("totalQualifyingExceptions", 0),
                            academicExceptionsCount = o.optInt("academicExceptionsCount", 0),
                            operationsExceptionsCount = o.optInt("operationsExceptionsCount", 0),
                            cdsExceptionsCount = o.optInt("cdsExceptionsCount", 0),
                            disciplineExceptionsCount = o.optInt("disciplineExceptionsCount", 0),
                            staffPopulationDenominator = o.optInt("staffPopulationDenominator", 0),
                            affectedStaffCount = o.optInt("affectedStaffCount", 0),
                            cleanStaffCount = o.optInt("cleanStaffCount", 0),
                            overallPerHead = o.optDouble("overallPerHead", 0.0).toFloat(),
                            rawFullSnapshotJson = o.optString("rawFullSnapshotJson"),
                            rawSectionMetricsJson = o.optString("rawSectionMetricsJson"),
                            rawDisciplineMetricsJson = o.optString("rawDisciplineMetricsJson"),
                            rawClassMetricsJson = o.optString("rawClassMetricsJson"),
                            rawCategoryMetricsJson = o.optString("rawCategoryMetricsJson"),
                            rawCategoryPatternsJson = o.optString("rawCategoryPatternsJson"),
                            rawSeverityDistributionJson = o.optString("rawSeverityDistributionJson"),
                            rawExecutiveSummaryJson = o.optString("rawExecutiveSummaryJson"),
                            rawComparisonsJson = o.optString("rawComparisonsJson"),
                            rawTrendsJson = o.optString("rawTrendsJson"),
                            createdAt = o.optString("createdAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
