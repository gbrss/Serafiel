package com.example.data

import android.net.Uri
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Música compartida del grupo: lo que se guarda en `groups/{id}.nowPlaying`. */
data class NowPlaying(
    val provider: String,
    val url: String,
    val videoId: String,
    val listId: String,
    val title: String,
    val byId: String,
    val byName: String,
    val playing: Boolean,
    val posMs: Long,
    val atMs: Long,
    val index: Int,
    /** true mientras la hora del servidor aún no llegó (escritura local pendiente). */
    val pending: Boolean = false,
) {
    val isYoutube get() = provider == PROVIDER_YOUTUBE

    /** Posición actual según la hora de inicio guardada por el servidor. */
    fun targetPositionMs(nowMs: Long = ServerClock.now()): Long =
        if (playing) (posMs + (nowMs - atMs)).coerceAtLeast(0L) else posMs

    companion object {
        const val PROVIDER_YOUTUBE = "youtube"
        const val PROVIDER_SOUNDCLOUD = "soundcloud"

        fun from(map: Map<String, Any?>): NowPlaying? {
            if (map.isEmpty()) return null
            val provider = map["provider"] as? String ?: return null
            val url = map["url"] as? String ?: return null
            return NowPlaying(
                provider = provider,
                url = url,
                videoId = map["videoId"] as? String ?: "",
                listId = map["listId"] as? String ?: "",
                title = map["title"] as? String ?: "",
                byId = map["byId"] as? String ?: "",
                byName = map["byName"] as? String ?: "",
                playing = map["playing"] as? Boolean ?: false,
                posMs = (map["posMs"] as? Number)?.toLong() ?: 0L,
                atMs = (map["at"] as? Timestamp)?.toDate()?.time ?: ServerClock.now(),
                index = (map["index"] as? Number)?.toInt() ?: 0,
                pending = map["at"] !is Timestamp,
            )
        }
    }
}

/**
 * Estimación de la diferencia entre el reloj del teléfono y el del servidor, para no
 * depender de que la hora del teléfono esté bien. Se afina con cada actualización en vivo.
 */
object ServerClock {
    @Volatile var offsetMs: Long = 0L
        private set

    fun now(): Long = System.currentTimeMillis() + offsetMs

    @Volatile private var calibrated = false

    /**
     * Mide la diferencia con la hora real usando la cabecera Date de un servidor de Google
     * (±1 s). Sin esto, un teléfono con el reloj desfasado calcula mal la posición y se
     * "corrige" saltando una y otra vez al inicio.
     */
    suspend fun calibrate() {
        if (calibrated) return
        withContext(Dispatchers.IO) {
            runCatching {
                val t0 = System.currentTimeMillis()
                val conn = (URL("https://www.gstatic.com/generate_204").openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"; connectTimeout = 5000; readTimeout = 5000; useCaches = false
                }
                try {
                    conn.responseCode
                    val t1 = System.currentTimeMillis()
                    val date = conn.date
                    if (date > 0L) {
                        // Date trunca a segundos: +500 ms es la media del truncado.
                        val est = date + 500L - (t0 + t1) / 2
                        if (kotlin.math.abs(est) < 3_600_000L) { offsetMs = est; calibrated = true }
                    }
                } finally { conn.disconnect() }
            }
        }
    }

    /** [serverAtMs] llegó ahora mismo (≈150 ms de latencia de entrega). */
    fun observeLive(serverAtMs: Long) {
        if (calibrated) return // la medición por HTTP es más fiable que la latencia de entrega
        val estimate = serverAtMs + 150L - System.currentTimeMillis()
        // Ignora valores absurdos (> 10 min) y suaviza las variaciones de latencia.
        if (kotlin.math.abs(estimate) > 600_000L) return
        offsetMs = if (offsetMs == 0L) estimate else (offsetMs + estimate) / 2
    }
}

/** Enlace reconocido (YouTube o SoundCloud). */
data class MusicLink(val provider: String, val url: String, val videoId: String = "", val listId: String = "")

object GroupMusic {
    private val ID_RE = Regex("^[A-Za-z0-9_-]{6,64}$")

