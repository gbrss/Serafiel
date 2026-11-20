package com.example.ui.screens

import com.example.R
import com.example.i18n.tr

import android.graphics.BitmapFactory
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ChatMediaHelper
import com.example.data.ChatMediaSettings
import com.example.data.ChunkedMediaStore
import com.example.model.DirectMessage
import com.example.model.MessageDeliveryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max

val QuickReactionEmojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

private val SeenBlue = Color(0xFF34B7F1)

/** Fila de reacciones rápidas (estilo WhatsApp) para el menú de un mensaje. */
@Composable
fun QuickReactionsRow(
    myReaction: String?,
    onReact: (String?) -> Unit,
    onOpenFullPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.testTag("quick_reactions_row"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            QuickReactionEmojis.forEach { emoji ->
                val selected = emoji == myReaction
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { onReact(if (selected) null else emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = emoji, fontSize = 22.sp)
                }
            }
            IconButton(onClick = onOpenFullPicker, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Outlined.AddReaction,
                    contentDescription = tr(R.string.s_mas_reacciones),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Chips de reacciones agrupadas bajo una burbuja. */
@Composable
fun ReactionChips(
    reactions: Map<String, String>,
    myUserId: String,
    onToggleMine: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (reactions.isEmpty()) return
    val grouped = remember(reactions) {
        reactions.entries.groupBy({ it.value }, { it.key })
            .entries.sortedByDescending { it.value.size }
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        grouped.forEach { (emoji, users) ->
            val mine = myUserId in users
            Surface(
                shape = RoundedCornerShape(50),
                color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(50),
                    )
                    .clip(RoundedCornerShape(50))
                    .clickable { onToggleMine(emoji) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(text = emoji, fontSize = 14.sp)
                    if (users.size > 1) {
                        Text(
                            text = users.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** ✓ enviado · ✓✓ entregado · ✓✓ azul visto. */
@Composable
fun DeliveryTicks(status: MessageDeliveryStatus, modifier: Modifier = Modifier) {
    val (icon, tint, label) = when (status) {
        MessageDeliveryStatus.SENT -> Triple(Icons.Rounded.Done, MaterialTheme.colorScheme.onSurfaceVariant, tr(R.string.s_enviado))
        MessageDeliveryStatus.DELIVERED -> Triple(Icons.Rounded.DoneAll, MaterialTheme.colorScheme.onSurfaceVariant, tr(R.string.s_entregado))
        MessageDeliveryStatus.SEEN -> Triple(Icons.Rounded.DoneAll, SeenBlue, tr(R.string.s_visto))
    }
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint,
        modifier = modifier.size(16.dp),
    )
}

/** "escribiendo..." con puntos animados. */
@Composable
fun TypingIndicatorText(color: Color, modifier: Modifier = Modifier, prefix: String = "") {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "typingDots",
    )
    val dots = ".".repeat(phase.toInt().coerceIn(0, 2) + 1)
    Text(
        text = prefix + tr(R.string.typing_word) + dots,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

/** Miniatura de video con botón de reproducción y duración. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoMessageContent(
    message: DirectMessage,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val thumb = remember(message.id, message.mediaData) {
        ChatMediaHelper.decodeBase64Bitmap(message.mediaData)?.asImageBitmap()
    }
    val progressMap by ChunkedMediaStore.downloadProgress.collectAsState()
    val progress = progressMap[message.id]
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp, max = 240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .testTag("video_message_${message.id}"),
        contentAlignment = Alignment.Center,
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = tr(R.string.s_video_3),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().heightIn(min = 140.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            if (progress != null) {
                CircularProgressIndicator(progress = { progress }, color = Color.White, modifier = Modifier.size(40.dp))
            } else {
                Icon(Icons.Rounded.PlayArrow, contentDescription = tr(R.string.s_reproducir_video), tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
        Text(
            text = DirectMessage.formatDuration(message.durationSec),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Reproductor de video a pantalla completa (descarga los trozos si hace falta). */
@Composable
fun VideoPlayerDialog(message: DirectMessage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var path by remember(message.id) { mutableStateOf<String?>(null) }
    var failed by remember(message.id) { mutableStateOf(false) }
    val progressMap by ChunkedMediaStore.downloadProgress.collectAsState()

    LaunchedEffect(message.id) {
        val file = ChunkedMediaStore.resolveFullMedia(context.applicationContext, message)
        if (file == null) failed = true else path = file.absolutePath
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val videoPath = path
            when {
                videoPath != null -> AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            val video = VideoView(ctx)
                            addView(
                                video,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    android.view.Gravity.CENTER,
                                ),
                            )
                            val controller = MediaController(ctx)
                            controller.setAnchorView(video)
                            video.setMediaController(controller)
                            video.setVideoPath(videoPath)
                            video.setOnPreparedListener { video.start() }
                        }
                    },
                    onRelease = { frame ->
                        (frame.getChildAt(0) as? VideoView)?.stopPlayback()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                failed -> Text(tr(R.string.s_no_se_pudo_cargar_el), color = Color.White)
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val p = progressMap[message.id]
                    if (p != null) {
                        CircularProgressIndicator(progress = { p }, color = Color.White)
                        Text(tr(R.string.s_descargando, (p * 100).toInt()), color = Color.White, modifier = Modifier.padding(top = 12.dp))
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = tr(R.string.s_cerrar_video), tint = Color.White)
            }
        }
    }
}

/** Pequeña etiqueta "HD" sobre fotos que tienen versión en alta calidad. */
@Composable
fun HdBadge(downloaded: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = "HD",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .alpha(if (downloaded) 1f else 0.85f)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/**
 * Carga la versión HD de una foto. Si [force] es falso solo lo hace cuando el usuario
 * tiene activada la descarga automática en HD (o ya está en caché).
 */
@Composable
fun rememberHdImage(message: DirectMessage?, force: Boolean, maxSidePx: Int = 2560): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(message?.id, force) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(message?.id, force) {
        val msg = message ?: return@LaunchedEffect
        if (msg.messageType != DirectMessage.TYPE_IMAGE || msg.chunkCount <= 0L) return@LaunchedEffect
        val appCtx = context.applicationContext
        val allowed = force || ChatMediaSettings.isAutoDownloadHd(appCtx) ||
            ChunkedMediaStore.cachedFileOrNull(appCtx, msg) != null
        if (!allowed) return@LaunchedEffect
        val file = ChunkedMediaStore.resolveFullMedia(appCtx, msg) ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSidePx) sample *= 2
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

/** Re-evalúa cada segundo una condición basada en la hora (p. ej. "escribiendo"). */
@Composable
fun rememberTicker(periodMs: Long = 1_000L): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Franja sobre el compositor mientras se comprime/sube un video. */
@Composable
fun MediaUploadBanner(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * Espectro de una nota de voz. Tocar o arrastrar en cualquier punto mueve la
 * reproducción a esa posición.
 */
@Composable
fun VoiceWaveformBar(
    levels: List<Float>,
    progress: Float,
    playedColor: Color,
    pendingColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = dragFraction ?: progress
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(levels) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(levels) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragFraction?.let(onSeek)
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                )
            }
            .testTag("voice_waveform"),
    ) {
        if (levels.isEmpty()) return@Canvas
        val count = levels.size
        val slot = size.width / count
        val barWidth = (slot * 0.55f).coerceAtLeast(2f)
        val minBar = size.height * 0.12f
        levels.forEachIndexed { i, level ->
            val h = minBar + (size.height - minBar) * level.coerceIn(0f, 1f)
            val x = i * slot + (slot - barWidth) / 2f
            val played = (i + 0.5f) / count <= shown
            drawRoundRect(
                color = if (played) playedColor else pendingColor,
                topLeft = androidx.compose.ui.geometry.Offset(x, (size.height - h) / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
        // Punto de posición actual.
        drawCircle(
            color = playedColor,
            radius = size.height * 0.16f,
            center = androidx.compose.ui.geometry.Offset(
                (shown * size.width).coerceIn(size.height * 0.16f, size.width - size.height * 0.16f),
                size.height / 2f,
            ),
        )
    }
}

/** Botón 1x / 1.5x / 2x de velocidad de reproducción. */
@Composable
fun PlaybackSpeedChip(speed: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = when (speed) {
        1f -> "1x"
        2f -> "2x"
        else -> "${speed}x"
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        modifier = modifier.testTag("voice_speed_chip"),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
