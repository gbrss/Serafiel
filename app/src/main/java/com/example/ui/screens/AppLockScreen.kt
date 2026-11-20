package com.example.ui.screens

import com.example.R
import com.example.i18n.tr

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.AppLock

private fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Pantalla que tapa toda la app mientras está bloqueada. */
@Composable
fun AppLockScreen() {
    val activity = LocalContext.current.findFragmentActivity()
    fun ask() {
        activity?.let { a -> AppLock.authenticate(a, tr(R.string.s_desbloquear_serafiel)) { ok -> if (ok) AppLock.unlock() } }
    }
    LaunchedEffect(Unit) { ask() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(tr(R.string.s_serafiel_esta_bloqueada), style = MaterialTheme.typography.titleLarge)
            Text(
                tr(R.string.s_usa_tu_huella_rostro_o),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = { ask() }) { Text(tr(R.string.s_desbloquear)) }
        }
    }
}

/** Tarjeta de ajustes: activar/desactivar el bloqueo (exige autenticarse para cambiarlo). */
@Composable
fun AppLockCard() {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val enabled by AppLock.enabled.collectAsState()
    val available = AppLock.isAvailable(context)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr(R.string.s_bloqueo_de_la_app), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (available) {
                            tr(R.string.s_pide_huella_rostro_o_pin)
                        } else {
                            tr(R.string.s_configura_un_pin_patron_o)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    enabled = available && activity != null,
                    onCheckedChange = { want ->
                        activity?.let { a ->
                            AppLock.authenticate(a, if (want) tr(R.string.s_activar_bloqueo) else tr(R.string.s_desactivar_bloqueo)) { ok ->
                                if (ok) AppLock.setEnabled(context, want)
                            }
                        }
                    },
                )
            }
        }
    }
}
