package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.domain.MediaCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class AvatarFallbackMode {
    INITIALS,
    SCHOOL_CREST
}

/**
 * Reusable, private-bucket-safe, read-only user avatar component.
 *
 * Resolves canonical B2 object paths (e.g. avatars/<uid>/avatar_<ts>.jpg), /api/storage/b2/file proxy
 * URLs, or legacy Supabase paths via [onResolveSignedUrl], renders full http(s) URLs directly, and
 * falls back cleanly to either user initials or the transparent [SchoolCrestBadge].
 */
@Composable
fun ProfileAvatar(
    avatarUrl: String,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    borderColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
    accentColor: Color = MaterialTheme.colorScheme.primary,
    fallbackMode: AvatarFallbackMode = AvatarFallbackMode.INITIALS,
    onResolveSignedUrl: (suspend (String) -> Result<String>)? = null,
    getAuthHeaders: ((String) -> Map<String, String>)? = null,
    contentDescription: String = "Profile photo of ${displayName.ifBlank { "Officer" }}",
    onClick: (() -> Unit)? = null,
    testTag: String? = null
) {
    val context = LocalContext.current
    val trimmedUrl = remember(avatarUrl) {
        avatarUrl.trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
    }
    val cacheKey = remember(trimmedUrl) {
        if (trimmedUrl.isBlank()) "" else MediaCacheManager.avatarCacheKey(trimmedUrl)
    }

    val isLocalFile = trimmedUrl.startsWith("/") && !trimmedUrl.startsWith("/api/storage/", ignoreCase = true)
    val isContentOrFileUri = trimmedUrl.startsWith("content://", ignoreCase = true) ||
        trimmedUrl.startsWith("file://", ignoreCase = true)

    val needsSigning = remember(trimmedUrl) {
        if (trimmedUrl.isBlank() || isLocalFile || isContentOrFileUri) {
            false
        } else {
            val lower = trimmedUrl.lowercase(Locale.US)
            val isHttp = lower.startsWith("http://") || lower.startsWith("https://")
            !isHttp ||
                lower.contains("/api/storage/b2/file") ||
                lower.contains("supabase.co/storage/v1/object/") ||
                (lower.contains("/file/") && !lower.contains("authorization="))
        }
    }

    var resolvedUrl by remember(trimmedUrl) {
        mutableStateOf(
            when {
                trimmedUrl.isBlank() -> null
                !needsSigning -> trimmedUrl
                else -> null
            }
        )
    }
    var isOfflineCachedOnly by remember(trimmedUrl) { mutableStateOf(false) }
    var loadFailed by remember(trimmedUrl, resolvedUrl) { mutableStateOf(false) }

    LaunchedEffect(trimmedUrl, needsSigning, cacheKey) {
        loadFailed = false
        isOfflineCachedOnly = false
        if (trimmedUrl.isBlank()) {
            resolvedUrl = null
            return@LaunchedEffect
        }
        val cachedOnDisk = withContext(Dispatchers.IO) {
            MediaCacheManager.hasCachedDiskEntry(context, cacheKey)
        }
        if (cachedOnDisk) {
            // Serve directly from Coil disk cache with zero network egress or signed-URL calls
            isOfflineCachedOnly = true
            resolvedUrl = "https://offline-cache.invalid/$cacheKey"
            return@LaunchedEffect
        }
        if (!needsSigning) {
            resolvedUrl = trimmedUrl
            return@LaunchedEffect
        }
        if (onResolveSignedUrl != null) {
            val signed = onResolveSignedUrl(trimmedUrl).getOrNull()?.trim().orEmpty()
            resolvedUrl = signed.ifBlank { null }
        } else {
            resolvedUrl = null
        }
    }

    val initials = remember(displayName) {
        displayName
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "JB" }
    }

    val baseModifier = modifier
        .let { if (testTag != null) it.testTag(testTag) else it }
        .let {
            if (onClick != null) {
                it
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Open profile settings",
                        onClick = onClick
                    )
            } else {
                it
            }
        }

    val showFallback = trimmedUrl.isBlank() || resolvedUrl.isNullOrBlank() || loadFailed

    Box(
        modifier = baseModifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (!showFallback) {
            val modelUrl = resolvedUrl!!
            val imageRequest = remember(modelUrl, cacheKey, isOfflineCachedOnly) {
                val dataModel: Any = if (modelUrl.startsWith("/") && !modelUrl.startsWith("/api/storage/", ignoreCase = true)) {
                    File(modelUrl)
                } else {
                    modelUrl
                }
                ImageRequest.Builder(context)
                    .data(dataModel)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .apply {
                        if (isOfflineCachedOnly) {
                            networkCachePolicy(CachePolicy.DISABLED)
                            diskCachePolicy(CachePolicy.READ_ONLY)
                        } else {
                            val headers = getAuthHeaders?.invoke(modelUrl).orEmpty()
                            headers.forEach { (k, v) -> addHeader(k, v) }
                        }
                    }
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onError = { loadFailed = true },
                onSuccess = { loadFailed = false },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.5.dp, borderColor, CircleShape)
            )
        } else if (fallbackMode == AvatarFallbackMode.SCHOOL_CREST) {
            SchoolCrestBadge(size = size)
        } else {
            val textSp = (size.value * 0.34f).coerceIn(11f, 24f).sp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f))
                    .border(1.2.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = accentColor,
                    fontSize = textSp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}
