package com.example.data

import com.example.R
import com.example.i18n.tr

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.floor

/**
 * Privacidad de la ubicación en el radar: precisión con la que se publica la baliza
 * y modo fantasma temporal. La ubicación exacta nunca sale del teléfono salvo que
 * la compartas tú en un chat.
 */
object PrivacySettings {
    enum class Precision(val level: Int, val label: String, val hint: String, internal val cellDeg: Double) {
        HIGH(0, "~250 m", tr(R.string.precision_250_hint), 0.0025),
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

    /** ¿Ya aceptó las normas de convivencia? */
    fun hasAcceptedRules(context: Context): Boolean = prefs(context).getBoolean("rules_accepted", false)

    fun markRulesAccepted(context: Context) {
        prefs(context).edit().putBoolean("rules_accepted", true).apply()
    }

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

    /** Unidad de la cuadrícula fina (grados). Las reglas de Firestore exigen múltiplos IMPARES de ella. */
    private const val UNIT = 0.00125

    private fun snapToUnit(x: Double): Double {
        var k = Math.round(x / UNIT)
        if (k % 2L == 0L) k += 1
        return k * UNIT
    }

    /**
     * Ajusta la posición a una cuadrícula fija en grados (igual para latitud y longitud) y devuelve
     * el centro de su celda, encajado en la cuadrícula fina de 250 m que validan las reglas.
     * Es determinista (no se puede promediar varias lecturas para recuperar el punto exacto).
     */
    fun blur(lat: Double, lon: Double, level: Precision = precision): Pair<Double, Double> {
        if (lat == 0.0 && lon == 0.0) return lat to lon
        val cell = level.cellDeg
        val latCenter = floor(lat / cell) * cell + cell / 2
        val lonCenter = floor(lon / cell) * cell + cell / 2
        return snapToUnit(latCenter) to snapToUnit(lonCenter)
    }
}
