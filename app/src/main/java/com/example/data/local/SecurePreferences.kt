package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AccentPalette
import com.example.data.model.ComplianceCheck
import com.example.data.model.ComplianceDivision
import com.example.data.model.LockPolicy
import com.example.data.model.SupabaseConfig
import com.example.data.model.ThemePreference
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jcbest_prefs")

data class AppPreferencesState(
    val supabaseConfig: SupabaseConfig,
    val session: UserSession?,
    val themePreference: ThemePreference = ThemePreference.DARK,
    val accentPalette: AccentPalette = AccentPalette.SOLAR,
    val autoFillEnabled: Boolean = true,
    val avatarUri: String = "",
    val customDisplayName: String = "",
    val deviceSetupCompleted: Boolean = false,
    val lockPolicy: LockPolicy = LockPolicy.HOURS_24,
    val biometricsEnabled: Boolean = false,
    val biometricsUserEmail: String = "",
    val lastActiveMillis: Long = 0L,
    val isManuallyLocked: Boolean = false,
    val customChecks: List<ComplianceCheck> = emptyList(),
    val rememberedEmail: String = "",
    val rememberedDisplayName: String = "",
    val rememberedAvatarUri: String = ""
)

class SecurePreferences(private val context: Context) {

    private object Keys {
        val SUPABASE_URL = stringPreferencesKey("supabase_url")
        val SUPABASE_ANON_KEY = stringPreferencesKey("supabase_anon_key")
        val DEVICE_ID = stringPreferencesKey("device_id")

        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val STAFF_NUMBER = stringPreferencesKey("staff_number")
        val USER_ROLE = stringPreferencesKey("user_role")
        val USER_STATUS = stringPreferencesKey("user_status")
        val USER_VERIFIED = booleanPreferencesKey("user_verified")
        val USER_OWNER = booleanPreferencesKey("user_owner")

        val REMEMBERED_EMAIL = stringPreferencesKey("remembered_email")
        val REMEMBERED_DISPLAY_NAME = stringPreferencesKey("remembered_display_name")
        val REMEMBERED_AVATAR_URI = stringPreferencesKey("remembered_avatar_uri")

        val THEME_PREF = stringPreferencesKey("theme_pref")
        val ACCENT_PALETTE = stringPreferencesKey("accent_palette")
        val AUTOFILL_ENABLED = booleanPreferencesKey("autofill_enabled")
        val AVATAR_URI = stringPreferencesKey("avatar_uri")
        val CUSTOM_DISPLAY_NAME = stringPreferencesKey("custom_display_name")

        val SETUP_COMPLETED_PREFIX = "setup_completed_"
        val LOCK_POLICY_PREFIX = "lock_policy_"
        val BIOMETRICS_ENABLED = booleanPreferencesKey("biometrics_enabled")
        val BIOMETRICS_EMAIL = stringPreferencesKey("biometrics_email")
        val LAST_ACTIVE_MILLIS = longPreferencesKey("last_active_millis")
        val MANUALLY_LOCKED = booleanPreferencesKey("manually_locked")
        val CUSTOM_CHECKS_JSON = stringPreferencesKey("custom_checks_json")
    }

