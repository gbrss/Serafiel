package com.example.ui.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.ChatAudioPlayer
import com.example.data.GroupMusic
import com.example.data.NowPlaying
import com.example.data.ServerClock
import com.example.i18n.tr
import com.example.model.SocialGroup
import kotlinx.coroutines.launch

/** Acciones de la música compartida de un grupo. */
class GroupMusicActions(
    val start: suspend (SocialGroup, String) -> Boolean = { _, _ -> false },
    val stopForAll: (SocialGroup) -> Unit = {},
    val playback: (SocialGroup, Boolean, Long, Int) -> Unit = { _, _, _, _ -> },
)

val LocalGroupMusic = compositionLocalOf { GroupMusicActions() }

/** Ventana para pegar un enlace de YouTube/SoundCloud (o quitar la música actual). */
@Composable
fun GroupMusicDialog(group: SocialGroup, hasMusic: Boolean, onDismiss: () -> Unit) {
    val actions = LocalGroupMusic.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.music_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr(R.string.music_dialog_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it.take(300); invalid = false },
                    singleLine = true,
                    isError = invalid,
                    placeholder = { Text("https://…") },
                    supportingText = if (invalid) ({ Text(tr(R.string.music_link_invalid)) }) else null,
                    modifier = Modifier.fillMaxWidth().testTag("group_music_link"),
                )
                if (hasMusic) {
                    TextButton(onClick = { actions.stopForAll(group); onDismiss() }) { Text(tr(R.string.music_remove_all)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = link.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val ok = actions.start(group, link)
                        busy = false
                        if (ok) onDismiss() else invalid = true
                    }
                },
            ) { Text(tr(R.string.music_play_all)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.appearance_close)) } },
    )
}

private class PlayerBridge(
    private val onReadyCb: () -> Unit,
    private val onTrackCb: (Int) -> Unit,
    private val onErrorCb: () -> Unit,
    private val onPlayingCb: () -> Unit,
    private val onSeekCb: (Long) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface fun onReady() { main.post { onReadyCb() } }
    @JavascriptInterface fun onTrack(index: Int) { main.post { onTrackCb(index) } }
    @JavascriptInterface fun onState(state: Int) { if (state == 1) main.post { onPlayingCb() } }
    @JavascriptInterface fun onSeek(posMs: Long) { main.post { onSeekCb(posMs) } }
    @JavascriptInterface fun onError(code: String) { main.post { onErrorCb() } }
}

/** Grupo cuya música sigue sonando aunque salgas de su chat, y cuántas barras visibles hay. */
object MusicSession {
    var groupId by mutableStateOf("")
    var visibleBars by mutableIntStateOf(0)
}

/** Mantiene el reproductor vivo (oculto) al salir del chat del grupo; se detiene al quitar la música. */
@Composable
fun BackgroundGroupMusic(groups: List<SocialGroup>, modifier: Modifier = Modifier) {
    if (MusicSession.visibleBars > 0 || MusicSession.groupId.isEmpty()) return
    val group = groups.firstOrNull { it.id == MusicSession.groupId } ?: return
    val np = remember(group.nowPlaying) { NowPlaying.from(group.nowPlaying) } ?: return
    Box(modifier.width(120.dp).height(1.dp)) {
        GroupMusicBar(group = group, np = np, recording = false, background = true)
    }
}

