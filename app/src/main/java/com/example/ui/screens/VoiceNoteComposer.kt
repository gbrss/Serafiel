package com.example.ui.screens

import com.example.R
import com.example.i18n.tr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import com.example.data.EncodedMediaPayload
import com.example.data.VoiceNoteRecorder
import com.example.data.VoiceWaveformCodec
import com.example.model.DirectMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class VoiceRecState { IDLE, HOLDING, LOCKED }

/**
 * Estado de una nota de voz estilo WhatsApp:
 * - Mantener pulsado el micrófono graba; al soltar se envía.
 * - Deslizar hacia arriba bloquea la grabación (sigue sola hasta pulsar enviar).
 * - Deslizar hacia la izquierda la cancela.
 * Máximo [VoiceNoteRecorder.MAX_VOICE_NOTE_SEC] segundos; al llegar se envía sola.
 */
@Stable
class VoiceNoteController internal constructor(
    private val recorder: VoiceNoteRecorder,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf(VoiceRecState.IDLE)
        internal set
    var elapsedMs by mutableLongStateOf(0L)
        internal set
    var level by mutableFloatStateOf(0f)
        internal set
    var dragX by mutableFloatStateOf(0f)
        internal set
    var dragY by mutableFloatStateOf(0f)
        internal set

    internal var onSend: (EncodedMediaPayload) -> Unit = {}
    internal var onShowMessage: (String) -> Unit = {}
    private var startedAt = 0L
    private val levelSamples = mutableListOf<Float>()

    val isActive: Boolean get() = state != VoiceRecState.IDLE

    internal fun start(): Boolean {
        if (isActive) return true
        val res = recorder.startRecording()
        if (res.isFailure) {
            onShowMessage(tr(R.string.s_no_se_pudo_iniciar_la))
            return false
        }
        startedAt = System.currentTimeMillis()
        levelSamples.clear()
        elapsedMs = 0L
        dragX = 0f
        dragY = 0f
        state = VoiceRecState.HOLDING
        return true
    }

    internal fun lock() {
        if (state == VoiceRecState.HOLDING) {
            state = VoiceRecState.LOCKED
            dragX = 0f
            dragY = 0f
        }
    }

    fun cancel() {
        if (!isActive) return
        recorder.cancelRecording()
        reset()
    }

    fun finishAndSend() {
        if (!isActive) return
        val tooShort = System.currentTimeMillis() - startedAt < MIN_DURATION_MS
        val waveform = VoiceWaveformCodec.encode(levelSamples.toList())
        reset()
        if (tooShort) {
            recorder.cancelRecording()
            onShowMessage(tr(R.string.s_manten_pulsado_para_grabar_suelta))
            return
        }
        scope.launch {
            recorder.finishAndEncode()
                .onSuccess { onSend(it.copy(waveform = waveform)) }
                .onFailure { onShowMessage(it.localizedMessage ?: tr(R.string.s_no_se_pudo_guardar_la)) }
        }
    }

    internal fun tick() {
        elapsedMs = System.currentTimeMillis() - startedAt
        level = recorder.currentLevel()
        levelSamples += level
        if (elapsedMs >= VoiceNoteRecorder.MAX_VOICE_NOTE_SEC * 1000L) {
            onShowMessage(tr(R.string.s_llegaste_al_maximo_de_5))
            finishAndSend()
        }
    }

    private fun reset() {
        state = VoiceRecState.IDLE
        dragX = 0f
        dragY = 0f
        level = 0f
    }

    companion object {
        const val MIN_DURATION_MS = 700L
    }
}

@Composable
fun rememberVoiceNoteController(
    key: Any?,
    onSend: (EncodedMediaPayload) -> Unit,
    onShowMessage: (String) -> Unit,
): VoiceNoteController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(key) { VoiceNoteController(VoiceNoteRecorder(context.applicationContext), scope) }
    val latestSend by rememberUpdatedState(onSend)
    val latestMessage by rememberUpdatedState(onShowMessage)
    controller.onSend = { latestSend(it) }
    controller.onShowMessage = { latestMessage(it) }

    LaunchedEffect(controller, controller.isActive) {
        while (controller.isActive) {
            controller.tick()
            delay(150L)
        }
    }
    DisposableEffect(controller) {
        onDispose { controller.cancel() }
    }
    return controller
}

