package com.example.data

import com.example.R
import com.example.i18n.tr

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Comprime videos en el teléfono antes de enviarlos (H.264 + AAC en MP4).
 * Estándar: lado corto 480 px a ~1 Mbps, hasta 2:30.
 * HD: lado corto 720 px a ~2,5 Mbps, hasta 1:00.
 */
object ChatVideoHelper {
    private const val SD_SHORT_SIDE = 480
    private const val HD_SHORT_SIDE = 720
    private const val SD_BITRATE = 1_000_000
    private const val HD_BITRATE = 2_500_000
    const val SD_MAX_SEC = 150L
    const val HD_MAX_SEC = 60L

    data class PreparedVideo(val payload: EncodedMediaPayload, val wasTrimmed: Boolean)

    fun isVideo(context: Context, uri: Uri): Boolean =
        context.contentResolver.getType(uri)?.startsWith("video/") == true

    suspend fun prepare(context: Context, uri: Uri, hd: Boolean): Result<PreparedVideo> {
        val appContext = context.applicationContext
        val meta = withContext(Dispatchers.IO) { readMetadata(appContext, uri) }
            ?: return Result.failure(IllegalArgumentException(tr(R.string.s_no_se_pudo_leer_el_2)))
        val maxSec = if (hd) HD_MAX_SEC else SD_MAX_SEC
        val trimmed = meta.durationMs > maxSec * 1000L
        val effectiveMs = min(meta.durationMs, maxSec * 1000L)

        val outDir = File(appContext.cacheDir, "outgoing_media").apply { mkdirs() }
        val out = File(outDir, "video_${System.currentTimeMillis()}.mp4")
        val transcode = withContext(Dispatchers.Main) {
            transcode(appContext, uri, out, meta, hd, if (trimmed) effectiveMs else null)
        }
        if (transcode.isFailure) {
            out.delete()
            return Result.failure(transcode.exceptionOrNull() ?: IllegalStateException(tr(R.string.s_no_se_pudo_comprimir_el)))
        }
        if (out.length() > ChunkedMediaStore.MAX_CHUNKED_BYTES) {
            out.delete()
            return Result.failure(IllegalArgumentException(tr(R.string.s_el_video_sigue_siendo_muy)))
        }
        val thumb = withContext(Dispatchers.IO) { thumbnailBase64(out) ?: meta.thumbBase64 }.orEmpty()
        val name = "video_${System.currentTimeMillis()}.mp4"
        return Result.success(
            PreparedVideo(
                payload = EncodedMediaPayload(
                    base64Data = thumb,
                    mimeType = "video/mp4",
                    displayName = name,
                    durationSec = (effectiveMs / 1000L).coerceAtLeast(1L),
                    fullFile = out,
                ),
                wasTrimmed = trimmed,
            )
        )
    }

    private data class VideoMeta(
        val durationMs: Long,
        val displayWidth: Int,
        val displayHeight: Int,
        val thumbBase64: String?,
    )

    private fun readMetadata(context: Context, uri: Uri): VideoMeta? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (duration <= 0L || w <= 0 || h <= 0) return null
            val rotated = rotation == 90 || rotation == 270
            val thumb = mmr.getFrameAtTime(0)?.let { encodeThumb(it) }
            VideoMeta(
                durationMs = duration,
                displayWidth = if (rotated) h else w,
                displayHeight = if (rotated) w else h,
                thumbBase64 = thumb,
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun thumbnailBase64(file: File): String? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            mmr.getFrameAtTime(0)?.let { encodeThumb(it) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun encodeThumb(frame: Bitmap): String {
        val scale = 480f / max(frame.width, frame.height).toFloat()
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                frame,
                (frame.width * scale).roundToInt().coerceAtLeast(1),
                (frame.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            frame
        }
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        if (bmp !== frame) bmp.recycle()
        frame.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    @OptIn(UnstableApi::class)
    private suspend fun transcode(
        context: Context,
        uri: Uri,
        out: File,
        meta: VideoMeta,
        hd: Boolean,
        clipEndMs: Long?,
    ): Result<Unit> = suspendCancellableCoroutine { cont ->
        val targetShort = min(if (hd) HD_SHORT_SIDE else SD_SHORT_SIDE, min(meta.displayWidth, meta.displayHeight))
        val portrait = meta.displayHeight >= meta.displayWidth
        val outHeight = if (portrait) {
            (targetShort.toFloat() * meta.displayHeight / meta.displayWidth).roundToInt()
        } else {
            targetShort
        }.let { it - (it % 2) }.coerceAtLeast(2)

        val itemBuilder = MediaItem.Builder().setUri(uri)
        if (clipEndMs != null) {
            itemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setEndPositionMs(clipEndMs).build()
            )
        }
        val edited = EditedMediaItem.Builder(itemBuilder.build())
            .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(outHeight))))
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder().setBitrate(if (hd) HD_BITRATE else SD_BITRATE).build()
            )
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(Result.success(Unit))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    if (cont.isActive) {
                        cont.resume(Result.failure(IllegalStateException(tr(R.string.s_no_se_pudo_comprimir_el), exportException)))
                    }
                }
            })
            .build()

        cont.invokeOnCancellation { runCatching { transformer.cancel() } }
        try {
            transformer.start(edited, out.absolutePath)
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(Result.failure(e))
        }
    }
}