    /** Reconoce enlaces de YouTube/SoundCloud; null si no es ninguno. */
    fun parse(raw: String): MusicLink? {
        val text = raw.trim()
        val uri = runCatching { Uri.parse(if (text.startsWith("http")) text else "https://$text") }.getOrNull() ?: return null
        val host = (uri.host ?: return null).lowercase().removePrefix("www.").removePrefix("m.")
        return when {
            host == "youtu.be" || host.endsWith("youtube.com") || host == "music.youtube.com" -> {
                val list = uri.getQueryParameter("list")?.takeIf { ID_RE.matches(it) }.orEmpty()
                val segs = uri.pathSegments
                val video = when {
                    host == "youtu.be" -> segs.firstOrNull()
                    uri.getQueryParameter("v") != null -> uri.getQueryParameter("v")
                    segs.size >= 2 && segs[0] in listOf("shorts", "embed", "live") -> segs[1]
                    else -> null
                }?.takeIf { ID_RE.matches(it) }.orEmpty()
                if (video.isEmpty() && list.isEmpty()) return null
                val canonical = if (list.isNotEmpty() && video.isEmpty()) "https://www.youtube.com/playlist?list=$list"
                else "https://www.youtube.com/watch?v=$video" + if (list.isNotEmpty()) "&list=$list" else ""
                MusicLink(NowPlaying.PROVIDER_YOUTUBE, canonical, video, list)
            }
            host == "soundcloud.com" || host == "on.soundcloud.com" -> {
                if (uri.pathSegments.isEmpty()) return null
                MusicLink(NowPlaying.PROVIDER_SOUNDCLOUD, "https://" + host + uri.path.orEmpty().take(200))
            }
            else -> null
        }
    }