    val state: Flow<AppPreferencesState> = context.dataStore.data.map { prefs ->
        val envUrl = (System.getenv("SUPABASE_URL") ?: System.getenv("VITE_SUPABASE_URL")).orEmpty()
        val envKey = (System.getenv("SUPABASE_PUBLISHABLE_KEY")
            ?: System.getenv("VITE_SUPABASE_PUBLISHABLE_KEY")
            ?: System.getenv("SUPABASE_ANON_KEY")
            ?: System.getenv("SUPABASE_KEY")).orEmpty()

        val buildUrl = readBuildConfig("VITE_SUPABASE_URL")
            .ifBlank { readBuildConfig("SUPABASE_URL") }
            .ifBlank { envUrl }
        val buildKey = readBuildConfig("VITE_SUPABASE_PUBLISHABLE_KEY")
            .ifBlank { readBuildConfig("SUPABASE_PUBLISHABLE_KEY") }
            .ifBlank { envKey }

        val storedUrl = prefs[Keys.SUPABASE_URL].orEmpty()
        val storedKey = prefs[Keys.SUPABASE_ANON_KEY].orEmpty()
        val effectiveUrl = storedUrl.ifBlank { buildUrl }.trim().trim('"', '\'').trimEnd('/')
        val effectiveKey = storedKey.ifBlank { buildKey }.trim().trim('"', '\'')

        val userId = prefs[Keys.USER_ID].orEmpty()
        val email = prefs[Keys.USER_EMAIL].orEmpty()
        val access = prefs[Keys.ACCESS_TOKEN].orEmpty()
        val refresh = prefs[Keys.REFRESH_TOKEN].orEmpty()

        val session = if (userId.isNotBlank() && access.isNotBlank()) {
            UserSession(
                userId = userId,
                email = email,
                accessToken = access,
                refreshToken = refresh,
                displayName = prefs[Keys.DISPLAY_NAME].orEmpty(),
                staffNumber = prefs[Keys.STAFF_NUMBER].orEmpty(),
                role = UserRole.fromKey(prefs[Keys.USER_ROLE]),
                status = prefs[Keys.USER_STATUS] ?: "active",
                isVerified = prefs[Keys.USER_VERIFIED] ?: true,
                isOwner = prefs[Keys.USER_OWNER] ?: false
            )
        } else null

        val setupKey = booleanPreferencesKey("${Keys.SETUP_COMPLETED_PREFIX}$userId")
        val policyKey = stringPreferencesKey("${Keys.LOCK_POLICY_PREFIX}$userId")

        AppPreferencesState(
            supabaseConfig = SupabaseConfig(effectiveUrl, effectiveKey),
            session = session,
            themePreference = if (prefs[Keys.THEME_PREF].isNullOrBlank()) ThemePreference.DARK else ThemePreference.fromKey(prefs[Keys.THEME_PREF]),
            accentPalette = if (prefs[Keys.ACCENT_PALETTE].isNullOrBlank()) AccentPalette.SOLAR else AccentPalette.fromKey(prefs[Keys.ACCENT_PALETTE]),
            autoFillEnabled = prefs[Keys.AUTOFILL_ENABLED] ?: true,
            avatarUri = prefs[Keys.AVATAR_URI].orEmpty(),
            customDisplayName = prefs[Keys.CUSTOM_DISPLAY_NAME].orEmpty(),
            deviceSetupCompleted = if (userId.isNotBlank()) (prefs[setupKey] ?: false) else false,
            lockPolicy = LockPolicy.fromKey(if (userId.isNotBlank()) prefs[policyKey] else null),
            biometricsEnabled = prefs[Keys.BIOMETRICS_ENABLED] ?: false,
            biometricsUserEmail = prefs[Keys.BIOMETRICS_EMAIL].orEmpty(),
            lastActiveMillis = prefs[Keys.LAST_ACTIVE_MILLIS] ?: 0L,
            isManuallyLocked = prefs[Keys.MANUALLY_LOCKED] ?: false,
            customChecks = parseCustomChecks(prefs[Keys.CUSTOM_CHECKS_JSON].orEmpty()),
            rememberedEmail = prefs[Keys.REMEMBERED_EMAIL] ?: prefs[Keys.USER_EMAIL].orEmpty(),
            rememberedDisplayName = prefs[Keys.REMEMBERED_DISPLAY_NAME] ?: prefs[Keys.DISPLAY_NAME].orEmpty(),
            rememberedAvatarUri = prefs[Keys.REMEMBERED_AVATAR_URI] ?: prefs[Keys.AVATAR_URI].orEmpty()
        )
    }

    suspend fun getOrCreateDeviceId(): String {
        val current = context.dataStore.data.first()[Keys.DEVICE_ID]
        if (!current.isNullOrBlank()) return current
        val created = "and-${UUID.randomUUID().toString().take(12)}"
        context.dataStore.edit { it[Keys.DEVICE_ID] = created }
        return created
    }

    suspend fun saveSupabaseConfig(url: String, anonKey: String) {
        context.dataStore.edit {
            it[Keys.SUPABASE_URL] = url.trim().trimEnd('/')
            it[Keys.SUPABASE_ANON_KEY] = anonKey.trim()
        }
    }

    suspend fun saveSession(session: UserSession) {
        context.dataStore.edit {
            it[Keys.USER_ID] = session.userId
            it[Keys.USER_EMAIL] = session.email
            it[Keys.ACCESS_TOKEN] = session.accessToken
            it[Keys.REFRESH_TOKEN] = session.refreshToken
            it[Keys.DISPLAY_NAME] = session.displayName
            it[Keys.STAFF_NUMBER] = session.staffNumber
            it[Keys.USER_ROLE] = session.role.key
            it[Keys.USER_STATUS] = session.status
            it[Keys.USER_VERIFIED] = session.isVerified
            it[Keys.USER_OWNER] = session.isOwner
            it[Keys.LAST_ACTIVE_MILLIS] = System.currentTimeMillis()
            it[Keys.MANUALLY_LOCKED] = false
            // Persist remembered credentials across sign-out
            if (session.email.isNotBlank()) it[Keys.REMEMBERED_EMAIL] = session.email
            if (session.displayName.isNotBlank()) it[Keys.REMEMBERED_DISPLAY_NAME] = session.displayName
            val currentAvatar = it[Keys.AVATAR_URI]
            if (!currentAvatar.isNullOrBlank()) it[Keys.REMEMBERED_AVATAR_URI] = currentAvatar
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(Keys.ACCESS_TOKEN)
            it.remove(Keys.REFRESH_TOKEN)
            it.remove(Keys.USER_ID)
            it.remove(Keys.USER_EMAIL)
            it.remove(Keys.DISPLAY_NAME)
            it.remove(Keys.STAFF_NUMBER)
            it.remove(Keys.USER_ROLE)
            it.remove(Keys.USER_STATUS)
            it.remove(Keys.USER_VERIFIED)
            it.remove(Keys.USER_OWNER)
            it[Keys.MANUALLY_LOCKED] = false
            // Note: Keys.REMEMBERED_EMAIL, REMEMBERED_DISPLAY_NAME, and REMEMBERED_AVATAR_URI are intentionally preserved
        }
    }

