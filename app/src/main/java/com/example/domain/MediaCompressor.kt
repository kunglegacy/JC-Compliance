package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.roundToInt

data class CompressedMediaResult(
    val file: File,
    val mimeType: String,
    val originalBytes: Long,
    val compressedBytes: Long
)

object MediaCompressor {

    private const val MAX_IMAGE_DIMENSION = 1440
    private const val TARGET_IMAGE_MAX_BYTES = 450 * 1024 // ~450 KB target like WhatsApp HD
    private const val MAX_AVATAR_DIMENSION = 512
    private const val MAX_VIDEO_DIMENSION = 1280
    private const val TARGET_VIDEO_BITRATE = 1_350_000 // ~1.35 Mbps crisp 720p
    private const val VIDEO_FRAME_RATE = 30
    private const val VIDEO_IFRAME_INTERVAL = 2

    suspend fun compressImageFile(
        context: Context,
        sourceFile: File,
        maxDimension: Int = MAX_IMAGE_DIMENSION,
        targetMaxBytes: Int = TARGET_IMAGE_MAX_BYTES,
        onProgress: (Float) -> Unit = {}
    ): Result<CompressedMediaResult> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(0.1f)
            val origSize = sourceFile.length().coerceAtLeast(1L)
            val orientation = readExifOrientation(sourceFile)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
            val rawW = bounds.outWidth
            val rawH = bounds.outHeight
            if (rawW <= 0 || rawH <= 0) {
                throw IllegalArgumentException("Unable to decode image dimensions.")
            }

