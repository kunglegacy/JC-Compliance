package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.AcademicWeekEngine
import com.example.data.local.ComplianceTaxonomy
import com.example.data.model.AppNotificationRecord
import com.example.data.model.Classroom
import com.example.data.model.ComplianceCheck
import com.example.data.model.ComplianceDivision
import com.example.data.model.ComplianceReport
import com.example.data.model.DirectorateUser
import com.example.data.model.ReportResolutionStatus
import com.example.data.model.StaffMember
import com.example.data.model.SubjectItem
import com.example.data.model.SupabaseConfig
import com.example.data.model.SystemSettings
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.UUID

class ComplianceRepository(
    private val context: Context,
    private val api: SupabaseApiClient = SupabaseApiClient()
) {
    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private val missingColRegex1 = Regex("'([a-zA-Z0-9_]+)'\\s+column", RegexOption.IGNORE_CASE)
    private val missingColRegex2 = Regex("column\\s+\"?([a-zA-Z0-9_]+)\"?", RegexOption.IGNORE_CASE)

    private suspend fun requestWithMissingColumnRetry(
        config: SupabaseConfig,
        method: String,
        path: String,
        payloadObj: JSONObject,
        token: String?,
        extraHeaders: Map<String, String> = emptyMap(),
        maxRetries: Int = 8
    ): ApiResult {
        val current = JSONObject(payloadObj.toString())
        var res = api.requestJson(config, method, path, current.toString(), token, extraHeaders)
        var attempts = 0
        while (!res.isSuccess && attempts < maxRetries) {
            val msg = res.errorMessage.orEmpty()
            val match = missingColRegex1.find(msg) ?: missingColRegex2.find(msg)
            val missingCol = match?.groupValues?.getOrNull(1)?.trim()
            if (!missingCol.isNullOrBlank() && current.has(missingCol)) {
                current.remove(missingCol)
                attempts++
                res = api.requestJson(config, method, path, current.toString(), token, extraHeaders)
            } else {
                break
            }
        }
        return res
    }

    private fun JSONObject.optCleanString(key: String): String {
        if (isNull(key)) return ""
        val raw = optString(key, "").trim()
        return if (raw.equals("null", ignoreCase = true)) "" else raw
    }

    private fun isOwnerEmail(email: String): Boolean {
        val e = email.trim().lowercase()
        return e == "ifeanyiof360@gmail.com" || e == "ifeanyiof777@gmail.com" || e.startsWith("ifeanyiof")
    }

    suspend fun signIn(config: SupabaseConfig, email: String, password: String): Result<UserSession> {
        val cleanEmail = email.trim().lowercase()
        val payload = JSONObject().apply {
            put("email", cleanEmail)
            put("password", password)
        }.toString()

        var res = api.requestJson(config, "POST", "/auth/v1/token?grant_type=password", payload)
        if (!res.isSuccess && res.errorMessage.orEmpty().lowercase().contains("email not confirmed")) {
            val rpcPayload = JSONObject().apply { put("target_email", cleanEmail) }.toString()
            api.requestJson(config, "POST", "/rest/v1/rpc/confirm_user_email", rpcPayload)
            res = api.requestJson(config, "POST", "/auth/v1/token?grant_type=password", payload)
        }
        if (!res.isSuccess) {
            return Result.failure(Exception(res.errorMessage ?: "Invalid credentials"))
        }

        val obj = api.parseJsonObjectSafe(res.body)
        val accessToken = obj.optString("access_token")
        val refreshToken = obj.optString("refresh_token")
        val userObj = obj.optJSONObject("user") ?: JSONObject()
        val userId = userObj.optString("id")
        val resolvedEmail = userObj.optString("email", cleanEmail)
        val meta = userObj.optJSONObject("user_metadata") ?: JSONObject()
        val metaName = meta.optString("display_name").ifBlank { meta.optString("full_name") }
        val metaAvatar = meta.optString("avatar_url").takeIf { it.isNotBlank() && it != "null" }.orEmpty()

        val roleInfo = fetchUserRoleRecord(config, accessToken, userId, resolvedEmail, metaName, metaAvatar)
        if (!roleInfo.isOwner && (
                !roleInfo.isVerified ||
                    roleInfo.status.equals("suspended", ignoreCase = true) ||
                    roleInfo.status.equals("inactive", ignoreCase = true)
                )
        ) {
            return Result.failure(Exception("This account has been suspended. Contact the Directorate Administrator."))
        }

        val rawAvatar = roleInfo.avatarUrl.ifBlank { metaAvatar }
        val resolvedAvatar = resolveAvatarDisplayUrl(config, accessToken, rawAvatar)

        return Result.success(
            UserSession(
                userId = userId,
                email = resolvedEmail,
                accessToken = accessToken,
                refreshToken = refreshToken,
                displayName = roleInfo.displayName.ifBlank { resolvedEmail.substringBefore("@") },
                staffNumber = roleInfo.staffNumber,
                role = roleInfo.role,
                status = roleInfo.status,
                isVerified = roleInfo.isVerified,
                isOwner = roleInfo.isOwner,
                avatarUrl = resolvedAvatar
            )
        )
    }

    suspend fun verifyPassword(config: SupabaseConfig, email: String, password: String): Boolean {
        val payload = JSONObject().apply {
            put("email", email.trim().lowercase())
            put("password", password)
        }.toString()
        val res = api.requestJson(config, "POST", "/auth/v1/token?grant_type=password", payload)
        return res.isSuccess
    }

    suspend fun refreshSession(config: SupabaseConfig, current: UserSession): Result<UserSession> {
        if (current.refreshToken.isBlank()) return Result.failure(Exception("No refresh token"))
        val payload = JSONObject().apply { put("refresh_token", current.refreshToken) }.toString()
        val res = api.requestJson(config, "POST", "/auth/v1/token?grant_type=refresh_token", payload)
        if (!res.isSuccess) return Result.failure(Exception(res.errorMessage ?: "Session expired"))
        val obj = api.parseJsonObjectSafe(res.body)
        val access = obj.optString("access_token")
        val refresh = obj.optString("refresh_token", current.refreshToken)
        if (access.isBlank()) return Result.failure(Exception("Session expired"))
        return Result.success(current.copy(accessToken = access, refreshToken = refresh))
    }

    suspend fun sendPasswordResetEmail(config: SupabaseConfig, email: String): Result<Unit> {
        val cleanEmail = email.trim().lowercase()
        val payload = JSONObject().apply { put("email", cleanEmail) }.toString()
        val res = api.requestJson(config, "POST", "/auth/v1/recover", payload)
        return if (res.isSuccess) Result.success(Unit)
        else Result.failure(Exception(res.errorMessage ?: "Failed to send password reset email."))
    }

    suspend fun updatePassword(
        config: SupabaseConfig,
        accessToken: String,
        newPassword: String
    ): Result<Unit> {
        val payload = JSONObject().apply { put("password", newPassword) }.toString()
        val res = api.requestJson(config, "PUT", "/auth/v1/user", payload, accessToken)
        return if (res.isSuccess) Result.success(Unit)
        else Result.failure(Exception(res.errorMessage ?: "Unable to update password."))
    }

    suspend fun updateDisplayNameOnServer(
        config: SupabaseConfig,
        session: UserSession,
        newDisplayName: String
    ) {
        val userPayload = JSONObject().apply {
            put("data", JSONObject().apply { put("display_name", newDisplayName.trim()) })
        }.toString()
        api.requestJson(config, "PUT", "/auth/v1/user", userPayload, session.accessToken)

        val rolePayload = JSONObject().apply { put("display_name", newDisplayName.trim()) }.toString()
        api.requestJson(
            config,
            "PATCH",
            "/rest/v1/user_roles?user_id=eq.${enc(session.userId)}",
            rolePayload,
            session.accessToken
        )
    }

    suspend fun uploadProfileAvatar(
        config: SupabaseConfig,
        session: UserSession,
        avatarFile: File
    ): Result<String> = withContext(Dispatchers.IO) {
        val bytes = runCatching { avatarFile.readBytes() }.getOrElse {
            return@withContext Result.failure(Exception("Unable to read compressed profile photo."))
        }
        val objectPath = "evidence/avatars/${session.userId}/avatar_${System.currentTimeMillis()}.jpg"
        val uploadRes = api.uploadBinary(config, "compliance-evidence", objectPath, bytes, "image/jpeg", session.accessToken)
        if (!uploadRes.isSuccess) {
            return@withContext Result.failure(Exception(uploadRes.errorMessage ?: "Unable to upload profile avatar."))
        }

        // Persist canonical storage path in auth user_metadata and user_roles
        val userPayload = JSONObject().apply {
            put("data", JSONObject().apply { put("avatar_url", objectPath) })
        }.toString()
        api.requestJson(config, "PUT", "/auth/v1/user", userPayload, session.accessToken)

        val rolePayload = JSONObject().apply { put("avatar_url", objectPath) }.toString()
        api.requestJson(
            config,
            "PATCH",
            "/rest/v1/user_roles?user_id=eq.${enc(session.userId)}",
            rolePayload,
            session.accessToken
        )

        val signedUrl = resolveAvatarDisplayUrl(config, session.accessToken, objectPath)
        Result.success(signedUrl.ifBlank { objectPath })
    }

    suspend fun fetchProfileAvatarUrl(
        config: SupabaseConfig,
        session: UserSession
    ): String {
        val roleRes = api.requestJson(
            config,
            "GET",
            "/rest/v1/user_roles?user_id=eq.${enc(session.userId)}&select=avatar_url",
            accessToken = session.accessToken
        )
        var rawPath = ""
        if (roleRes.isSuccess) {
            val arr = api.parseJsonArraySafe(roleRes.body)
            if (arr.length() > 0) {
                rawPath = arr.getJSONObject(0).optString("avatar_url").takeIf { it != "null" }.orEmpty()
            }
        }
        if (rawPath.isBlank()) {
            val userRes = api.requestJson(config, "GET", "/auth/v1/user", accessToken = session.accessToken)
            if (userRes.isSuccess) {
                val uObj = api.parseJsonObjectSafe(userRes.body)
                val meta = uObj.optJSONObject("user_metadata") ?: JSONObject()
                rawPath = meta.optString("avatar_url").takeIf { it != "null" }.orEmpty()
            }
        }
        return resolveAvatarDisplayUrl(config, session.accessToken, rawPath)
    }

    private suspend fun resolveAvatarDisplayUrl(
        config: SupabaseConfig,
        token: String,
        rawPath: String
    ): String {
        val trimmed = rawPath.trim()
        if (trimmed.isBlank() || trimmed == "null") return ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (trimmed.startsWith("evidence/")) {
            return getSignedEvidenceUrl(config, token, trimmed).getOrDefault(trimmed)
        }
        return trimmed
    }

    suspend fun signOut(config: SupabaseConfig, accessToken: String) {
        api.requestJson(config, "POST", "/auth/v1/logout?scope=local", "{}", accessToken)
    }

    private suspend fun fetchUserRoleRecord(
        config: SupabaseConfig,
        accessToken: String,
        userId: String,
        email: String,
        fallbackName: String,
        fallbackAvatar: String = ""
    ): DirectorateUser {
        val isOwner = isOwnerEmail(email)
        val res = api.requestJson(
            config,
            "GET",
            "/rest/v1/user_roles?user_id=eq.${enc(userId)}&select=*",
            accessToken = accessToken
        )
        if (res.isSuccess) {
            val arr = api.parseJsonArraySafe(res.body)
            if (arr.length() > 0) {
                val obj = arr.getJSONObject(0)
                val roleStr = obj.optCleanString("role").ifBlank { "standard" }
                val effectiveRole = if (isOwner) UserRole.SUPER_ADMIN else UserRole.fromKey(roleStr)
                val verified = if (obj.has("is_verified") && !obj.isNull("is_verified")) obj.optBoolean("is_verified", true) else true
                val rawStatus = obj.optCleanString("status")
                val resolvedStatus = when {
                    !verified -> "Inactive"
                    rawStatus.isNotBlank() -> rawStatus
                    else -> "Active"
                }
                return DirectorateUser(
                    userId = userId,
                    email = obj.optCleanString("email").ifBlank { email },
                    displayName = obj.optCleanString("display_name").ifBlank { fallbackName },
                    staffNumber = obj.optCleanString("staff_number"),
                    role = effectiveRole,
                    status = resolvedStatus,
                    isVerified = verified,
                    isOwner = isOwner || obj.optBoolean("is_owner", false),
                    isFounder = obj.optBoolean("is_founder", false),
                    avatarUrl = obj.optCleanString("avatar_url").ifBlank { fallbackAvatar }
                )
            }
        }
        return DirectorateUser(
            userId = userId,
            email = email,
            displayName = fallbackName,
            staffNumber = "",
            role = if (isOwner) UserRole.SUPER_ADMIN else UserRole.STANDARD,
            status = "active",
            isVerified = true,
            isOwner = isOwner,
            isFounder = isOwner,
            avatarUrl = fallbackAvatar
        )
    }

    // ---------------------------------------------------------------------
    // SYSTEM SETTINGS
    // ---------------------------------------------------------------------
    suspend fun fetchSettings(config: SupabaseConfig, token: String): SystemSettings {
        val kvRes = api.requestJson(config, "GET", "/rest/v1/system_settings?select=*", accessToken = token)
        if (kvRes.isSuccess) {
            val arr = api.parseJsonArraySafe(kvRes.body)
            if (arr.length() > 0) {
                val map = mutableMapOf<String, String>()
                for (i in 0 until arr.length()) {
                    val row = arr.optJSONObject(i) ?: continue
                    val k = row.optCleanString("key")
                    val v = row.optCleanString("value")
                    if (k.isNotBlank()) map[k] = v
                }
                return SystemSettings(
                    academicSession = map["current_session"].orEmpty().ifBlank { "2025/2026" },
                    activeTerm = map["current_term"].orEmpty().ifBlank { "First Term" },
                    termStartDate = map["term_start_date"].orEmpty().ifBlank { "2025-09-08" },
                    manualWeekOverride = map["manual_week_override"].orEmpty().ifBlank { "none" }
                )
            }
        }

        val res = api.requestJson(config, "GET", "/rest/v1/settings?select=*&limit=1", accessToken = token)
        if (res.isSuccess) {
            val arr = api.parseJsonArraySafe(res.body)
            if (arr.length() > 0) {
                val obj = arr.getJSONObject(0)
                return SystemSettings(
                    academicSession = obj.optCleanString("academic_session").ifBlank { "2025/2026" },
                    activeTerm = obj.optCleanString("active_term").ifBlank { "First Term" },
                    termStartDate = obj.optCleanString("term_start_date").ifBlank { "2025-09-08" },
                    manualWeekOverride = obj.optCleanString("manual_week_override").ifBlank { "none" }
                )
            }
        }
        return SystemSettings()
    }

    suspend fun updateSettings(config: SupabaseConfig, token: String, settings: SystemSettings): Result<SystemSettings> {
        val kvRows = JSONArray().apply {
            put(JSONObject().apply { put("key", "current_session"); put("value", settings.academicSession) })
            put(JSONObject().apply { put("key", "current_term"); put("value", settings.activeTerm) })
            put(JSONObject().apply { put("key", "term_start_date"); put("value", settings.termStartDate) })
            put(JSONObject().apply { put("key", "manual_week_override"); put("value", settings.manualWeekOverride) })
        }
        val kvRes = api.requestJson(
            config,
            "POST",
            "/rest/v1/system_settings?on_conflict=key",
            kvRows.toString(),
            token,
            mapOf("Prefer" to "resolution=merge-duplicates,return=representation")
        )
        if (kvRes.isSuccess) {
            return Result.success(settings)
        }

        val kvErr = kvRes.errorMessage.orEmpty().lowercase()
        val missingKvTable = kvRes.code == 404 ||
            kvErr.contains("system_settings") ||
            kvErr.contains("does not exist") ||
            kvErr.contains("schema cache")
        if (!missingKvTable) {
            return Result.failure(Exception(kvRes.errorMessage ?: "Failed to update settings."))
        }

        val check = api.requestJson(config, "GET", "/rest/v1/settings?select=id&limit=1", accessToken = token)
        val payload = JSONObject().apply {
            put("academic_session", settings.academicSession)
            put("active_term", settings.activeTerm)
            put("term_start_date", settings.termStartDate)
            put("manual_week_override", settings.manualWeekOverride)
        }.toString()

        val existingId = if (check.isSuccess) {
            api.parseJsonArraySafe(check.body).optJSONObject(0)?.optString("id").orEmpty()
        } else ""

        val res = if (existingId.isNotBlank()) {
            api.requestJson(
                config,
                "PATCH",
                "/rest/v1/settings?id=eq.${enc(existingId)}",
                payload,
                token,
                mapOf("Prefer" to "return=representation")
            )
        } else {
            api.requestJson(
                config,
                "POST",
                "/rest/v1/settings",
                payload,
                token,
                mapOf("Prefer" to "return=representation")
            )
        }
        return if (res.isSuccess) Result.success(settings)
        else Result.failure(Exception(res.errorMessage ?: "Failed to update settings."))
    }

    suspend fun purgeEvidenceBucket(config: SupabaseConfig, token: String): Result<Int> {
        val listPayload = JSONObject().apply {
            put("prefix", "evidence")
            put("limit", 1000)
        }.toString()
        val listRes = api.requestJson(config, "POST", "/storage/v1/object/list/compliance-evidence", listPayload, token)
        if (!listRes.isSuccess) {
            return Result.failure(Exception(listRes.errorMessage ?: "Unable to inspect evidence bucket."))
        }
        val items = api.parseJsonArraySafe(listRes.body)
        val prefixes = mutableListOf<String>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val name = item.optString("name")
            if (name.isNotBlank()) {
                prefixes.add("evidence/$name")
            }
        }
        if (prefixes.isEmpty()) return Result.success(0)

        var deleted = 0
        for (prefix in prefixes) {
            val subPayload = JSONObject().apply {
                put("prefix", prefix)
                put("limit", 200)
            }.toString()
            val subRes = api.requestJson(config, "POST", "/storage/v1/object/list/compliance-evidence", subPayload, token)
            if (subRes.isSuccess) {
                val subArr = api.parseJsonArraySafe(subRes.body)
                val pathsToDelete = mutableListOf<String>()
                for (j in 0 until subArr.length()) {
                    val subItem = subArr.optJSONObject(j) ?: continue
                    val n = subItem.optString("name")
                    if (n.isNotBlank()) pathsToDelete.add("$prefix/$n")
                }
                if (pathsToDelete.isNotEmpty()) {
                    val delPayload = JSONObject().apply {
                        put("prefixes", JSONArray(pathsToDelete))
                    }.toString()
                    val delRes = api.requestJson(config, "DELETE", "/storage/v1/object/compliance-evidence", delPayload, token)
                    if (delRes.isSuccess) deleted += pathsToDelete.size
                }
            }
        }
        return Result.success(deleted)
    }

    // ---------------------------------------------------------------------
    // STAFF DIRECTORY
    // ---------------------------------------------------------------------
    suspend fun fetchStaff(config: SupabaseConfig, token: String): List<StaffMember> {
        val res = api.requestJson(config, "GET", "/rest/v1/staff?select=*&order=name.asc", accessToken = token)
        if (!res.isSuccess) return emptyList()
        val arr = api.parseJsonArraySafe(res.body)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(parseStaff(o))
            }
        }
    }

    private fun parseStaff(o: JSONObject): StaffMember {
        val dept = o.optString("department").ifBlank {
            if (o.optString("category").contains("Non", ignoreCase = true)) "Operation" else "Academic"
        }
        val cat = o.optString("category").ifBlank {
            if (dept.equals("Operation", ignoreCase = true)) "Non-Academic" else "Academic"
        }
        val code = o.optString("staff_code").ifBlank { o.optString("staff_number") }
        val activeFlag = if (o.has("is_active") && !o.isNull("is_active")) {
            o.optBoolean("is_active", true)
        } else {
            !o.optString("status").equals("Archived", ignoreCase = true)
        }
        return StaffMember(
            id = o.optString("id"),
            staffNumber = o.optString("staff_number").ifBlank { code },
            staffCode = code,
            name = o.optString("name"),
            category = cat,
            staffGroup = o.optString("staff_group").ifBlank { dept },
            role = o.optString("role").ifBlank { "Teacher" },
            department = dept,
            assignedLocation = o.optString("assigned_location"),
            isActive = activeFlag
        )
    }

    suspend fun upsertStaff(
        config: SupabaseConfig,
        token: String,
        staff: StaffMember,
        isNew: Boolean
    ): Result<StaffMember> {
        val cleanName = staff.name.trim()
        val cleanCode = staff.staffCode.trim()
        if (cleanName.isBlank()) {
            return Result.failure(Exception("Full legal staff name is required."))
        }
        if (cleanCode.isBlank()) {
            return Result.failure(Exception("Designated staff code is required."))
        }

        // Verify unique staff_code across staff table (matching supabase.js)
        val dupCheck = api.requestJson(
            config,
            "GET",
            "/rest/v1/staff?select=id,staff_code&staff_code=ilike.${enc(cleanCode)}",
            accessToken = token
        )
        if (dupCheck.isSuccess) {
            val dupArr = api.parseJsonArraySafe(dupCheck.body)
            for (i in 0 until dupArr.length()) {
                val existingId = dupArr.optJSONObject(i)?.optCleanString("id").orEmpty()
                if (existingId.isNotBlank() && (isNew || !existingId.equals(staff.id, ignoreCase = true))) {
                    return Result.failure(
                        Exception("Unique Constraint Violated: Staff code '$cleanCode' is already assigned to a personnel record.")
                    )
                }
            }
        }

        val nowIso = java.time.Instant.now().toString()
        val dept = if (staff.department.contains("oper", ignoreCase = true)) "Operations" else "Academic"
        val loc = staff.assignedLocation.trim().ifBlank {
            if (dept == "Academic") "Classroom" else "Duty Station"
        }
        val payloadObj = JSONObject().apply {
            put("name", cleanName)
            put("staff_code", cleanCode)
            put("department", dept)
            put("staff_group", staff.staffGroup.ifBlank { if (dept == "Academic") "Languages" else "Operations" })
            put("role", staff.role.ifBlank { if (dept == "Academic") "Subject Teacher" else "Operations Officer" })
            put("assigned_location", loc)
            put("status", if (staff.isActive) "Active" else "Archived")
            put("updated_at", nowIso)
            if (isNew) {
                put("created_at", nowIso)
            }
        }

        val res = if (isNew) {
            requestWithMissingColumnRetry(
                config,
                "POST",
                "/rest/v1/staff",
                payloadObj,
                token,
                mapOf("Prefer" to "return=representation")
            )
        } else {
            requestWithMissingColumnRetry(
                config,
                "PATCH",
                "/rest/v1/staff?id=eq.${enc(staff.id)}",
                payloadObj,
                token,
                mapOf("Prefer" to "return=representation")
            )
        }

        if (!res.isSuccess) {
            return Result.failure(Exception(res.errorMessage ?: "Failed to save staff member."))
        }
        val arr = api.parseJsonArraySafe(res.body)
        return if (arr.length() > 0) {
            Result.success(parseStaff(arr.getJSONObject(0)))
        } else {
            Result.failure(Exception("Staff record update was blocked by database permissions or record was not found."))
        }
    }

    suspend fun setStaffActiveStatus(
        config: SupabaseConfig,
        token: String,
        staffId: String,
        isActive: Boolean
    ): Result<Unit> {
        val payloadObj = JSONObject().apply {
            put("status", if (isActive) "Active" else "Archived")
            put("updated_at", java.time.Instant.now().toString())
        }
        val res = requestWithMissingColumnRetry(
            config,
            "PATCH",
            "/rest/v1/staff?id=eq.${enc(staffId)}",
            payloadObj,
            token,
            mapOf("Prefer" to "return=representation")
        )
        if (!res.isSuccess) {
            return Result.failure(Exception(res.errorMessage ?: "Unable to update staff status."))
        }
        val arr = api.parseJsonArraySafe(res.body)
        return if (arr.length() > 0) Result.success(Unit)
        else Result.failure(Exception("Staff status update was blocked by database permissions or record not found."))
    }

    // ---------------------------------------------------------------------
    // CLASSES, SUBJECTS & CHECKS
    // ---------------------------------------------------------------------
    suspend fun fetchClasses(config: SupabaseConfig, token: String): List<Classroom> {
        val res = api.requestJson(config, "GET", "/rest/v1/classes?select=*&order=name.asc", accessToken = token)
        if (!res.isSuccess) return ComplianceTaxonomy.defaultClasses
        val arr = api.parseJsonArraySafe(res.body)
        if (arr.length() == 0) return ComplianceTaxonomy.defaultClasses
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    Classroom(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        section = o.optString("section")
                    )
                )
            }
        }
    }

    suspend fun fetchSubjects(config: SupabaseConfig, token: String): List<SubjectItem> {
        val res = api.requestJson(config, "GET", "/rest/v1/subjects?select=*&order=name.asc", accessToken = token)
        if (!res.isSuccess) return ComplianceTaxonomy.defaultSubjects
        val arr = api.parseJsonArraySafe(res.body)
        if (arr.length() == 0) return ComplianceTaxonomy.defaultSubjects
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    SubjectItem(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        department = o.optString("department", "Academic")
                    )
                )
            }
        }
    }

    suspend fun fetchComplianceChecks(config: SupabaseConfig, token: String): List<ComplianceCheck> {
        val res = api.requestJson(config, "GET", "/rest/v1/compliance_checks?select=*", accessToken = token)
        if (!res.isSuccess) return emptyList()
        val arr = api.parseJsonArraySafe(res.body)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val div = ComplianceDivision.fromAny(o.optString("division")) ?: ComplianceDivision.ACADEMIC
                val name = o.optString("name")
                if (name.isNotBlank()) {
                    add(
                        ComplianceCheck(
                            id = o.optString("id").ifBlank { "CHK-${UUID.randomUUID().toString().take(6)}" },
                            name = name,
                            category = o.optString("category").ifBlank { div.label },
                            division = div,
                            subSection = o.optString("sub_section"),
                            defaultEffect = o.optString("default_effect"),
                            isCustom = true
                        )
                    )
                }
            }
        }
    }

    suspend fun createComplianceCheck(
        config: SupabaseConfig,
        token: String,
        division: ComplianceDivision,
        name: String,
        category: String
    ): Result<ComplianceCheck> {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return Result.failure(Exception("Compliance check name is required."))
        }
        val payloadObj = JSONObject().apply {
            put("division", division.dbValue)
            put("area_name", category.ifBlank { division.label }.trim())
            put("name", cleanName)
            put("is_active", true)
        }
        val res = requestWithMissingColumnRetry(
            config,
            "POST",
            "/rest/v1/compliance_checks",
            payloadObj,
            token,
            mapOf("Prefer" to "return=representation")
        )
        if (!res.isSuccess) {
            return Result.failure(Exception(res.errorMessage ?: "Failed to create compliance check."))
        }
        val arr = api.parseJsonArraySafe(res.body)
        val o = arr.optJSONObject(0) ?: return Result.failure(
            Exception("Compliance check insert was blocked by database permissions.")
        )
        return Result.success(
            ComplianceCheck(
                id = o.optCleanString("id").ifBlank { "CHK-${UUID.randomUUID().toString().take(6).uppercase()}" },
                name = o.optCleanString("name").ifBlank { cleanName },
                category = o.optCleanString("area_name").ifBlank { category.ifBlank { division.label } },
                division = ComplianceDivision.fromAny(o.optCleanString("division")) ?: division,
                subSection = o.optCleanString("sub_section"),
                defaultEffect = o.optCleanString("default_effect"),
                isCustom = true
            )
        )
    }

    // ---------------------------------------------------------------------
    // DIRECTORATE USERS (ADMIN / SUPER_ADMIN)
    // ---------------------------------------------------------------------
    suspend fun fetchDirectorateUsers(
        config: SupabaseConfig,
        token: String,
        currentSession: UserSession? = null
    ): List<DirectorateUser> {
        val rpcRes = api.requestJson(config, "POST", "/rest/v1/rpc/get_directorate_users", "{}", token)
        val rpcArr = if (rpcRes.isSuccess) api.parseJsonArraySafe(rpcRes.body) else JSONArray()

        val arr = if (rpcArr.length() > 0) {
            rpcArr
        } else {
            val tableRes = api.requestJson(
                config,
                "GET",
                "/rest/v1/user_roles?select=*&order=created_at.desc",
                accessToken = token
            )
            if (tableRes.isSuccess) api.parseJsonArraySafe(tableRes.body) else JSONArray()
        }

        val users = mutableListOf<DirectorateUser>()
        val seenIds = mutableSetOf<String>()

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val uid = o.optCleanString("user_id").ifBlank { o.optCleanString("id") }
            if (uid.isBlank() || !seenIds.add(uid.lowercase())) continue

            var email = o.optCleanString("email").lowercase()
            if (!email.contains("@") && uid.isNotBlank()) {
                val rpcEmailRes = api.requestJson(
                    config,
                    "POST",
                    "/rest/v1/rpc/get_auth_email",
                    JSONObject().apply { put("uid", uid) }.toString(),
                    token
                )
                if (rpcEmailRes.isSuccess) {
                    val cleaned = rpcEmailRes.body.trim().trim('"').trim().lowercase()
                    if (cleaned.contains("@")) {
                        email = cleaned
                    }
                }
            }
            if (!email.contains("@") && currentSession != null && currentSession.userId.equals(uid, true)) {
                email = currentSession.email.trim().lowercase()
            }
            if (!email.contains("@")) {
                val staffNo = o.optCleanString("staff_number")
                if (staffNo.contains("@")) email = staffNo.lowercase()
            }

            val isOwner = isOwnerEmail(email) || o.optBoolean("is_owner", false)
            val verified = if (o.has("is_verified") && !o.isNull("is_verified")) o.optBoolean("is_verified", true) else true
            val rawStatus = o.optCleanString("status")
            val resolvedStatus = when {
                !verified -> "Inactive"
                rawStatus.isNotBlank() -> rawStatus
                else -> "Active"
            }
            val displayName = o.optCleanString("display_name")
                .ifBlank { email.substringBefore("@").ifBlank { "Officer" } }

            users.add(
                DirectorateUser(
                    userId = uid,
                    email = email,
                    displayName = displayName,
                    staffNumber = o.optCleanString("staff_number"),
                    role = if (isOwner) UserRole.SUPER_ADMIN else UserRole.fromKey(o.optCleanString("role")),
                    status = resolvedStatus,
                    isVerified = verified,
                    isOwner = isOwner,
                    isFounder = o.optBoolean("is_founder", false) || isOwner,
                    createdAt = o.optCleanString("created_at"),
                    avatarUrl = o.optCleanString("avatar_url")
                )
            )
        }

        if (currentSession != null &&
            currentSession.userId.isNotBlank() &&
            seenIds.add(currentSession.userId.lowercase())
        ) {
            users.add(
                0,
                DirectorateUser(
                    userId = currentSession.userId,
                    email = currentSession.email,
                    displayName = currentSession.displayName.ifBlank { currentSession.email.substringBefore("@") },
                    staffNumber = currentSession.staffNumber,
                    role = currentSession.role,
                    status = currentSession.status.ifBlank { "Active" },
                    isVerified = currentSession.isVerified,
                    isOwner = currentSession.isOwner,
                    isFounder = currentSession.isOwner,
                    createdAt = null,
                    avatarUrl = currentSession.avatarUrl
                )
            )
        }

        return users
    }

    suspend fun updateDirectorateUserRole(
        config: SupabaseConfig,
        token: String,
        targetUserId: String,
        newRole: UserRole
    ): Result<Unit> {
        val payloadObj = JSONObject().apply {
            put("role", newRole.key)
            put("updated_at", java.time.Instant.now().toString())
        }
        val res = requestWithMissingColumnRetry(
            config,
            "PATCH",
            "/rest/v1/user_roles?user_id=eq.${enc(targetUserId)}",
            payloadObj,
            token,
            mapOf("Prefer" to "return=representation")
        )
        if (!res.isSuccess) {
            return Result.failure(Exception(res.errorMessage ?: "Failed to update role."))
        }
        val arr = api.parseJsonArraySafe(res.body)
        return if (arr.length() > 0) Result.success(Unit)
        else Result.failure(Exception("Permission denied or officer record not found in user_roles."))
    }

    suspend fun updateDirectorateUserStatus(
        config: SupabaseConfig,
        token: String,
        targetUserId: String,
        newStatus: String
    ): Result<Unit> {
        val isActive = newStatus.equals("active", ignoreCase = true)
        val canonicalStatus = if (isActive) "Active" else "Inactive"
        val payloadObj = JSONObject().apply {
            put("is_verified", isActive)
            put("status", canonicalStatus)
            put("updated_at", java.time.Instant.now().toString())
        }
        val res = requestWithMissingColumnRetry(
            config,
            "PATCH",
            "/rest/v1/user_roles?user_id=eq.${enc(targetUserId)}",
            payloadObj,
            token,
            mapOf("Prefer" to "return=representation")
        )
        if (!res.isSuccess) {
            return Result.failure(Exception(res.errorMessage ?: "Failed to update account status."))
        }
        val arr = api.parseJsonArraySafe(res.body)
        return if (arr.length() > 0) Result.success(Unit)
        else Result.failure(Exception("Permission denied or officer record not found in user_roles."))
    }

    suspend fun updateDirectorateUserEmail(
        config: SupabaseConfig,
        token: String,
        targetUserId: String,
        newEmail: String
    ): Result<Unit> {
        val clean = newEmail.trim().lowercase()
        val rpcPayload = JSONObject().apply {
            put("target_user_id", targetUserId)
            put("new_email", clean)
        }.toString()
        val rpcRes = api.requestJson(config, "POST", "/rest/v1/rpc/admin_update_user_email", rpcPayload, token)

        val rolePayload = JSONObject().apply {
            put("email", clean)
            put("staff_number", clean)
            put("updated_at", java.time.Instant.now().toString())
        }
        val patchRes = requestWithMissingColumnRetry(
            config,
            "PATCH",
            "/rest/v1/user_roles?user_id=eq.${enc(targetUserId)}",
            rolePayload,
            token,
            mapOf("Prefer" to "return=representation")
        )

        if (rpcRes.isSuccess) {
            api.requestJson(
                config,
                "POST",
                "/rest/v1/rpc/confirm_user_email",
                JSONObject().apply { put("target_email", clean) }.toString(),
                token
            )
            return Result.success(Unit)
        }
        return if (patchRes.isSuccess && api.parseJsonArraySafe(patchRes.body).length() > 0) {
            Result.success(Unit)
        } else {
            Result.failure(
                Exception(
                    rpcRes.errorMessage
                        ?: patchRes.errorMessage
                        ?: "Unable to update email."
                )
            )
        }
    }

    suspend fun forceResetDirectoratePassword(
        config: SupabaseConfig,
        token: String,
        targetUserId: String,
        email: String
    ): Result<String> {
        val rpcPayload = JSONObject().apply {
            put("target_user_id", targetUserId)
            put("new_password", "12345678")
        }.toString()
        val rpcRes = api.requestJson(config, "POST", "/rest/v1/rpc/admin_reset_user_password", rpcPayload, token)
        if (rpcRes.isSuccess) {
            return Result.success("Password reset to temporary credential (12345678) for $email.")
        }
        val mailRes = sendPasswordResetEmail(config, email)
        return if (mailRes.isSuccess) {
            Result.success("Recovery reset email dispatched to $email.")
        } else {
            Result.failure(Exception(rpcRes.errorMessage ?: mailRes.exceptionOrNull()?.message ?: "Reset failed."))
        }
    }

    suspend fun provisionDirectorateUser(
        config: SupabaseConfig,
        token: String,
        email: String,
        password: String,
        displayName: String,
        staffNumber: String,
        role: UserRole
    ): Result<DirectorateUser> {
        val cleanEmail = email.trim().lowercase()
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(Exception("A valid institutional email address is required."))
        }
        val ownerAccount = isOwnerEmail(cleanEmail)
        val validRole = if (ownerAccount) UserRole.SUPER_ADMIN else role
        val finalName = displayName.trim().ifBlank { cleanEmail.substringBefore("@") }

        val signUpPayload = JSONObject().apply {
            put("email", cleanEmail)
            put("password", password)
            put("data", JSONObject().apply {
                put("display_name", finalName)
                put("staff_number", staffNumber.trim())
                put("role", validRole.key)
                put("department", "Quality Assurance")
            })
        }.toString()

        // Sign up without replacing the current admin session token
        val signRes = api.requestJson(config, "POST", "/auth/v1/signup", signUpPayload, accessToken = null)
        if (!signRes.isSuccess) {
            return Result.failure(Exception(signRes.errorMessage ?: "Unable to provision user account."))
        }

        val createdObj = api.parseJsonObjectSafe(signRes.body)
        val userObj = createdObj.optJSONObject("user") ?: createdObj
        var createdUserId = userObj.optCleanString("id").ifBlank { createdObj.optCleanString("id") }
        val identities = userObj.optJSONArray("identities")
        if (identities != null && identities.length() == 0) {
            return Result.failure(Exception("An account with email $cleanEmail already exists."))
        }

        api.requestJson(
            config,
            "POST",
            "/rest/v1/rpc/confirm_user_email",
            JSONObject().apply { put("target_email", cleanEmail) }.toString(),
            token
        )

        if (createdUserId.isBlank()) {
            val uidRpc = api.requestJson(
                config,
                "POST",
                "/rest/v1/rpc/get_user_id_by_email",
                JSONObject().apply { put("target_email", cleanEmail) }.toString(),
                token
            )
            if (uidRpc.isSuccess) {
                val parsedUid = uidRpc.body.trim().trim('"').trim()
                if (parsedUid.isNotBlank() && !parsedUid.equals("null", true)) {
                    createdUserId = parsedUid
                }
            }
        }

        if (createdUserId.isNotBlank()) {
            val rolePayload = JSONObject().apply {
                put("user_id", createdUserId)
                put("email", cleanEmail)
                put("display_name", finalName)
                put("staff_number", staffNumber.trim().ifBlank { cleanEmail })
                put("role", validRole.key)
                put("status", "Active")
                put("is_verified", true)
                put("is_owner", ownerAccount)
                put("is_founder", ownerAccount)
                put("updated_at", java.time.Instant.now().toString())
            }
            var upsertRes = requestWithMissingColumnRetry(
                config,
                "POST",
                "/rest/v1/user_roles?on_conflict=user_id",
                rolePayload,
                token,
                mapOf("Prefer" to "resolution=merge-duplicates,return=representation")
            )
            if (!upsertRes.isSuccess || api.parseJsonArraySafe(upsertRes.body).length() == 0) {
                val patchPayload = JSONObject(rolePayload.toString()).apply { remove("user_id") }
                upsertRes = requestWithMissingColumnRetry(
                    config,
                    "PATCH",
                    "/rest/v1/user_roles?user_id=eq.${enc(createdUserId)}",
                    patchPayload,
                    token,
                    mapOf("Prefer" to "return=representation")
                )
            }
            if (!upsertRes.isSuccess) {
                return Result.failure(
                    Exception(upsertRes.errorMessage ?: "Auth account created, but saving user_roles profile failed.")
                )
            }
        }

        return Result.success(
            DirectorateUser(
                userId = createdUserId.ifBlank { "pending-$cleanEmail" },
                email = cleanEmail,
                displayName = finalName,
                staffNumber = staffNumber.trim().ifBlank { cleanEmail },
                role = validRole,
                status = "Active",
                isVerified = true,
                isOwner = ownerAccount,
                isFounder = ownerAccount,
                createdAt = java.time.Instant.now().toString()
            )
        )
    }

    // ---------------------------------------------------------------------
    // COMPLIANCE REPORTS
    // ---------------------------------------------------------------------
    suspend fun fetchReports(config: SupabaseConfig, session: UserSession): Result<List<ComplianceReport>> {
        val query = if (session.role.isAdminOrAbove) {
            "/rest/v1/compliance_reports?select=*&order=created_at.desc"
        } else {
            "/rest/v1/compliance_reports?reported_by=eq.${enc(session.userId)}&select=*&order=created_at.desc"
        }
        val res = api.requestJson(config, "GET", query, accessToken = session.accessToken)
        if (!res.isSuccess) {
            return Result.failure(Exception(res.errorMessage ?: "Failed to load compliance reports."))
        }
        val arr = api.parseJsonArraySafe(res.body)
        val list = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(parseReport(o))
            }
        }
        return Result.success(list)
    }

    private fun parseReport(o: JSONObject): ComplianceReport {
        val incDate = o.optCleanString("incident_date").ifBlank {
            o.optCleanString("date").ifBlank {
                o.optCleanString("created_at").take(10)
            }
        }
        val rawReviewStatus = o.optCleanString("review_status")
        val seenAtVal = o.optCleanString("seen_at").takeIf { it.isNotBlank() }
        val seenByVal = o.optCleanString("seen_by").takeIf { it.isNotBlank() }
        val parsedStatus = ReportResolutionStatus.fromKey(o.optCleanString("status"))
        val isSeen = rawReviewStatus.equals("SEEN", ignoreCase = true) ||
            rawReviewStatus.equals("PENDING", ignoreCase = true) ||
            parsedStatus != ReportResolutionStatus.OPEN ||
            o.optBoolean("is_seen", false) ||
            o.optBoolean("seen", false) ||
            seenAtVal != null
        val effectiveReviewStatus = rawReviewStatus.ifBlank {
            when {
                parsedStatus == ReportResolutionStatus.PENDING -> "PENDING"
                isSeen -> "SEEN"
                else -> "UNSEEN"
            }
        }

        val divStr = o.optCleanString("division").ifBlank {
            o.optCleanString("department").ifBlank { "Academic" }
        }
        val singleUrl = o.optCleanString("evidence_url").takeIf { it.isNotBlank() }
        val singleType = o.optCleanString("evidence_type").ifBlank { "none" }
        val parsedUrls = parseJsonStringList(o.optJSONArray("evidence_urls")).ifEmpty {
            listOfNotNull(singleUrl)
        }
        val parsedTypes = parseJsonStringList(o.optJSONArray("evidence_types")).ifEmpty {
            if (singleUrl != null) listOf(singleType) else emptyList()
        }
        val resNotes = o.optCleanString("resolution_notes").takeIf { it.isNotBlank() }
        val parsedEvidenceNotes = parseJsonStringList(o.optJSONArray("evidence_notes")).ifEmpty {
            resNotes?.lines()?.map { it.trim() }?.filter { it.startsWith("[Evidence") }.orEmpty()
        }

        return ComplianceReport(
            id = o.optCleanString("id"),
            reportNumber = o.optCleanString("report_number").ifBlank { o.optCleanString("id").take(8).uppercase() },
            reportedBy = o.optCleanString("reported_by").ifBlank { o.optCleanString("reporter_id") },
            reporterName = o.optCleanString("reporter_name").ifBlank { o.optCleanString("reporting_officer") },
            reporterRole = o.optCleanString("reporter_role").ifBlank { "standard" },
            incidentDate = incDate,
            weekNumber = o.optInt("week_number", 1),
            term = o.optCleanString("term").ifBlank { "First Term" },
            staffId = o.optCleanString("staff_id").takeIf { it.isNotBlank() },
            staffName = o.optCleanString("staff_name").takeIf { it.isNotBlank() },
            staffCode = o.optCleanString("staff_code").takeIf { it.isNotBlank() },
            staffCategory = o.optCleanString("staff_category").takeIf { it.isNotBlank() },
            staffGroup = o.optCleanString("staff_group").takeIf { it.isNotBlank() },
            role = o.optCleanString("role").takeIf { it.isNotBlank() },
            department = o.optCleanString("department").takeIf { it.isNotBlank() },
            assignedLocation = o.optCleanString("assigned_location").takeIf { it.isNotBlank() },
            checkId = o.optCleanString("check_id").takeIf { it.isNotBlank() },
            classId = o.optCleanString("class_id").takeIf { it.isNotBlank() },
            className = o.optCleanString("class_name").takeIf { it.isNotBlank() },
            subjectId = o.optCleanString("subject_id").takeIf { it.isNotBlank() },
            subject = o.optCleanString("subject").takeIf { it.isNotBlank() },
            exceptionText = o.optCleanString("exception").ifBlank { o.optCleanString("exception_text") },
            effectText = o.optCleanString("effect").ifBlank { o.optCleanString("effect_text") },
            category = o.optCleanString("category").ifBlank { divStr },
            division = divStr,
            targetType = o.optCleanString("target_type").ifBlank { "staff" },
            complianceCheck = o.optCleanString("compliance_check"),
            evidenceUrl = singleUrl ?: parsedUrls.firstOrNull(),
            evidenceType = if (singleType != "none") singleType else (parsedTypes.firstOrNull() ?: "none"),
            evidenceName = o.optCleanString("evidence_name").takeIf { it.isNotBlank() },
            evidenceUrls = parsedUrls,
            evidenceTypes = parsedTypes,
            evidenceNotes = parsedEvidenceNotes,
            status = parsedStatus,
            reviewStatus = effectiveReviewStatus,
            isSeen = isSeen,
            seenAt = seenAtVal,
            seenBy = seenByVal,
            resolutionNotes = resNotes,
            resolvedBy = o.optCleanString("resolved_by").takeIf { it.isNotBlank() },
            resolvedAt = o.optCleanString("resolved_at").takeIf { it.isNotBlank() },
            isEdited = o.optBoolean("is_edited", false),
            editedAt = o.optCleanString("edited_at").takeIf { it.isNotBlank() },
            createdAt = o.optCleanString("created_at")
        )
    }

    private fun parseJsonStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optString(i).trim()
                if (s.isNotBlank() && s != "null") add(s)
            }
        }
    }

    suspend fun createReport(
        config: SupabaseConfig,
        session: UserSession,
        payload: JSONObject
    ): Result<ComplianceReport> {
        val current = JSONObject(payload.toString())
        val optionalCols = listOf(
            "role", "staff_group", "department", "assigned_location",
            "evidence_name", "evidence_urls", "evidence_types", "evidence_notes",
            "pending_at", "pending_by", "is_edited", "edited_at"
        )

        var res = api.requestJson(
            config,
            "POST",
            "/rest/v1/compliance_reports",
            current.toString(),
            session.accessToken,
            mapOf("Prefer" to "return=representation")
        )

        if (!res.isSuccess) {
            val errLow = res.errorMessage.orEmpty().lowercase()
            var stripped = false
            optionalCols.forEach { col ->
                if (errLow.contains(col)) {
                    current.remove(col)
                    stripped = true
                }
            }
            if (!stripped && (errLow.contains("column") || errLow.contains("schema cache"))) {
                optionalCols.forEach { current.remove(it) }
                stripped = true
            }
            if (stripped) {
                res = api.requestJson(
                    config,
                    "POST",
                    "/rest/v1/compliance_reports",
                    current.toString(),
                    session.accessToken,
                    mapOf("Prefer" to "return=representation")
                )
            }
        }

        if (!res.isSuccess) {
            return Result.failure(Exception(res.errorMessage ?: "Failed to submit compliance report."))
        }
        val arr = api.parseJsonArraySafe(res.body)
        return if (arr.length() > 0) Result.success(parseReport(arr.getJSONObject(0)))
        else Result.failure(Exception("Report saved but response could not be parsed."))
    }

    suspend fun updateReport(
        config: SupabaseConfig,
        token: String,
        reportId: String,
        updates: JSONObject
    ): Result<ComplianceReport> {
        val current = JSONObject(updates.toString())
        val optionalCols = listOf(
            "role", "staff_group", "department", "assigned_location",
            "evidence_name", "evidence_urls", "evidence_types", "evidence_notes",
            "pending_at", "pending_by", "is_edited", "edited_at"
        )
        var res = api.requestJson(
            config,
            "PATCH",
            "/rest/v1/compliance_reports?id=eq.${enc(reportId)}",
            current.toString(),
            token,
            mapOf("Prefer" to "return=representation")
        )
        if (!res.isSuccess) {
            val errLow = res.errorMessage.orEmpty().lowercase()
            var stripped = false
            optionalCols.forEach { col ->
                if (errLow.contains(col)) {
                    current.remove(col)
                    stripped = true
                }
            }
            if (!stripped && (errLow.contains("column") || errLow.contains("schema cache"))) {
                optionalCols.forEach { current.remove(it) }
                stripped = true
            }
            if (stripped) {
                res = api.requestJson(
                    config,
                    "PATCH",
                    "/rest/v1/compliance_reports?id=eq.${enc(reportId)}",
                    current.toString(),
                    token,
                    mapOf("Prefer" to "return=representation")
                )
            }
        }
        if (!res.isSuccess) {
            return Result.failure(Exception(res.errorMessage ?: "Failed to update report."))
        }
        val arr = api.parseJsonArraySafe(res.body)
        return if (arr.length() > 0) Result.success(parseReport(arr.getJSONObject(0)))
        else Result.failure(Exception("Report update returned no data."))
    }

    suspend fun setReportSeenState(
        config: SupabaseConfig,
        session: UserSession,
        reportId: String,
        seen: Boolean,
        clearPendingToInReview: Boolean = false
    ): Result<ComplianceReport> {
        val updates = JSONObject().apply {
            if (seen) {
                put("review_status", "SEEN")
                put("is_seen", true)
                put("seen_at", java.time.Instant.now().toString())
                put("seen_by", session.displayName.ifBlank { session.email })
                if (clearPendingToInReview) {
                    put("status", ReportResolutionStatus.ACCEPTED.key)
                }
            } else {
                put("review_status", "UNSEEN")
                put("is_seen", false)
                put("seen_at", JSONObject.NULL)
                put("seen_by", JSONObject.NULL)
                put("status", ReportResolutionStatus.OPEN.key)
            }
        }
        return updateReport(config, session.accessToken, reportId, updates)
    }

    suspend fun updateReportResolution(
        config: SupabaseConfig,
        session: UserSession,
        reportId: String,
        status: ReportResolutionStatus,
        notes: String
    ): Result<ComplianceReport> {
        val adminActor = session.displayName.ifBlank { session.email }
        val nowIso = java.time.Instant.now().toString()
        val trimmedNotes = notes.trim()
        val updates = JSONObject().apply {
            put("status", status.key)
            put("resolution_notes", trimmedNotes)
            if (status == ReportResolutionStatus.OPEN && trimmedNotes.isBlank()) {
                put("is_seen", false)
                put("review_status", "UNSEEN")
                put("seen_at", JSONObject.NULL)
                put("seen_by", JSONObject.NULL)
            } else {
                put("is_seen", true)
                put("seen_at", nowIso)
                put("seen_by", adminActor)
                put(
                    "review_status",
                    if (status == ReportResolutionStatus.PENDING) "PENDING" else "SEEN"
                )
            }
            if (status == ReportResolutionStatus.PENDING) {
                put("pending_at", nowIso)
                put("pending_by", adminActor)
            }
            if (status == ReportResolutionStatus.ACCEPTED) {
                put("resolved_by", adminActor)
                put("resolved_at", nowIso)
            }
        }
        return updateReport(config, session.accessToken, reportId, updates)
    }

    suspend fun deleteReport(
        config: SupabaseConfig,
        token: String,
        reportId: String
    ): Result<Unit> {
        val res = api.requestJson(
            config,
            "DELETE",
            "/rest/v1/compliance_reports?id=eq.${enc(reportId)}",
            accessToken = token
        )
        return if (res.isSuccess) Result.success(Unit)
        else Result.failure(Exception(res.errorMessage ?: "Failed to delete report."))
    }

    // ---------------------------------------------------------------------
    // NOTIFICATIONS (NATIVE ANDROID SYNC)
    // ---------------------------------------------------------------------
    suspend fun fetchUnreadNotifications(
        config: SupabaseConfig,
        session: UserSession
    ): List<AppNotificationRecord> {
        val res = api.requestJson(
            config,
            "GET",
            "/rest/v1/notifications?is_read=eq.false&order=created_at.desc&limit=40",
            accessToken = session.accessToken
        )
        if (!res.isSuccess) return emptyList()
        val arr = api.parseJsonArraySafe(res.body)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val recipientUid = o.optCleanString("recipient_user_id").takeIf { it.isNotBlank() }
                val recipientRole = o.optCleanString("recipient_role").ifBlank { "user" }
                val forCurrentUser = when {
                    recipientUid != null && recipientUid.equals(session.userId, true) -> true
                    recipientRole.equals("admin", true) && session.role.isAdminOrAbove -> true
                    else -> false
                }
                if (!forCurrentUser) continue
                add(
                    AppNotificationRecord(
                        id = o.optCleanString("id"),
                        recipientUserId = recipientUid,
                        recipientRole = recipientRole,
                        type = o.optCleanString("type"),
                        title = o.optCleanString("title"),
                        body = o.optCleanString("body"),
                        reportId = o.optCleanString("report_id").takeIf { it.isNotBlank() },
                        reportNumber = o.optCleanString("report_number").takeIf { it.isNotBlank() },
                        actorName = o.optCleanString("actor_name"),
                        isRead = o.optBoolean("is_read", false),
                        createdAt = o.optCleanString("created_at")
                    )
                )
            }
        }
    }

    suspend fun insertNotificationBestEffort(
        config: SupabaseConfig,
        token: String,
        recipientUserId: String?,
        recipientRole: String,
        type: String,
        title: String,
        body: String,
        reportId: String?,
        reportNumber: String?,
        actorName: String
    ) {
        val payload = JSONObject().apply {
            if (!recipientUserId.isNullOrBlank()) put("recipient_user_id", recipientUserId)
            else put("recipient_user_id", JSONObject.NULL)
            put("recipient_role", recipientRole)
            put("type", type)
            put("title", title)
            put("body", body)
            if (!reportId.isNullOrBlank()) put("report_id", reportId)
            if (!reportNumber.isNullOrBlank()) put("report_number", reportNumber)
            put("actor_name", actorName)
            put("is_read", false)
        }.toString()
        runCatching {
            api.requestJson(config, "POST", "/rest/v1/notifications", payload, token)
        }
    }

    suspend fun markNotificationRead(config: SupabaseConfig, token: String, notificationId: String) {
        if (notificationId.isBlank()) return
        runCatching {
            api.requestJson(
                config,
                "PATCH",
                "/rest/v1/notifications?id=eq.${enc(notificationId)}",
                JSONObject().apply { put("is_read", true) }.toString(),
                token
            )
        }
    }

    // ---------------------------------------------------------------------
    // STORAGE (EVIDENCE)
    // ---------------------------------------------------------------------
    suspend fun uploadEvidenceFile(
        config: SupabaseConfig,
        token: String,
        reportNumber: String,
        file: File,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val bytes = file.readBytes()
        uploadEvidenceBytes(config, token, reportNumber, bytes, file.name, mimeType)
    }

    suspend fun uploadEvidenceUri(
        config: SupabaseConfig,
        token: String,
        reportNumber: String,
        uri: Uri,
        fallbackMime: String
    ): Result<Triple<String, String, String>> = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: fallbackMime
        val name = resolveUriDisplayName(uri) ?: "evidence_${System.currentTimeMillis()}"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext Result.failure(Exception("Unable to read selected evidence file."))
        val uploadRes = uploadEvidenceBytes(config, token, reportNumber, bytes, name, mime)
        uploadRes.map { path -> Triple(path, name, mime) }
    }

    private suspend fun uploadEvidenceBytes(
        config: SupabaseConfig,
        token: String,
        reportNumber: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<String> {
        val maxBytes = 50 * 1024 * 1024
        if (bytes.size > maxBytes) {
            return Result.failure(Exception("Evidence file exceeds 50 MB upload limit."))
        }
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val rand = UUID.randomUUID().toString().replace("-", "").take(12)
        val path = "evidence/$reportNumber/$rand/$safeName"
        val res = api.uploadBinary(config, "compliance-evidence", path, bytes, mimeType, token)
        return if (res.isSuccess) Result.success(path)
        else Result.failure(Exception(res.errorMessage ?: "Failed to upload evidence."))
    }

    suspend fun getSignedEvidenceUrl(
        config: SupabaseConfig,
        token: String,
        storedPathOrUrl: String
    ): Result<String> {
        if (storedPathOrUrl.startsWith("http://") || storedPathOrUrl.startsWith("https://")) {
            return Result.success(storedPathOrUrl)
        }
        val cleanPath = storedPathOrUrl.trimStart('/')
        val payload = JSONObject().apply { put("expiresIn", 3600) }.toString()
        val res = api.requestJson(
            config,
            "POST",
            "/storage/v1/object/sign/compliance-evidence/$cleanPath",
            payload,
            token
        )
        if (!res.isSuccess) {
            return Result.failure(Exception(res.errorMessage ?: "Unable to authorize evidence view."))
        }
        val obj = api.parseJsonObjectSafe(res.body)
        val signedPath = obj.optString("signedURL").ifBlank { obj.optString("signedUrl") }
        if (signedPath.isBlank()) {
            return Result.failure(Exception("No signed URL returned from storage."))
        }
        val fullUrl = if (signedPath.startsWith("http")) signedPath else {
            val base = config.url.trimEnd('/')
            if (signedPath.startsWith("/")) "$base/storage/v1$signedPath" else "$base/storage/v1/$signedPath"
        }
        return Result.success(fullUrl)
    }

    private fun resolveUriDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) c.getString(idx) else null
                    } else null
                } ?: uri.lastPathSegment
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    fun generateReportNumber(): String {
        val year = AcademicWeekEngine.todayIso().take(4)
        val seq = (1000..9999).random()
        val hex = UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
        return "JCB-$year-$seq-$hex"
    }
}
