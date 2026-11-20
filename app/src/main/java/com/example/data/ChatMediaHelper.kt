package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class EncodedMediaPayload(
    /** Contenido en línea (imagen estándar, miniatura de video o audio corto). Puede ir vacío. */
    val base64Data: String,
    val mimeType: String,
    val displayName: String,
    val durationSec: Long = 0L,
    /** Archivo completo que se sube troceado (HD, audio largo, video). */
    val fullFile: File? = null,
    /** Onda de la nota de voz codificada (ver [VoiceWaveformCodec]). */
    val waveform: String? = null,
)

/**
 * Codifica la onda de una nota de voz en un texto corto: [BARS] niveles de 0 a 31,
 * un carácter por barra. Se guarda en el mensaje para dibujar el espectro.
 */
object VoiceWaveformCodec {
    const val BARS = 48
    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuv"

    fun encode(samples: List<Float>): String? {
        if (samples.isEmpty()) return null
        val bars = (0 until BARS).map { i ->
            val from = (i * samples.size) / BARS
            val to = (((i + 1) * samples.size) / BARS).coerceAtLeast(from + 1).coerceAtMost(samples.size)
            samples.subList(from.coerceAtMost(samples.size - 1), to).maxOrNull() ?: 0f
        }
        val peak = bars.maxOrNull()?.takeIf { it > 0.01f } ?: 1f
        return bars.joinToString("") { level ->
            val norm = (level / peak).coerceIn(0f, 1f)
            ALPHABET[(norm * 31f).toInt().coerceIn(0, 31)].toString()
        }
    }

    fun decode(encoded: String?): List<Float>? {
        if (encoded.isNullOrBlank()) return null
        val values = encoded.mapNotNull { c -> ALPHABET.indexOf(c).takeIf { it >= 0 }?.let { it / 31f } }
        return values.takeIf { it.size >= 8 }
    }

    /** Onda estable de relleno para mensajes antiguos (derivada del id). */
    fun pseudo(seed: String, bars: Int = BARS): List<Float> {
        var x = seed.hashCode().toLong() and 0xffffffffL
        return List(bars) { i ->
            x = (x * 1103515245L + 12345L) and 0x7fffffffL
            val r = (x % 1000L) / 1000f
            val envelope = 0.55f + 0.45f * kotlin.math.sin(i / bars.toFloat() * Math.PI.toFloat())
            (0.25f + r * 0.75f) * envelope
        }
    }
}

data class AudioPlaybackUiState(
    val activeMessageId: String? = null,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val currentSec: Long = 0L,
)

object ChatMediaHelper {
    private const val MAX_IMAGE_DIMENSION_PX = 640
    private const val MAX_IMAGE_BYTES = 220_000
    private const val MAX_AVATAR_DIMENSION_PX = 256
    private const val MAX_AVATAR_BYTES = 65_000
    private const val MAX_AUDIO_BYTES = 450_000