            val sampleSize = calculateInSampleSize(rawW, rawH, maxDimension)
            onProgress(0.35f)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOpts)
                ?: throw IllegalStateException("Failed to decode bitmap.")

            bitmap = applyExifOrientation(bitmap, orientation)
            bitmap = scaleDownIfNeeded(bitmap, maxDimension)
            onProgress(0.65f)

            val outDir = File(context.cacheDir, "compressed_media").apply { if (!exists()) mkdirs() }
            val outFile = File(outDir, "img_${System.currentTimeMillis()}_${sourceFile.nameWithoutExtension}.jpg")

            var quality = 80
            var bestBytes: ByteArray
            while (true) {
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                bestBytes = baos.toByteArray()
                if (bestBytes.size <= targetMaxBytes || quality <= 56) break
                quality -= 8
            }
            bitmap.recycle()

            FileOutputStream(outFile).use { it.write(bestBytes) }
            onProgress(1f)

            val finalFile = if (outFile.length() < origSize || orientation != ExifInterface.ORIENTATION_NORMAL) {
                outFile
            } else {
                sourceFile
            }
            CompressedMediaResult(
                file = finalFile,
                mimeType = "image/jpeg",
                originalBytes = origSize,
                compressedBytes = finalFile.length()
            )
        }
    }

    suspend fun compressImageUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = MAX_IMAGE_DIMENSION,
        targetMaxBytes: Int = TARGET_IMAGE_MAX_BYTES,
        onProgress: (Float) -> Unit = {}
    ): Result<CompressedMediaResult> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "compressed_src").apply { if (!exists()) mkdirs() }
        val tempFile = File(tempDir, "src_${System.currentTimeMillis()}.jpg")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            tempFile.exists() && tempFile.length() > 0L
        }.getOrDefault(false)

        if (!copied) {
            return@withContext Result.failure(IllegalStateException("Unable to read selected image."))
        }
        val res = compressImageFile(context, tempFile, maxDimension, targetMaxBytes, onProgress)
        if (res.getOrNull()?.file?.absolutePath != tempFile.absolutePath) {
            runCatching { tempFile.delete() }
        }
        res
    }

    suspend fun compressAvatarUri(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<CompressedMediaResult> = compressImageUri(
        context = context,
        uri = uri,
        maxDimension = MAX_AVATAR_DIMENSION,
        targetMaxBytes = 140 * 1024,
        onProgress = onProgress
    )

    suspend fun compressVideoFile(
        context: Context,
        sourceFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<CompressedMediaResult> = withContext(Dispatchers.IO) {
        val origSize = sourceFile.length().coerceAtLeast(1L)
        onProgress(0.05f)

        val retriever = MediaMetadataRetriever()
        var width = 0
        var height = 0
        var rotation = 0
        var durationMs = 0L
        var bitrate = 0
        runCatching {
            retriever.setDataSource(sourceFile.absolutePath)
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
        }
        runCatching { retriever.release() }

        // If the video is already lightweight (< 2.5 MB and <= 720p), skip expensive re-encode
        val maxSide = max(width, height)
        if (origSize <= 2_500_000L && (maxSide == 0 || maxSide <= MAX_VIDEO_DIMENSION) && (bitrate == 0 || bitrate <= 1_800_000)) {
            onProgress(1f)
            return@withContext Result.success(
                CompressedMediaResult(
                    file = sourceFile,
                    mimeType = "video/mp4",
                    originalBytes = origSize,
                    compressedBytes = origSize
                )
            )
        }

        val outDir = File(context.cacheDir, "compressed_media").apply { if (!exists()) mkdirs() }
        val outFile = File(outDir, "vid_${System.currentTimeMillis()}_${sourceFile.nameWithoutExtension}.mp4")

        val transcodeRes = runCatching {
            transcodeVideo720p(
                inputPath = sourceFile.absolutePath,
                outputPath = outFile.absolutePath,
                srcWidth = width,
                srcHeight = height,
                rotationDegrees = rotation,
                durationUs = durationMs * 1000L,
                onProgress = onProgress
            )
        }

        if (transcodeRes.isSuccess && outFile.exists() && outFile.length() in 1024 until origSize) {
            onProgress(1f)
            Result.success(
                CompressedMediaResult(
                    file = outFile,
                    mimeType = "video/mp4",
                    originalBytes = origSize,
                    compressedBytes = outFile.length()
                )
            )
        } else {
            runCatching { outFile.delete() }
            onProgress(1f)
            Result.success(
                CompressedMediaResult(
                    file = sourceFile,
                    mimeType = "video/mp4",
                    originalBytes = origSize,
                    compressedBytes = origSize
                )
            )
        }
    }

    suspend fun compressVideoUri(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<CompressedMediaResult> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "compressed_src").apply { if (!exists()) mkdirs() }
        val tempFile = File(tempDir, "src_vid_${System.currentTimeMillis()}.mp4")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            tempFile.exists() && tempFile.length() > 0L
        }.getOrDefault(false)

        if (!copied) {
            return@withContext Result.failure(IllegalStateException("Unable to read selected video."))
        }
        val res = compressVideoFile(context, tempFile, onProgress)
        if (res.getOrNull()?.file?.absolutePath != tempFile.absolutePath) {
            runCatching { tempFile.delete() }
        }
        res
    }

    suspend fun extractVideoThumbnail(
        context: Context,
        pathOrUri: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            if (pathOrUri.startsWith("/")) {
                retriever.setDataSource(pathOrUri)
            } else {
                retriever.setDataSource(context, Uri.parse(pathOrUri))
            }
            retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readExifOrientation(file: File): Int = runCatching {
        ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        while ((width / sample) > maxDim * 2 || (height / sample) > maxDim * 2) {
            sample *= 2
        }
        return sample
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = max(w, h)
        if (longest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / longest.toFloat()
        val targetW = (w * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (h * ratio).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    private fun transcodeVideo720p(
        inputPath: String,
        outputPath: String,
        srcWidth: Int,
        srcHeight: Int,
        rotationDegrees: Int,
        durationUs: Long,
        onProgress: (Float) -> Unit
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(inputPath)

        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var inputVideoFormat: MediaFormat? = null
        var inputAudioFormat: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("video/") && videoTrackIndex < 0) {
                videoTrackIndex = i
                inputVideoFormat = fmt
            } else if (mime.startsWith("audio/") && audioTrackIndex < 0) {
                audioTrackIndex = i
                inputAudioFormat = fmt
            }
        }

        if (videoTrackIndex < 0 || inputVideoFormat == null) {
            extractor.release()
            throw IllegalStateException("No video track found.")
        }

        val rawW = if (srcWidth > 0) srcWidth else inputVideoFormat.getInteger(MediaFormat.KEY_WIDTH)
        val rawH = if (srcHeight > 0) srcHeight else inputVideoFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val longest = max(rawW, rawH).coerceAtLeast(1)
        val scale = if (longest > MAX_VIDEO_DIMENSION) MAX_VIDEO_DIMENSION.toFloat() / longest.toFloat() else 1f
        // Align to even multiples of 16 for hardware H.264 encoders
        val targetW = (((rawW * scale) / 16f).roundToInt() * 16).coerceAtLeast(160)
        val targetH = (((rawH * scale) / 16f).roundToInt() * 16).coerceAtLeast(160)

        val outputFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetW, targetH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, TARGET_VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_IFRAME_INTERVAL)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val inputMime = inputVideoFormat.getString(MediaFormat.KEY_MIME)!!
        val decoder = MediaCodec.createDecoderByType(inputMime)
        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        if (rotationDegrees != 0) {
            muxer.setOrientationHint(rotationDegrees)
        }

        var inputSurface: CodecInputSurface? = null
        var outputSurface: CodecOutputSurface? = null
        var muxerStarted = false

        try {
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = CodecInputSurface(encoder.createInputSurface())
            encoder.start()

            inputSurface.makeCurrent()
            outputSurface = CodecOutputSurface()
            decoder.configure(inputVideoFormat, outputSurface.surface, null, 0)
            decoder.start()

            extractor.selectTrack(videoTrackIndex)

            var muxerVideoTrack = -1
            var muxerAudioTrack = -1
            val canPassAudio = inputAudioFormat?.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_AAC

            val decInfo = MediaCodec.BufferInfo()
            val encInfo = MediaCodec.BufferInfo()
            var extractorDone = false
            var decoderDone = false
            var encoderDone = false
            val totalUs = durationUs.coerceAtLeast(1_000_000L)

            while (!encoderDone) {
                if (!extractorDone) {
                    val inIdx = decoder.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(buf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            extractorDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                if (!decoderDone) {
                    val decStatus = decoder.dequeueOutputBuffer(decInfo, 10_000L)
                    if (decStatus >= 0) {
                        val render = decInfo.size > 0
                        decoder.releaseOutputBuffer(decStatus, render)
                        if (render) {
                            outputSurface.awaitNewImage()
                            outputSurface.drawImage()
                            inputSurface.setPresentationTime(decInfo.presentationTimeUs * 1000L)
                            inputSurface.swapBuffers()
                            onProgress((decInfo.presentationTimeUs.toFloat() / totalUs.toFloat()).coerceIn(0.08f, 0.9f))
                        }
                        if ((decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            decoderDone = true
                            encoder.signalEndOfInputStream()
                        }
                    }
                }

                var draining = true
                while (draining) {
                    val encStatus = encoder.dequeueOutputBuffer(encInfo, 5_000L)
                    when {
                        encStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> draining = false
                        encStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                            if (canPassAudio && inputAudioFormat != null) {
                                muxerAudioTrack = muxer.addTrack(inputAudioFormat)
                            }
                            muxer.start()
                            muxerStarted = true
                        }
                        encStatus >= 0 -> {
                            val outBuf = encoder.getOutputBuffer(encStatus)
                            if (outBuf != null && encInfo.size > 0 && (encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && muxerStarted) {
                                outBuf.position(encInfo.offset)
                                outBuf.limit(encInfo.offset + encInfo.size)
                                muxer.writeSampleData(muxerVideoTrack, outBuf, encInfo)
                            }
                            encoder.releaseOutputBuffer(encStatus, false)
                            if ((encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                encoderDone = true
                                draining = false
                            }
                        }
                    }
                }
            }

            // Copy AAC audio track verbatim (fast & preserves clean voice/ambient audio)
            if (muxerStarted && muxerAudioTrack >= 0 && audioTrackIndex >= 0) {
                extractor.unselectTrack(videoTrackIndex)
                extractor.selectTrack(audioTrackIndex)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val audioBuf = ByteBuffer.allocate(256 * 1024)
                val audioInfo = MediaCodec.BufferInfo()
                while (true) {
                    val size = extractor.readSampleData(audioBuf, 0)
                    if (size < 0) break
                    audioInfo.offset = 0
                    audioInfo.size = size
                    audioInfo.presentationTimeUs = extractor.sampleTime
                    audioInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerAudioTrack, audioBuf, audioInfo)
                    extractor.advance()
                }
            }
            onProgress(0.98f)
        } finally {
            runCatching { decoder.stop(); decoder.release() }
            runCatching { encoder.stop(); encoder.release() }
            runCatching { outputSurface?.release() }
            runCatching { inputSurface?.release() }
            runCatching { if (muxerStarted) muxer.stop(); muxer.release() }
            runCatching { extractor.release() }
        }
    }

    private class CodecInputSurface(private var surface: Surface?) {
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        init {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)
            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)
            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            val surfAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfAttribs, 0)
        }

        fun makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        fun swapBuffers() {
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun setPresentationTime(nsecs: Long) {
            android.opengl.EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
        }

        fun release() {
            if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eglDisplay)
            }
            surface?.release()
            surface = null
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }
    }

    private class CodecOutputSurface : SurfaceTexture.OnFrameAvailableListener {
        private val frameSync = Object()
        private var frameAvailable = false
        private val textureId: Int
        private val surfaceTexture: SurfaceTexture
        val surface: Surface

        private val program: Int
        private val uSTMatrixHandle: Int
        private val aPositionHandle: Int
        private val aTextureHandle: Int
        private val stMatrix = FloatArray(16)
        private val vertexBuffer: FloatBuffer

        init {
            val triangleVertices = floatArrayOf(
                -1.0f, -1.0f, 0f, 0f, 0f,
                1.0f, -1.0f, 0f, 1f, 0f,
                -1.0f, 1.0f, 0f, 0f, 1f,
                1.0f, 1.0f, 0f, 1f, 1f
            )
            vertexBuffer = ByteBuffer.allocateDirect(triangleVertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(triangleVertices); position(0) }

            val texIds = IntArray(1)
            GLES20.glGenTextures(1, texIds, 0)
            textureId = texIds[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            program = buildProgram()
            aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
            uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")

            surfaceTexture = SurfaceTexture(textureId)
            surfaceTexture.setOnFrameAvailableListener(this)
            surface = Surface(surfaceTexture)
        }

        override fun onFrameAvailable(st: SurfaceTexture?) {
            synchronized(frameSync) {
                frameAvailable = true
                frameSync.notifyAll()
            }
        }

        fun awaitNewImage() {
            synchronized(frameSync) {
                while (!frameAvailable) {
                    frameSync.wait(2500L)
                    if (!frameAvailable) break
                }
                frameAvailable = false
            }
            surfaceTexture.updateTexImage()
        }

        fun drawImage() {
            surfaceTexture.getTransformMatrix(stMatrix)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

            vertexBuffer.position(0)
            GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 20, vertexBuffer)
            GLES20.glEnableVertexAttribArray(aPositionHandle)

            vertexBuffer.position(3)
            GLES20.glVertexAttribPointer(aTextureHandle, 2, GLES20.GL_FLOAT, false, 20, vertexBuffer)
            GLES20.glEnableVertexAttribArray(aTextureHandle)

            GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, stMatrix, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glFinish()
        }

        fun release() {
            surface.release()
            surfaceTexture.release()
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }

        private fun buildProgram(): Int {
            val vSrc = """
                attribute vec4 aPosition;
                attribute vec4 aTextureCoord;
                uniform mat4 uSTMatrix;
                varying vec2 vTextureCoord;
                void main() {
                    gl_Position = aPosition;
                    vTextureCoord = (uSTMatrix * aTextureCoord).xy;
                }
            """.trimIndent()
            val fSrc = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    gl_FragColor = texture2D(sTexture, vTextureCoord);
                }
            """.trimIndent()
            val vShader = compileShader(GLES20.GL_VERTEX_SHADER, vSrc)
            val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fSrc)
            val prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, vShader)
            GLES20.glAttachShader(prog, fShader)
            GLES20.glLinkProgram(prog)
            return prog
        }

        private fun compileShader(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            return shader
        }
    }
}
