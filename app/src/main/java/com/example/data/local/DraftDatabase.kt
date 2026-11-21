package com.example.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ComplianceDivision
import com.example.data.model.DraftMediaItem
import com.example.data.model.OfflineDraft
import com.example.data.model.ReportEvidenceAttachment
import com.example.data.model.ReportFormDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Entity(tableName = "offline_drafts")
data class DraftEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
    val title: String,
    val notes: String,
    val mediaPath: String?,
    val mediaType: String,
    val mediaName: String?,
    val mediaItemsJson: String = "[]",
    val incidentDate: String,
    val createdAtMillis: Long,
    val formDataJson: String = "",
    val isQueuedForSubmit: Boolean = false,
    val queuedAtMillis: Long = 0L
) {
    companion object {
        fun encodeMediaItems(items: List<DraftMediaItem>): String {
            val arr = JSONArray()
            items.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("pathOrUri", item.pathOrUri)
                        put("mediaType", item.mediaType)
                        put("capturedAtLabel", item.capturedAtLabel)
                    }
                )
            }
            return arr.toString()
        }

        fun decodeMediaItems(raw: String): List<DraftMediaItem> {
            if (raw.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val path = o.optString("pathOrUri")
                        if (path.isNotBlank()) {
                            add(
                                DraftMediaItem(
                                    id = o.optString("id").ifBlank { "dm_${i}_${path.hashCode()}" },
                                    pathOrUri = path,
                                    mediaType = o.optString("mediaType", "image"),
                                    capturedAtLabel = o.optString("capturedAtLabel")
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun formatDraftDateTime(millis: Long): String {
            val safe = if (millis > 0L) millis else System.currentTimeMillis()
            return SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US).format(Date(safe))
        }
    }
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM offline_drafts WHERE userId = :userId ORDER BY createdAtMillis DESC")
    fun observeForUser(userId: String): Flow<List<DraftEntity>>

    @Query("SELECT * FROM offline_drafts WHERE userId = :userId ORDER BY createdAtMillis DESC")
    suspend fun listForUser(userId: String): List<DraftEntity>

    @Query("SELECT * FROM offline_drafts WHERE userId = :userId AND isQueuedForSubmit = 1 ORDER BY queuedAtMillis ASC")
    suspend fun listQueuedForUser(userId: String): List<DraftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DraftEntity)

    @Query("SELECT * FROM offline_drafts WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getByIdForUser(id: String, userId: String): DraftEntity?

    @Query("DELETE FROM offline_drafts WHERE id = :id AND userId = :userId")
    suspend fun deleteByIdForUser(id: String, userId: String)
}

@Database(entities = [DraftEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun draftDao(): DraftDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offline_drafts ADD COLUMN formDataJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE offline_drafts ADD COLUMN isQueuedForSubmit INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE offline_drafts ADD COLUMN queuedAtMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jcbest_compliance_local.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

// Per-account device-local encrypted draft repository.
// - Draft text/metadata is AES-256-GCM encrypted in SQLite keyed by userId.
// - Media files live at rest inside isolated encrypted account buckets (filesDir/account_buckets/<safeUserId>/).
// - Decrypted active-session copies are materialized strictly for the signed-in, unlocked user inside
//   cacheDir/active_session_media/<safeUserId>/ and wiped whenever locking or signing out.
class DraftRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).draftDao()
    private val vault = EncryptedAuthStore(context)

    private fun safeAccountKey(userId: String): String =
        userId.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "anonymous" }

    private fun accountEncryptedBucketDir(userId: String): File =
        File(context.filesDir, "account_buckets/${safeAccountKey(userId)}").apply {
            if (!exists()) mkdirs()
        }

    private fun activeSessionCacheDir(userId: String): File =
        File(context.cacheDir, "active_session_media/${safeAccountKey(userId)}").apply {
            if (!exists()) mkdirs()
        }

    fun observeDraftsForUser(userId: String): Flow<List<OfflineDraft>> =
        dao.observeForUser(userId).map { list ->
            withContext(Dispatchers.IO) {
                list.map { toDecryptedModel(it, materializeMedia = true) }
            }
        }

    suspend fun warmActiveSessionMediaCache(userId: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        dao.listForUser(userId).forEach { toDecryptedModel(it, materializeMedia = true) }
    }

    fun wipeActiveSessionMediaCache() {
        runCatching {
            File(context.cacheDir, "active_session_media").deleteRecursively()
            context.cacheDir.listFiles()?.forEach { f ->
                if (f.isDirectory && f.name.startsWith("cam_capture_")) {
                    f.deleteRecursively()
                }
            }
        }
    }

    private fun storeMediaEncrypted(userId: String, filePrefix: String, sourceFile: File): String? {
        if (!sourceFile.exists() || !sourceFile.isFile) return null
        val bucketDir = accountEncryptedBucketDir(userId)
        val cacheDir = activeSessionCacheDir(userId)
        val cleanOrigName = sourceFile.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val baseName = "${filePrefix}_$cleanOrigName".removeSuffix(".enc")
        val encryptedFile = File(bucketDir, "$baseName.enc")
        val activeCacheFile = File(cacheDir, baseName)

        val ok = vault.encryptFileToBucket(sourceFile, encryptedFile)
        if (!ok) {
            return null
        }
        runCatching { sourceFile.copyTo(activeCacheFile, overwrite = true) }
        return encryptedFile.absolutePath
    }

    private fun resolveReadablePath(userId: String, storedPath: String, materializeMedia: Boolean): String {
        if (!storedPath.startsWith("/")) return storedPath
        val storedFile = File(storedPath)
        if (storedPath.endsWith(".enc")) {
            if (!storedFile.exists()) return storedPath
            if (!materializeMedia) return storedPath
            val plainName = storedFile.name.removeSuffix(".enc").ifBlank { "evidence_item" }
            val cacheFile = File(activeSessionCacheDir(userId), plainName)
            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                vault.decryptBucketFileToCache(storedFile, cacheFile)
            }
            return if (cacheFile.exists()) cacheFile.absolutePath else storedPath
        }
        return storedPath
    }

    private fun deleteStoredBucketAndCacheFile(userId: String, storedPath: String?) {
        if (storedPath.isNullOrBlank() || !storedPath.startsWith("/")) return
        runCatching { File(storedPath).delete() }
        if (storedPath.endsWith(".enc")) {
            val plainName = File(storedPath).name.removeSuffix(".enc")
            runCatching { File(activeSessionCacheDir(userId), plainName).delete() }
        }
    }

    private fun readStoredItemsRaw(entity: DraftEntity): List<DraftMediaItem> {
        val decryptedJson = vault.decryptDraftText(entity.mediaItemsJson)
        val parsed = DraftEntity.decodeMediaItems(decryptedJson)
        val decryptedMediaPath = entity.mediaPath?.let { vault.decryptDraftText(it) }?.takeIf { it.isNotBlank() }
        return if (parsed.isNotEmpty()) {
            parsed
        } else if (!decryptedMediaPath.isNullOrBlank()) {
            listOf(
                DraftMediaItem(
                    id = "primary_${entity.id}",
                    pathOrUri = decryptedMediaPath,
                    mediaType = entity.mediaType,
                    capturedAtLabel = DraftEntity.formatDraftDateTime(entity.createdAtMillis)
                )
            )
        } else {
            emptyList()
        }
    }

    private fun toDecryptedModel(entity: DraftEntity, materializeMedia: Boolean): OfflineDraft {
        val rawItems = readStoredItemsRaw(entity)
        val resolvedItems = rawItems.map { item ->
            item.copy(pathOrUri = resolveReadablePath(entity.userId, item.pathOrUri, materializeMedia))
        }
        val rawPrimary = entity.mediaPath?.let { vault.decryptDraftText(it) }?.takeIf { it.isNotBlank() }
        val resolvedPrimary = rawPrimary?.let { resolveReadablePath(entity.userId, it, materializeMedia) }
            ?: resolvedItems.firstOrNull()?.pathOrUri

        val rawFormJson = entity.formDataJson.takeIf { it.isNotBlank() }?.let { vault.decryptDraftText(it) }
        val decodedFormDraft = rawFormJson?.takeIf { it.isNotBlank() }?.let {
            decodeFormDraftJson(entity.userId, it, materializeMedia)
        }

        return OfflineDraft(
            id = entity.id,
            userId = entity.userId,
            title = vault.decryptDraftText(entity.title),
            notes = vault.decryptDraftText(entity.notes),
            mediaPath = resolvedPrimary,
            mediaType = if (entity.mediaType != "none") entity.mediaType else (resolvedItems.firstOrNull()?.mediaType ?: "none"),
            mediaName = entity.mediaName?.let { vault.decryptDraftText(it) },
            mediaItems = resolvedItems,
            incidentDate = vault.decryptDraftText(entity.incidentDate),
            createdAtMillis = entity.createdAtMillis,
            formDraft = decodedFormDraft,
            isQueuedForSubmit = entity.isQueuedForSubmit,
            queuedAtMillis = entity.queuedAtMillis.takeIf { it > 0L }
        )
    }

    suspend fun listAllDraftsOnce(userId: String, materializeMedia: Boolean = false): List<OfflineDraft> =
        withContext(Dispatchers.IO) {
            if (userId.isBlank()) return@withContext emptyList()
            dao.listForUser(userId).map { toDecryptedModel(it, materializeMedia = materializeMedia) }
        }

    suspend fun listQueuedForAutoSubmit(userId: String): List<OfflineDraft> =
        withContext(Dispatchers.IO) {
            if (userId.isBlank()) return@withContext emptyList()
            dao.listQueuedForUser(userId).map { toDecryptedModel(it, materializeMedia = true) }
        }

    suspend fun saveFullFormDraft(
        userId: String,
        existingDraftId: String?,
        formDraft: ReportFormDraft,
        attachments: List<ReportEvidenceAttachment>,
        queueForAutoSubmit: Boolean = false
    ): OfflineDraft = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existingEntity = existingDraftId?.takeIf { it.isNotBlank() }?.let { dao.getByIdForUser(it, userId) }
        val id = existingEntity?.id ?: "draft_${UUID.randomUUID().toString().take(12)}"

        val storedMediaItems = mutableListOf<DraftMediaItem>()
        attachments.forEachIndexed { index, att ->
            if (att.isExistingCloud) return@forEachIndexed
            val pathOrUri = att.pathOrUri.trim()
            if (pathOrUri.isBlank()) return@forEachIndexed
            val mediaType = att.mediaType.ifBlank { "image" }
            val stamp = DraftEntity.formatDraftDateTime(now)
            val encryptedBucketPath = when {
                pathOrUri.startsWith("/") && pathOrUri.endsWith(".enc") -> pathOrUri
                pathOrUri.startsWith("/") -> {
                    val bucketCandidate = toBucketPathIfExists(userId, pathOrUri)
                    if (bucketCandidate.endsWith(".enc") && File(bucketCandidate).exists()) {
                        bucketCandidate
                    } else {
                        val f = File(pathOrUri)
                        if (f.exists()) storeMediaEncrypted(userId, "${id}_${now}_$index", f) ?: pathOrUri
                        else pathOrUri
                    }
                }
                pathOrUri.startsWith("content://") -> {
                    val uri = Uri.parse(pathOrUri)
                    val safeName = (att.displayName.ifBlank { resolveFileName(uri) ?: "evidence_$index" })
                        .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val tempFile = File(context.cacheDir, "uri_tmp_${now}_${index}_$safeName")
                    val copied = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tempFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        tempFile.exists() && tempFile.length() > 0L
                    }.getOrDefault(false)
                    if (copied) {
                        val stored = storeMediaEncrypted(userId, "${id}_${now}_$index", tempFile)
                        runCatching { tempFile.delete() }
                        stored ?: pathOrUri
                    } else {
                        pathOrUri
                    }
                }
                else -> pathOrUri
            }
            storedMediaItems.add(
                DraftMediaItem(
                    id = att.id.ifBlank { "dm_${now}_$index" },
                    pathOrUri = encryptedBucketPath,
                    mediaType = mediaType,
                    capturedAtLabel = att.displayName.ifBlank { stamp }
                )
            )
        }

        val normalizedFormDraft = formDraft.copy(
            sourceDraftId = id,
            selectedDraftMedia = storedMediaItems,
            localMediaPath = storedMediaItems.firstOrNull()?.pathOrUri,
            evidenceType = storedMediaItems.firstOrNull()?.mediaType ?: "none",
            evidenceName = when {
                storedMediaItems.isEmpty() -> null
                storedMediaItems.size == 1 -> storedMediaItems.first().capturedAtLabel
                else -> "${storedMediaItems.size} items"
            },
            hasUserEdited = true,
            updatedAtMillis = now
        )

        val divLabel = ComplianceDivision.fromAny(formDraft.divisionKey)?.label ?: "Compliance"
        val resolvedTitle = listOfNotNull(
            formDraft.checkName?.takeIf { it.isNotBlank() },
            formDraft.staffName?.takeIf { it.isNotBlank() }
        ).joinToString(" • ").ifBlank { "$divLabel Offline Draft" }
        val primaryStored = storedMediaItems.firstOrNull()?.pathOrUri
        val primaryType = storedMediaItems.firstOrNull()?.mediaType ?: "none"
        val primaryName = storedMediaItems.firstOrNull()?.capturedAtLabel

        val formJsonRaw = encodeFormDraftJson(userId, normalizedFormDraft)

        val entity = DraftEntity(
            id = id,
            userId = userId,
            title = vault.encryptDraftText(resolvedTitle),
            notes = vault.encryptDraftText(formDraft.exceptionText.trim()),
            mediaPath = primaryStored?.let { vault.encryptDraftText(it) },
            mediaType = primaryType,
            mediaName = primaryName?.let { vault.encryptDraftText(it) },
            mediaItemsJson = vault.encryptDraftText(DraftEntity.encodeMediaItems(storedMediaItems)),
            incidentDate = vault.encryptDraftText(formDraft.incidentDate.ifBlank { AcademicWeekEngine.todayIso() }),
            createdAtMillis = existingEntity?.createdAtMillis ?: now,
            formDataJson = vault.encryptDraftText(formJsonRaw),
            isQueuedForSubmit = queueForAutoSubmit,
            queuedAtMillis = if (queueForAutoSubmit) now else 0L
        )
        dao.upsert(entity)
        toDecryptedModel(entity, materializeMedia = true)
    }

    suspend fun setDraftQueuedState(
        userId: String,
        draftId: String,
        queued: Boolean
    ): OfflineDraft? = withContext(Dispatchers.IO) {
        val existing = dao.getByIdForUser(draftId, userId) ?: return@withContext null
        val updated = existing.copy(
            isQueuedForSubmit = queued,
            queuedAtMillis = if (queued) System.currentTimeMillis() else 0L
        )
        dao.upsert(updated)
        toDecryptedModel(updated, materializeMedia = true)
    }

    suspend fun saveDraft(
        userId: String,
        title: String,
        notes: String,
        mediaFile: File?,
        mediaType: String,
        mediaName: String?,
        incidentDate: String = AcademicWeekEngine.todayIso()
    ): OfflineDraft = withContext(Dispatchers.IO) {
        val id = "draft_${UUID.randomUUID().toString().take(12)}"
        val now = System.currentTimeMillis()
        val storedBucketPath = if (mediaFile != null && mediaFile.exists()) {
            storeMediaEncrypted(userId, "${id}_$now", mediaFile)?.also {
                if (mediaFile.absolutePath.contains("cam_capture_") || mediaFile.absolutePath.contains("uri_tmp_")) {
                    runCatching { mediaFile.delete() }
                }
            }
        } else {
            null
        }
        val items = if (storedBucketPath != null) {
            listOf(
                DraftMediaItem(
                    id = "dm_${now}_${UUID.randomUUID().toString().take(6)}",
                    pathOrUri = storedBucketPath,
                    mediaType = mediaType,
                    capturedAtLabel = DraftEntity.formatDraftDateTime(now)
                )
            )
        } else {
            emptyList()
        }
        val rawTitle = title.ifBlank { "Captured Evidence" }
        val rawName = mediaName ?: mediaFile?.name
        val entity = DraftEntity(
            id = id,
            userId = userId,
            title = vault.encryptDraftText(rawTitle),
            notes = vault.encryptDraftText(notes),
            mediaPath = storedBucketPath?.let { vault.encryptDraftText(it) },
            mediaType = mediaType,
            mediaName = rawName?.let { vault.encryptDraftText(it) },
            mediaItemsJson = vault.encryptDraftText(DraftEntity.encodeMediaItems(items)),
            incidentDate = vault.encryptDraftText(incidentDate),
            createdAtMillis = now
        )
        dao.upsert(entity)
        toDecryptedModel(entity, materializeMedia = true)
    }

    suspend fun saveDraftFromUri(
        userId: String,
        title: String,
        notes: String,
        uri: Uri,
        mediaType: String,
        incidentDate: String = AcademicWeekEngine.todayIso()
    ): OfflineDraft = withContext(Dispatchers.IO) {
        val fileName = resolveFileName(uri) ?: "evidence_${System.currentTimeMillis()}"
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val tempFile = File(context.cacheDir, "uri_tmp_${System.currentTimeMillis()}_$safeName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        saveDraft(userId, title, notes, tempFile, mediaType, safeName, incidentDate)
    }

    suspend fun addMediaFileToDraft(
        userId: String,
        draftId: String,
        mediaFile: File,
        mediaType: String
    ): OfflineDraft? = withContext(Dispatchers.IO) {
        val existing = dao.getByIdForUser(draftId, userId) ?: return@withContext null
        if (!mediaFile.exists()) return@withContext toDecryptedModel(existing, materializeMedia = true)
        val now = System.currentTimeMillis()
        val storedBucketPath = storeMediaEncrypted(userId, "${draftId}_$now", mediaFile)?.also {
            if (mediaFile.absolutePath.contains("cam_capture_") || mediaFile.absolutePath.contains("uri_tmp_")) {
                runCatching { mediaFile.delete() }
            }
        } ?: return@withContext toDecryptedModel(existing, materializeMedia = true)

        val currentRawItems = readStoredItemsRaw(existing)
        val newItem = DraftMediaItem(
            id = "dm_${now}_${UUID.randomUUID().toString().take(6)}",
            pathOrUri = storedBucketPath,
            mediaType = mediaType,
            capturedAtLabel = DraftEntity.formatDraftDateTime(now)
        )
        val updatedRawItems = currentRawItems + newItem
        val existingPrimaryRaw = existing.mediaPath?.let { vault.decryptDraftText(it) }?.takeIf { it.isNotBlank() }
        val updatedEntity = existing.copy(
            mediaPath = vault.encryptDraftText(existingPrimaryRaw ?: storedBucketPath),
            mediaType = if (existing.mediaType == "none") mediaType else existing.mediaType,
            mediaItemsJson = vault.encryptDraftText(DraftEntity.encodeMediaItems(updatedRawItems))
        )
        dao.upsert(updatedEntity)
        toDecryptedModel(updatedEntity, materializeMedia = true)
    }

    suspend fun addMediaUriToDraft(
        userId: String,
        draftId: String,
        uri: Uri,
        mediaType: String
    ): OfflineDraft? = withContext(Dispatchers.IO) {
        val fileName = resolveFileName(uri) ?: "evidence_${System.currentTimeMillis()}"
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val tempFile = File(context.cacheDir, "uri_tmp_${System.currentTimeMillis()}_$safeName")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            tempFile.exists() && tempFile.length() > 0L
        }.getOrDefault(false)
        if (!copied) return@withContext null
        addMediaFileToDraft(userId, draftId, tempFile, mediaType)
    }

    suspend fun removeMediaItemFromDraft(
        userId: String,
        draftId: String,
        mediaId: String
    ): OfflineDraft? = withContext(Dispatchers.IO) {
        val existing = dao.getByIdForUser(draftId, userId) ?: return@withContext null
        val currentRawItems = readStoredItemsRaw(existing)
        val target = currentRawItems.firstOrNull { it.id == mediaId }
        if (target != null) {
            deleteStoredBucketAndCacheFile(userId, target.pathOrUri)
        }
        val remaining = currentRawItems.filterNot { it.id == mediaId }
        val nextPrimaryRaw = remaining.firstOrNull()?.pathOrUri
        val updatedEntity = existing.copy(
            mediaPath = nextPrimaryRaw?.let { vault.encryptDraftText(it) },
            mediaType = remaining.firstOrNull()?.mediaType ?: "none",
            mediaItemsJson = vault.encryptDraftText(DraftEntity.encodeMediaItems(remaining))
        )
        dao.upsert(updatedEntity)
        toDecryptedModel(updatedEntity, materializeMedia = true)
    }

    suspend fun deleteDraft(
        userId: String,
        id: String,
        deleteMediaFile: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getByIdForUser(id, userId)
        if (deleteMediaFile && existing != null) {
            val rawPrimary = existing.mediaPath?.let { vault.decryptDraftText(it) }?.takeIf { it.isNotBlank() }
            val rawItems = readStoredItemsRaw(existing)
            val paths = (listOfNotNull(rawPrimary) + rawItems.map { it.pathOrUri }).distinct()
            paths.forEach { path -> deleteStoredBucketAndCacheFile(userId, path) }
        }
        dao.deleteByIdForUser(id, userId)
    }

    suspend fun deleteMediaOnly(userId: String, path: String?) = withContext(Dispatchers.IO) {
        deleteStoredBucketAndCacheFile(userId, path)
    }

    // ---------------------------------------------------------------------
    // IN-PROGRESS REPORT COMPOSER DRAFT (ENCRYPTED PER-USER & DEVICE BUCKET)
    // ---------------------------------------------------------------------
    private fun toBucketPathIfExists(userId: String, activeOrBucketPath: String): String {
        if (!activeOrBucketPath.startsWith("/")) return activeOrBucketPath
        if (activeOrBucketPath.endsWith(".enc")) return activeOrBucketPath
        val name = File(activeOrBucketPath).name
        val candidate = File(accountEncryptedBucketDir(userId), "$name.enc")
        return if (candidate.exists()) candidate.absolutePath else activeOrBucketPath
    }

    suspend fun stageComposerMediaFile(userId: String, sourceFile: File): String? = withContext(Dispatchers.IO) {
        if (userId.isBlank() || !sourceFile.exists() || !sourceFile.isFile) return@withContext null
        val prefix = "composer_media_${System.currentTimeMillis()}"
        val bucketPath = storeMediaEncrypted(userId, prefix, sourceFile) ?: return@withContext null
        resolveReadablePath(userId, bucketPath, materializeMedia = true)
    }

    suspend fun stageComposerMediaUri(userId: String, uri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext null
        val displayName = resolveFileName(uri) ?: "evidence_${System.currentTimeMillis()}"
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val tempFile = File(context.cacheDir, "uri_tmp_${System.currentTimeMillis()}_$safeName")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            tempFile.exists() && tempFile.length() > 0L
        }.getOrDefault(false)
        if (!copied) return@withContext null
        val bucketPath = storeMediaEncrypted(userId, "composer_media_${System.currentTimeMillis()}", tempFile)
        runCatching { tempFile.delete() }
        val resolved = bucketPath?.let { resolveReadablePath(userId, it, materializeMedia = true) } ?: return@withContext null
        resolved to displayName
    }

    suspend fun saveComposerFormDraft(userId: String, draft: ReportFormDraft) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        vault.saveEncryptedFormDraft(userId, encodeFormDraftJson(userId, draft))
    }

    suspend fun loadComposerFormDraft(userId: String): ReportFormDraft? = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext null
        val raw = vault.loadEncryptedFormDraft(userId) ?: return@withContext null
        decodeFormDraftJson(userId, raw, materializeMedia = true)
    }

    private fun encodeFormDraftJson(userId: String, draft: ReportFormDraft): String {
        val storedMediaItems = draft.selectedDraftMedia.map { item ->
            item.copy(pathOrUri = toBucketPathIfExists(userId, item.pathOrUri))
        }
        val storedLocalPath = draft.localMediaPath?.let { toBucketPathIfExists(userId, it) }
        return JSONObject().apply {
            put("divisionKey", draft.divisionKey)
            put("targetType", draft.targetType)
            put("classTierFilter", draft.classTierFilter)
            put("staffSearch", draft.staffSearch)
            put("staffId", draft.staffId ?: JSONObject.NULL)
            put("staffCode", draft.staffCode ?: JSONObject.NULL)
            put("staffName", draft.staffName ?: JSONObject.NULL)
            put("classId", draft.classId ?: JSONObject.NULL)
            put("className", draft.className ?: JSONObject.NULL)
            put("subjectId", draft.subjectId ?: JSONObject.NULL)
            put("subjectName", draft.subjectName ?: JSONObject.NULL)
            put("operationScope", draft.operationScope)
            put("selectedBus", draft.selectedBus)
            put("opPersonOrLocation", draft.opPersonOrLocation)
            put("studentNamesArm", draft.studentNamesArm)
            put("checkId", draft.checkId ?: JSONObject.NULL)
            put("checkName", draft.checkName ?: JSONObject.NULL)
            put("incidentDate", draft.incidentDate)
            put("exceptionText", draft.exceptionText)
            put("effectText", draft.effectText)
            put("sourceDraftId", draft.sourceDraftId ?: JSONObject.NULL)
            put("selectedDraftMediaJson", DraftEntity.encodeMediaItems(storedMediaItems))
            put("localMediaPath", storedLocalPath ?: JSONObject.NULL)
            put("evidenceType", draft.evidenceType)
            put("evidenceName", draft.evidenceName ?: JSONObject.NULL)
            put("hasUserEdited", draft.hasUserEdited)
            put("updatedAtMillis", draft.updatedAtMillis)
        }.toString()
    }

    private fun decodeFormDraftJson(userId: String, raw: String, materializeMedia: Boolean): ReportFormDraft? =
        runCatching {
            val o = JSONObject(raw)
            fun optNullStr(k: String): String? =
                if (o.isNull(k)) null else o.optString(k).takeIf { it.isNotBlank() && it != "null" }

            val rawItems = DraftEntity.decodeMediaItems(o.optString("selectedDraftMediaJson", "[]"))
            val resolvedItems = rawItems.mapNotNull { item ->
                val resolved = resolveReadablePath(userId, item.pathOrUri, materializeMedia)
                if (materializeMedia && resolved.startsWith("/") && !File(resolved).exists()) null
                else item.copy(pathOrUri = resolved)
            }

            val rawLocalPath = optNullStr("localMediaPath")
            val resolvedLocalPath = rawLocalPath?.let { path ->
                val res = resolveReadablePath(userId, path, materializeMedia)
                if (materializeMedia && res.startsWith("/") && !File(res).exists()) null else res
            }
            val rawEvType = o.optString("evidenceType", "none")
            val validEvType = if (rawLocalPath != null && resolvedLocalPath == null && resolvedItems.isEmpty()) "none" else rawEvType

            ReportFormDraft(
                divisionKey = o.optString("divisionKey", ComplianceDivision.ACADEMIC.key),
                targetType = o.optString("targetType", "staff"),
                classTierFilter = o.optString("classTierFilter", "All Tiers"),
                staffSearch = o.optString("staffSearch", ""),
                staffId = optNullStr("staffId"),
                staffCode = optNullStr("staffCode"),
                staffName = optNullStr("staffName"),
                classId = optNullStr("classId"),
                className = optNullStr("className"),
                subjectId = optNullStr("subjectId"),
                subjectName = optNullStr("subjectName"),
                operationScope = o.optString("operationScope", "Maintenance"),
                selectedBus = o.optString("selectedBus", "Bus 1"),
                opPersonOrLocation = o.optString("opPersonOrLocation", ""),
                studentNamesArm = o.optString("studentNamesArm", ""),
                checkId = optNullStr("checkId"),
                checkName = optNullStr("checkName"),
                incidentDate = o.optString("incidentDate", ""),
                exceptionText = o.optString("exceptionText", ""),
                effectText = o.optString("effectText", ""),
                sourceDraftId = optNullStr("sourceDraftId"),
                selectedDraftMedia = resolvedItems,
                localMediaPath = resolvedLocalPath,
                evidenceType = validEvType,
                evidenceName = if (validEvType == "none") null else optNullStr("evidenceName"),
                hasUserEdited = o.optBoolean("hasUserEdited", false),
                updatedAtMillis = o.optLong("updatedAtMillis", 0L)
            )
        }.getOrNull()

    suspend fun clearComposerFormDraft(userId: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        vault.clearEncryptedFormDraft(userId)
        runCatching {
            accountEncryptedBucketDir(userId).listFiles()?.forEach { f ->
                if (f.name.startsWith("composer_media_")) f.delete()
            }
            activeSessionCacheDir(userId).listFiles()?.forEach { f ->
                if (f.name.startsWith("composer_media_")) f.delete()
            }
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                } ?: uri.lastPathSegment
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }
}
