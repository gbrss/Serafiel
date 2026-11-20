package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.i18n.tr
import com.example.ui.theme.ThemeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fondo de los chats según la apariencia elegida (color, degradado o foto propia). */
@Composable
fun Modifier.chatBackground(): Modifier {
    val bg by ThemeSettings.background.collectAsState()
    val version by ThemeSettings.photoVersion.collectAsState()
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val photo = produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, bg, version) {
        value = if (bg == ThemeSettings.Background.PHOTO) withContext(Dispatchers.IO) { ThemeSettings.loadPhoto(context) } else null
    }
    val custom by ThemeSettings.customColors.collectAsState()
    return when (bg) {
        ThemeSettings.Background.NONE -> this
        ThemeSettings.Background.PHOTO -> this.clipToBounds().drawBehind {
            photo.value?.let { img ->
                val scale = maxOf(size.width / img.width, size.height / img.height)
                val dw = (img.width * scale).toInt()
                val dh = (img.height * scale).toInt()
                drawImage(
                    image = img,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(img.width, img.height),
                    dstOffset = IntOffset(((size.width - dw) / 2).toInt(), ((size.height - dh) / 2).toInt()),
                    dstSize = IntSize(dw, dh),
                )
                drawRect(Color.Black.copy(alpha = if (dark) 0.45f else 0.12f), size = Size(size.width, size.height))
            }
        }
        else -> {
            val (a, b) = if (bg == ThemeSettings.Background.CUSTOM) custom else bg.colors
            this.clipToBounds().drawBehind {
                drawRect(
                    Brush.linearGradient(
                        listOf(Color(a), Color(b)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                if (dark) drawRect(Color.Black.copy(alpha = 0.3f), size = Size(size.width, size.height))
            }
        }
    }
}

@Composable
fun AppearanceCard() {
    val context = LocalContext.current
    val mode by ThemeSettings.mode.collectAsState()
    val accent by ThemeSettings.accent.collectAsState()
    val bg by ThemeSettings.background.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !ThemeSettings.setPhoto(context, uri)) {
            android.widget.Toast.makeText(context, tr(R.string.appearance_photo_error), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr(R.string.appearance_title), style = MaterialTheme.typography.titleSmall)
            Text(
                tr(R.string.appearance_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(
                    ThemeSettings.Mode.SYSTEM to R.string.appearance_mode_system,
                    ThemeSettings.Mode.LIGHT to R.string.appearance_mode_light,
                    ThemeSettings.Mode.DARK to R.string.appearance_mode_dark,
                ).forEach { (m, label) ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { ThemeSettings.setMode(context, m) },
                        label = { Text(tr(label)) },
                        modifier = Modifier.testTag("theme_mode_${m.name}"),
                    )
                }
            }

            Text(tr(R.string.appearance_accent), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ThemeSettings.Accent.entries.forEach { a ->
                    val color = if (a == ThemeSettings.Accent.AMBER) Color(0xFFE77E3A) else Color(a.lightPrimary)
                    Swatch(
                        brush = Brush.linearGradient(listOf(color, color)),
                        selected = accent == a,
                        tag = "accent_${a.name}",
                    ) { ThemeSettings.setAccent(context, a) }
                }
            }

            Text(tr(R.string.appearance_background), style = MaterialTheme.typography.labelLarge)
            val custom by ThemeSettings.customColors.collectAsState()
            var showCustom by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                // 5 degradados predefinidos (tocar el elegido otra vez lo quita)
                ThemeSettings.Background.entries
                    .filter { it != ThemeSettings.Background.NONE && it != ThemeSettings.Background.CUSTOM && it != ThemeSettings.Background.PHOTO }
                    .forEach { b ->
                        Swatch(
                            brush = Brush.linearGradient(listOf(Color(b.colors.first), Color(b.colors.second))),
                            selected = bg == b,
                            tag = "chat_bg_${b.name}",
                        ) {
                            ThemeSettings.setBackground(context, if (bg == b) ThemeSettings.Background.NONE else b)
                        }
                    }
                // 6º: degradado propio con selector RGB
                Swatch(
                    brush = Brush.linearGradient(listOf(Color(custom.first), Color(custom.second))),
                    selected = bg == ThemeSettings.Background.CUSTOM,
                    tag = "chat_bg_custom",
                    label = "🎨",
                ) { showCustom = true }
                // 7º: foto propia
                Swatch(
                    brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)),
                    selected = bg == ThemeSettings.Background.PHOTO,
                    tag = "chat_bg_photo",
                    label = "📷",
                ) { picker.launch("image/*") }
            }
            if (showCustom) {
                CustomGradientDialog(
                    initialA = custom.first,
                    initialB = custom.second,
                    onDismiss = { showCustom = false },
                    onApply = { a, b -> showCustom = false; ThemeSettings.setCustomGradient(context, a, b) },
                )
            }
        }
    }
}

@Composable
private fun Swatch(brush: Brush, selected: Boolean, tag: String, label: String? = null, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(brush)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .testTag(tag),
    ) {
        if (label != null) Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CustomGradientDialog(
    initialA: Long,
    initialB: Long,
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit,
) {
    var a by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Color(initialA)) }
    var b by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Color(initialB)) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.appearance_custom_bg)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(width = 0.dp, height = 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(a, b))),
                )
                Text(tr(R.string.appearance_color_1), style = MaterialTheme.typography.labelLarge)
                RgbSliders(a) { a = it }
                Text(tr(R.string.appearance_color_2), style = MaterialTheme.typography.labelLarge)
                RgbSliders(b) { b = it }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onApply(a.toArgb().toLong() and 0xFFFFFFFFL, b.toArgb().toLong() and 0xFFFFFFFFL)
            }) { Text(tr(R.string.appearance_apply)) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(tr(R.string.s_cancelar)) } },
    )
}

@Composable
private fun RgbSliders(color: Color, onChange: (Color) -> Unit) {
    listOf(
        Triple("R", color.red, Color(0xFFE53935)),
        Triple("G", color.green, Color(0xFF43A047)),
        Triple("B", color.blue, Color(0xFF1E88E5)),
    ).forEachIndexed { i, (name, value, tint) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, modifier = Modifier.size(width = 18.dp, height = 20.dp), style = MaterialTheme.typography.labelMedium)
            androidx.compose.material3.Slider(
                value = value,
                onValueChange = { v ->
                    onChange(
                        when (i) {
                            0 -> Color(v, color.green, color.blue)
                            1 -> Color(color.red, v, color.blue)
                            else -> Color(color.red, color.green, v)
                        },
                    )
                },
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
