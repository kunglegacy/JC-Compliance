package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.AcademicWeekEngine
import com.example.data.local.ComplianceTaxonomy
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

        val roleInfo = fetchUserRoleRecord(config, accessToken, userId, resolvedEmail, metaName)
        if (roleInfo.status.equals("suspended", ignoreCase = true) ||
            roleInfo.status.equals("inactive", ignoreCase = true)
        ) {
            return Result.failure(Exception("This account has been suspended. Contact the Directorate Administrator."))
        }

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
                isOwner = roleInfo.isOwner
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

    suspend fun signOut(config: SupabaseConfig, accessToken: String) {
        api.requestJson(config, "POST", "/auth/v1/logout", null, accessToken)
    }

    private suspend fun fetchUserRoleRecord(
        config: SupabaseConfig,
        accessToken: String,
        userId: String,
        email: String,
        fallbackName: String
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
                val roleStr = obj.optString("role", "standard")
                val effectiveRole = if (isOwner) UserRole.SUPER_ADMIN else UserRole.fromKey(roleStr)
                return DirectorateUser(
                    userId = userId,
                    email = obj.optString("email", email),
                    displayName = obj.optString("display_name").ifBlank { fallbackName },
                    staffNumber = obj.optString("staff_number"),
                    role = effectiveRole,
                    status = obj.optString("status", "active"),
                    isVerified = obj.optBoolean("is_verified", true),
                    isOwner = isOwner || obj.optBoolean("is_owner", false),
                    isFounder = obj.optBoolean("is_founder", false)
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
            isFounder = isOwner
        )
    }

    // ---------------------------------------------------------------------
    // SYSTEM SETTINGS
    // ---------------------------------------------------------------------
    suspend fun fetchSettings(config: SupabaseConfig, token: String): SystemSettings {
        val res = api.requestJson(config, "GET", "/rest/v1/settings?select=*&limit=1", accessToken = token)
        if (res.isSuccess) {
            val arr = api.parseJsonArraySafe(res.body)
            if (arr.length() > 0) {
                val obj = arr.getJSONObject(0)
                return SystemSettings(
                    academicSession = obj.optString("academic_session").ifBlank { "2025/2026" },
                    activeTerm = obj.optString("active_term").ifBlank { "First Term" },
                    termStartDate = obj.optString("term_start_date").ifBlank { "2025-09-08" },
                    manualWeekOverride = obj.optString("manual_week_override").ifBlank { "none" }
                )
            }
        }
        return SystemSettings()
    }

    suspend fun updateSettings(config: SupabaseConfig, token: String, settings: SystemSettings): Result<SystemSettings> {
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
        val payloadObj = JSONObject().apply {
            put("name", staff.name.trim())
            put("staff_code", staff.staffCode.trim())
            put("staff_number", staff.staffNumber.ifBlank { staff.staffCode }.trim())
            put("category", staff.category)
            put("department", staff.department)
            put("staff_group", staff.staffGroup.ifBlank { staff.department })
            put("role", staff.role)
            put("assigned_location", staff.assignedLocation.trim())
            put("is_active", staff.isActive)
            put("status", if (staff.isActive) "Active" else "Archived")
        }

        val res = if (isNew) {
            api.requestJson(
                config,
                "POST",
                "/rest/v1/staff",
                payloadObj.toString(),
                token,
                mapOf("Prefer" to "return=representation")
            )
        } else {
            api.requestJson(
                config,
                "PATCH",
                "/rest/v1/staff?id=eq.${enc(staff.id)}",
                payloadObj.toString(),
                token,
                mapOf("Prefer" to "return=representation")
            )
        }

        if (!res.isSuccess) {
            return Result.failure(Exception(res.errorMessage ?: "Failed to save staff member."))
        }
        val arr = api.parseJsonArraySafe(res.body)
        return if (arr.length() > 0) Result.success(parseStaff(arr.getJSONObject(0)))
        else Result.success(staff)
    }

    suspend fun setStaffActiveStatus(
        config: SupabaseConfig,
        token: String,
        staffId: String,
        isActive: Boolean
    ): Result<Unit> {
        val payload = JSONObject().apply {
            put("is_active", isActive)
            put("status", if (isActive) "Active" else "Archived")
        }.toString()
        val res = api.requestJson(config, "PATCH", "/rest/v1/staff?id=eq.${enc(staffId)}", payload, token)
        return if (res.isSuccess) Result.success(Unit)
        else Result.failure(Exception(res.errorMessage ?: "Unable to update staff status."))
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

    // ---------------------------------------------------------------------
    // DIRECTORATE USERS (ADMIN / SUPER_ADMIN)
    // ---------------------------------------------------------------------
    suspend fun fetchDirectorateUsers(config: SupabaseConfig, token: String): List<DirectorateUser> {
        val rpcRes = api.requestJson(config, "POST", "/rest/v1/rpc/get_directorate_users", "{}", token)
        val rawBody = if (rpcRes.isSuccess) rpcRes.body else {
            val tableRes = api.requestJson(config, "GET", "/rest/v1/user_roles?select=*&order=created_at.desc", accessToken = token)
            if (tableRes.isSuccess) tableRes.body else return emptyList()
        }
        val arr = api.parseJsonArraySafe(rawBody)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val email = o.optString("email")
                val isOwner = isOwnerEmail(email) || o.optBoolean("is_owner", false)
                add(
                    DirectorateUser(
                        userId = o.optString("user_id").ifBlank { o.optString("id") },
                        email = email,
                        displayName = o.optString("display_name").ifBlank { email.substringBefore("@") },
                        staffNumber = o.optString("staff_number"),
                        role = if (isOwner) UserRole.SUPER_ADMIN else UserRole.fromKey(o.optString("role")),
                        status = o.optString("status", "active"),
                        isVerified = o.optBoolean("is_verified", true),
                        isOwner = isOwner,
                        isFounder = o.optBoolean("is_founder", false),
                        createdAt = o.optString("created_at")
                    )
                )
            }
        }
    }

    suspend fun updateDirectorateUserRole(
        config: SupabaseConfig,
        token: String,
        targetUserId: String,
        newRole: UserRole
    ): Result<Unit> {
        val payload = JSONObject().apply { put("role", newRole.key) }.toString()
        val res = api.requestJson(config, "PATCH", "/rest/v1/user_roles?user_id=eq.${enc(targetUserId)}", payload, token)
        return if (res.isSuccess) Result.success(Unit)
        else Result.failure(Exception(res.errorMessage ?: "Failed to update role."))
    }

    suspend fun updateDirectorateUserStatus(
        config: SupabaseConfig,
        token: String,
        targetUserId: String,
        newStatus: String
    ): Result<Unit> {
        val payload = JSONObject().apply { put("status", newStatus) }.toString()
        val res = api.requestJson(config, "PATCH", "/rest/v1/user_roles?user_id=eq.${enc(targetUserId)}", payload, token)
        return if (res.isSuccess) Result.success(Unit)
        else Result.failure(Exception(res.errorMessage ?: "Failed to update account status."))
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
        if (rpcRes.isSuccess) return Result.success(Unit)

        val rolePayload = JSONObject().apply { put("email", clean) }.toString()
        val res = api.requestJson(config, "PATCH", "/rest/v1/user_roles?user_id=eq.${enc(targetUserId)}", rolePayload, token)
        return if (res.isSuccess) Result.success(Unit)
        else Result.failure(Exception(res.errorMessage ?: "Unable to update email."))
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
    ): Result<Unit> {
        val cleanEmail = email.trim().lowercase()
        val signUpPayload = JSONObject().apply {
            put("email", cleanEmail)
            put("password", password)
            put("data", JSONObject().apply {
                put("display_name", displayName.trim())
                put("staff_number", staffNumber.trim())
            })
        }.toString()

        // Sign up without replacing the current admin session token
        val signRes = api.requestJson(config, "POST", "/auth/v1/signup", signUpPayload, accessToken = null)
        if (!signRes.isSuccess) {
            return Result.failure(Exception(signRes.errorMessage ?: "Unable to provision user account."))
        }

        val createdObj = api.parseJsonObjectSafe(signRes.body)
        val createdUserId = createdObj.optJSONObject("user")?.optString("id").orEmpty()
            .ifBlank { createdObj.optString("id") }

        api.requestJson(
            config,
            "POST",
            "/rest/v1/rpc/confirm_user_email",
            JSONObject().apply { put("target_email", cleanEmail) }.toString(),
            token
        )

        if (createdUserId.isNotBlank()) {
            val rolePayload = JSONObject().apply {
                put("user_id", createdUserId)
                put("email", cleanEmail)
                put("display_name", displayName.trim())
                put("staff_number", staffNumber.trim())
                put("role", role.key)
                put("status", "active")
                put("is_verified", true)
            }.toString()
            api.requestJson(
                config,
                "POST",
                "/rest/v1/user_roles",
                rolePayload,
                token,
                mapOf("Prefer" to "resolution=merge-duplicates")
            )
        }
        return Result.success(Unit)
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
        val incDate = o.optString("incident_date").ifBlank {
            o.optString("date").ifBlank {
                o.optString("created_at").take(10)
            }
        }
        val isSeen = o.optString("review_status").equals("SEEN", ignoreCase = true) ||
            o.optBoolean("is_seen", false) ||
            o.optBoolean("seen", false) ||
            o.optString("seen_at").isNotBlank()

        val divStr = o.optString("division").ifBlank {
            o.optString("department").ifBlank { "Academic" }
        }
        return ComplianceReport(
            id = o.optString("id"),
            reportNumber = o.optString("report_number").ifBlank { o.optString("id").take(8).uppercase() },
            reportedBy = o.optString("reported_by").ifBlank { o.optString("reporter_id") },
            reporterName = o.optString("reporter_name").ifBlank { o.optString("reporting_officer") },
            reporterRole = o.optString("reporter_role", "standard"),
            incidentDate = incDate,
            weekNumber = o.optInt("week_number", 1),
            term = o.optString("term").ifBlank { "First Term" },
            staffId = o.optString("staff_id").takeIf { it.isNotBlank() && it != "null" },
            staffName = o.optString("staff_name").takeIf { it.isNotBlank() && it != "null" },
            staffCode = o.optString("staff_code").takeIf { it.isNotBlank() && it != "null" },
            staffCategory = o.optString("staff_category").takeIf { it.isNotBlank() && it != "null" },
            staffGroup = o.optString("staff_group").takeIf { it.isNotBlank() && it != "null" },
            role = o.optString("role").takeIf { it.isNotBlank() && it != "null" },
            department = o.optString("department").takeIf { it.isNotBlank() && it != "null" },
            assignedLocation = o.optString("assigned_location").takeIf { it.isNotBlank() && it != "null" },
            checkId = o.optString("check_id").takeIf { it.isNotBlank() && it != "null" },
            classId = o.optString("class_id").takeIf { it.isNotBlank() && it != "null" },
            className = o.optString("class_name").takeIf { it.isNotBlank() && it != "null" },
            subjectId = o.optString("subject_id").takeIf { it.isNotBlank() && it != "null" },
            subject = o.optString("subject").takeIf { it.isNotBlank() && it != "null" },
            exceptionText = o.optString("exception").ifBlank { o.optString("exception_text") },
            effectText = o.optString("effect").ifBlank { o.optString("effect_text") },
            category = o.optString("category").ifBlank { divStr },
            division = divStr,
            targetType = o.optString("target_type", "staff"),
            complianceCheck = o.optString("compliance_check"),
            evidenceUrl = o.optString("evidence_url").takeIf { it.isNotBlank() && it != "null" },
            evidenceType = o.optString("evidence_type").ifBlank { "none" },
            evidenceName = o.optString("evidence_name").takeIf { it.isNotBlank() && it != "null" },
            status = ReportResolutionStatus.fromKey(o.optString("status")),
            isSeen = isSeen,
            seenAt = o.optString("seen_at").takeIf { it.isNotBlank() && it != "null" },
            seenBy = o.optString("seen_by").takeIf { it.isNotBlank() && it != "null" },
            resolutionNotes = o.optString("resolution_notes").takeIf { it.isNotBlank() && it != "null" },
            resolvedBy = o.optString("resolved_by").takeIf { it.isNotBlank() && it != "null" },
            resolvedAt = o.optString("resolved_at").takeIf { it.isNotBlank() && it != "null" },
            createdAt = o.optString("created_at")
        )
    }

    suspend fun createReport(
        config: SupabaseConfig,
        session: UserSession,
        payload: JSONObject
    ): Result<ComplianceReport> {
        val current = JSONObject(payload.toString())
        val optionalCols = listOf("role", "staff_group", "department", "assigned_location", "evidence_name")

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
        val res = api.requestJson(
            config,
            "PATCH",
            "/rest/v1/compliance_reports?id=eq.${enc(reportId)}",
            updates.toString(),
            token,
            mapOf("Prefer" to "return=representation")
        )
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
        seen: Boolean
    ): Result<ComplianceReport> {
        val updates = JSONObject().apply {
            if (seen) {
                put("review_status", "SEEN")
                put("is_seen", true)
                put("seen_at", java.time.Instant.now().toString())
                put("seen_by", session.displayName.ifBlank { session.email })
            } else {
                put("review_status", "UNSEEN")
                put("is_seen", false)
                put("seen_at", JSONObject.NULL)
                put("seen_by", JSONObject.NULL)
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
        val updates = JSONObject().apply {
            put("status", status.key)
            put("resolution_notes", notes.trim())
            if (status == ReportResolutionStatus.RESOLVED) {
                put("resolved_by", session.displayName.ifBlank { session.email })
                put("resolved_at", java.time.Instant.now().toString())
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
