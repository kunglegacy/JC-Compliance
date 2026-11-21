package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.data.local.AcademicWeekEngine
import com.example.data.local.ComplianceTaxonomy
import com.example.data.model.AppNotificationRecord
import com.example.data.model.Classroom
import com.example.data.model.ComplianceCheck
import com.example.data.model.ComplianceDivision
import com.example.data.model.ComplianceReport
import com.example.data.model.DirectorateUser
import com.example.data.model.MobileAppConfig
import com.example.data.model.ReportResolutionStatus
import com.example.data.model.StaffMember
import com.example.data.model.StorageProviderConfig
import com.example.data.model.SubjectItem
import com.example.data.model.SupabaseConfig
import com.example.data.model.SystemSettings
import com.example.data.model.TermAnalysisSnapshot
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ComplianceRepository(
    private val context: Context,
    private val api: SupabaseApiClient = SupabaseApiClient()
) {
    private data class CachedSignedUrl(val url: String, val expiresAtMs: Long)
    private val signedUrlCache = ConcurrentHashMap<String, CachedSignedUrl>()
    private val signedUrlTtlMs = 45L * 60L * 1000L // Cache 60m B2/Supabase signed URLs cleanly for 45m
    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun dec(value: String): String = runCatching {
        URLDecoder.decode(value, "UTF-8")
    }.getOrDefault(value)

    fun buildB2AuthHeaders(config: SupabaseConfig, token: String?): Map<String, String> {
        val bearer = token?.takeIf { it.isNotBlank() } ?: config.anonKey
        return mapOf(
            "Authorization" to "Bearer $bearer",
            "x-supabase-url" to config.url.trimEnd('/'),
            "x-supabase-anon-key" to config.anonKey
        )
    }

    fun isB2FileProxyUrl(config: SupabaseConfig, url: String): Boolean {
        val base = config.normalizedApiBaseUrl
        val lower = url.trim().lowercase(Locale.US)
        if (lower.startsWith("/api/storage/b2/file")) return true
        if (base.isNotBlank() && lower.startsWith("${base.lowercase(Locale.US)}/api/storage/b2/file")) return true
        return false
    }

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

        val canonicalAvatar = normalizeCanonicalAvatarPath(roleInfo.avatarUrl.ifBlank { metaAvatar })
        if (canonicalAvatar.isNotBlank()) {
            // Warm in-memory signed URL cache on sign-in while storing canonical relative path in session
            runCatching { getSignedEvidenceUrl(config, accessToken, canonicalAvatar) }
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
                isOwner = roleInfo.isOwner,
                avatarUrl = canonicalAvatar
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

    fun normalizeCanonicalAvatarPath(rawPath: String): String {
        val raw = rawPath.trim()
        if (raw.isBlank() || raw.equals("null", ignoreCase = true)) return ""
        return when (val ref = parseMediaReference(raw)) {
            is ParsedMediaRef.B2Object -> ref.objectPath.trimStart('/')
            is ParsedMediaRef.SupabaseLegacy -> {
                if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw.substringBefore('?')
                else ref.objectPath.trimStart('/')
            }
            is ParsedMediaRef.DirectUrl -> raw
        }
    }

    suspend fun updateDisplayNameOnServer(
        config: SupabaseConfig,
        session: UserSession,
        newDisplayName: String
    ): Result<Unit> {
        val cleanName = newDisplayName.trim()
        val nowIso = java.time.Instant.now().toString()
        val userPayload = JSONObject().apply {
            put("data", JSONObject().apply { put("display_name", cleanName) })
        }.toString()
        val authRes = api.requestJson(config, "PUT", "/auth/v1/user", userPayload, session.accessToken)

        val rolePayload = JSONObject().apply {
            put("display_name", cleanName)
            put("avatar_updated_at", nowIso)
        }
        var roleRes = requestWithMissingColumnRetry(
            config = config,
            method = "PATCH",
            path = "/rest/v1/user_roles?user_id=eq.${enc(session.userId)}",
            payloadObj = rolePayload,
            token = session.accessToken,
            extraHeaders = mapOf("Prefer" to "return=representation")
        )
        val updatedRows = if (roleRes.isSuccess) api.parseJsonArraySafe(roleRes.body).length() else 0
        if (!roleRes.isSuccess || updatedRows == 0) {
            val rpcPayload = JSONObject().apply {
                put("p_display_name", cleanName)
                put("p_clear_avatar", false)
            }.toString()
            roleRes = api.requestJson(config, "POST", "/rest/v1/rpc/update_my_profile", rpcPayload, session.accessToken)
        }
        return if (roleRes.isSuccess || authRes.isSuccess) Result.success(Unit)
        else Result.failure(Exception(roleRes.errorMessage ?: authRes.errorMessage ?: "Unable to update display name."))
    }

    suspend fun uploadProfileAvatar(
        config: SupabaseConfig,
        session: UserSession,
        avatarFile: File,
        previousAvatarPath: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!config.isB2Configured) {
            return@withContext Result.failure(
                Exception("Web storage backend URL (API_BASE_URL) is not configured. Open Connection Setup to enter it.")
            )
        }
        val bytes = runCatching { avatarFile.readBytes() }.getOrElse {
            return@withContext Result.failure(Exception("Unable to read compressed profile photo."))
        }
        if (bytes.isEmpty()) {
            return@withContext Result.failure(Exception("Compressed profile photo is empty."))
        }

        val fileName = "avatar_${System.currentTimeMillis()}.jpg"
        val objectKey = "avatars/${session.userId}/$fileName"
        val uploadEndpoint = "${config.normalizedApiBaseUrl}/api/storage/b2/upload"
        val uploadHeaders = buildB2AuthHeaders(config, session.accessToken) + mapOf(
            "x-object-path" to enc(objectKey),
            "x-content-type" to "image/jpeg",
            "x-file-name" to enc(fileName),
            "x-category" to "avatar",
            "x-uploaded-by" to session.userId
        )

        val uploadRes = api.postRawBytesToUrl(uploadEndpoint, bytes, uploadHeaders)
        if (!uploadRes.isSuccess) {
            return@withContext Result.failure(
                Exception(uploadRes.errorMessage ?: "Failed to upload profile photo to Backblaze B2.")
            )
        }

        val obj = api.parseJsonObjectSafe(uploadRes.body)
        val canonicalObjectKey = normalizeCanonicalAvatarPath(
            obj.optCleanString("path").ifBlank { objectKey }
        ).ifBlank { objectKey }

        // Persist ONLY canonical relative B2 object path in public.user_roles and Supabase auth metadata
        val nowIso = java.time.Instant.now().toString()
        val rolePayload = JSONObject().apply {
            put("avatar_url", canonicalObjectKey)
            put("avatar_updated_at", nowIso)
        }
        var roleRes = requestWithMissingColumnRetry(
            config = config,
            method = "PATCH",
            path = "/rest/v1/user_roles?user_id=eq.${enc(session.userId)}",
            payloadObj = rolePayload,
            token = session.accessToken,
            extraHeaders = mapOf("Prefer" to "return=representation")
        )
        val updatedRows = if (roleRes.isSuccess) api.parseJsonArraySafe(roleRes.body).length() else 0
        if (!roleRes.isSuccess || updatedRows == 0) {
            val rpcPayload = JSONObject().apply {
                put("p_avatar_url", canonicalObjectKey)
                put("p_clear_avatar", false)
            }.toString()
            roleRes = api.requestJson(config, "POST", "/rest/v1/rpc/update_my_profile", rpcPayload, session.accessToken)
        }
        if (!roleRes.isSuccess) {
            // Roll back uploaded orphan if DB write failed
            runCatching { deleteEvidenceMedia(config, session.accessToken, canonicalObjectKey) }
            return@withContext Result.failure(
                Exception(roleRes.errorMessage ?: "Unable to save profile photo to database.")
            )
        }

        val userPayload = JSONObject().apply {
            put("data", JSONObject().apply { put("avatar_url", canonicalObjectKey) })
        }.toString()
        api.requestJson(config, "PUT", "/auth/v1/user", userPayload, session.accessToken)

        // Clean up previous avatar object in B2 & public.storage_objects so orphans do not accumulate
        val normalizedPrev = normalizeCanonicalAvatarPath(previousAvatarPath)
        if (normalizedPrev.isNotBlank() && normalizedPrev != canonicalObjectKey) {
            signedUrlCache.remove(normalizedPrev)
            runCatching { deleteEvidenceMedia(config, session.accessToken, normalizedPrev) }
        }

        // Pre-warm signed URL cache cleanly
        runCatching { getSignedEvidenceUrl(config, session.accessToken, canonicalObjectKey) }
        Result.success(canonicalObjectKey)
    }

    suspend fun removeProfileAvatar(
        config: SupabaseConfig,
        session: UserSession,
        previousAvatarPath: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val nowIso = java.time.Instant.now().toString()
        val rolePayload = JSONObject().apply {
            put("avatar_url", JSONObject.NULL)
            put("avatar_updated_at", nowIso)
        }
        var roleRes = requestWithMissingColumnRetry(
            config = config,
            method = "PATCH",
            path = "/rest/v1/user_roles?user_id=eq.${enc(session.userId)}",
            payloadObj = rolePayload,
            token = session.accessToken,
            extraHeaders = mapOf("Prefer" to "return=representation")
        )
        val updatedRows = if (roleRes.isSuccess) api.parseJsonArraySafe(roleRes.body).length() else 0
        if (!roleRes.isSuccess || updatedRows == 0) {
            val rpcPayload = JSONObject().apply {
                put("p_clear_avatar", true)
            }.toString()
            roleRes = api.requestJson(config, "POST", "/rest/v1/rpc/update_my_profile", rpcPayload, session.accessToken)
        }
        if (!roleRes.isSuccess) {
            return@withContext Result.failure(
                Exception(roleRes.errorMessage ?: "Unable to clear profile photo in database.")
            )
        }

        val userPayload = JSONObject().apply {
            put("data", JSONObject().apply { put("avatar_url", JSONObject.NULL) })
        }.toString()
        api.requestJson(config, "PUT", "/auth/v1/user", userPayload, session.accessToken)

        val normalizedPrev = normalizeCanonicalAvatarPath(previousAvatarPath)
        if (normalizedPrev.isNotBlank()) {
            signedUrlCache.remove(normalizedPrev)
            runCatching { deleteEvidenceMedia(config, session.accessToken, normalizedPrev) }
        }
        Result.success(Unit)
    }

    suspend fun fetchCurrentUserProfile(
        config: SupabaseConfig,
        session: UserSession
    ): Result<Pair<String, String>> {
        val roleRes = api.requestJson(
            config,
            "GET",
            "/rest/v1/user_roles?user_id=eq.${enc(session.userId)}&select=avatar_url,display_name",
            accessToken = session.accessToken
        )
        if (roleRes.isSuccess) {
            val arr = api.parseJsonArraySafe(roleRes.body)
            if (arr.length() > 0) {
                val obj = arr.getJSONObject(0)
                val name = obj.optCleanString("display_name")
                val canonicalAvatar = normalizeCanonicalAvatarPath(obj.optCleanString("avatar_url"))
                return Result.success(name to canonicalAvatar)
            }
        }
        val userRes = api.requestJson(config, "GET", "/auth/v1/user", accessToken = session.accessToken)
        if (userRes.isSuccess) {
            val uObj = api.parseJsonObjectSafe(userRes.body)
            val meta = uObj.optJSONObject("user_metadata") ?: JSONObject()
            val name = meta.optCleanString("display_name").ifBlank { meta.optCleanString("full_name") }
            val canonicalAvatar = normalizeCanonicalAvatarPath(meta.optCleanString("avatar_url"))
            return Result.success(name to canonicalAvatar)
        }
        return Result.failure(Exception(roleRes.errorMessage ?: userRes.errorMessage ?: "Unable to fetch profile."))
    }

    suspend fun signOut(config: SupabaseConfig, accessToken: String) {
        signedUrlCache.clear()
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
        if (!config.isB2Configured) {
            return Result.failure(
                Exception("Web storage backend URL (API_BASE_URL) is not configured. Open Connection Setup to configure it.")
            )
        }
        val queryRes = api.requestJson(
            config = config,
            method = "GET",
            path = "/rest/v1/storage_objects?is_deleted=eq.false&select=*",
            accessToken = token
        )
        if (!queryRes.isSuccess) {
            return Result.failure(Exception(queryRes.errorMessage ?: "Unable to query storage_objects inventory."))
        }
        val items = api.parseJsonArraySafe(queryRes.body)
        if (items.length() == 0) return Result.success(0)

        var deleted = 0
        for (i in 0 until items.length()) {
            val row = items.optJSONObject(i) ?: continue
            val objectPath = row.optCleanString("object_path")
                .ifBlank { row.optCleanString("path") }
                .ifBlank { row.optCleanString("file_path") }
                .ifBlank { row.optCleanString("storage_path") }
            if (objectPath.isBlank()) continue
            val delRes = deleteEvidenceMedia(config, token, objectPath)
            if (delRes.isSuccess) {
                deleted++
            }
        }
        return Result.success(deleted)
    }

    suspend fun fetchStorageProviderConfig(
        config: SupabaseConfig,
        token: String
    ): StorageProviderConfig? {
        val res = api.requestJson(
            config = config,
            method = "POST",
            path = "/rest/v1/rpc/get_storage_provider_config",
            bodyJson = "{}",
            accessToken = token
        )
        if (!res.isSuccess) return null
        val obj = when {
            res.body.trim().startsWith("[") -> api.parseJsonArraySafe(res.body).optJSONObject(0)
            else -> api.parseJsonObjectSafe(res.body)
        } ?: return null
        return StorageProviderConfig(
            provider = obj.optCleanString("provider").ifBlank { "backblaze_b2" },
            bucketName = obj.optCleanString("bucket_name").ifBlank { "JC-Best-Compliance" },
            isPrivate = obj.optBoolean("is_private", true)
        )
    }

    suspend fun fetchMobileAppConfig(
        config: SupabaseConfig,
        token: String
    ): MobileAppConfig? {
        val res = api.requestJson(
            config = config,
            method = "POST",
            path = "/rest/v1/rpc/get_mobile_app_config",
            bodyJson = "{}",
            accessToken = token
        )
        if (res.isSuccess) {
            val obj = when {
                res.body.trim().startsWith("[") -> api.parseJsonArraySafe(res.body).optJSONObject(0)
                else -> api.parseJsonObjectSafe(res.body)
            }
            if (obj != null) {
                val url = obj.optCleanString("download_url")
                    .ifBlank { obj.optCleanString("apk_url") }
                    .ifBlank { obj.optCleanString("release_url") }
                val version = obj.optCleanString("latest_version")
                    .ifBlank { obj.optCleanString("version_name") }
                    .ifBlank { obj.optCleanString("version") }
                val notes = obj.optCleanString("release_notes")
                    .ifBlank { obj.optCleanString("notes") }
                val minVer = obj.optCleanString("min_version")
                if (url.isNotBlank() || version.isNotBlank()) {
                    return MobileAppConfig(
                        latestVersion = version,
                        downloadUrl = url,
                        releaseNotes = notes,
                        minVersion = minVer
                    )
                }
            }
        }
        return null
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
            isActive = activeFlag,
            schoolSection = o.optCleanString("school_section"),
            subjects = o.optCleanString("subjects")
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
                    avatarUrl = normalizeCanonicalAvatarPath(o.optCleanString("avatar_url"))
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
            createdAt = o.optCleanString("created_at"),
            schoolSection = o.optCleanString("school_section").takeIf { it.isNotBlank() },
            disciplineGroup = o.optCleanString("discipline_group").takeIf { it.isNotBlank() },
            session = o.optCleanString("session").takeIf { it.isNotBlank() }
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

    private suspend fun executeReportMutationWithRetry(
        config: SupabaseConfig,
        method: String,
        path: String,
        payload: JSONObject,
        token: String,
        maxRetries: Int = 16
    ): Pair<ApiResult, JSONObject> {
        val current = JSONObject(payload.toString())
        val optionalCols = listOf(
            "role", "staff_group", "department", "assigned_location", "staff_category",
            "check_id", "class_id", "subject_id", "reporter_id", "reporting_officer",
            "evidence_name", "evidence_urls", "evidence_types", "evidence_notes",
            "pending_at", "pending_by", "is_edited", "edited_at",
            "date", "exception_text", "effect_text", "violation_level"
        )
        var res = api.requestJson(
            config,
            method,
            path,
            current.toString(),
            token,
            mapOf("Prefer" to "return=representation")
        )
        var attempts = 0
        var divisionToggled = false
        while (!res.isSuccess && attempts < maxRetries) {
            val msg = res.errorMessage.orEmpty()
            val msgLow = msg.lowercase()
            var changed = false

            // 1. Check PostgREST missing column / schema cache mismatch
            val match = missingColRegex1.find(msg) ?: missingColRegex2.find(msg)
            val regexCol = match?.groupValues?.getOrNull(1)?.trim()
            if (!regexCol.isNullOrBlank() && current.has(regexCol)) {
                current.remove(regexCol)
                changed = true
            } else {
                for (col in optionalCols) {
                    if (current.has(col) && msgLow.contains(col) &&
                        (msgLow.contains("column") || msgLow.contains("schema cache") || msgLow.contains("could not find"))
                    ) {
                        current.remove(col)
                        changed = true
                    }
                }
            }

            // 2. Check Foreign Key (23503) or Invalid UUID (22P02) on optional relation columns
            if (!changed && (msgLow.contains("foreign key") || msgLow.contains("23503") || msgLow.contains("22p02") || msgLow.contains("invalid input syntax for type uuid"))) {
                when {
                    msgLow.contains("check_id") && current.has("check_id") -> {
                        current.remove("check_id")
                        changed = true
                    }
                    msgLow.contains("class_id") && current.has("class_id") -> {
                        current.remove("class_id")
                        changed = true
                    }
                    msgLow.contains("subject_id") && current.has("subject_id") -> {
                        current.remove("subject_id")
                        changed = true
                    }
                    msgLow.contains("reporter_id") && current.has("reporter_id") -> {
                        current.remove("reporter_id")
                        changed = true
                    }
                }
            }

            // 3. Legacy Operation vs Operations division check compatibility
            if (!changed && !divisionToggled && msgLow.contains("division_check")) {
                val curDiv = current.optString("division", "")
                if (curDiv.equals("Operation", ignoreCase = true)) {
                    current.put("division", "Operations")
                    divisionToggled = true
                    changed = true
                } else if (curDiv.equals("Operations", ignoreCase = true)) {
                    current.put("division", "Operation")
                    divisionToggled = true
                    changed = true
                }
            }

            if (!changed) break
            attempts++
            res = api.requestJson(
                config,
                method,
                path,
                current.toString(),
                token,
                mapOf("Prefer" to "return=representation")
            )
        }
        return res to current
    }

    suspend fun createReport(
        config: SupabaseConfig,
        session: UserSession,
        payload: JSONObject
    ): Result<ComplianceReport> {
        val (res, submitted) = executeReportMutationWithRetry(
            config = config,
            method = "POST",
            path = "/rest/v1/compliance_reports",
            payload = payload,
            token = session.accessToken
        )

        if (!res.isSuccess) {
            val rawErr = res.errorMessage.orEmpty()
            val friendly = when {
                rawErr.contains("chk_compliance_reports_staff_integrity", ignoreCase = true) ->
                    "Staff selection or Operation division constraint check failed on the database. Ensure a staff member is selected for Academic reports."
                rawErr.contains("42501") || rawErr.contains("row-level security", ignoreCase = true) ->
                    "Permission denied by server security policy. Please sign out and sign back in."
                else -> rawErr.ifBlank { "Failed to submit compliance report." }
            }
            return Result.failure(Exception(friendly))
        }

        val arr = api.parseJsonArraySafe(res.body)
        if (arr.length() > 0) {
            return Result.success(parseReport(arr.getJSONObject(0)))
        }

        val obj = api.parseJsonObjectSafe(res.body)
        if (obj.length() > 0 && obj.has("report_number")) {
            return Result.success(parseReport(obj))
        }

        // HTTP 2xx with empty body (e.g. restrictive SELECT RETURNING policy): build local optimistic model
        val fallback = JSONObject(submitted.toString()).apply {
            if (optString("id").isBlank()) put("id", java.util.UUID.randomUUID().toString())
            if (optString("created_at").isBlank()) put("created_at", java.time.Instant.now().toString())
        }
        return Result.success(parseReport(fallback))
    }

    suspend fun updateReport(
        config: SupabaseConfig,
        token: String,
        reportId: String,
        updates: JSONObject
    ): Result<ComplianceReport> {
        val (res, _) = executeReportMutationWithRetry(
            config = config,
            method = "PATCH",
            path = "/rest/v1/compliance_reports?id=eq.${enc(reportId)}",
            payload = updates,
            token = token
        )
        if (!res.isSuccess) {
            return Result.failure(Exception(res.errorMessage ?: "Failed to update report."))
        }
        val arr = api.parseJsonArraySafe(res.body)
        if (arr.length() > 0) {
            return Result.success(parseReport(arr.getJSONObject(0)))
        }
        val obj = api.parseJsonObjectSafe(res.body)
        if (obj.length() > 0 && (obj.has("id") || obj.has("report_number"))) {
            return Result.success(parseReport(obj))
        }
        return Result.failure(Exception("Report update returned no data."))
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
                val notifType = o.optCleanString("type")
                val forCurrentUser = when {
                    recipientUid != null && recipientUid.equals(session.userId, true) -> true
                    recipientRole.equals("super_admin", true) && session.role.isSuperAdmin -> true
                    notifType.equals("NEW_SUBMISSION", true) ->
                        session.role.isSuperAdmin &&
                            (recipientRole.equals("super_admin", true) || recipientRole.equals("admin", true))
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
    // STORAGE (BACKBLAZE B2 VIA WEB PROXY + LEGACY SUPABASE FALLBACK)
    // ---------------------------------------------------------------------
    suspend fun uploadEvidenceFile(
        config: SupabaseConfig,
        token: String,
        reportNumber: String,
        file: File,
        mimeType: String,
        reportId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val bytes = file.readBytes()
        uploadBytesToB2(
            config = config,
            token = token,
            reportScopeId = reportId?.takeIf { it.isNotBlank() } ?: reportNumber,
            bytes = bytes,
            fileName = file.name,
            mimeType = mimeType,
            category = "evidence",
            reportId = reportId ?: reportNumber,
            generateThumbnail = true
        )
    }

    suspend fun uploadEvidenceUri(
        config: SupabaseConfig,
        token: String,
        reportNumber: String,
        uri: Uri,
        fallbackMime: String,
        reportId: String? = null
    ): Result<Triple<String, String, String>> = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: fallbackMime
        val name = resolveUriDisplayName(uri) ?: "evidence_${System.currentTimeMillis()}"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext Result.failure(Exception("Unable to read selected evidence file."))
        val uploadRes = uploadBytesToB2(
            config = config,
            token = token,
            reportScopeId = reportId?.takeIf { it.isNotBlank() } ?: reportNumber,
            bytes = bytes,
            fileName = name,
            mimeType = mime,
            category = "evidence",
            reportId = reportId ?: reportNumber,
            generateThumbnail = true
        )
        uploadRes.map { path -> Triple(path, name, mime) }
    }

    private suspend fun uploadBytesToB2(
        config: SupabaseConfig,
        token: String,
        reportScopeId: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        category: String = "evidence",
        reportId: String? = null,
        generateThumbnail: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!config.isB2Configured) {
            return@withContext Result.failure(
                Exception("Web storage backend URL (API_BASE_URL) is not configured. Open Connection Setup to configure it.")
            )
        }
        val maxBytes = 50 * 1024 * 1024
        if (bytes.size > maxBytes) {
            return@withContext Result.failure(Exception("Evidence file exceeds 50 MB upload limit."))
        }

        val cleanScope = reportScopeId
            .trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "audit" }
        val random16Hex = UUID.randomUUID().toString().replace("-", "").take(16).lowercase(Locale.US)
        val dotIdx = fileName.lastIndexOf('.')
        val rawBase = if (dotIdx > 0) fileName.substring(0, dotIdx) else fileName
        val rawExt = if (dotIdx > 0 && dotIdx < fileName.length - 1) fileName.substring(dotIdx + 1) else ""
        val sanitizedBaseName = rawBase
            .trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_', '.')
            .ifBlank { "file" }
            .take(64)
        val ext = rawExt.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "").ifBlank {
            mimeToExtension(mimeType)
        }

        val objectPath = "evidence/$cleanScope/$random16Hex/$sanitizedBaseName.$ext"
        val uploadEndpoint = "${config.normalizedApiBaseUrl}/api/storage/b2/upload"
        val baseHeaders = buildB2AuthHeaders(config, token)
        val uploadHeaders = baseHeaders + buildMap {
            put("x-object-path", enc(objectPath))
            put("x-content-type", mimeType)
            put("x-file-name", enc("$sanitizedBaseName.$ext"))
            put("x-category", category)
            if (!reportId.isNullOrBlank()) {
                put("x-report-id", reportId)
            }
        }

        val res = api.postRawBytesToUrl(uploadEndpoint, bytes, uploadHeaders)
        if (!res.isSuccess) {
            return@withContext Result.failure(Exception(res.errorMessage ?: "Failed to upload evidence to Backblaze B2."))
        }

        // Best-effort companion WebP thumbnail for images/videos (matches web admin convention)
        if (generateThumbnail && (mimeType.startsWith("image/", true) || mimeType.startsWith("video/", true))) {
            runCatching {
                val thumbBytes = createWebpThumbnailBytes(bytes, mimeType)
                if (thumbBytes != null && thumbBytes.isNotEmpty()) {
                    val thumbPath = "evidence/$cleanScope/$random16Hex/thumb_$sanitizedBaseName.webp"
                    val thumbHeaders = baseHeaders + buildMap {
                        put("x-object-path", enc(thumbPath))
                        put("x-content-type", "image/webp")
                        put("x-file-name", enc("thumb_$sanitizedBaseName.webp"))
                        put("x-category", "thumbnail")
                        if (!reportId.isNullOrBlank()) {
                            put("x-report-id", reportId)
                        }
                    }
                    api.postRawBytesToUrl(uploadEndpoint, thumbBytes, thumbHeaders)
                }
            }
        }

        val obj = api.parseJsonObjectSafe(res.body)
        val returnedProxy = obj.optCleanString("proxyUrl")
        val returnedPath = obj.optCleanString("path").ifBlank { objectPath }
        val canonicalProxyUrl = when {
            returnedProxy.startsWith("/api/storage/b2/file") -> returnedProxy
            else -> "/api/storage/b2/file?path=${enc(returnedPath)}"
        }
        Result.success(canonicalProxyUrl)
    }

    private fun createWebpThumbnailBytes(bytes: ByteArray, mimeType: String): ByteArray? {
        val rawBitmap: Bitmap? = when {
            mimeType.startsWith("image/", true) -> {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
                val maxSide = maxOf(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
                val sampleSize = (maxSide / 320).coerceAtLeast(1)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            }
            mimeType.startsWith("video/", true) -> {
                val tmp = File.createTempFile("thumb_src_", ".mp4", context.cacheDir)
                try {
                    tmp.writeBytes(bytes)
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(tmp.absolutePath)
                        retriever.getFrameAtTime(200_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.frameAtTime
                    } finally {
                        runCatching { retriever.release() }
                    }
                } finally {
                    tmp.delete()
                }
            }
            else -> null
        }
        if (rawBitmap == null) return null

        val maxDim = 320f
        val scale = minOf(maxDim / rawBitmap.width.coerceAtLeast(1), maxDim / rawBitmap.height.coerceAtLeast(1), 1f)
        val thumbBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                rawBitmap,
                (rawBitmap.width * scale).toInt().coerceAtLeast(1),
                (rawBitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else rawBitmap

        return try {
            ByteArrayOutputStream().use { baos ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                thumbBitmap.compress(format, 75, baos)
                baos.toByteArray()
            }
        } finally {
            if (thumbBitmap !== rawBitmap) thumbBitmap.recycle()
            rawBitmap.recycle()
        }
    }

    private fun mimeToExtension(mimeType: String): String = when (mimeType.lowercase(Locale.US).substringBefore(';').trim()) {
        "image/webp" -> "webp"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/heic" -> "heic"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        "audio/mp4", "audio/m4a", "audio/aac" -> "m4a"
        "audio/mpeg" -> "mp3"
        "audio/wav" -> "wav"
        "application/pdf" -> "pdf"
        else -> "jpg"
    }

    sealed class ParsedMediaRef {
        data class SupabaseLegacy(val bucket: String, val objectPath: String, val alreadySignedUrl: String? = null) : ParsedMediaRef()
        data class B2Object(val objectPath: String, val isBareRelativeEvidence: Boolean = false) : ParsedMediaRef()
        data class DirectUrl(val url: String) : ParsedMediaRef()
    }

    fun parseMediaReference(storedPathOrUrl: String): ParsedMediaRef {
        val raw = storedPathOrUrl.trim()
        val lower = raw.lowercase(Locale.US)

        // 1. Legacy Supabase Storage URLs
        val supaMarker = "/storage/v1/object/"
        val supaIdx = lower.indexOf(supaMarker)
        if (lower.contains("supabase.co/storage/v1/object/") || supaIdx >= 0) {
            if (lower.contains("token=") || lower.contains("/storage/v1/object/public/")) {
                if (raw.startsWith("http://") || raw.startsWith("https://")) {
                    return ParsedMediaRef.SupabaseLegacy("compliance-evidence", "", alreadySignedUrl = raw)
                }
            }
            val afterMarker = raw.substring(supaIdx + supaMarker.length).trimStart('/')
            val withoutMode = when {
                afterMarker.startsWith("public/") -> afterMarker.removePrefix("public/")
                afterMarker.startsWith("sign/") -> afterMarker.removePrefix("sign/")
                afterMarker.startsWith("authenticated/") -> afterMarker.removePrefix("authenticated/")
                else -> afterMarker
            }.substringBefore('?')
            val slashIdx = withoutMode.indexOf('/')
            if (slashIdx > 0 && slashIdx < withoutMode.length - 1) {
                val bucket = withoutMode.substring(0, slashIdx)
                val objPath = dec(withoutMode.substring(slashIdx + 1))
                return ParsedMediaRef.SupabaseLegacy(bucket, objPath)
            }
        }

        // 2. Proxy URL: /api/storage/b2/file?path=... or full https://host/api/storage/b2/file?path=...
        if (lower.contains("/api/storage/b2/file")) {
            val extracted = extractQueryParam(raw, "path")
            if (extracted.isNotBlank()) {
                return ParsedMediaRef.B2Object(extracted.trimStart('/'))
            }
        }

        // 3. b2:// scheme: b2://JC-Best-Compliance/evidence/... or b2://JC-Best-Compliance/avatars/...
        if (lower.startsWith("b2://")) {
            val withoutScheme = raw.substring(5).trimStart('/')
            val evIdx = withoutScheme.indexOf("evidence/")
            val avIdx = withoutScheme.indexOf("avatars/")
            val objectPath = when {
                evIdx >= 0 -> withoutScheme.substring(evIdx)
                avIdx >= 0 -> withoutScheme.substring(avIdx)
                withoutScheme.contains("/") -> withoutScheme.substringAfter("/")
                else -> withoutScheme
            }
            return ParsedMediaRef.B2Object(objectPath.trimStart('/'))
        }

        // 4. Direct B2 friendly URL without Authorization token: https://.../file/<bucket>/<path>
        if ((lower.startsWith("http://") || lower.startsWith("https://")) && lower.contains("/file/")) {
            if (lower.contains("authorization=")) {
                return ParsedMediaRef.DirectUrl(raw)
            }
            val afterFile = raw.substring(lower.indexOf("/file/") + 6).substringBefore('?')
            val slashIdx = afterFile.indexOf('/')
            if (slashIdx > 0 && slashIdx < afterFile.length - 1) {
                val objPath = dec(afterFile.substring(slashIdx + 1)).trimStart('/')
                return ParsedMediaRef.B2Object(objPath)
            }
        }

        // 5. Generic full HTTP/HTTPS URL
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return ParsedMediaRef.DirectUrl(raw)
        }

        // 6. Bare relative object path (e.g. avatars/... or evidence/...)
        val trimmedPath = raw.trimStart('/')
        return ParsedMediaRef.B2Object(
            objectPath = trimmedPath,
            isBareRelativeEvidence = trimmedPath.startsWith("evidence/", ignoreCase = true)
        )
    }

    private fun extractQueryParam(url: String, key: String): String {
        val qIdx = url.indexOf('?')
        if (qIdx < 0 || qIdx >= url.length - 1) return ""
        val query = url.substring(qIdx + 1)
        for (pair in query.split('&')) {
            val eqIdx = pair.indexOf('=')
            val k = if (eqIdx >= 0) pair.substring(0, eqIdx) else pair
            if (k == key && eqIdx >= 0) {
                return dec(pair.substring(eqIdx + 1))
            }
        }
        return ""
    }

    suspend fun getSignedEvidenceUrl(
        config: SupabaseConfig,
        token: String,
        storedPathOrUrl: String
    ): Result<String> {
        val raw = storedPathOrUrl.trim()
        if (raw.isBlank()) return Result.failure(Exception("Evidence path is empty."))

        val now = System.currentTimeMillis()
        signedUrlCache[raw]?.let { cached ->
            if (cached.expiresAtMs > now && cached.url.isNotBlank()) {
                return Result.success(cached.url)
            }
        }

        val resolvedResult: Result<String> = when (val ref = parseMediaReference(raw)) {
            is ParsedMediaRef.DirectUrl -> Result.success(ref.url)
            is ParsedMediaRef.SupabaseLegacy -> signSupabaseLegacyObject(config, token, ref)
            is ParsedMediaRef.B2Object -> {
                val cleanObjPath = ref.objectPath.trimStart('/')
                signedUrlCache[cleanObjPath]?.let { cached ->
                    if (cached.expiresAtMs > now && cached.url.isNotBlank()) {
                        return Result.success(cached.url)
                    }
                }
                if (config.isB2Configured) {
                    val endpoint = "${config.normalizedApiBaseUrl}/api/storage/b2/signed-url"
                    val payload = JSONObject().apply {
                        put("path", cleanObjPath)
                        put("expiresIn", 3600)
                    }.toString()
                    val res = api.postJsonToUrl(endpoint, payload, buildB2AuthHeaders(config, token))
                    if (res.isSuccess) {
                        val obj = api.parseJsonObjectSafe(res.body)
                        val returned = obj.optCleanString("signedUrl")
                            .ifBlank { obj.optCleanString("signedURL") }
                            .ifBlank { obj.optCleanString("url") }
                        if (returned.isNotBlank()) {
                            val resolved = when {
                                returned.startsWith("http://") || returned.startsWith("https://") -> returned
                                returned.startsWith("/") -> "${config.normalizedApiBaseUrl}$returned"
                                else -> "${config.normalizedApiBaseUrl}/$returned"
                            }
                            signedUrlCache[raw] = CachedSignedUrl(resolved, now + signedUrlTtlMs)
                            signedUrlCache[cleanObjPath] = CachedSignedUrl(resolved, now + signedUrlTtlMs)
                            return Result.success(resolved)
                        }
                    }
                    // If bare legacy evidence path isn't in B2 (404), fall back to legacy Supabase Storage
                    if (ref.isBareRelativeEvidence && res.code == 404) {
                        val legacyTry = signSupabaseLegacyObject(
                            config,
                            token,
                            ParsedMediaRef.SupabaseLegacy("compliance-evidence", cleanObjPath)
                        )
                        if (legacyTry.isSuccess) {
                            val u = legacyTry.getOrThrow()
                            signedUrlCache[raw] = CachedSignedUrl(u, now + signedUrlTtlMs)
                            return legacyTry
                        }
                    }
                    // Fallback to authenticated proxy streaming URL on shared web backend
                    val proxyFallback = "${config.normalizedApiBaseUrl}/api/storage/b2/file?path=${enc(cleanObjPath)}"
                    Result.success(proxyFallback)
                } else if (ref.isBareRelativeEvidence) {
                    signSupabaseLegacyObject(
                        config,
                        token,
                        ParsedMediaRef.SupabaseLegacy("compliance-evidence", cleanObjPath)
                    )
                } else {
                    Result.failure(
                        Exception("Web storage backend URL (API_BASE_URL) is not configured to view B2 media.")
                    )
                }
            }
        }
        resolvedResult.onSuccess { url ->
            if (url.isNotBlank() && !isB2FileProxyUrl(config, url)) {
                signedUrlCache[raw] = CachedSignedUrl(url, now + signedUrlTtlMs)
            }
        }
        return resolvedResult
    }

    private suspend fun signSupabaseLegacyObject(
        config: SupabaseConfig,
        token: String,
        ref: ParsedMediaRef.SupabaseLegacy
    ): Result<String> {
        if (!ref.alreadySignedUrl.isNullOrBlank()) return Result.success(ref.alreadySignedUrl)
        val cleanPath = ref.objectPath.trimStart('/')
        if (cleanPath.isBlank()) return Result.failure(Exception("Empty legacy storage path."))
        val payload = JSONObject().apply { put("expiresIn", 3600) }.toString()
        val res = api.requestJson(
            config,
            "POST",
            "/storage/v1/object/sign/${ref.bucket}/$cleanPath",
            payload,
            token
        )
        if (!res.isSuccess) {
            return Result.failure(Exception(res.errorMessage ?: "Unable to authorize legacy evidence view."))
        }
        val obj = api.parseJsonObjectSafe(res.body)
        val signedPath = obj.optString("signedURL").ifBlank { obj.optString("signedUrl") }
        if (signedPath.isBlank()) {
            return Result.failure(Exception("No signed URL returned from legacy storage."))
        }
        val fullUrl = if (signedPath.startsWith("http")) signedPath else {
            val base = config.url.trimEnd('/')
            if (signedPath.startsWith("/")) "$base/storage/v1$signedPath" else "$base/storage/v1/$signedPath"
        }
        return Result.success(fullUrl)
    }

    suspend fun downloadEvidenceToCache(
        config: SupabaseConfig,
        token: String,
        storedPathOrUrl: String,
        suggestedName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        val safeName = suggestedName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "evidence_${System.currentTimeMillis()}" }
        val ref = parseMediaReference(storedPathOrUrl)
        val downloadDir = File(context.cacheDir, "evidence_downloads")

        when (ref) {
            is ParsedMediaRef.B2Object -> {
                if (config.isB2Configured) {
                    val url = "${config.normalizedApiBaseUrl}/api/storage/b2/file?path=${enc(ref.objectPath)}&download=1"
                    val headers = buildB2AuthHeaders(config, token)
                    val ext = ref.objectPath.substringAfterLast('.', "").takeIf { it.length in 2..5 }
                    val fileName = if (!safeName.contains('.') && !ext.isNullOrBlank()) "$safeName.$ext" else safeName
                    val direct = api.downloadToFile(url, File(downloadDir, fileName), headers)
                    if (direct.isSuccess) return@withContext direct
                }
                val signed = getSignedEvidenceUrl(config, token, storedPathOrUrl).getOrElse {
                    return@withContext Result.failure(it)
                }
                val extraHeaders = if (isB2FileProxyUrl(config, signed)) buildB2AuthHeaders(config, token) else emptyMap()
                api.downloadToFile(signed, File(downloadDir, safeName), extraHeaders)
            }
            else -> {
                val signed = getSignedEvidenceUrl(config, token, storedPathOrUrl).getOrElse {
                    return@withContext Result.failure(it)
                }
                api.downloadToFile(signed, File(downloadDir, safeName))
            }
        }
    }

    suspend fun deleteEvidenceMedia(
        config: SupabaseConfig,
        token: String,
        pathOrUrl: String
    ): Result<Unit> {
        val raw = pathOrUrl.trim()
        if (raw.isBlank()) return Result.success(Unit)
        val ref = parseMediaReference(raw)
        if (ref is ParsedMediaRef.SupabaseLegacy || ref is ParsedMediaRef.DirectUrl) {
            return Result.success(Unit)
        }
        if (!config.isB2Configured) {
            return Result.failure(Exception("Web storage backend URL (API_BASE_URL) is not configured."))
        }
        val objectPath = (ref as ParsedMediaRef.B2Object).objectPath
        val payload = JSONObject().apply {
            put("path", objectPath)
        }.toString()
        val endpoint = "${config.normalizedApiBaseUrl}/api/storage/b2/delete"
        val res = api.postJsonToUrl(endpoint, payload, buildB2AuthHeaders(config, token))
        return if (res.isSuccess) Result.success(Unit)
        else Result.failure(Exception(res.errorMessage ?: "Failed to delete B2 storage object."))
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

    // ---------------------------------------------------------------------
    // DURABLE TERM ANALYSIS SNAPSHOTS & PPTX PRESENTATION EXPORT
    // ---------------------------------------------------------------------
    suspend fun fetchTermSnapshots(config: SupabaseConfig, token: String): List<TermAnalysisSnapshot> {
        val res = api.requestJson(
            config,
            "GET",
            "/rest/v1/term_analysis_snapshots?select=*&order=snapshot_version.desc,created_at.desc",
            accessToken = token
        )
        if (!res.isSuccess) return emptyList()
        val arr = api.parseJsonArraySafe(res.body)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                fun jsonField(key: String): String {
                    if (o.isNull(key)) return ""
                    val obj = o.optJSONObject(key)
                    if (obj != null) return obj.toString()
                    val jArr = o.optJSONArray(key)
                    if (jArr != null) return jArr.toString()
                    return o.optCleanString(key)
                }
                add(
                    TermAnalysisSnapshot(
                        id = o.optCleanString("id"),
                        session = o.optCleanString("session"),
                        term = o.optCleanString("term"),
                        snapshotVersion = o.optInt("snapshot_version", 1),
                        officialTermEndDate = o.optCleanString("official_term_end_date").takeIf { it.isNotBlank() },
                        status = o.optCleanString("status").ifBlank { "DRAFT" },
                        reconciliationStatus = o.optCleanString("reconciliation_status").ifBlank { "PENDING" },
                        totalQualifyingExceptions = o.optInt("total_qualifying_exceptions", 0),
                        academicExceptionsCount = o.optInt("academic_exceptions_count", 0),
                        operationsExceptionsCount = o.optInt("operations_exceptions_count", 0),
                        cdsExceptionsCount = o.optInt("cds_exceptions_count", 0),
                        disciplineExceptionsCount = o.optInt("discipline_exceptions_count", 0),
                        staffPopulationDenominator = o.optInt("staff_population_denominator", 0),
                        affectedStaffCount = o.optInt("affected_staff_count", 0),
                        cleanStaffCount = o.optInt("clean_staff_count", 0),
                        overallPerHead = o.optDouble("overall_per_head", 0.0).toFloat(),
                        rawFullSnapshotJson = jsonField("full_snapshot"),
                        rawSectionMetricsJson = jsonField("section_metrics"),
                        rawDisciplineMetricsJson = jsonField("discipline_metrics"),
                        rawClassMetricsJson = jsonField("class_metrics"),
                        rawCategoryMetricsJson = jsonField("category_metrics"),
                        rawCategoryPatternsJson = jsonField("category_patterns"),
                        rawSeverityDistributionJson = jsonField("severity_distribution"),
                        rawExecutiveSummaryJson = jsonField("executive_summary"),
                        rawComparisonsJson = jsonField("comparisons"),
                        rawTrendsJson = jsonField("trends"),
                        createdAt = o.optCleanString("created_at")
                    )
                )
            }
        }
    }

    suspend fun downloadCompliancePptx(
        config: SupabaseConfig,
        token: String,
        payloadJson: String,
        suggestedFilename: String
    ): Result<File> {
        if (!config.isB2Configured) {
            return Result.failure(
                Exception("Web backend API URL is not configured. Add it in Account -> Backend Connection to build official .pptx decks.")
            )
        }
        val safeName = suggestedFilename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val target = File(File(context.cacheDir, "exports"), safeName)
        val endpoint = "${config.normalizedApiBaseUrl}/api/reports/compliance-pptx"
        return api.postJsonDownloadToFile(
            fullUrl = endpoint,
            bodyJson = payloadJson,
            targetFile = target,
            headers = buildB2AuthHeaders(config, token)
        )
    }
}