    fun createTempCameraImageUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val photoFile = File(dir, "cam_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile,
        )
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(degrees) }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        } catch (_: Exception) {
            bitmap
        }
    }

    suspend fun compressAvatarToBase64(context: Context, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    return@withContext Result.failure(IllegalArgumentException("No se pudo leer la foto de perfil."))
                }

                var sampleSize = 1
                val minSideOrig = kotlin.math.min(bounds.outWidth, bounds.outHeight)
                while (minSideOrig / sampleSize > MAX_AVATAR_DIMENSION_PX * 2) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val sampled = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                } ?: return@withContext Result.failure(IllegalArgumentException("No se pudo abrir la foto seleccionada."))

                val cropSize = kotlin.math.min(sampled.width, sampled.height)
                val cropX = ((sampled.width - cropSize) / 2).coerceAtLeast(0)
                val cropY = ((sampled.height - cropSize) / 2).coerceAtLeast(0)
                val square = Bitmap.createBitmap(sampled, cropX, cropY, cropSize, cropSize).also {
                    if (it !== sampled) sampled.recycle()
                }
                val scaled = if (cropSize > MAX_AVATAR_DIMENSION_PX) {
                    Bitmap.createScaledBitmap(square, MAX_AVATAR_DIMENSION_PX, MAX_AVATAR_DIMENSION_PX, true).also {
                        if (it !== square) square.recycle()
                    }
                } else {
                    square
                }

                val baos = ByteArrayOutputStream()
                var quality = 80
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                while (baos.size() > MAX_AVATAR_BYTES && quality > 30) {
                    baos.reset()
                    quality -= 10
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                }
                val bytes = baos.toByteArray()
                scaled.recycle()
                Result.success(Base64.encodeToString(bytes, Base64.NO_WRAP))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private const val HD_IMAGE_DIMENSION_PX = 2048
    private const val HD_IMAGE_MAX_BYTES = 3_500_000

    /**
     * Comprime la imagen a calidad estándar (en línea) y, si [hd] está activo, además
     * genera una versión HD (hasta 2048 px) que se sube troceada.
     */
    suspend fun compressImageToBase64(context: Context, uri: Uri, hd: Boolean): Result<EncodedMediaPayload> {
        val standard = compressImageToBase64(context, uri)
        if (!hd || standard.isFailure) return standard
        val hdFile = withContext(Dispatchers.IO) {
            runCatching { encodeHdImageFile(context, uri) }.getOrNull()
        }
        return Result.success(standard.getOrThrow().copy(fullFile = hdFile))
    }

    private fun encodeHdImageFile(context: Context, uri: Uri): File? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        val maxSide = max(bounds.outWidth, bounds.outHeight)
        while (maxSide / (sampleSize * 2) >= HD_IMAGE_DIMENSION_PX) sampleSize *= 2
        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: return null
        val rotated = applyExifRotation(context, uri, raw)
        val scale = HD_IMAGE_DIMENSION_PX.toFloat() / max(rotated.width, rotated.height).toFloat()
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                rotated,
                (rotated.width * scale).roundToInt().coerceAtLeast(1),
                (rotated.height * scale).roundToInt().coerceAtLeast(1),
                true,
            ).also { if (it !== rotated) rotated.recycle() }
        } else {
            rotated
        }
        val dir = File(context.cacheDir, "outgoing_media").apply { mkdirs() }
        val out = File(dir, "hd_${System.currentTimeMillis()}.jpg")
        var quality = 90
        while (true) {
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            if (out.length() <= HD_IMAGE_MAX_BYTES || quality <= 60) break
            quality -= 8
        }
        bitmap.recycle()
        return out
    }

    suspend fun compressImageToBase64(context: Context, uri: Uri): Result<EncodedMediaPayload> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    return@withContext Result.failure(IllegalArgumentException("No se pudo leer la imagen seleccionada."))
                }

                var sampleSize = 1
                val maxSide = max(bounds.outWidth, bounds.outHeight)
                while (maxSide / sampleSize > MAX_IMAGE_DIMENSION_PX * 2) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val sampledRaw = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                } ?: return@withContext Result.failure(IllegalArgumentException("No se pudo abrir la imagen."))
                val sampled = applyExifRotation(context, uri, sampledRaw)

                val scale = MAX_IMAGE_DIMENSION_PX.toFloat() / max(sampled.width, sampled.height).toFloat()
                val bitmap = if (scale < 1f) {
                    val targetW = (sampled.width * scale).roundToInt().coerceAtLeast(1)
                    val targetH = (sampled.height * scale).roundToInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(sampled, targetW, targetH, true).also {
                        if (it !== sampled) sampled.recycle()
                    }
                } else {
                    sampled
                }

                val baos = ByteArrayOutputStream()
                var quality = 82
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                while (baos.size() > MAX_IMAGE_BYTES && quality > 28) {
                    baos.reset()
                    quality -= 12
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                }
                val bytes = baos.toByteArray()
                bitmap.recycle()

                if (bytes.size > MAX_IMAGE_BYTES * 2) {
                    return@withContext Result.failure(IllegalArgumentException("La imagen sigue siendo muy pesada para enviarla."))
                }

                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val name = queryDisplayName(context, uri) ?: "imagen.jpg"
                Result.success(
                    EncodedMediaPayload(
                        base64Data = base64,
                        mimeType = "image/jpeg",
                        displayName = name.take(120),
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun readAudioToBase64(context: Context, uri: Uri): Result<EncodedMediaPayload> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mime = resolver.getType(uri)?.takeIf { it.startsWith("audio/") } ?: "audio/mp4"
                val name = queryDisplayName(context, uri) ?: "audio.m4a"
                val dir = File(context.cacheDir, "outgoing_media").apply { mkdirs() }
                val copy = File(dir, "audio_${System.currentTimeMillis()}")
                val total = resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(copy).use { out ->
                        val chunk = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val read = input.read(chunk)
                            if (read <= 0) break
                            total += read
                            if (total > ChunkedMediaStore.MAX_CHUNKED_BYTES) {
                                copy.delete()
                                return@withContext Result.failure(
                                    IllegalArgumentException("El audio supera el límite de 24 MB.")
                                )
                            }
                            out.write(chunk, 0, read)
                        }
                        total
                    }
                } ?: return@withContext Result.failure(IllegalArgumentException("No se pudo leer el archivo de audio."))

                if (total <= 0L) {
                    copy.delete()
                    return@withContext Result.failure(IllegalArgumentException("El archivo de audio está vacío."))
                }

                val durationSec = extractAudioDurationSec(context, uri)
                val payload = if (total <= MAX_AUDIO_BYTES) {
                    val base64 = Base64.encodeToString(copy.readBytes(), Base64.NO_WRAP)
                    copy.delete()
                    EncodedMediaPayload(base64, mime.take(64), name.take(120), durationSec)
                } else {
                    EncodedMediaPayload("", mime.take(64), name.take(120), durationSec, fullFile = copy)
                }
                Result.success(payload)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun decodeBase64Bitmap(base64Data: String?): Bitmap? {
        if (base64Data.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAudioDurationSec(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            (ms / 1000L).coerceIn(0L, 3600L)
        } catch (_: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}

class VoiceNoteRecorder(private val context: Context) {
    companion object {
        const val MAX_VOICE_NOTE_SEC = 300L
        const val MAX_VOICE_NOTE_MS = (MAX_VOICE_NOTE_SEC * 1000L).toInt()
    }

    private var recorder: MediaRecorder? = null

    val isRecording: Boolean get() = recorder != null

    /** Nivel de entrada 0..1 desde la última llamada (para la onda de grabación). */
    fun currentLevel(): Float {
        val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        return (amp / 18_000f).coerceIn(0f, 1f)
    }

    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    fun startRecording(): Result<Unit> {
        stopSilently()
        return try {
            val dir = File(context.cacheDir, "voice_notes").apply { mkdirs() }
            val file = File(dir, "rec_${System.currentTimeMillis()}.m4a")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context.applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(24_000)
            mr.setAudioSamplingRate(16_000)
            mr.setMaxDuration(MAX_VOICE_NOTE_MS)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            outputFile = file
            startedAtMs = System.currentTimeMillis()
            Result.success(Unit)
        } catch (e: Exception) {
            stopSilently()
            Result.failure(e)
        }
    }

    fun cancelRecording() {
        stopSilently()
        outputFile?.delete()
        outputFile = null
    }

    suspend fun finishAndEncode(): Result<EncodedMediaPayload> = withContext(Dispatchers.IO) {
        val file = outputFile
        val elapsedSec = ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceAtLeast(1L)
        stopSilently()
        outputFile = null

        if (file == null || !file.exists() || file.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("No se grabó audio válido."))
        }

        return@withContext try {
            val duration = elapsedSec.coerceIn(1L, MAX_VOICE_NOTE_SEC)
            if (file.length() <= ChunkedMediaStore.INLINE_LIMIT_BYTES) {
                val base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                file.delete()
                Result.success(EncodedMediaPayload(base64, "audio/mp4", "nota_voz.m4a", duration))
            } else {
                Result.success(EncodedMediaPayload("", "audio/mp4", "nota_voz.m4a", duration, fullFile = file))
            }
        } catch (e: Exception) {
            file.delete()
            Result.failure(e)
        }
    }

    private fun stopSilently() {
        val mr = recorder ?: return
        recorder = null
        try {
            mr.stop()
        } catch (_: Exception) {
        }
        try {
            mr.release()
        } catch (_: Exception) {
        }
    }
}

object ChatAudioPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var tempFile: File? = null

    private val _playbackState = MutableStateFlow(AudioPlaybackUiState())
    val playbackState: StateFlow<AudioPlaybackUiState> = _playbackState.asStateFlow()

    // Notas de voz del chat abierto, en orden cronológico, para encadenar la reproducción.
    private var playlist: List<com.example.model.DirectMessage> = emptyList()
    private var appContextRef: Context? = null
    private var userPaused = false

    /** Registra las notas de voz del chat abierto para reproducir la siguiente al terminar. */
    fun updatePlaylist(messages: List<com.example.model.DirectMessage>) {
        playlist = messages.filter { it.messageType == com.example.model.DirectMessage.TYPE_VOICE }
    }

    private fun onPlaybackCompleted(finishedId: String?) {
        val ctx = appContextRef
        stop()
        if (finishedId == null || ctx == null) return
        val idx = playlist.indexOfFirst { it.id == finishedId }
        val next = if (idx >= 0) playlist.getOrNull(idx + 1) else null
        if (next != null) playFrom(ctx, next, 0f)
    }

    val speeds = listOf(1f, 1.5f, 2f)
    private val _speed = MutableStateFlow(1f)
    /** Velocidad de reproducción global de notas de voz (1x, 1.5x, 2x). */
    val speed: StateFlow<Float> = _speed.asStateFlow()

    fun cycleSpeed() {
        val idx = speeds.indexOf(_speed.value).takeIf { it >= 0 } ?: 0
        _speed.value = speeds[(idx + 1) % speeds.size]
        applySpeedIfPlaying()
    }

    private fun applySpeedIfPlaying() {
        val mp = mediaPlayer ?: return
        // Cambiar playbackParams con el reproductor en pausa lo reanuda en algunos equipos.
        if (!mp.isPlaying) return
        runCatching { mp.playbackParams = mp.playbackParams.setSpeed(_speed.value) }
    }

    /** Reproduce/pausa un mensaje de voz o audio (en línea o troceado). */
    fun togglePlayMessage(context: Context, message: com.example.model.DirectMessage) {
        val current = _playbackState.value
        if (current.activeMessageId == message.id && mediaPlayer != null) {
            pauseOrResume()
            return
        }
        playFrom(context, message, 0f)
    }

    /**
     * Salta a [fraction] (0..1) del mensaje. Si ya está cargado solo mueve la posición
     * (respetando pausa/reproducción); si no, empieza a reproducir desde ahí.
     */
    fun seekMessage(context: Context, message: com.example.model.DirectMessage, fraction: Float) {
        val f = fraction.coerceIn(0f, 1f)
        val mp = mediaPlayer
        if (_playbackState.value.activeMessageId == message.id && mp != null) {
            val duration = mp.duration.coerceAtLeast(1)
            val pos = (duration * f).toInt().coerceIn(0, duration - 1)
            runCatching { mp.seekTo(pos) }
            _playbackState.value = _playbackState.value.copy(progress = f, currentSec = pos / 1000L)
            return
        }
        playFrom(context, message, f)
    }

    private fun playFrom(context: Context, message: com.example.model.DirectMessage, fraction: Float) {
        stop()
        _playbackState.value = AudioPlaybackUiState(activeMessageId = message.id, isLoading = true, progress = fraction)
        val appContext = context.applicationContext
        appContextRef = appContext
        userPaused = false
        scope.launch {
            val file = ChunkedMediaStore.resolveFullMedia(appContext, message)
            if (_playbackState.value.activeMessageId != message.id) return@launch
            if (file == null) {
                _playbackState.value = AudioPlaybackUiState()
                return@launch
            }
            startPlayer(message.id, file, deleteOnStop = false, startFraction = fraction)
        }
    }

    private fun pauseOrResume() {
        val mp = mediaPlayer ?: return
        val current = _playbackState.value
        if (mp.isPlaying) {
            userPaused = true
            mp.pause()
            progressJob?.cancel()
            _playbackState.value = current.copy(isPlaying = false)
        } else {
            userPaused = false
            mp.start()
            applySpeedIfPlaying()
            _playbackState.value = current.copy(isPlaying = true)
            startProgressTicker()
        }
    }

    private fun startPlayer(messageId: String, file: File, deleteOnStop: Boolean, startFraction: Float = 0f) {
        try {
            tempFile = if (deleteOnStop) file else null
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { onPlaybackCompleted(messageId) }
                prepare()
            }
            if (startFraction > 0f) {
                val duration = mp.duration.coerceAtLeast(1)
                mp.seekTo((duration * startFraction).toInt().coerceIn(0, duration - 1))
            }
            mp.start()
            mediaPlayer = mp
            applySpeedIfPlaying()
            _playbackState.value = AudioPlaybackUiState(
                activeMessageId = messageId,
                isPlaying = true,
                progress = startFraction,
                currentSec = (mp.currentPosition / 1000L),
            )
            startProgressTicker()
        } catch (_: Exception) {
            stop()
        }
    }

    /** Compatibilidad: reproduce audio en línea identificado solo por id + base64. */
    fun togglePlay(context: Context, messageId: String, base64Data: String?) {
        val current = _playbackState.value
        if (current.activeMessageId == messageId && mediaPlayer != null) {
            pauseOrResume()
            return
        }
        if (base64Data.isNullOrBlank()) return
        stop()
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                try {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    val dir = File(context.cacheDir, "audio_play").apply { mkdirs() }
                    val f = File(dir, "play_${messageId.hashCode()}.m4a")
                    FileOutputStream(f).use { it.write(bytes) }
                    f
                } catch (_: Exception) {
                    null
                }
            } ?: return@launch
            startPlayer(messageId, file, deleteOnStop = true)
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                val mp = mediaPlayer ?: break
                if (!mp.isPlaying) {
                    // Terminó sin pausa del usuario: algunos equipos tardan en avisar la finalización.
                    val dur = runCatching { mp.duration }.getOrDefault(0)
                    val pos = runCatching { mp.currentPosition }.getOrDefault(0)
                    if (!userPaused && dur > 0 && pos >= dur - 400) {
                        onPlaybackCompleted(_playbackState.value.activeMessageId)
                    }
                    break
                }
                val duration = mp.duration.coerceAtLeast(1)
                val pos = mp.currentPosition.coerceAtLeast(0)
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = true,
                    isLoading = false,
                    progress = (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                    currentSec = (pos / 1000L),
                )
                delay(100L)
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        tempFile?.delete()
        tempFile = null
        _playbackState.value = AudioPlaybackUiState()
    }
}
