package com.example.data

import android.content.Context
import android.util.Base64
import com.example.R
import com.example.model.DirectMessage
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Guarda archivos grandes (HD, notas de voz largas, videos) dentro de Firestore,
 * repartidos en documentos `.../messages/{id}/chunks/{index}`. Cada trozo lleva
 * como mucho [PART_BYTES] bytes (≈ 683 KB en base64), bajo el límite de 1 MiB por
 * documento y el máximo de 700 000 caracteres de las reglas.
 */
object ChunkedMediaStore {
    const val PART_BYTES = 512_000
    /** Tamaño a partir del cual un adjunto deja de ir en línea y se trocea. */
    const val INLINE_LIMIT_BYTES = 480_000
    const val MAX_CHUNKED_BYTES = 24L * 1024L * 1024L

    private val fileLocks = mutableMapOf<String, Mutex>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<File?>>()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    /** messageId -> progreso 0..1 de descargas en curso. */
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    fun db(context: Context): FirebaseFirestore =
        FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id))

    fun partCount(sizeBytes: Long): Int =
        ((sizeBytes + PART_BYTES - 1) / PART_BYTES).toInt().coerceAtLeast(1)

    /** Sube [file] en trozos bajo [messageRef] (el mensaje aún puede no existir). */
    suspend fun upload(messageRef: DocumentReference, senderId: String, file: File): Int =
        withContext(Dispatchers.IO) {
            val size = file.length()
            require(size > 0) { "Archivo vacío" }
            require(size <= MAX_CHUNKED_BYTES) { "El archivo supera ${MAX_CHUNKED_BYTES / (1024 * 1024)} MB" }
            val parts = partCount(size)
            RandomAccessFile(file, "r").use { raf ->
                val buffer = ByteArray(PART_BYTES)
                for (index in 0 until parts) {
                    raf.seek(index.toLong() * PART_BYTES)
                    val read = raf.read(buffer, 0, PART_BYTES)
                    val encoded = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                    messageRef.collection("chunks").document(index.toString()).set(
                        mapOf(
                            "index" to index.toLong(),
                            "senderId" to senderId,
                            "data" to encoded,
                        )
                    ).await()
                }
            }
            parts
        }

    /** Borra los trozos de un mensaje (mejor esfuerzo). */
    suspend fun deleteChunks(messageRef: DocumentReference) {
        runCatching {
            val snaps = messageRef.collection("chunks").get().await()
            snaps.documents.forEach { it.reference.delete().await() }
        }
    }

    fun cacheFile(context: Context, messageId: String, extension: String): File {
        val dir = File(context.cacheDir, "chat_media").apply { mkdirs() }
        return File(dir, "${messageId}.$extension")
    }

    fun cachedFileOrNull(context: Context, message: DirectMessage): File? {
        val f = cacheFile(context, message.id, extensionFor(message))
        return f.takeIf { it.exists() && it.length() > 0 }
    }

    fun extensionFor(message: DirectMessage): String = when (message.messageType) {
        DirectMessage.TYPE_IMAGE -> "jpg"
        DirectMessage.TYPE_VIDEO -> "mp4"
        else -> when {
            message.mediaMimeType?.contains("mpeg") == true -> "mp3"
            message.mediaMimeType?.contains("ogg") == true -> "ogg"
            message.mediaMimeType?.contains("wav") == true -> "wav"
            message.mediaMimeType?.contains("amr") == true -> "amr"
            else -> "m4a"
        }
    }

    /**
     * Devuelve el archivo completo del mensaje (descargando y uniendo trozos si hace
     * falta). Para mensajes sin trozos decodifica [DirectMessage.mediaData].
     */
    suspend fun resolveFullMedia(context: Context, message: DirectMessage): File? {
        cachedFileOrNull(context, message)?.let { return it }
        val key = message.id
        val (deferred, owner) = synchronized(inFlight) {
            val existing = inFlight[key]
            if (existing != null) existing to false
            else CompletableDeferred<File?>().also { inFlight[key] = it } to true
        }
        if (!owner) return deferred.await()
        val result = try {
            lockFor(key).withLock { downloadInternal(context, message) }
        } catch (_: Exception) {
            null
        }
        deferred.complete(result)
        synchronized(inFlight) { inFlight.remove(key) }
        return result
    }

    private suspend fun downloadInternal(context: Context, message: DirectMessage): File? =
        withContext(Dispatchers.IO) {
            cachedFileOrNull(context, message)?.let { return@withContext it }
            val target = cacheFile(context, message.id, extensionFor(message))
            val tmp = File(target.parentFile, "${target.name}.part")
            if (message.chunkCount <= 0L) {
                val b64 = message.mediaData ?: return@withContext null
                if (b64.isBlank()) return@withContext null
                FileOutputStream(tmp).use { it.write(Base64.decode(b64, Base64.DEFAULT)) }
            } else {
                val parentId = message.chatId
                val collection = message.sourceCollection.ifBlank { DirectMessage.SOURCE_CHATS }
                val chunksRef = db(context).collection(collection).document(parentId)
                    .collection("messages").document(message.id)
                    .collection("chunks")
                val total = message.chunkCount.toInt()
                setProgress(message.id, 0f)
                FileOutputStream(tmp).use { out ->
                    // Se piden de a uno para no cargar todo el video en memoria.
                    for (index in 0 until total) {
                        val snap = chunksRef.document(index.toString()).get().await()
                        val data = snap.getString("data") ?: error("Falta el trozo $index")
                        out.write(Base64.decode(data, Base64.DEFAULT))
                        setProgress(message.id, (index + 1f) / total)
                    }
                }
                clearProgress(message.id)
            }
            if (tmp.length() <= 0L) {
                tmp.delete()
                return@withContext null
            }
            tmp.renameTo(target)
            target
        }

    private fun lockFor(key: String): Mutex = synchronized(fileLocks) {
        fileLocks.getOrPut(key) { Mutex() }
    }

    private fun setProgress(messageId: String, value: Float) {
        _downloadProgress.value = _downloadProgress.value + (messageId to value)
    }

    private fun clearProgress(messageId: String) {
        _downloadProgress.value = _downloadProgress.value - messageId
    }
}

/** Preferencias locales de calidad de medios (por dispositivo, como en WhatsApp). */
object ChatMediaSettings {
    private const val PREFS = "serafiel_media_settings"
    private const val KEY_SEND_HD = "send_hd"
    private const val KEY_AUTO_DOWNLOAD_HD = "auto_download_hd"

    private val _sendHd = MutableStateFlow<Boolean?>(null)
    private val _autoDownloadHd = MutableStateFlow<Boolean?>(null)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSendHd(context: Context): Boolean =
        _sendHd.value ?: prefs(context).getBoolean(KEY_SEND_HD, false).also { _sendHd.value = it }

    fun setSendHd(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SEND_HD, enabled).apply()
        _sendHd.value = enabled
    }

    fun isAutoDownloadHd(context: Context): Boolean =
        _autoDownloadHd.value ?: prefs(context).getBoolean(KEY_AUTO_DOWNLOAD_HD, true).also { _autoDownloadHd.value = it }

    fun setAutoDownloadHd(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_DOWNLOAD_HD, enabled).apply()
        _autoDownloadHd.value = enabled
    }
}
