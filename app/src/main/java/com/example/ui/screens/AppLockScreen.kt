package com.example.ui.screens

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
        activity?.let { a -> AppLock.authenticate(a, "Desbloquear Serafiel") { ok -> if (ok) AppLock.unlock() } }
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
            Text("Serafiel está bloqueada", style = MaterialTheme.typography.titleLarge)
            Text(
                "Usa tu huella, rostro o PIN para continuar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = { ask() }) { Text("Desbloquear") }
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
                    Text("Bloqueo de la app", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (available) {
                            "Pide huella, rostro o PIN al abrir Serafiel y al volver tras estar un rato fuera."
                        } else {
                            "Configura un PIN, patrón o huella en los ajustes del teléfono para poder activarlo."
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
                            AppLock.authenticate(a, if (want) "Activar bloqueo" else "Desactivar bloqueo") { ok ->
                                if (ok) AppLock.setEnabled(context, want)
                            }
                        }
                    },
                )
            }
        }
    }
}
