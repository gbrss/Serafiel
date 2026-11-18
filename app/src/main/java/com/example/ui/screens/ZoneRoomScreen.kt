package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.LocationHelper
import com.example.model.AvatarColor
import com.example.model.PeerVisualInfo
import com.example.model.ReportReason
import com.example.model.SocialPurpose
import com.example.model.UiState
import com.example.model.ZoneLocation
import com.example.model.ZoneMessage

@Composable
fun ZoneRoomScreen(
    currentUserId: String,
    currentZone: ZoneLocation,
    zoneMessagesState: UiState<List<ZoneMessage>>,
    peerVisualMap: Map<String, PeerVisualInfo> = emptyMap(),
    mentionCandidates: List<MentionCandidate> = emptyList(),
    onSendMessage: (String, SocialPurpose, String?, String?, String?) -> Unit,
    onEditOwnMessage: (String, String) -> Unit,
    onArchiveOwnMessage: (String) -> Unit,
    onStartDirectChat: (ZoneMessage) -> Unit,
    onBlockUser: (String, String) -> Unit,
    onSubmitReport: (ZoneMessage, ReportReason, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    var draftText by remember { mutableStateOf(TextFieldValue("")) }
    var selectedPurpose by remember { mutableStateOf(SocialPurpose.CASUAL) }
    var replyDraft by remember { mutableStateOf<ReplyDraft?>(null) }
    var editingMessage by remember { mutableStateOf<ZoneMessage?>(null) }
    var selectedMessageForActions by remember { mutableStateOf<ZoneMessage?>(null) }
    var pendingDeleteMessage by remember { mutableStateOf<ZoneMessage?>(null) }
    var reportTarget by remember { mutableStateOf<ZoneMessage?>(null) }
    val listState = rememberLazyListState()

    val messages = (zoneMessagesState as? UiState.Success)?.data.orEmpty()

    val resolvedMentionCandidates = remember(mentionCandidates, messages, peerVisualMap, currentUserId) {
        val byId = linkedMapOf<String, MentionCandidate>()
        mentionCandidates.forEach { if (it.userId != currentUserId) byId[it.userId] = it }
        messages.forEach { msg ->
            if (msg.senderId != currentUserId && msg.senderName.isNotBlank() && msg.senderId !in byId) {
                val visual = peerVisualMap[msg.senderId]
                byId[msg.senderId] = MentionCandidate(
                    userId = msg.senderId,
                    displayName = msg.senderName,
                    avatarColorKey = msg.senderColor,
                    isOnline = visual?.isOnline == true,
                    photoData = visual?.photoData,
                    photoUrl = visual?.photoUrl,
                    subtitle = visual?.distanceKm?.let { "A ${LocationHelper.formatCompactDistance(it)}" }
                        ?: "En la sala local",
                )
            }
        }
        byId.values.sortedBy { it.displayName.lowercase() }
    }
    val knownMentionNames = remember(resolvedMentionCandidates, messages) {
        (resolvedMentionCandidates.map { it.displayName } + messages.map { it.senderName })
            .filter { it.isNotBlank() }
            .distinct()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
        ) {
            // Safe community room banner
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        val safeZoneTitle = currentZone.zoneName
                            .replace("Plaza Central · ", "", ignoreCase = true)
                            .replace("Plaza Central", "", ignoreCase = true)
                            .trim()
                        Text(
                            text = if (safeZoneTitle.isBlank() || safeZoneTitle.equals("Zona Abierta", ignoreCase = true)) {
                                "Sala Local · Zona Abierta"
                            } else {
                                "Sala Local · $safeZoneTitle"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = "Chat abierto de proximidad: amistad, ayuda, turismo, networking o charla.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            // Messages Stream
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (zoneMessagesState) {
                    is UiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    is UiState.Error -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = zoneMessagesState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    is UiState.Success -> {
                        if (messages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(22.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        val safeZoneName = currentZone.zoneName
                                            .replace("Plaza Central · ", "", ignoreCase = true)
                                            .replace("Plaza Central", "Zona Abierta", ignoreCase = true)
                                        Text(
                                            text = "Inicia la conversación en $safeZoneName",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text(
                                            text = "Pide recomendaciones locales, ofrece ayuda, busca compañía para un café o simplemente di hola.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(messages, key = { it.id }) { msg ->
                                    val visual = peerVisualMap[msg.senderId]
                                    val mine = msg.senderId == currentUserId
                                    SwipeToReplyWrapper(
                                        onSwipeReply = {
                                            editingMessage = null
                                            replyDraft = ReplyDraft(
                                                messageId = msg.id,
                                                senderName = if (mine) "Tú" else msg.senderName,
                                                previewText = msg.text,
                                            )
                                        },
                                    ) {
                                        ZoneMessageItem(
                                            message = msg,
                                            isMine = mine,
                                            peerVisual = visual,
                                            mentionNames = knownMentionNames,
                                            onSelect = { selectedMessageForActions = msg },
                                            onReply = {
                                                editingMessage = null
                                                replyDraft = ReplyDraft(
                                                    messageId = msg.id,
                                                    senderName = if (mine) "Tú" else msg.senderName,
                                                    previewText = msg.text,
                                                )
                                            },
                                            onEditOwn = {
                                                replyDraft = null
                                                editingMessage = msg
                                                draftText = TextFieldValue(msg.text, TextRange(msg.text.length))
                                            },
                                            onStartDirectChat = { onStartDirectChat(msg) },
                                            onArchiveOwn = { pendingDeleteMessage = msg },
                                            onReport = { reportTarget = msg },
                                            onBlock = { onBlockUser(msg.senderId, msg.senderName) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val activeMention = remember(draftText.text, draftText.selection.end) {
                findActiveMentionQuery(draftText.text, draftText.selection.end)
            }
            if (activeMention != null) {
                MentionSuggestionsPanel(
                    candidates = resolvedMentionCandidates,
                    query = activeMention.second,
                    onSelect = { candidate ->
                        draftText = insertMention(draftText, activeMention.first, candidate.displayName, maxLength = 500)
                    },
                )
            }

            ReplyOrEditComposerBanner(
                replyDraft = replyDraft,
                editingTextPreview = editingMessage?.text,
                onCancel = {
                    replyDraft = null
                    if (editingMessage != null) {
                        editingMessage = null
                        draftText = TextFieldValue("")
                    }
                },
            )

            // Composer with Purpose Tag Selector
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SocialPurpose.entries.forEach { purpose ->
                            FilterChip(
                                selected = selectedPurpose == purpose,
                                onClick = { selectedPurpose = purpose },
                                label = { Text(purpose.labelEs) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = purpose.icon(),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = draftText,
                            onValueChange = { incoming ->
                                draftText = if (incoming.text.length <= 500) incoming else incoming.copy(text = incoming.text.take(500))
                            },
                            placeholder = {
                                Text(
                                    if (editingMessage != null) "Editar mensaje..."
                                    else "Escribe a tu zona (@ para etiquetar)..."
                                )
                            },
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("zone_message_input"),
                        )
                        IconButton(
                            onClick = {
                                val clean = draftText.text.trim()
                                if (clean.isNotEmpty()) {
                                    val editing = editingMessage
                                    if (editing != null) {
                                        onEditOwnMessage(editing.id, clean)
                                        editingMessage = null
                                    } else {
                                        val reply = replyDraft
                                        onSendMessage(
                                            clean,
                                            selectedPurpose,
                                            reply?.messageId,
                                            reply?.senderName,
                                            reply?.previewText,
                                        )
                                        replyDraft = null
                                    }
                                    draftText = TextFieldValue("")
                                }
                            },
                            enabled = draftText.text.trim().isNotEmpty(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("zone_send_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.cd_send_message),
                            )
                        }
                    }
                }
            }
        }
    }

    selectedMessageForActions?.let { msg ->
        val mine = msg.senderId == currentUserId
        MessageActionsDialog(
            senderName = if (mine) "Tú" else msg.senderName,
            previewText = msg.text,
            canCopy = msg.text.isNotBlank(),
            canEdit = mine,
            canDelete = mine,
            onDismiss = { selectedMessageForActions = null },
            onReply = {
                editingMessage = null
                replyDraft = ReplyDraft(
                    messageId = msg.id,
                    senderName = if (mine) "Tú" else msg.senderName,
                    previewText = msg.text,
                )
            },
            onCopy = {
                if (msg.text.isNotBlank()) {
                    clipboardManager.setText(AnnotatedString(msg.text))
                }
            },
            onEdit = {
                replyDraft = null
                editingMessage = msg
                draftText = TextFieldValue(msg.text, TextRange(msg.text.length))
            },
            onDelete = { pendingDeleteMessage = msg },
        )
    }

    pendingDeleteMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMessage = null },
            title = { Text("¿Eliminar mensaje?") },
            text = { Text("Se retirará tu mensaje de esta Sala Local.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteMessage = null
                        if (editingMessage?.id == msg.id) {
                            editingMessage = null
                            draftText = TextFieldValue("")
                        }
                        onArchiveOwnMessage(msg.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMessage = null }) {
                    Text("Cancelar")
                }
            },
        )
    }

    reportTarget?.let { target ->
        SafetyReportDialog(
            targetName = target.senderName,
            onDismiss = { reportTarget = null },
            onSubmit = { reason, details, alsoBlock ->
                reportTarget = null
                onSubmitReport(target, reason, details, alsoBlock)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoneMessageItem(
    message: ZoneMessage,
    isMine: Boolean,
    peerVisual: PeerVisualInfo? = null,
    mentionNames: List<String> = emptyList(),
    onSelect: () -> Unit,
    onReply: () -> Unit,
    onEditOwn: () -> Unit,
    onStartDirectChat: () -> Unit,
    onArchiveOwn: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val purpose = SocialPurpose.fromRaw(message.purpose)
    val avatarColor = AvatarColor.fromKey(message.senderColor)
    val isOnline = peerVisual?.isOnline == true
    val distLabel = if (!isMine && peerVisual?.distanceKm != null) {
        " · A ${LocationHelper.formatCompactDistance(peerVisual.distanceKm)}"
    } else {
        ""
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isMine) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isMine) 0.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onSelect,
            )
            .testTag("zone_message_${message.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AvatarCircle(
                    name = message.senderName,
                    avatarColor = avatarColor,
                    size = 36.dp,
                    verified = true,
                    isOnline = isOnline,
                    photoData = peerVisual?.photoData,
                    photoUrl = peerVisual?.photoUrl,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isMine) "${message.senderName} (Tú)" else message.senderName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = buildString {
                            if (isOnline) append("En línea · ")
                            append(formatRelativeTimestamp(message.createdAt))
                            if (message.edited) append(" · Editado")
                            append(distLabel)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOnline) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PurposeBadge(purpose = purpose)
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.cd_safety_menu),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Responder") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onReply()
                            },
                        )
                        if (isMine) {
                            DropdownMenuItem(
                                text = { Text("Editar mensaje") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onEditOwn()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar mensaje") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onArchiveOwn()
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Abrir chat privado") },
                                leadingIcon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onStartDirectChat()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Reportar mensaje") },
                                leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onReport()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Bloquear usuario") },
                                leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onBlock()
                                },
                            )
                        }
                    }
                }
            }

            if (!message.replyToMessageId.isNullOrBlank()) {
                ReplyQuoteBox(
                    senderName = message.replyToSenderName,
                    previewText = message.replyToPreview.orEmpty(),
                )
            }

            Text(
                text = buildMentionAnnotatedText(
                    text = message.text,
                    mentionNames = mentionNames,
                    highlightColor = MaterialTheme.colorScheme.primary,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
