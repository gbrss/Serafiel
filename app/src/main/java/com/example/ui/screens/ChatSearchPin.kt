package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DirectMessage
import java.text.Normalizer

/** (esGrupo, idChatOGrupo, idMensaje o null para desfijar, resumen) */
val LocalPinHandler = compositionLocalOf<(Boolean, String, String?, String?) -> Unit> { { _, _, _, _ -> } }

private fun normalize(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** Búsqueda sin distinguir mayúsculas ni tildes en texto, nombre de archivo y lugar. */
fun DirectMessage.matchesSearch(query: String): Boolean {
    val q = normalize(query.trim())
    if (q.isEmpty()) return false
    return listOfNotNull(text, mediaName, locationLabel, senderName).any { normalize(it).contains(q) }
}

@Composable
fun ChatSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    resultCount: Int,
    position: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = FocusRequester()
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Cerrar búsqueda")
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Buscar en el chat") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .testTag("chat_search_field"),
            )
            if (query.isNotBlank()) {
                Text(
                    text = if (resultCount == 0) "0" else "${position + 1}/$resultCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                IconButton(onClick = onPrev, enabled = resultCount > 0 && position > 0) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Resultado anterior")
                }
                IconButton(onClick = onNext, enabled = resultCount > 0 && position < resultCount - 1) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Resultado siguiente")
                }
            }
        }
    }
}

@Composable
fun PinnedMessageBanner(
    preview: String,
    onJump: () -> Unit,
    onUnpin: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable { onJump() }
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                .testTag("pinned_message_banner"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.width(18.dp))
            Text(
                text = preview.ifBlank { "Mensaje fijado" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onUnpin) {
                Icon(Icons.Outlined.Close, contentDescription = "Desfijar mensaje")
            }
        }
    }
}
