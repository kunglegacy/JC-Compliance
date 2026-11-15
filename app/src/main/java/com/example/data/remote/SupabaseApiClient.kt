package com.example.data.remote

import com.example.data.model.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ApiResult(
    val isSuccess: Boolean,
    val code: Int,
    val body: String,
    val errorMessage: String? = null
)

class SupabaseApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun requestJson(
        config: SupabaseConfig,
        method: String,
        path: String,
        bodyJson: String? = null,
        accessToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): ApiResult = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext ApiResult(
                isSuccess = false,
                code = 400,
                body = "",
                errorMessage = "Supabase connection is not configured."
            )
        }
        val cleanBase = config.url.trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        val url = "$cleanBase$cleanPath"

        val reqBody = when {
            bodyJson != null -> bodyJson.toRequestBody(jsonMediaType)
            method.uppercase() in listOf("POST", "PUT", "PATCH") -> "{}".toRequestBody(jsonMediaType)
            else -> null
        }

        val builder = Request.Builder()
            .url(url)
            .header("apikey", config.anonKey)
            .header("Accept", "application/json")

        val bearer = accessToken?.takeIf { it.isNotBlank() } ?: config.anonKey
        builder.header("Authorization", "Bearer $bearer")

        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        builder.method(method.uppercase(), reqBody)

        try {
            client.newCall(builder.build()).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    ApiResult(true, resp.code, respBody, null)
                } else {
                    ApiResult(false, resp.code, respBody, extractError(respBody, resp.code))
                }
            }
        } catch (e: Exception) {
            ApiResult(false, 0, "", e.message ?: "Network request failed.")
        }
    }

    suspend fun uploadBinary(
        config: SupabaseConfig,
        bucket: String,
        objectPath: String,
        bytes: ByteArray,
        mimeType: String,
        accessToken: String?
    ): ApiResult = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext ApiResult(false, 400, "", "Supabase connection is not configured.")
        }
        val cleanBase = config.url.trimEnd('/')
        val url = "$cleanBase/storage/v1/object/$bucket/${objectPath.trimStart('/')}"
        val reqBody = bytes.toRequestBody(mimeType.toMediaType())
        val bearer = accessToken?.takeIf { it.isNotBlank() } ?: config.anonKey

        val req = Request.Builder()
            .url(url)
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer $bearer")
            .header("Content-Type", mimeType)
            .header("x-upsert", "true")
            .post(reqBody)
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    ApiResult(true, resp.code, respBody, null)
                } else {
                    ApiResult(false, resp.code, respBody, extractError(respBody, resp.code))
                }
            }
        } catch (e: Exception) {
            ApiResult(false, 0, "", e.message ?: "Evidence upload failed.")
        }
    }

    private fun extractError(rawBody: String, statusCode: Int): String {
        if (rawBody.isBlank()) return "Server error ($statusCode)"
        return try {
            val obj = JSONObject(rawBody)
            obj.optString("error_description").takeIf { it.isNotBlank() }
                ?: obj.optString("msg").takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
                ?: obj.optString("error").takeIf { it.isNotBlank() }
                ?: obj.optString("hint").takeIf { it.isNotBlank() }
                ?: "Request error ($statusCode)"
        } catch (_: Exception) {
            rawBody.take(180)
        }
    }

    fun parseJsonArraySafe(raw: String): JSONArray = try {
        JSONArray(raw)
    } catch (_: Exception) {
        JSONArray()
    }

    fun parseJsonObjectSafe(raw: String): JSONObject = try {
        JSONObject(raw)
    } catch (_: Exception) {
        JSONObject()
    }
}
