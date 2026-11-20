package com.example.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

/** Apariencia local (por dispositivo): modo claro/oscuro, color de acento y fondo de los chats. */
object ThemeSettings {
    enum class Mode { SYSTEM, LIGHT, DARK }

    enum class Accent(
        val lightPrimary: Long, val lightContainer: Long, val lightOnContainer: Long,
        val darkPrimary: Long, val darkContainer: Long, val darkOnContainer: Long,
    ) {
        AMBER(0, 0, 0, 0, 0, 0),
        OCEAN(0xFF1F6FA5, 0xFFD3E8F7, 0xFF0B2D45, 0xFF7DB8E8, 0xFF174768, 0xFFD3E8F7),
        FOREST(0xFF2E7D4F, 0xFFD2EDDB, 0xFF0D3320, 0xFF7FCB9B, 0xFF1C4D31, 0xFFD2EDDB),
        ROSE(0xFFC2416B, 0xFFFAD6E0, 0xFF4A0F23, 0xFFF29BB5, 0xFF6E2039, 0xFFFAD6E0),
        VIOLET(0xFF6B4FBB, 0xFFE5DCFA, 0xFF241454, 0xFFBBA6F2, 0xFF3F2C7A, 0xFFE5DCFA),
    }

    enum class Background(val colors: Pair<Long, Long>) {
        NONE(0L to 0L),
        SUNSET(0xFFFFB347 to 0xFFE8575C),
        OCEAN(0xFF56CCF2 to 0xFF2F80ED),
        FOREST(0xFF7BD389 to 0xFF1E8E6E),
        VIOLET(0xFFB993F0 to 0xFF7A4FD1),
        NIGHT(0xFF2C3E63 to 0xFF0F1526),
        CUSTOM(0L to 0L),
        PHOTO(0L to 0L),
    }

    private const val PREFS = "serafiel_theme"
    private const val PHOTO_FILE = "chat_background.jpg"

    private val _mode = MutableStateFlow(Mode.SYSTEM)
    val mode: StateFlow<Mode> = _mode.asStateFlow()
    private val _accent = MutableStateFlow(Accent.AMBER)
    val accent: StateFlow<Accent> = _accent.asStateFlow()
    private val _background = MutableStateFlow(Background.NONE)
    val background: StateFlow<Background> = _background.asStateFlow()
    private val _customColors = MutableStateFlow(0xFFFFB347 to 0xFF7A4FD1)
    /** Par de colores (ARGB) del degradado personalizado. */
    val customColors: StateFlow<Pair<Long, Long>> = _customColors.asStateFlow()
    private val _photoVersion = MutableStateFlow(0)
    val photoVersion: StateFlow<Int> = _photoVersion.asStateFlow()

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(context: Context) {
        val p = prefs(context)
        _mode.value = runCatching { Mode.valueOf(p.getString("mode", null) ?: "SYSTEM") }.getOrDefault(Mode.SYSTEM)
        _accent.value = runCatching { Accent.valueOf(p.getString("accent", null) ?: "AMBER") }.getOrDefault(Accent.AMBER)
        var bg = runCatching { Background.valueOf(p.getString("background", null) ?: "NONE") }.getOrDefault(Background.NONE)
        if (bg == Background.PHOTO && !File(context.filesDir, PHOTO_FILE).exists()) bg = Background.NONE
        _background.value = bg
        _customColors.value = p.getLong("custom_a", 0xFFFFB347) to p.getLong("custom_b", 0xFF7A4FD1)
    }

    fun setCustomGradient(context: Context, a: Long, b: Long) {
        prefs(context).edit().putLong("custom_a", a).putLong("custom_b", b).apply()
        _customColors.value = a to b
        setBackground(context, Background.CUSTOM)
    }

    fun setMode(context: Context, mode: Mode) {
        prefs(context).edit().putString("mode", mode.name).apply(); _mode.value = mode
    }

    fun setAccent(context: Context, accent: Accent) {
        prefs(context).edit().putString("accent", accent.name).apply(); _accent.value = accent
    }

    fun setBackground(context: Context, bg: Background) {
        prefs(context).edit().putString("background", bg.name).apply(); _background.value = bg
    }

    /** Guarda una foto elegida como fondo (reducida a máx. 1280 px). */
    fun setPhoto(context: Context, uri: android.net.Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("decode")
        FileOutputStream(File(context.filesDir, PHOTO_FILE)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        _photoVersion.value += 1
        setBackground(context, Background.PHOTO)
        true
    }.getOrDefault(false)

    fun loadPhoto(context: Context): ImageBitmap? = runCatching {
        BitmapFactory.decodeFile(File(context.filesDir, PHOTO_FILE).absolutePath)?.asImageBitmap()
    }.getOrNull()

    fun color(v: Long) = Color(v)
}
