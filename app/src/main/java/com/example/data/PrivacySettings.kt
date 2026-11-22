package com.example.data

import com.example.R
import com.example.i18n.tr

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max

/**
 * Privacidad de la ubicación en el radar: precisión con la que se publica la baliza
 * y modo fantasma temporal. La ubicación exacta nunca sale del teléfono salvo que
 * la compartas tú en un chat.
 */
object PrivacySettings {
    enum class Precision(val level: Int, val label: String, val hint: String, internal val cellDeg: Double) {
        EXACT(0, tr(R.string.s_exacta), tr(R.string.s_te_ven_en_tu_posicion), 0.0),
        APPROX(1, "~500 m", tr(R.string.s_te_ven_dentro_de_una), 0.005),
        ZONE(2, tr(R.string.s_2_km), tr(R.string.s_solo_se_publica_tu_zona), 0.02),
    }

    private const val PREFS = "serafiel_privacy"
    private const val KEY_PRECISION = "precision"
    private const val KEY_GHOST_UNTIL = "ghost_until_ms"

    @Volatile
    var precision: Precision = Precision.APPROX
        private set

    private val _precisionFlow = MutableStateFlow(Precision.APPROX)
    val precisionState: StateFlow<Precision> = _precisionFlow.asStateFlow()

    private val _ghostUntilMs = MutableStateFlow(0L)
    /** Instante (ms) hasta el que estás en modo fantasma; 0 = desactivado. */
    val ghostUntilMs: StateFlow<Long> = _ghostUntilMs.asStateFlow()

    private var appContext: Context? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(context: Context) {
        appContext = context.applicationContext
        val p = prefs(context)
        val level = p.getInt(KEY_PRECISION, Precision.APPROX.level)
        precision = Precision.entries.firstOrNull { it.level == level } ?: Precision.APPROX
        _precisionFlow.value = precision
        _ghostUntilMs.value = p.getLong(KEY_GHOST_UNTIL, 0L)
    }

    /** ¿Ya eligió el usuario su precisión (aviso de primera vez)? */
    fun hasChosenPrecision(context: Context): Boolean = prefs(context).getBoolean("precision_chosen", false)

    fun markPrecisionChosen(context: Context) {
        prefs(context).edit().putBoolean("precision_chosen", true).apply()
    }

    fun setPrecision(context: Context, value: Precision) {
        prefs(context).edit().putInt(KEY_PRECISION, value.level).apply()
        precision = value
        _precisionFlow.value = value
    }

    fun setGhostUntil(context: Context, untilMs: Long) {
        prefs(context).edit().putLong(KEY_GHOST_UNTIL, untilMs).apply()
        _ghostUntilMs.value = untilMs
    }

    fun clearGhost() {
        appContext?.let { prefs(it).edit().putLong(KEY_GHOST_UNTIL, 0L).apply() }
        _ghostUntilMs.value = 0L
    }

    /**
     * Ajusta la posición a una cuadrícula y devuelve su centro. Es determinista
     * (no se puede promediar varias lecturas para recuperar el punto exacto).
     */
    fun blur(lat: Double, lon: Double, level: Precision = precision): Pair<Double, Double> {
        val cell = level.cellDeg
        if (cell <= 0.0 || (lat == 0.0 && lon == 0.0)) return lat to lon
        val latCenter = floor(lat / cell) * cell + cell / 2
        val lonCell = cell / max(cos(Math.toRadians(latCenter)), 0.2)
        val lonCenter = floor(lon / lonCell) * lonCell + lonCell / 2
        return latCenter to lonCenter
    }
}
