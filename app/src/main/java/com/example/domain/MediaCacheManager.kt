package com.example.domain

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File
import java.net.URLDecoder
import java.util.Locale

object MediaCacheManager {

    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appCtx = context.applicationContext
            val loader = ImageLoader.Builder(appCtx)
                .memoryCache {
                    MemoryCache.Builder(appCtx)
                        .maxSizePercent(0.20)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(appCtx.cacheDir, "coil_media_cache"))
                        .maxSizeBytes(150L * 1024L * 1024L)
                        .build()
                }
                .respectCacheHeaders(false)
                .crossfade(true)
                .build()
            Coil.setImageLoader(loader)
            initialized = true
        }
    }

    /**
     * Strips rotating query tokens (?Authorization=..., ?token=...) or proxy prefixes
     * so avatars and evidence photos share one canonical cache key across signed URL renewals.
     */
    fun canonicalObjectKey(rawPathOrUrl: String): String {
        val raw = rawPathOrUrl.trim()
        if (raw.isBlank()) return ""
        val lower = raw.lowercase(Locale.US)

        if (lower.contains("/api/storage/b2/file")) {
            val qIdx = raw.indexOf('?')
            if (qIdx >= 0 && qIdx < raw.length - 1) {
                for (pair in raw.substring(qIdx + 1).split('&')) {
                    val eqIdx = pair.indexOf('=')
                    if (eqIdx > 0 && pair.substring(0, eqIdx) == "path") {
                        val decoded = runCatching {
                            URLDecoder.decode(pair.substring(eqIdx + 1), "UTF-8")
                        }.getOrDefault(pair.substring(eqIdx + 1))
                        if (decoded.isNotBlank()) return decoded.trimStart('/')
                    }
                }
            }
        }

        if (lower.contains("/file/") && (lower.startsWith("http://") || lower.startsWith("https://"))) {
            val afterFile = raw.substring(lower.indexOf("/file/") + 6).substringBefore('?')
            val slashIdx = afterFile.indexOf('/')
            if (slashIdx > 0 && slashIdx < afterFile.length - 1) {
                return runCatching {
                    URLDecoder.decode(afterFile.substring(slashIdx + 1), "UTF-8")
                }.getOrDefault(afterFile.substring(slashIdx + 1)).trimStart('/')
            }
        }

        if (lower.startsWith("b2://")) {
            val withoutScheme = raw.substring(5).trimStart('/')
            return withoutScheme.substringAfter('/', withoutScheme).trimStart('/')
        }

        if (lower.contains("supabase.co/storage/v1/object/")) {
            return raw.substringBefore('?')
        }

        return raw.substringBefore('?').trimStart('/')
    }

    fun avatarCacheKey(rawPathOrUrl: String): String {
        val canonical = canonicalObjectKey(rawPathOrUrl).ifBlank { rawPathOrUrl.trim() }
        return "avatar_$canonical"
    }

    fun evidenceCacheKey(rawPathOrUrl: String): String {
        val canonical = canonicalObjectKey(rawPathOrUrl).ifBlank { rawPathOrUrl.trim() }
        return "evidence_$canonical"
    }

    fun hasCachedDiskEntry(context: Context, cacheKey: String): Boolean {
        if (cacheKey.isBlank()) return false
        ensureInitialized(context)
        return runCatching {
            val diskCache = Coil.imageLoader(context.applicationContext).diskCache ?: return false
            diskCache.openSnapshot(cacheKey)?.use { true } ?: false
        }.getOrDefault(false)
    }
}