    suspend fun saveThemePreference(pref: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME_PREF] = pref.key }
    }

    suspend fun saveAccentPalette(palette: AccentPalette) {
        context.dataStore.edit { it[Keys.ACCENT_PALETTE] = palette.key }
    }

    suspend fun saveAutoFillEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTOFILL_ENABLED] = enabled }
    }

    suspend fun saveProfileCustomization(displayName: String?, avatarUri: String?) {
        context.dataStore.edit {
            if (displayName != null) {
                it[Keys.CUSTOM_DISPLAY_NAME] = displayName.trim()
                it[Keys.REMEMBERED_DISPLAY_NAME] = displayName.trim()
            }
            if (avatarUri != null) {
                it[Keys.AVATAR_URI] = avatarUri
                it[Keys.REMEMBERED_AVATAR_URI] = avatarUri
            }
        }
    }

    suspend fun completeDeviceSetup(
        userId: String,
        lockPolicy: LockPolicy,
        biometricsEnabled: Boolean,
        biometricsEmail: String
    ) {
        context.dataStore.edit {
            it[booleanPreferencesKey("${Keys.SETUP_COMPLETED_PREFIX}$userId")] = true
            it[stringPreferencesKey("${Keys.LOCK_POLICY_PREFIX}$userId")] = lockPolicy.key
            it[Keys.BIOMETRICS_ENABLED] = biometricsEnabled
            if (biometricsEnabled && biometricsEmail.isNotBlank()) {
                it[Keys.BIOMETRICS_EMAIL] = biometricsEmail
            }
            it[Keys.LAST_ACTIVE_MILLIS] = System.currentTimeMillis()
            it[Keys.MANUALLY_LOCKED] = false
        }
    }

    suspend fun updateLockPolicy(userId: String, lockPolicy: LockPolicy) {
        context.dataStore.edit {
            it[stringPreferencesKey("${Keys.LOCK_POLICY_PREFIX}$userId")] = lockPolicy.key
        }
    }

    suspend fun updateBiometrics(enabled: Boolean, userEmail: String) {
        context.dataStore.edit {
            it[Keys.BIOMETRICS_ENABLED] = enabled
            if (enabled && userEmail.isNotBlank()) {
                it[Keys.BIOMETRICS_EMAIL] = userEmail
            } else if (!enabled) {
                it.remove(Keys.BIOMETRICS_EMAIL)
            }
        }
    }

    suspend fun touchLastActive() {
        context.dataStore.edit {
            it[Keys.LAST_ACTIVE_MILLIS] = System.currentTimeMillis()
            it[Keys.MANUALLY_LOCKED] = false
        }
    }

    suspend fun setManuallyLocked(locked: Boolean) {
        context.dataStore.edit { it[Keys.MANUALLY_LOCKED] = locked }
    }

    suspend fun addCustomCheck(check: ComplianceCheck) {
        val current = state.first().customChecks.toMutableList()
        if (current.none { it.id == check.id }) {
            current.add(check)
        }
        val arr = JSONArray()
        current.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("category", c.category)
                put("division", c.division.dbValue)
                put("subSection", c.subSection)
            })
        }
        context.dataStore.edit { it[Keys.CUSTOM_CHECKS_JSON] = arr.toString() }
    }

    private fun parseCustomChecks(raw: String): List<ComplianceCheck> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val div = ComplianceDivision.fromAny(obj.optString("division")) ?: ComplianceDivision.ACADEMIC
                    add(
                        ComplianceCheck(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            category = obj.optString("category"),
                            division = div,
                            subSection = obj.optString("subSection"),
                            isCustom = true
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readBuildConfig(fieldName: String): String {
        return try {
            val clazz = Class.forName("com.example.BuildConfig")
            val field = clazz.getField(fieldName)
            (field.get(null) as? String).orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}
