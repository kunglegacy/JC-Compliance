package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android Keystore AES-256-GCM credential, lock-state & device-local draft bucket vault.
 * - Persists `isAppLocked` synchronously across process kills and cold launches.
 * - Encrypts saved account password for remembered biometric sign-in after sign-out.
 * - Provides AES-256-GCM string & chunked binary file encryption for per-account device draft buckets.
 */
class EncryptedAuthStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAppLockedSync(): Boolean = prefs.getBoolean(KEY_APP_LOCKED, false)

    fun setAppLockedSync(locked: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCKED, locked).commit()
    }

    fun saveBiometricCredential(email: String, password: String) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || password.isBlank()) return
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(KEY_ALIAS))
            val iv = cipher.iv
            val encrypted = cipher.doFinal(password.toByteArray(StandardCharsets.UTF_8))
            val encodedIv = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encodedData = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            prefs.edit()
                .putString(KEY_BIO_CRED_EMAIL, cleanEmail)
                .putString(KEY_BIO_CRED_DATA, "$encodedIv:$encodedData")
                .commit()
        }
    }

    fun hasBiometricCredentialFor(email: String): Boolean {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return false
        val savedEmail = prefs.getString(KEY_BIO_CRED_EMAIL, null).orEmpty()
        val savedData = prefs.getString(KEY_BIO_CRED_DATA, null).orEmpty()
        return savedEmail == cleanEmail && savedData.contains(":")
    }

    fun decryptBiometricCredential(email: String): String? {
        val cleanEmail = email.trim().lowercase()
        val savedEmail = prefs.getString(KEY_BIO_CRED_EMAIL, null).orEmpty()
        if (cleanEmail.isBlank() || savedEmail != cleanEmail) return null
        val blob = prefs.getString(KEY_BIO_CRED_DATA, null) ?: return null
        val parts = blob.split(":", limit = 2)
        if (parts.size != 2) return null
        return runCatching {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(KEY_ALIAS), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    fun clearBiometricCredential() {
        prefs.edit()
            .remove(KEY_BIO_CRED_EMAIL)
            .remove(KEY_BIO_CRED_DATA)
            .commit()
    }

    /**
     * Encrypts UTF-8 text (used for per-user draft metadata stored in Room) with AES-256-GCM.
     */
    fun encryptDraftText(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(DRAFT_KEY_ALIAS))
            val ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val encB64 = Base64.encodeToString(
                cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8)),
                Base64.NO_WRAP
            )
            "$DRAFT_TEXT_PREFIX$ivB64:$encB64"
        }.getOrDefault(plainText)
    }

    fun decryptDraftText(value: String): String {
        if (value.isEmpty() || !value.startsWith(DRAFT_TEXT_PREFIX)) return value
        val body = value.removePrefix(DRAFT_TEXT_PREFIX)
        val parts = body.split(":", limit = 2)
        if (parts.size != 2) return ""
        return runCatching {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(DRAFT_KEY_ALIAS), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    fun saveEncryptedFormDraft(userId: String, json: String) {
        val cleanId = userId.trim()
        if (cleanId.isBlank() || json.isBlank()) return
        val key = "$KEY_FORM_DRAFT_PREFIX${safeKey(cleanId)}"
        prefs.edit().putString(key, encryptDraftText(json)).commit()
    }

    fun loadEncryptedFormDraft(userId: String): String? {
        val cleanId = userId.trim()
        if (cleanId.isBlank()) return null
        val key = "$KEY_FORM_DRAFT_PREFIX${safeKey(cleanId)}"
        val raw = prefs.getString(key, null) ?: return null
        return decryptDraftText(raw).takeIf { it.isNotBlank() }
    }

    fun clearEncryptedFormDraft(userId: String) {
        val cleanId = userId.trim()
        if (cleanId.isBlank()) return
        val key = "$KEY_FORM_DRAFT_PREFIX${safeKey(cleanId)}"
        prefs.edit().remove(key).commit()
    }

    private fun safeKey(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "anonymous" }

    /**
     * Chunked AES-256-GCM file encryption into an account-isolated device bucket.
     * Supports large video files without loading the whole file into heap.
     */
    fun encryptFileToBucket(sourceFile: File, encryptedDest: File): Boolean {
        if (!sourceFile.exists() || !sourceFile.isFile) return false
        return runCatching {
            encryptedDest.parentFile?.mkdirs()
            val key = getOrCreateSecretKey(DRAFT_KEY_ALIAS)
            val buffer = ByteArray(CHUNK_SIZE_BYTES)
            BufferedInputStream(FileInputStream(sourceFile)).use { input ->
                DataOutputStream(BufferedOutputStream(FileOutputStream(encryptedDest))).use { output ->
                    output.write(FILE_MAGIC)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        val cipher = Cipher.getInstance(TRANSFORMATION)
                        cipher.init(Cipher.ENCRYPT_MODE, key)
                        val iv = cipher.iv
                        val encryptedChunk = cipher.doFinal(buffer, 0, read)
                        output.writeInt(iv.size)
                        output.write(iv)
                        output.writeInt(encryptedChunk.size)
                        output.write(encryptedChunk)
                    }
                    output.flush()
                }
            }
            encryptedDest.exists() && encryptedDest.length() > FILE_MAGIC.size
        }.getOrDefault(false)
    }

    /**
     * Decrypts a chunked AES-256-GCM bucket file into an active-session cache file for UI/player/upload use.
     */
    fun decryptBucketFileToCache(encryptedFile: File, destPlainFile: File): Boolean {
        if (!encryptedFile.exists() || !encryptedFile.isFile) return false
        return runCatching {
            destPlainFile.parentFile?.mkdirs()
            val key = getOrCreateSecretKey(DRAFT_KEY_ALIAS)
            DataInputStream(BufferedInputStream(FileInputStream(encryptedFile))).use { input ->
                val magic = ByteArray(FILE_MAGIC.size)
                input.readFully(magic)
                if (!magic.contentEquals(FILE_MAGIC)) {
                    // Fallback copy if file is a legacy unencrypted draft file
                    encryptedFile.copyTo(destPlainFile, overwrite = true)
                    return true
                }
                BufferedOutputStream(FileOutputStream(destPlainFile)).use { output ->
                    while (true) {
                        val ivLen = try {
                            input.readInt()
                        } catch (_: java.io.EOFException) {
                            break
                        }
                        if (ivLen <= 0 || ivLen > 64) return false
                        val iv = ByteArray(ivLen)
                        input.readFully(iv)
                        val chunkLen = input.readInt()
                        if (chunkLen <= 0 || chunkLen > CHUNK_SIZE_BYTES + 128) return false
                        val cipherChunk = ByteArray(chunkLen)
                        input.readFully(cipherChunk)

                        val cipher = Cipher.getInstance(TRANSFORMATION)
                        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                        val plainChunk = cipher.doFinal(cipherChunk)
                        output.write(plainChunk)
                    }
                    output.flush()
                }
            }
            destPlainFile.exists()
        }.getOrDefault(false)
    }

    private fun getOrCreateSecretKey(alias: String): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "jcbest_vault_prefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jcbest_biometric_vault_v1"
        private const val DRAFT_KEY_ALIAS = "jcbest_draft_bucket_vault_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val DRAFT_TEXT_PREFIX = "encv1:"
        private val FILE_MAGIC = byteArrayOf('J'.code.toByte(), 'C'.code.toByte(), 'B'.code.toByte(), '1'.code.toByte())
        private const val CHUNK_SIZE_BYTES = 256 * 1024
        private const val KEY_APP_LOCKED = "is_app_locked_sync"
        private const val KEY_BIO_CRED_EMAIL = "bio_cred_email"
        private const val KEY_BIO_CRED_DATA = "bio_cred_blob"
        private const val KEY_FORM_DRAFT_PREFIX = "enc_form_draft_"
    }
}