    /** Los enlaces cortos (on.soundcloud.com) no los entiende el reproductor: se resuelven al enlace real. */
    suspend fun resolve(link: MusicLink): MusicLink {
        if (link.provider != NowPlaying.PROVIDER_SOUNDCLOUD || !link.url.contains("://on.soundcloud.com")) return link
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(link.url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true; connectTimeout = 6000; readTimeout = 6000
                }
                try {
                    conn.inputStream.close()
                    parse(conn.url.toString())?.takeIf { it.provider == NowPlaying.PROVIDER_SOUNDCLOUD && !it.url.contains("://on.") } ?: link
                } finally { conn.disconnect() }
            }.getOrDefault(link)
        }
    }

    /** Título vía oEmbed (sin clave). Si falla, devuelve un título genérico. */
    suspend fun fetchTitle(link: MusicLink, fallback: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = if (link.provider == NowPlaying.PROVIDER_YOUTUBE) "https://www.youtube.com/oembed" else "https://soundcloud.com/oembed"
            val q = "$endpoint?format=json&url=" + Uri.encode(link.url)
            val conn = (URL(q).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000; readTimeout = 6000
            }
            try {
                JSONObject(conn.inputStream.bufferedReader().readText()).optString("title").trim().take(120).ifBlank { fallback }
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(fallback)
    }

    /** Página HTML con el reproductor oficial incrustado y un puente `Bridge`. */
    fun playerHtml(np: NowPlaying): String = if (np.isYoutube) youtubeHtml(np) else soundcloudHtml(np)

    private const val COMMON = """
        html,body{margin:0;height:100%;background:#000;overflow:hidden}
        #p{position:absolute;top:0;left:0;right:0;bottom:0;width:100%;height:100%;border:0}
    """

    private fun youtubeHtml(np: NowPlaying): String {
        val vars = buildString {
            append("playsinline:1,controls:1,disablekb:1,rel:0,modestbranding:1,fs:0,iv_load_policy:3")
            if (np.listId.isNotEmpty()) append(",listType:'playlist',list:'${np.listId}'")
        }
        val video = if (np.listId.isNotEmpty() && np.videoId.isEmpty()) "" else "videoId:'${np.videoId}',"
        return """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>$COMMON</style></head><body><div id="p"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
var want=false, pl=null, prog=0, lastPos=-1, lastT=0;
function mark(){prog=Date.now()+2500;}
setInterval(function(){
  try{
    if(!pl){return;}
    var st=pl.getPlayerState(), cur=pl.getCurrentTime()*1000, now=Date.now();
    if(st!==1){ if(lastPos>=0){lastT=now;} return; }  // en carga o pausa: congela la referencia
    if(lastPos>=0 && now>prog && Math.abs(cur-(lastPos+(now-lastT)))>2000){prog=now+2500;Bridge.onSeek(Math.round(cur));}
    lastPos=cur; lastT=now;
  }catch(x){}
},700);
function onYouTubeIframeAPIReady(){
  pl=new YT.Player('p',{width:'100%',height:'100%',${video}playerVars:{$vars},events:{
    onReady:function(){Bridge.onReady();},
    onStateChange:function(e){
      var idx=-1; try{idx=pl.getPlaylistIndex();}catch(x){}
      if(e.data===1){Bridge.onTrack(idx);}
      if(e.data===2 && want && document.hidden){setTimeout(function(){try{pl.playVideo();}catch(x){}},300);}
      Bridge.onState(e.data);
    },
    onError:function(e){Bridge.onError(String(e.data));}
  }});
}
function sync(playing,ms,idx){
  want=playing;
  try{
    var changed=false;
    if(idx>=0 && ${if (np.listId.isNotEmpty()) "true" else "false"}){var c=pl.getPlaylistIndex(); if(c!==idx){pl.playVideoAt(idx);changed=true;}}
    mark();
    var go=function(){
      mark();
      var cur=-1; try{cur=pl.getCurrentTime()*1000;}catch(x){}
      if(!(playing && pl.getPlayerState()===1 && Math.abs(cur-ms)<1500)){pl.seekTo(ms/1000,true);lastPos=-1;}
      if(playing){pl.playVideo();}else{pl.pauseVideo();}
    };
    if(changed){setTimeout(go,1200);}else{go();}
  }catch(x){}
}
function check(ms){
  try{
    var st=pl.getPlayerState();
    if(st===3||Date.now()<prog+4000){return;}
    if(st!==1){pl.playVideo();return;}
    var d=pl.getCurrentTime()*1000-ms;
    if(Math.abs(d)>3000){mark();lastPos=-1;pl.seekTo(ms/1000,true);}
  }catch(x){}
}
function vol(n){try{pl.setVolume(n);}catch(x){}}
function stopAll(){want=false;try{pl.stopVideo();}catch(x){}}
</script></body></html>"""
    }

    private fun soundcloudHtml(np: NowPlaying): String {
        val src = "https://w.soundcloud.com/player/?url=" + Uri.encode(np.url) +
            "&auto_play=false&hide_related=true&show_comments=false&show_user=true&show_reposts=false&visual=false&color=%23ff5500"
        return """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>$COMMON iframe{border:0}</style></head><body>
<iframe id="p" src="$src" allow="autoplay"></iframe>
<script src="https://w.soundcloud.com/player/api.js"></script>
<script>
var want=false, w=null, ready=false, prog=0;
function mark(){prog=Date.now()+2500;}
window.onload=function(){
  w=SC.Widget(document.getElementById('p'));
  w.bind(SC.Widget.Events.READY,function(){ready=true;Bridge.onReady();});
  w.bind(SC.Widget.Events.PLAY,function(){w.getCurrentSoundIndex(function(i){Bridge.onTrack(i);});Bridge.onState(1);});
  w.bind(SC.Widget.Events.PAUSE,function(){ if(want && document.hidden){setTimeout(function(){w.play();},300);} Bridge.onState(2);});
  w.bind(SC.Widget.Events.FINISH,function(){Bridge.onState(0);});
  w.bind(SC.Widget.Events.SEEK,function(e){ if(Date.now()>prog){Bridge.onSeek(Math.round(e.currentPosition));} });
  w.bind(SC.Widget.Events.ERROR,function(){Bridge.onError('sc');});
};
function sync(playing,ms,idx){
  want=playing;
  try{
    mark();
    if(idx>=0){w.getCurrentSoundIndex(function(c){ if(c!==idx){w.skip(idx);} });}
    if(playing){w.play(); setTimeout(function(){w.getPosition(function(p){ if(Math.abs(p-ms)>=1500){mark();w.seekTo(ms);} });},800);}
    else{mark();w.seekTo(ms); w.pause();}
  }catch(x){}
}
function check(ms){
  try{
    if(Date.now()<prog+4000){return;}
    w.isPaused(function(p){
      if(p){w.play();return;}
      w.getPosition(function(pos){ if(Math.abs(pos-ms)>3000){mark();w.seekTo(ms);} });
    });
  }catch(x){}
}
function vol(n){try{w.setVolume(n);}catch(x){}}
function stopAll(){want=false;try{w.pause();}catch(x){}}
</script></body></html>"""
    }
}
