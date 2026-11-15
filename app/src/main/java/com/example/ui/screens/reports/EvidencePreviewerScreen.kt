package com.example.ui.screens.reports

import android.content.Intent
import android.content.res.Configuration
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ThemePreference
import com.example.ui.components.GlassCircleIcon
import com.example.ui.components.LivingAtmosphericBackground
import com.example.ui.components.StatusGlassChip
import com.example.ui.components.TactileEvidenceScrubberPill
import com.example.ui.components.TangerineGlowButton
import com.example.ui.components.VerdictPill
import com.example.ui.components.complianceGlassCard
import com.example.ui.components.glassFieldColors
import com.example.ui.theme.JCBestTheme
import com.example.ui.theme.LocalCompliancePalette
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

enum class EvidenceVerdict {
    PASS,
    FLAG,
    NOTE
}

data class EvidencePreviewItem(
    val id: String,
    val storedPath: String,
    val mediaType: String,
    val title: String,
    val inspectorName: String = "Compliance Officer"
)

@Composable
fun EvidencePreviewerScreen(
    items: List<EvidencePreviewItem>,
    initialIndex: Int = 0,
    onResolveSignedUrl: suspend (String) -> Result<String>,
    onVerdict: ((EvidencePreviewItem, EvidenceVerdict, String?) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var activeIndex by remember(items, initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    }
    val activeItem = items.getOrNull(activeIndex)

    var resolvedUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember(activeIndex, activeItem?.storedPath) { mutableStateOf(true) }
    var errorMessage by remember(activeIndex, activeItem?.storedPath) { mutableStateOf<String?>(null) }
    var retryNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeItem?.storedPath, retryNonce) {
        val path = activeItem?.storedPath.orEmpty().trim()
        if (path.isBlank()) {
            isLoading = false
            errorMessage = "Evidence path is unavailable."
            return@LaunchedEffect
        }

        val cached = resolvedUrls[path]
        if (!cached.isNullOrBlank()) {
            isLoading = false
            errorMessage = null
            return@LaunchedEffect
        }

        // If it's already a local content/file URI or existing file on disk, display immediately
        if (isDirectLocalUriOrFile(path)) {
            val normalized = normalizeDirectModelString(path)
            resolvedUrls = resolvedUrls + (path to normalized)
            isLoading = false
            errorMessage = null
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null
        onResolveSignedUrl(path)
            .onSuccess { url ->
                val cleanUrl = url.trim()
                if (cleanUrl.isNotBlank()) {
                    resolvedUrls = resolvedUrls + (path to cleanUrl)
                    isLoading = false
                } else if (isHttpUrl(path)) {
                    resolvedUrls = resolvedUrls + (path to path)
                    isLoading = false
                } else {
                    errorMessage = "Resolved evidence URL was empty."
                    isLoading = false
                }
            }
            .onFailure { err ->
                // Graceful fallback if storedPath itself is already a valid HTTPS URL
                if (isHttpUrl(path)) {
                    resolvedUrls = resolvedUrls + (path to path)
                    errorMessage = null
                } else {
                    errorMessage = err.message ?: "Unable to resolve evidence media."
                }
                isLoading = false
            }
    }

    val activeUrl = activeItem?.storedPath?.trim()?.let { resolvedUrls[it] }

    EvidencePreviewerScaffold(
        items = items,
        activeIndex = activeIndex,
        activeUrl = activeUrl,
        resolvedUrls = resolvedUrls,
        isLoading = isLoading,
        errorMessage = errorMessage,
        onSelectIndex = { activeIndex = it },
        onRetry = {
            activeItem?.storedPath?.trim()?.let { path ->
                resolvedUrls = resolvedUrls - path
            }
            retryNonce++
        },
        onOpenExternal = {
            if (!activeUrl.isNullOrBlank()) {
                runCatching {
                    val uri = toPlayableUri(activeUrl)
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    )
                }
            }
        },
        onVerdict = { verdict, note ->
            if (activeItem != null) {
                onVerdict?.invoke(activeItem, verdict, note)
            }
        },
        onBack = onBack
    )
}

