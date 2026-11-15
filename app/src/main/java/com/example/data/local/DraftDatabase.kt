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
import com.example.data.model.OfflineDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Entity(tableName = "offline_drafts")
data class DraftEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    val mediaPath: String?,
    val mediaType: String,
    val mediaName: String?,
    val incidentDate: String,
    val createdAtMillis: Long
) {
    fun toModel() = OfflineDraft(
        id = id,
        title = title,
        notes = notes,
        mediaPath = mediaPath,
        mediaType = mediaType,
        mediaName = mediaName,
        incidentDate = incidentDate,
        createdAtMillis = createdAtMillis
    )
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM offline_drafts ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<DraftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DraftEntity)

    @Query("SELECT * FROM offline_drafts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DraftEntity?

    @Query("DELETE FROM offline_drafts WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(entities = [DraftEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun draftDao(): DraftDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jcbest_compliance_local.db"
                ).build().also { instance = it }
            }
        }
    }
}

class DraftRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).draftDao()
    private val mediaDir: File
        get() = File(context.filesDir, "draft_media").apply { if (!exists()) mkdirs() }

    val drafts: Flow<List<OfflineDraft>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun saveDraft(
        title: String,
        notes: String,
        mediaFile: File?,
        mediaType: String,
        mediaName: String?,
        incidentDate: String = AcademicWeekEngine.todayIso()
    ): OfflineDraft = withContext(Dispatchers.IO) {
        val id = "draft_${UUID.randomUUID().toString().take(12)}"
        val storedPath = if (mediaFile != null && mediaFile.exists()) {
            val dest = File(mediaDir, "${id}_${mediaFile.name}")
            if (mediaFile.absolutePath != dest.absolutePath) {
                mediaFile.copyTo(dest, overwrite = true)
            }
            dest.absolutePath
        } else {
            null
        }
        val entity = DraftEntity(
            id = id,
            title = title.ifBlank { "Captured Evidence" },
            notes = notes,
            mediaPath = storedPath,
            mediaType = mediaType,
            mediaName = mediaName ?: mediaFile?.name,
            incidentDate = incidentDate,
            createdAtMillis = System.currentTimeMillis()
        )
        dao.upsert(entity)
        entity.toModel()
    }

    suspend fun saveDraftFromUri(
        title: String,
        notes: String,
        uri: Uri,
        mediaType: String,
        incidentDate: String = AcademicWeekEngine.todayIso()
    ): OfflineDraft = withContext(Dispatchers.IO) {
        val fileName = resolveFileName(uri) ?: "evidence_${System.currentTimeMillis()}"
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val tempFile = File(mediaDir, "uri_${System.currentTimeMillis()}_$safeName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        saveDraft(title, notes, tempFile, mediaType, safeName, incidentDate)
    }

    suspend fun deleteDraft(id: String, deleteMediaFile: Boolean = true) = withContext(Dispatchers.IO) {
        val existing = dao.getById(id)
        if (deleteMediaFile && !existing?.mediaPath.isNullOrBlank()) {
            runCatching { File(existing!!.mediaPath!!).delete() }
        }
        dao.deleteById(id)
    }

    suspend fun deleteMediaOnly(path: String?) = withContext(Dispatchers.IO) {
        if (!path.isNullOrBlank()) {
            runCatching { File(path).delete() }
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
