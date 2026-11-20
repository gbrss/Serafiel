package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AvatarColor
import com.example.model.DirectMessage
import com.example.ui.viewmodel.SerafielViewModel.ForwardTarget

/** Permite a cualquier pantalla de chat pedir "Reenviar" sin cablear callbacks por todos lados. */
val LocalForwardHandler = compositionLocalOf<(DirectMessage) -> Unit> { {} }

const val MAX_FORWARD_TARGETS = 5

@Composable
fun ForwardDialog(
    message: DirectMessage,
    targets: List<ForwardTarget>,
    onDismiss: () -> Unit,
    onForward: (List<ForwardTarget>) -> Unit,
) = ShareTargetDialog(
    title = "Reenviar a…",
    preview = message.previewLabel(),
    targets = targets,
    onDismiss = onDismiss,
    onConfirm = onForward,
)

@Composable
fun ShareTargetDialog(
    title: String,
    preview: String,
    targets: List<ForwardTarget>,
    onDismiss: () -> Unit,
    onConfirm: (List<ForwardTarget>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    val filtered = remember(query, targets) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) targets else targets.filter { it.title.lowercase().contains(q) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Buscar chat o grupo") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = "No hay chats ni grupos disponibles.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.id }) { target ->
                        val checked = target.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selected.remove(target.id)
                                    else if (selected.size < MAX_FORWARD_TARGETS) selected.add(target.id)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AvatarCircle(
                                name = target.title,
                                avatarColor = AvatarColor.fromKey(target.colorKey),
                                size = 40.dp,
                            )
                            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = target.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (target is ForwardTarget.Group) "Grupo" else "Chat",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Checkbox(checked = checked, onCheckedChange = null)
                        }
                    }
                }
                if (selected.size >= MAX_FORWARD_TARGETS) {
                    Text(
                        text = "Máximo $MAX_FORWARD_TARGETS destinos por reenvío",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = { onConfirm(targets.filter { it.id in selected }) },
            ) {
                Text(if (selected.isEmpty()) "Enviar" else "Enviar (${selected.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