/**
 * Botón de micrófono con los gestos de WhatsApp. Debe mantenerse en el mismo sitio
 * del árbol mientras se graba (estado HOLDING) para no cortar el gesto.
 */
@Composable
fun VoiceMicButton(
    controller: VoiceNoteController,
    modifier: Modifier = Modifier,
    testTag: String = "record_voice_button",
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val lockThresholdPx = with(density) { 90.dp.toPx() }
    val cancelThresholdPx = with(density) { 120.dp.toPx() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            controller.onShowMessage(tr(R.string.s_activa_el_permiso_de_microfono))
        } else {
            controller.onShowMessage(tr(R.string.s_listo_manten_pulsado_el_microfono))
        }
    }
    val hasMic = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val latestHasMic by rememberUpdatedState(hasMic)

    val holding = controller.state == VoiceRecState.HOLDING
    val scale by animateFloatAsState(if (holding) 1.45f else 1f, label = "micScale")

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (holding) {
            // Indicador de "desliza para bloquear" flotando sobre el micrófono.
            val lift = (-controller.dragY / lockThresholdPx).coerceIn(0f, 1f)
            Popup(
                alignment = Alignment.BottomCenter,
                offset = IntOffset(0, with(density) { (-72).dp.roundToPx() }),
                properties = PopupProperties(focusable = false),
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .width(44.dp)
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = tr(R.string.s_desliza_hacia_arriba_para_bloquear),
                            tint = if (lift > 0.9f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Icon(
                            Icons.Rounded.KeyboardArrowUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .offset(y = (-6 * lift).dp),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        if (holding) controller.dragX.roundToInt() else 0,
                        if (holding) controller.dragY.roundToInt() else 0,
                    )
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (holding) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                )
                .testTag(testTag)
                .pointerInput(controller) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        if (!latestHasMic()) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@awaitEachGesture
                        }
                        if (!controller.start()) return@awaitEachGesture
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        var total = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (change == null || !change.pressed) {
                                if (controller.state == VoiceRecState.HOLDING) controller.finishAndSend()
                                break
                            }
                            total += change.positionChange()
                            change.consume()
                            controller.dragX = total.x.coerceIn(-cancelThresholdPx, 0f)
                            controller.dragY = total.y.coerceIn(-lockThresholdPx, 0f)
                            if (total.y <= -lockThresholdPx) {
                                controller.lock()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                break
                            }
                            if (total.x <= -cancelThresholdPx) {
                                controller.cancel()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                break
                            }
                        }
                        // Consume lo que quede del gesto hasta soltar.
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = tr(R.string.s_manten_pulsado_para_grabar_un),
                tint = if (holding) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Contenido que reemplaza al campo de texto mientras se mantiene pulsado el micrófono. */
@Composable
fun RowScope.VoiceHoldingContent(controller: VoiceNoteController) {
    val cancelFade = (1f + controller.dragX / 300f).coerceIn(0.2f, 1f)
    Row(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RecordingDot(controller.level)
        Text(
            text = DirectMessage.formatDuration(controller.elapsedMs / 1000L),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .alpha(cancelFade)
                .offset { IntOffset((controller.dragX / 3f).roundToInt(), 0) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = tr(R.string.s_desliza_para_cancelar),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Barra con la grabación bloqueada: papelera, tiempo y botón de enviar. */
@Composable
fun VoiceLockedBar(controller: VoiceNoteController, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(
            onClick = { controller.cancel() },
            modifier = Modifier.testTag("cancel_voice_button"),
        ) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = tr(R.string.s_descartar_nota_de_voz),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        RecordingDot(controller.level)
        Text(
            text = "${DirectMessage.formatDuration(controller.elapsedMs / 1000L)} / ${DirectMessage.formatDuration(VoiceNoteRecorder.MAX_VOICE_NOTE_SEC)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { controller.finishAndSend() },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .size(48.dp)
                .testTag("send_voice_button"),
        ) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = tr(R.string.s_enviar_nota_de_voz))
        }
    }
}

@Composable
private fun RecordingDot(level: Float) {
    val pulse by animateFloatAsState(0.75f + level * 0.6f, label = "recPulse")
    Box(
        modifier = Modifier
            .size(12.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error),
    )
}
