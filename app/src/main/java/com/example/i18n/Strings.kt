package com.example.i18n

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Idioma de la app. "auto" sigue el idioma del teléfono (si no es uno de los soportados, inglés).
 * El cambio reinicia el proceso para que todo el texto se vuelva a leer en el idioma elegido.
 */
object Lang {
    const val AUTO = "auto"
    /** Código → nombre del idioma en su propio idioma (no se traduce). */
    val supported: List<Pair<String, String>> = listOf(
        "es" to "Español",
        "en" to "English",
        "pt" to "Português",
        "fr" to "Français",
        "de" to "Deutsch",
        "it" to "Italiano",
        "ru" to "Русский",
    )

    private const val PREFS = "serafiel_lang"
    private const val KEY = "code"

    fun saved(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, AUTO) ?: AUTO

    /** Código de idioma efectivo (es, en, pt, ...). */
    fun effectiveCode(context: Context): String {
        val s = saved(context)
        if (s != AUTO) return s
        val sys = Locale.getDefault().language
        return if (supported.any { it.first == sys }) sys else "en"
    }

    /** Envuelve un Context para que sus recursos salgan en el idioma elegido. */
    fun wrap(base: Context): Context {
        val locale = Locale(effectiveCode(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /** Guarda el idioma y reinicia la app. */
    fun change(activity: Activity, code: String) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).commit()
        val intent = Intent(activity, com.example.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}

/** Acceso global a los textos traducidos desde cualquier capa (UI, ViewModel, repositorio, servicios). */
object AppStrings {
    @Volatile
    lateinit var appContext: Context

    fun init(context: Context) { appContext = context }
}

fun tr(@StringRes id: Int, vararg args: Any?): String =
    AppStrings.appContext.getString(id, *args)