@Composable
fun EvidencePreviewerScaffold(
    items: List<EvidencePreviewItem>,
    activeIndex: Int,
    activeUrl: String?,
    resolvedUrls: Map<String, String> = emptyMap(),
    isLoading: Boolean,
    errorMessage: String?,
    onSelectIndex: (Int) -> Unit,
    onRetry: () -> Unit = {},
    onOpenExternal: () -> Unit,
    onVerdict: (EvidenceVerdict, String?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val p = LocalCompliancePalette.current
    val activeItem = items.getOrNull(activeIndex)
    val mediaType = remember(activeItem?.mediaType, activeUrl, activeItem?.storedPath) {
        normalizeEvidenceMediaType(
            rawType = activeItem?.mediaType.orEmpty(),
            pathOrUrl = activeUrl ?: activeItem?.storedPath
        )
    }

    var showMenu by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }
    var selectedVerdict by remember(activeIndex) { mutableStateOf<EvidenceVerdict?>(null) }

    // Image inspection state (zoom 1.0x .. 4.0x + pan + reticle overlay + Coil load state)
    var imageScale by remember(activeIndex) { mutableFloatStateOf(1f) }
    var imageOffset by remember(activeIndex) { mutableStateOf(Offset.Zero) }
    var showAnnotationReticle by remember(activeIndex) { mutableStateOf(false) }
    var imageDecodeLoading by remember(activeIndex, activeUrl) { mutableStateOf(false) }
    var imageDecodeError by remember(activeIndex, activeUrl) { mutableStateOf<String?>(null) }

    // Video state
    var videoViewRef by remember(activeIndex, activeUrl) { mutableStateOf<VideoView?>(null) }
    var videoMediaPlayer by remember(activeIndex, activeUrl) { mutableStateOf<MediaPlayer?>(null) }
    var videoBuffering by remember(activeIndex, activeUrl) { mutableStateOf(false) }
    var videoError by remember(activeIndex, activeUrl) { mutableStateOf<String?>(null) }
    var videoPlaying by remember(activeIndex, activeUrl) { mutableStateOf(true) }
    var videoMuted by remember(activeIndex, activeUrl) { mutableStateOf(false) }
    var videoDurationMs by remember(activeIndex, activeUrl) { mutableIntStateOf(0) }
    var videoPositionMs by remember(activeIndex, activeUrl) { mutableIntStateOf(0) }

    // Audio state
    val audioPlayer = remember { MediaPlayer() }
    var audioPrepared by remember(activeIndex, activeUrl) { mutableStateOf(false) }
    var audioPlaying by remember(activeIndex, activeUrl) { mutableStateOf(false) }
    var audioDurationMs by remember(activeIndex, activeUrl) { mutableIntStateOf(0) }
    var audioPositionMs by remember(activeIndex, activeUrl) { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                if (audioPlayer.isPlaying) audioPlayer.stop()
                audioPlayer.release()
            }
        }
    }

    DisposableEffect(activeIndex, activeUrl) {
        onDispose {
            runCatching { videoViewRef?.stopPlayback() }
            videoViewRef = null
            videoMediaPlayer = null
        }
    }

    LaunchedEffect(activeIndex, activeUrl, mediaType) {
        audioPrepared = false
        audioPlaying = false
        audioDurationMs = 0
        audioPositionMs = 0
        runCatching {
            if (audioPlayer.isPlaying) audioPlayer.stop()
            audioPlayer.reset()
        }
        if (mediaType == "audio" && !activeUrl.isNullOrBlank()) {
            runCatching {
                val uri = toPlayableUri(activeUrl)
                audioPlayer.setDataSource(context, uri)
                audioPlayer.setOnPreparedListener { mp ->
                    audioPrepared = true
                    audioDurationMs = mp.duration.coerceAtLeast(1)
                    mp.start()
                    audioPlaying = true
                }
                audioPlayer.setOnCompletionListener {
                    audioPlaying = false
                    audioPositionMs = audioDurationMs
                }
                audioPlayer.prepareAsync()
            }
        }
    }

    // Live ticker for video/audio position
    LaunchedEffect(activeIndex, activeUrl, videoPlaying, audioPlaying, mediaType) {
        while (true) {
            if (mediaType == "video") {
                val vv = videoViewRef
                if (vv != null) {
                    runCatching {
                        if (vv.duration > 0) videoDurationMs = vv.duration
                        videoPositionMs = vv.currentPosition.coerceAtLeast(0)
                        videoPlaying = vv.isPlaying
                    }
                }
            } else if (mediaType == "audio" && audioPrepared) {
                runCatching {
                    audioPositionMs = audioPlayer.currentPosition.coerceAtLeast(0)
                    audioPlaying = audioPlayer.isPlaying
                }
            }
            delay(250L)
        }
    }

    val sealedChipLabel = when (mediaType) {
        "video" -> "HD • GPS Sealed"
        "image" -> "RAW • GPS Sealed"
        "audio" -> "AAC • Voice Sealed"
        else -> "DOC • Hash Sealed"
    }

    val scrubberProgress = when (mediaType) {
        "video" -> if (videoDurationMs > 0) videoPositionMs.toFloat() / videoDurationMs.toFloat() else 0f
        "audio" -> if (audioDurationMs > 0) audioPositionMs.toFloat() / audioDurationMs.toFloat() else 0f
        "image" -> ((imageScale - 1f) / 3f).coerceIn(0f, 1f)
        else -> 1f
    }

    val scrubberLabel = when (mediaType) {
        "video" -> "${formatTimeMs(videoPositionMs)} / ${formatTimeMs(videoDurationMs)}"
        "audio" -> "${formatTimeMs(audioPositionMs)} / ${formatTimeMs(audioDurationMs)}"
        "image" -> String.format(Locale.US, "%.1fx Zoom", imageScale)
        else -> "Document Ready"
    }

    LivingAtmosphericBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Full-bleed Evidence Canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (p.isDark) Color(0xFF07090C) else Color(0xFFE9EDF5)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = p.tangerine)
                            Text(
                                text = "Decrypting sealed evidence...",
                                color = p.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    !errorMessage.isNullOrBlank() -> {
                        EvidenceErrorGlassCard(
                            title = "Evidence Unavailable",
                            message = errorMessage,
                            onRetry = onRetry,
                            onOpenExternal = if (!activeUrl.isNullOrBlank()) onOpenExternal else null
                        )
                    }

                    activeUrl.isNullOrBlank() -> {
                        EvidenceErrorGlassCard(
                            title = "No Media Source",
                            message = "This evidence item does not have a playable URL or file attached yet.",
                            onRetry = onRetry,
                            onOpenExternal = null
                        )
                    }

                    else -> {
                        when (mediaType) {
                            "video" -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    key(activeIndex, activeUrl) {
                                        val playableUri = remember(activeUrl) { toPlayableUri(activeUrl) }
                                        AndroidView(
                                            factory = { ctx ->
                                                VideoView(ctx).apply {
                                                    videoBuffering = true
                                                    videoError = null
                                                    setOnPreparedListener { mp ->
                                                        videoMediaPlayer = mp
                                                        videoBuffering = false
                                                        mp.isLooping = true
                                                        val vol = if (videoMuted) 0f else 1f
                                                        mp.setVolume(vol, vol)
                                                        videoDurationMs = mp.duration.coerceAtLeast(1)
                                                        start()
                                                        videoPlaying = true
                                                    }
                                                    setOnInfoListener { _, what, _ ->
                                                        when (what) {
                                                            MediaPlayer.MEDIA_INFO_BUFFERING_START -> videoBuffering = true
                                                            MediaPlayer.MEDIA_INFO_BUFFERING_END,
                                                            MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> videoBuffering = false
                                                        }
                                                        false
                                                    }
                                                    setOnErrorListener { _, what, extra ->
                                                        videoBuffering = false
                                                        videoPlaying = false
                                                        videoError = "Playback failed (code $what/$extra). Try opening externally."
                                                        true
                                                    }
                                                    setVideoURI(playableUri)
                                                    videoViewRef = this
                                                }
                                            },
                                            update = { vv ->
                                                videoViewRef = vv
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("evidence_video_player")
                                        )
                                    }

                                    if (videoBuffering && videoError == null) {
                                        CircularProgressIndicator(color = p.tangerine)
                                    }

                                    if (!videoError.isNullOrBlank()) {
                                        EvidenceErrorGlassCard(
                                            title = "Video Stream Error",
                                            message = videoError.orEmpty(),
                                            onRetry = onRetry,
                                            onOpenExternal = onOpenExternal
                                        )
                                    }
                                }
                            }

                            "image" -> {
                                val imageModel = remember(activeUrl) {
                                    toCoilModel(activeUrl)
                                }
                                val imageRequest = remember(imageModel, context) {
                                    ImageRequest.Builder(context)
                                        .data(imageModel)
                                        .crossfade(true)
                                        .build()
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(activeIndex) {
                                            detectTapGestures(
                                                onDoubleTap = {
                                                    if (imageScale > 1.05f) {
                                                        imageScale = 1f
                                                        imageOffset = Offset.Zero
                                                    } else {
                                                        imageScale = 2.5f
                                                    }
                                                }
                                            )
                                        }
                                        .pointerInput(activeIndex) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                val nextScale = (imageScale * zoom).coerceIn(1f, 4f)
                                                imageScale = nextScale
                                                imageOffset = if (nextScale <= 1.02f) {
                                                    Offset.Zero
                                                } else {
                                                    Offset(
                                                        x = (imageOffset.x + pan.x).coerceIn(-650f, 650f),
                                                        y = (imageOffset.y + pan.y).coerceIn(-650f, 650f)
                                                    )
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = imageRequest,
                                        contentDescription = activeItem?.title ?: "Evidence Photo",
                                        contentScale = ContentScale.Fit,
                                        onLoading = {
                                            imageDecodeLoading = true
                                            imageDecodeError = null
                                        },
                                        onSuccess = {
                                            imageDecodeLoading = false
                                            imageDecodeError = null
                                        },
                                        onError = { err ->
                                            imageDecodeLoading = false
                                            imageDecodeError = err.result.throwable.message
                                                ?: "Failed to decode evidence image."
                                        },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(
                                                scaleX = imageScale,
                                                scaleY = imageScale,
                                                translationX = imageOffset.x,
                                                translationY = imageOffset.y
                                            )
                                            .testTag("evidence_image_canvas")
                                    )

                                    if (imageDecodeLoading) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            CircularProgressIndicator(color = p.tangerine)
                                            Text(
                                                text = "Rendering high-res evidence...",
                                                color = p.textSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    if (!imageDecodeError.isNullOrBlank()) {
                                        EvidenceErrorGlassCard(
                                            title = "Image Load Failed",
                                            message = imageDecodeError.orEmpty(),
                                            onRetry = onRetry,
                                            onOpenExternal = onOpenExternal
                                        )
                                    }

                                    if (showAnnotationReticle && imageDecodeError == null) {
                                        val reticleColor = p.tangerine
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val cx = size.width * 0.5f
                                            val cy = size.height * 0.46f
                                            val r = size.minDimension * 0.16f
                                            drawCircle(
                                                color = reticleColor.copy(alpha = 0.85f),
                                                radius = r,
                                                center = Offset(cx, cy),
                                                style = Stroke(width = 2.5.dp.toPx())
                                            )
                                            drawLine(
                                                color = reticleColor.copy(alpha = 0.65f),
                                                start = Offset(cx - r * 1.35f, cy),
                                                end = Offset(cx - r * 0.65f, cy),
                                                strokeWidth = 2.dp.toPx()
                                            )
                                            drawLine(
                                                color = reticleColor.copy(alpha = 0.65f),
                                                start = Offset(cx + r * 0.65f, cy),
                                                end = Offset(cx + r * 1.35f, cy),
                                                strokeWidth = 2.dp.toPx()
                                            )
                                        }
                                    }
                                }
                            }

                            "audio" -> {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp)
                                        .complianceGlassCard(28.dp)
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(p.tangerine.copy(alpha = 0.18f))
                                            .border(1.dp, p.tangerine.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Mic,
                                            contentDescription = "Audio memo",
                                            tint = p.tangerine,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                    Text(
                                        text = activeItem?.title ?: "Voice Evidence Memo",
                                        color = p.textPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = if (audioPlaying) "Playing encrypted officer voice note..." else "Paused • Use the capsule scrubber below",
                                        color = p.textSecondary,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            else -> {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp)
                                        .complianceGlassCard(28.dp)
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .background(p.tangerine.copy(alpha = 0.18f))
                                            .border(1.dp, p.tangerine.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Description,
                                            contentDescription = "Document evidence",
                                            tint = p.tangerine,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Text(
                                        text = activeItem?.title ?: "Compliance Document",
                                        color = p.textPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "External compliance PDF / document ready for inspection.",
                                        color = p.textSecondary,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    TangerineGlowButton(
                                        text = "Open Document Externally",
                                        onClick = onOpenExternal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Soft top & bottom gradient scrims so floating HUD stays razor-sharp over bright photos
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                (if (p.isDark) Color(0xFF090B0E) else Color(0xFFF3F5F9)).copy(alpha = 0.78f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                (if (p.isDark) Color(0xFF090B0E) else Color(0xFFF3F5F9)).copy(alpha = 0.88f)
                            )
                        )
                    )
            )

            // 2. Floating Top Glass Bar + Contextual Chips
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassCircleIcon(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        modifier = Modifier.testTag("evidence_back_button")
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .complianceGlassCard(22.dp)
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = "EVIDENCE INSPECTOR",
                            color = p.textSecondary,
                            fontSize = 10.sp,
                            letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = activeItem?.title?.ifBlank { "Compliance Evidence" } ?: "Compliance Evidence",
                            color = p.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box {
                        GlassCircleIcon(
                            icon = Icons.Rounded.MoreVert,
                            contentDescription = "Evidence actions",
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("evidence_more_button")
                        )
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Reload Evidence") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onRetry()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open Externally") },
                                leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onOpenExternal()
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusGlassChip(
                        dotColor = p.emerald,
                        text = sealedChipLabel
                    )
                    StatusGlassChip(
                        dotColor = p.tangerine,
                        text = "Inspector: ${activeItem?.inspectorName?.ifBlank { "Officer" } ?: "Officer"}"
                    )
                    if (selectedVerdict != null) {
                        val (verdictText, verdictColor) = when (selectedVerdict) {
                            EvidenceVerdict.PASS -> "PASS" to p.emerald
                            EvidenceVerdict.FLAG -> "FLAGGED" to p.rose
                            EvidenceVerdict.NOTE -> "NOTE" to p.tangerine
                            null -> "" to p.emerald
                        }
                        StatusGlassChip(
                            dotColor = verdictColor,
                            text = verdictText
                        )
                    }
                }
            }

            // 3. Bottom Evidence Action Dock
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Multi-evidence thumbnail switcher strip (renders real photo previews when resolved)
                if (items.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(items, key = { idx, it -> "${it.id}_$idx" }) { idx, item ->
                            val isSelected = idx == activeIndex
                            val thumbSource = resolvedUrls[item.storedPath.trim()]
                                ?: item.storedPath.trim().takeIf { isDirectLocalUriOrFile(it) || isHttpUrl(it) }
                            val itemType = normalizeEvidenceMediaType(item.mediaType, thumbSource ?: item.storedPath)

                            Box(
                                modifier = Modifier
                                    .size(width = 74.dp, height = 52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(p.trackFill)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) p.tangerine else p.borderTop.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onSelectIndex(idx) }
                                    .testTag("evidence_strip_thumb_$idx"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (itemType == "image" && !thumbSource.isNullOrBlank()) {
                                    AsyncImage(
                                        model = toCoilModel(thumbSource),
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = when (itemType) {
                                            "video" -> Icons.Rounded.PlayCircle
                                            "audio" -> Icons.Rounded.Mic
                                            "document" -> Icons.Rounded.Description
                                            else -> Icons.Rounded.Image
                                        },
                                        contentDescription = item.title,
                                        tint = if (isSelected) p.tangerine else p.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Tactile Frosted Scrubber Capsule
                if (mediaType != "document") {
                    TactileEvidenceScrubberPill(
                        progress = scrubberProgress,
                        label = scrubberLabel,
                        active = when (mediaType) {
                            "video" -> videoPlaying
                            "audio" -> audioPlaying
                            "image" -> imageScale > 1.05f || showAnnotationReticle
                            else -> false
                        },
                        leadingIcon = when (mediaType) {
                            "video" -> if (videoPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow
                            "audio" -> if (audioPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow
                            else -> if (imageScale > 1.05f) Icons.Rounded.Refresh else Icons.Rounded.ZoomIn
                        },
                        trailingIcon = when (mediaType) {
                            "video" -> if (videoMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp
                            "audio" -> Icons.Rounded.Mic
                            else -> Icons.Rounded.CenterFocusStrong
                        },
                        onProgressChange = { next ->
                            when (mediaType) {
                                "video" -> {
                                    val targetMs = (next * videoDurationMs.coerceAtLeast(1)).toInt()
                                    videoPositionMs = targetMs
                                    runCatching { videoViewRef?.seekTo(targetMs) }
                                }
                                "audio" -> {
                                    val targetMs = (next * audioDurationMs.coerceAtLeast(1)).toInt()
                                    audioPositionMs = targetMs
                                    if (audioPrepared) {
                                        runCatching { audioPlayer.seekTo(targetMs) }
                                    }
                                }
                                "image" -> {
                                    imageScale = (1f + next * 3f).coerceIn(1f, 4f)
                                    if (imageScale <= 1.02f) imageOffset = Offset.Zero
                                }
                            }
                        },
                        onLeadingClick = {
                            when (mediaType) {
                                "video" -> {
                                    val vv = videoViewRef
                                    if (vv != null) {
                                        if (videoPlaying) {
                                            runCatching { vv.pause() }
                                            videoPlaying = false
                                        } else {
                                            runCatching { vv.start() }
                                            videoPlaying = true
                                        }
                                    }
                                }
                                "audio" -> {
                                    if (audioPrepared) {
                                        if (audioPlaying) {
                                            runCatching { audioPlayer.pause() }
                                            audioPlaying = false
                                        } else {
                                            runCatching { audioPlayer.start() }
                                            audioPlaying = true
                                        }
                                    }
                                }
                                "image" -> {
                                    if (imageScale > 1.05f) {
                                        imageScale = 1f
                                        imageOffset = Offset.Zero
                                    } else {
                                        imageScale = 2f
                                    }
                                }
                            }
                        },
                        onTrailingClick = {
                            when (mediaType) {
                                "video" -> {
                                    videoMuted = !videoMuted
                                    val vol = if (videoMuted) 0f else 1f
                                    runCatching { videoMediaPlayer?.setVolume(vol, vol) }
                                }
                                "image" -> {
                                    showAnnotationReticle = !showAnnotationReticle
                                }
                                else -> Unit
                            }
                        }
                    )
                }

                // Verdict Action Dock
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .complianceGlassCard(24.dp)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VerdictPill(
                        label = "Pass",
                        tint = p.emerald,
                        selected = selectedVerdict == EvidenceVerdict.PASS,
                        onClick = {
                            selectedVerdict = EvidenceVerdict.PASS
                            onVerdict(EvidenceVerdict.PASS, null)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("evidence_verdict_pass")
                    )
                    VerdictPill(
                        label = "Flag Issue",
                        tint = p.rose,
                        selected = selectedVerdict == EvidenceVerdict.FLAG,
                        onClick = {
                            selectedVerdict = EvidenceVerdict.FLAG
                            onVerdict(EvidenceVerdict.FLAG, null)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("evidence_verdict_flag")
                    )
                    VerdictPill(
                        label = "Add Note",
                        tint = p.tangerine,
                        selected = selectedVerdict == EvidenceVerdict.NOTE,
                        onClick = { showNoteDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("evidence_verdict_note")
                    )
                }
            }
        }
    }

    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            containerColor = if (p.isDark) Color(0xFF151923) else Color(0xFFF6F8FC),
            titleContentColor = p.textPrimary,
            textContentColor = p.textSecondary,
            icon = { Icon(Icons.Rounded.EditNote, contentDescription = null, tint = p.tangerine) },
            title = { Text("Add Inspector Note", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    label = { Text("Inspection Observation") },
                    minLines = 3,
                    colors = glassFieldColors(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedVerdict = EvidenceVerdict.NOTE
                        onVerdict(EvidenceVerdict.NOTE, noteDraft.trim())
                        showNoteDialog = false
                    },
                    enabled = noteDraft.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = p.tangerine, contentColor = Color.White)
                ) {
                    Text("Save Note", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) {
                    Text("Cancel", color = p.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun EvidenceErrorGlassCard(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onOpenExternal: (() -> Unit)?
) {
    val p = LocalCompliancePalette.current
    Column(
        modifier = Modifier
            .padding(24.dp)
            .complianceGlassCard(24.dp)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.BrokenImage,
            contentDescription = null,
            tint = p.rose,
            modifier = Modifier.size(34.dp)
        )
        Text(
            text = title,
            color = p.rose,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = message,
            color = p.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = p.tangerine, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Retry", fontWeight = FontWeight.SemiBold)
            }
            if (onOpenExternal != null) {
                TextButton(onClick = onOpenExternal) {
                    Text("Open Externally", color = p.textPrimary)
                }
            }
        }
    }
}

private fun normalizeEvidenceMediaType(rawType: String, pathOrUrl: String?): String {
    val t = rawType.trim().lowercase(Locale.US)
    val ext = pathOrUrl
        ?.substringBefore('?')
        ?.substringAfterLast('.', "")
        ?.trim()
        ?.lowercase(Locale.US)
        .orEmpty()

    return when {
        t.contains("video") || t in setOf("mp4", "mov", "mkv", "webm", "3gp", "avi") ||
            ext in setOf("mp4", "mov", "mkv", "webm", "3gp", "avi") -> "video"

        t.contains("image") || t.contains("photo") || t.contains("picture") ||
            t in setOf("jpg", "jpeg", "png", "webp", "heic", "bmp", "gif") ||
            ext in setOf("jpg", "jpeg", "png", "webp", "heic", "bmp", "gif") -> "image"

        t.contains("audio") || t.contains("voice") ||
            t in setOf("m4a", "mp3", "wav", "aac", "ogg") ||
            ext in setOf("m4a", "mp3", "wav", "aac", "ogg") -> "audio"

        else -> "document"
    }
}

private fun isHttpUrl(value: String): Boolean {
    val lower = value.trim().lowercase(Locale.US)
    return lower.startsWith("https://") || lower.startsWith("http://")
}

private fun isDirectLocalUriOrFile(value: String): Boolean {
    val trimmed = value.trim()
    val lower = trimmed.lowercase(Locale.US)
    if (lower.startsWith("content://") || lower.startsWith("file://")) return true
    if (trimmed.startsWith("/")) return File(trimmed).exists()
    return false
}

private fun normalizeDirectModelString(value: String): String {
    val trimmed = value.trim()
    return if (trimmed.startsWith("/")) Uri.fromFile(File(trimmed)).toString() else trimmed
}

private fun toPlayableUri(source: String): Uri {
    val trimmed = source.trim()
    return if (trimmed.startsWith("/")) Uri.fromFile(File(trimmed)) else Uri.parse(trimmed)
}

private fun toCoilModel(source: String): Any {
    val trimmed = source.trim()
    return if (trimmed.startsWith("/")) File(trimmed) else trimmed
}

private fun formatTimeMs(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

@Preview(name = "Evidence Previewer Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewEvidencePreviewerDark() {
    JCBestTheme(themePreference = ThemePreference.DARK) {
        EvidencePreviewerScaffold(
            items = listOf(
                EvidencePreviewItem("1", "exit_clip.mp4", "video", "Fire Exit Corridor Clip", "A. Bello"),
                EvidencePreviewItem("2", "lab_log.jpg", "image", "Science Lab Logbook", "A. Bello")
            ),
            activeIndex = 0,
            activeUrl = null,
            isLoading = false,
            errorMessage = null,
            onSelectIndex = {},
            onOpenExternal = {},
            onVerdict = { _, _ -> },
            onBack = {}
        )
    }
}

@Preview(name = "Evidence Previewer Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun PreviewEvidencePreviewerLight() {
    JCBestTheme(themePreference = ThemePreference.LIGHT) {
        EvidencePreviewerScaffold(
            items = listOf(
                EvidencePreviewItem("1", "lab_log.jpg", "image", "Science Lab Logbook", "C. Okafor")
            ),
            activeIndex = 0,
            activeUrl = null,
            isLoading = false,
            errorMessage = null,
            onSelectIndex = {},
            onOpenExternal = {},
            onVerdict = { _, _ -> },
            onBack = {}
        )
    }
}