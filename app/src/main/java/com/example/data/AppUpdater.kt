package com.example.data

import com.example.R
import com.example.i18n.tr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actualización dentro de la app: consulta la última release pública de GitHub,
 * descarga el APK y abre el instalador de Android. No requiere cuenta ni token.
 */
object AppUpdater {
    private const val RELEASE_URL = "https://api.github.com/repos/gbrss/Serafiel/releases/tags/latest"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    enum class Status { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR }

    data class UpdateInfo(val build: Int, val title: String, val apkUrl: String, val sizeBytes: Long)

    data class UpdateState(
        val status: Status = Status.IDLE,
        val info: UpdateInfo? = null,
        /** 0..1 durante la descarga. */
        val progress: Float = 0f,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentBuild: Int get() = BuildConfig.VERSION_CODE
    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    private fun apkFile(context: Context): File =
        File(File(context.cacheDir, "updates").apply { mkdirs() }, "Serafiel-update.apk")

    /** Busca una versión más nueva. [silent] evita mostrar "al día" si ya hay un estado más útil. */
    fun check(silent: Boolean = false) {
        val current = _state.value.status
        if (current == Status.CHECKING || current == Status.DOWNLOADING) return
        if (silent && (current == Status.AVAILABLE || current == Status.READY)) return
        _state.value = UpdateState(status = Status.CHECKING)
        job = scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fetchLatest() } }
            result.onSuccess { info ->
                _state.value = when {
                    info == null -> UpdateState(Status.UP_TO_DATE, message = tr(R.string.s_no_se_pudo_leer_la))
                    info.build > currentBuild -> UpdateState(Status.AVAILABLE, info = info)
                    else -> UpdateState(Status.UP_TO_DATE, info = info)
                }
            }.onFailure {
                _state.value = UpdateState(
                    Status.ERROR,
                    message = if (silent) null else tr(R.string.s_no_se_pudo_buscar_actualizaciones),
                )
            }
        }
    }

    private fun fetchLatest(): UpdateInfo? {
        val conn = (URL(RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Serafiel-Android")
        }
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val title = json.optString("name")
            val build = Regex("#(\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    return UpdateInfo(build, title, a.getString("browser_download_url"), a.optLong("size"))
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** Descarga el APK y, al terminar, abre el instalador. */
    fun downloadAndInstall(context: Context) {
        val info = _state.value.info ?: return
        val appContext = context.applicationContext
        val st = _state.value.status
        if (st == Status.DOWNLOADING) return
        if (st == Status.READY) {
            install(appContext)
            return
        }
        _state.value = UpdateState(Status.DOWNLOADING, info = info)
        job = scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val target = apkFile(appContext)
                    val tmp = File(target.parentFile, "${target.name}.part")
                    val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "Serafiel-Android")
                    }
                    try {
                        if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
                        val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                        var done = 0L
                        var lastEmit = 0L
                        conn.inputStream.use { input ->
                            tmp.outputStream().use { out ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    done += n
                                    val now = System.currentTimeMillis()
                                    if (total > 0 && now - lastEmit > 150) {
                                        lastEmit = now
                                        _state.value = _state.value.copy(progress = (done.toFloat() / total).coerceIn(0f, 1f))
                                    }
                                }
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                    if (target.exists()) target.delete()
                    check(tmp.renameTo(target)) { tr(R.string.s_no_se_pudo_guardar_el) }
                }.isSuccess
            }
            if (ok) {
                _state.value = UpdateState(Status.READY, info = info, progress = 1f)
                install(appContext)
            } else {
                _state.value = UpdateState(Status.AVAILABLE, info = info, message = tr(R.string.s_la_descarga_fallo_intentalo_de))
            }
        }
    }

    /** Abre el instalador; si falta el permiso de "instalar apps desconocidas" lleva a ajustes. */
    fun install(context: Context) {
        val file = apkFile(context)
        if (!file.exists()) {
            _state.value = _state.value.copy(status = Status.AVAILABLE, message = tr(R.string.s_vuelve_a_descargar_la_actualizacion))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = _state.value.copy(
                status = Status.READY,
                message = tr(R.string.s_permite_instalar_desde_serafiel_y),
            )
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { _state.value = _state.value.copy(message = tr(R.string.s_no_se_pudo_abrir_el)) }
    }
}
