package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.i18n.tr
import com.example.model.DirectChat

/**
 * Herramienta de seguridad: eliges a una persona de confianza (con un chat aceptado) y le compartes
 * tu ubicación en vivo con un toque, por 1, 2 u 8 horas. Reutiliza la ubicación en tiempo real del chat.
 */
@Composable
fun TrustedContactCard(
    myId: String,
    chats: List<DirectChat>,
    trustedId: String,
    sharing: Boolean,
    onPick: (String) -> Unit,
    onShare: (DirectChat, Int) -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    val candidates = chats.filter { it.status == "active" }
    fun peerOf(c: DirectChat) = c.participantIds.firstOrNull { it != myId }.orEmpty()
    fun nameOf(c: DirectChat) = c.participantNames[peerOf(c)].orEmpty().ifBlank { tr(R.string.s_contacto_de_chat) }
    val trustedChat = candidates.firstOrNull { peerOf(it) == trustedId }

    var picking by remember { mutableStateOf(false) }
    var pendingMins by remember { mutableIntStateOf(0) }
    var showDurations by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val ok = grants.values.any { it }
        val chat = trustedChat
        if (ok && chat != null && pendingMins > 0) onShare(chat, pendingMins)
    }
    fun start(mins: Int) {
        val chat = trustedChat ?: return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) onShare(chat, mins) else {
            pendingMins = mins
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(R.string.trusted_title), style = MaterialTheme.typography.titleMedium)
            Text(tr(R.string.trusted_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                candidates.isEmpty() -> Text(tr(R.string.trusted_no_chats), style = MaterialTheme.typography.bodyMedium)
                trustedChat == null -> Button(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text(tr(R.string.trusted_choose)) }
                else -> {
                    Text(tr(R.string.trusted_current, nameOf(trustedChat)), style = MaterialTheme.typography.bodyMedium)
                    if (sharing) {
                        Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text(tr(R.string.trusted_stop)) }
                    } else {
                        Button(onClick = { showDurations = true }, modifier = Modifier.fillMaxWidth()) { Text(tr(R.string.trusted_share)) }
                    }
                    TextButton(onClick = { picking = true }) { Text(tr(R.string.trusted_change)) }
                }
            }
        }
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(tr(R.string.trusted_choose)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    candidates.forEach { c ->
                        TextButton(onClick = { onPick(peerOf(c)); picking = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(nameOf(c))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text(tr(R.string.s_cancelar)) } },
        )
    }
    if (showDurations && trustedChat != null) {
        AlertDialog(
            onDismissRequest = { showDurations = false },
            title = { Text(tr(R.string.trusted_share_with, nameOf(trustedChat))) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60 to "1 h", 120 to "2 h", 480 to "8 h").forEach { (mins, label) ->
                        OutlinedButton(onClick = { showDurations = false; start(mins) }) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showDurations = false }) { Text(tr(R.string.s_cancelar)) } },
        )
    }
}
