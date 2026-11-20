package com.example.ui.screens

import com.example.R
import com.example.i18n.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DirectMessage
import com.example.model.SocialGroup

/** Acciones de encuesta disponibles para las pantallas de grupo. */
class PollActions(
    val create: (SocialGroup, String, List<String>) -> Unit = { _, _, _ -> },
    val vote: (SocialGroup, String, Int?) -> Unit = { _, _, _ -> },
)

val LocalPollActions = compositionLocalOf { PollActions() }

private const val MAX_OPTIONS = 6

@Composable
fun CreatePollDialog(
    onDismiss: () -> Unit,
    onCreate: (question: String, options: List<String>) -> Unit,
) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    val validOptions = options.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val canCreate = question.isNotBlank() && validOptions.size >= 2

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.s_nueva_encuesta)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it.take(200) },
                    label = { Text(tr(R.string.s_pregunta)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                options.forEachIndexed { index, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { options[index] = it.take(60) },
                            label = { Text(tr(R.string.s_opcion, index + 1)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (options.size > 2) {
                            IconButton(onClick = { options.removeAt(index) }) {
                                Icon(Icons.Outlined.Close, contentDescription = tr(R.string.s_quitar_opcion))
                            }
                        }
                    }
                }
                if (options.size < MAX_OPTIONS) {
                    TextButton(onClick = { options.add("") }) { Text(tr(R.string.s_anadir_opcion)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canCreate, onClick = { onCreate(question.trim(), validOptions) }) {
                Text(tr(R.string.s_enviar))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.s_cancelar)) } },
    )
}

/** Burbuja de encuesta: opciones con barra de porcentaje; tocar una opción vota (otra vez, retira el voto). */
@Composable
fun PollContent(
    message: DirectMessage,
    myUserId: String,
    contentColor: Color,
    onVote: (Int?) -> Unit,
) {
    val votes = message.pollVotes
    val total = votes.size
    val mine = votes[myUserId]?.toInt()
    val counts = remember(votes, message.pollOptions) {
        IntArray(message.pollOptions.size).also { arr ->
            votes.values.forEach { v -> v.toInt().takeIf { it in arr.indices }?.let { arr[it]++ } }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "📊 ${message.text}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
        message.pollOptions.forEachIndexed { index, option ->
            val count = counts[index]
            val fraction = if (total == 0) 0f else count.toFloat() / total
            val selected = mine == index
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(contentColor.copy(alpha = 0.08f))
                    .clickable { onVote(if (selected) null else index) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        .heightIn(min = 40.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.6f),
                    )
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (total == 0) "0" else "$count · ${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.8f),
                    )
                }
            }
        }
        Text(
            text = if (total == 1) tr(R.string.s_1_voto) else "$total votos",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.7f),
        )
    }
}
