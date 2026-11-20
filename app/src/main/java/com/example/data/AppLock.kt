package com.example.data

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bloqueo de la app con huella / rostro / PIN o patrón del teléfono.
 * La app se bloquea al abrirla y al volver tras estar más de [GRACE_MS] en segundo plano.
 */
object AppLock {
    private const val PREFS = "serafiel_lock"
    private const val KEY_ENABLED = "enabled"
    private const val GRACE_MS = 30_000L
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundAtMs = 0L
    private var promptShowing = false

    fun init(context: Context) {
        val on = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
        _enabled.value = on
        _locked.value = on
    }

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun onAppBackgrounded() {
        backgroundAtMs = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        if (_enabled.value && backgroundAtMs > 0 && System.currentTimeMillis() - backgroundAtMs > GRACE_MS) {
            _locked.value = true
        }
    }

    /** Pide la huella/PIN. [onResult] recibe true si el usuario se autenticó. */
    fun authenticate(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
        if (promptShowing) return
        promptShowing = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing = false
                    onResult(false)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }

    fun unlock() {
        backgroundAtMs = 0L
        _locked.value = false
    }

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
        if (!on) _locked.value = false
    }
}
