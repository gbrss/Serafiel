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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
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
            val (a, b) = if (dark) bg.dark else bg.light
            this.background(Brush.verticalGradient(listOf(Color(a), Color(b))))
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ThemeSettings.Background.entries.filter { it != ThemeSettings.Background.PHOTO }.forEach { b ->
                    val brush = if (b == ThemeSettings.Background.NONE) {
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background))
                    } else {
                        Brush.verticalGradient(listOf(Color(b.light.first), Color(b.light.second)))
                    }
                    Swatch(brush = brush, selected = bg == b, tag = "chat_bg_${b.name}") {
                        ThemeSettings.setBackground(context, b)
                    }
                }
                FilterChip(
                    selected = bg == ThemeSettings.Background.PHOTO,
                    onClick = { picker.launch("image/*") },
                    label = { Text(tr(R.string.appearance_my_photo)) },
                    modifier = Modifier.testTag("chat_bg_photo"),
                )
            }
        }
    }
}

@Composable
private fun Swatch(brush: Brush, selected: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(brush)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .testTag(tag),
    )
}