/**
 * Barra de música compartida: todos oyen lo mismo a la vez (sincronía aproximada).
 * Silenciar, detener y el volumen son locales; pausar/reanudar valen para todo el grupo.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GroupMusicBar(group: SocialGroup, np: NowPlaying, recording: Boolean, background: Boolean = false) {
    val context = LocalContext.current
    val actions = LocalGroupMusic.current
    if (!background) {
        DisposableEffect(group.id) {
            MusicSession.groupId = group.id
            MusicSession.visibleBars++
            onDispose { MusicSession.visibleBars-- }
        }
    }
    // Al iniciar una pista nueva, cada teléfono tarda en cargar el reproductor: en vez de
    // empezar a los 2 s, el reloj de esta pista arranca cuando el reproductor está listo.
    val startSkew = remember(np.atMs, np.url) { longArrayOf(-1L) }
    fun target(): Long {
        val t = np.targetPositionMs()
        if (startSkew[0] < 0L) startSkew[0] = if (np.playing && np.posMs == 0L && t in 0L..10_000L) t else 0L
        return (t - startSkew[0]).coerceAtLeast(0L)
    }
    val npState = rememberUpdatedState(np)
    val groupState = rememberUpdatedState(group)

    var ready by remember(np.url) { mutableStateOf(false) }
    var error by remember(np.url) { mutableStateOf(false) }
    var localStopped by remember(np.url) { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var expanded by remember(np.provider) { mutableStateOf(true) }
    var resyncTick by remember { mutableIntStateOf(0) }
    var checkTick by remember(np.url) { mutableIntStateOf(0) }
    var ignoreTrackUntil by remember(np.url) { mutableStateOf(0L) }

    val voicePlaying = ChatAudioPlayer.playbackState.collectAsState().value.isPlaying
    val targetVolume = when {
        muted -> 0
        recording -> 5
        voicePlaying -> 30
        else -> 100
    }
    val volume by animateIntAsState(targetVolume, tween(350), label = "music_volume")

    val webView = remember(np.provider, np.url, np.videoId, np.listId) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(android.graphics.Color.BLACK)
            webViewClient = WebViewClient()
            webChromeClient = android.webkit.WebChromeClient()
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            addJavascriptInterface(
                PlayerBridge(
                    onReadyCb = { ready = true },
                    onTrackCb = { idx ->
                        val cur = npState.value
                        val tracksMatter = cur.listId.isNotEmpty() || !cur.isYoutube
                        if (tracksMatter && idx >= 0 && idx != cur.index && cur.playing &&
                            System.currentTimeMillis() > ignoreTrackUntil
                        ) {
                            actions.playback(groupState.value, true, 0L, idx)
                        }
                    },
                    onErrorCb = { error = true },
                    onPlayingCb = { checkTick++ },
                    onSeekCb = { pos ->
                        // Alguien movió la barra: pasa a ser la posición de todo el grupo.
                        val cur = npState.value
                        actions.playback(groupState.value, cur.playing, pos, cur.index)
                    },
                ),
                "Bridge",
            )
            loadDataWithBaseURL("https://serafiel.app/", GroupMusic.playerHtml(np), "text/html", "utf-8", null)
        }
    }
    DisposableEffect(webView) { onDispose { webView.destroy() } }

    // Sincroniza con el estado compartido (al entrar, al pausar/reanudar, al cambiar de pista).
    LaunchedEffect(Unit) { ServerClock.calibrate(); resyncTick++ }
    LaunchedEffect(ready, np.playing, np.posMs, np.atMs, np.index, localStopped, resyncTick) {
        if (!ready || localStopped) return@LaunchedEffect
        ignoreTrackUntil = System.currentTimeMillis() + 4000
        val idx = if (np.listId.isNotEmpty() || !np.isYoutube) np.index else -1
        webView.evaluateJavascript("sync(${np.playing},${target()},$idx)", null)
    }
    // Afina el reloj del servidor con cada actualización en vivo (no con la primera lectura).
    var firstAt by remember(np.url) { mutableStateOf(np.atMs) }
    LaunchedEffect(np.atMs, np.pending) {
        if (!np.pending && np.atMs != firstAt) ServerClock.observeLive(np.atMs)
    }
    // Corrección continua del desfase: al empezar a sonar y cada pocos segundos.
    LaunchedEffect(ready, np.playing, np.atMs, np.index, localStopped, checkTick) {
        if (!ready || localStopped || !np.playing) return@LaunchedEffect
        kotlinx.coroutines.delay(6000)
        while (true) {
            webView.evaluateJavascript("check(${target()})", null)
            kotlinx.coroutines.delay(8000)
        }
    }
    LaunchedEffect(ready, volume) {
        if (ready) webView.evaluateJavascript("vol($volume)", null)
    }

    if (background) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        return
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().testTag("group_music_bar")) {
        Column {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(np.title.ifBlank { np.url }, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        tr(R.string.music_by, np.byName.ifBlank { "…" }, if (np.isYoutube) "YouTube" else "SoundCloud"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { actions.playback(group, !np.playing, target(), np.index) },
                    modifier = Modifier.size(36.dp),
                ) { Icon(if (np.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tr(R.string.music_cd_playpause)) }
                IconButton(onClick = { muted = !muted }, modifier = Modifier.size(36.dp)) {
                    Icon(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, tr(R.string.music_cd_mute))
                }
                IconButton(onClick = { startSkew[0] = 0L; resyncTick++ }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Refresh, tr(R.string.music_cd_resync))
                }
                IconButton(
                    onClick = {
                        localStopped = true
                        webView.evaluateJavascript("stopAll()", null)
                    },
                    modifier = Modifier.size(36.dp),
                ) { Icon(Icons.Rounded.Stop, tr(R.string.music_cd_stop_local)) }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, tr(R.string.music_cd_expand))
                }
            }
            if (localStopped) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr(R.string.music_stopped_local), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { localStopped = false; resyncTick++ }) { Text(tr(R.string.music_resume)) }
                }
            }
            if (error) {
                Text(
                    tr(R.string.music_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            // El reproductor siempre está montado (para que siga sonando); plegado queda mínimo.
            val showPlayer = !localStopped && expanded
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!showPlayer) Modifier.height(1.dp)
                        else if (np.isYoutube) Modifier.aspectRatio(16f / 9f)
                        else Modifier.height(166.dp)
                    ),
            ) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
