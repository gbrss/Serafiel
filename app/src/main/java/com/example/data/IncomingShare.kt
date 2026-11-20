package com.example.data

import com.example.R
import com.example.i18n.tr

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Contenido recibido desde otra app mediante "Compartir con Serafiel". */
data class SharedContent(val text: String?, val uris: List<Uri>) {
    val isEmpty get() = text.isNullOrBlank() && uris.isEmpty()

    fun describe(context: Context): String {
        val parts = mutableListOf<String>()
        text?.takeIf { it.isNotBlank() }?.let { parts += "\"" + it.trim().take(80) + "\"" }
        if (uris.isNotEmpty()) {
            val images = uris.count { mimeOf(context, it).startsWith("image/") }
            val videos = uris.count { mimeOf(context, it).startsWith("video/") }
            val audios = uris.count { mimeOf(context, it).startsWith("audio/") }
            if (images > 0) parts += if (images == 1) tr(R.string.s_1_imagen) else tr(R.string.s_imagenes, images)
            if (videos > 0) parts += if (videos == 1) tr(R.string.s_1_video) else "$videos videos"
            if (audios > 0) parts += if (audios == 1) tr(R.string.s_1_audio) else "$audios audios"
        }
        return parts.joinToString(" · ").ifBlank { tr(R.string.s_contenido_compartido) }
    }

    companion object {
        const val MAX_ITEMS = 10

        fun mimeOf(context: Context, uri: Uri): String =
            runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()

        /** Extrae el contenido de un intent SEND / SEND_MULTIPLE; null si no es un intent de compartir. */
        fun fromIntent(intent: Intent?): SharedContent? {
            intent ?: return null
            val uris = when (intent.action) {
                Intent.ACTION_SEND ->
                    listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
                Intent.ACTION_SEND_MULTIPLE ->
                    IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                else -> return null
            }.filter { it.scheme == ContentResolver.SCHEME_CONTENT }.take(MAX_ITEMS)
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.take(4000)
            return SharedContent(text, uris).takeUnless { it.isEmpty }
        }
    }
}

/** Puente entre la Activity (que recibe el intent) y la UI de Compose. */
object IncomingShare {
    val pending = MutableStateFlow<SharedContent?>(null)
}
