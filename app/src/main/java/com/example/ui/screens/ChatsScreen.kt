package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ChatAudioPlayer
import com.example.data.ChatLocationPoint
import com.example.data.ChatMediaHelper
import com.example.data.EncodedMediaPayload
import com.example.data.LocationHelper
import com.example.data.VoiceNoteRecorder
import com.example.model.AvatarColor
import com.example.model.DirectChat
import com.example.model.DirectMessage
import com.example.model.PeerVisualInfo
import com.example.model.ReportReason
import com.example.model.SocialPurpose
import com.example.model.UiState
import com.example.model.ZoneLocation
import com.example.ui.viewmodel.SerafielViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatsTabScreen(
    currentUserId: String,
    currentZone: ZoneLocation,
    myName: String,
    myAvatarColor: AvatarColor,
    myPhotoData: String?,
    myPhotoUrl: String?,
    myChatsState: UiState<List<DirectChat>>,
    selectedChatId: String?,
    selectedChatMessagesState: UiState<List<DirectMessage>>,
    peerVisualMap: Map<String, PeerVisualInfo> = emptyMap(),
    liveLocationState: SerafielViewModel.LiveLocationShareState,
    onOpenChat: (String) -> Unit,
    onCloseSelectedChatView: () -> Unit,
    onSendDirectMessage: (DirectChat, String, String?, String?, String?) -> Unit,
    onEditDirectMessage: (DirectChat, String, String, Boolean) -> Unit,
    onDeleteDirectMessage: (DirectChat, String, String?) -> Unit,
    onSendDirectImage: (DirectChat, Uri) -> Unit,
    onSendVoiceNote: (DirectChat, EncodedMediaPayload) -> Unit,
    onSendAudioUri: (DirectChat, Uri) -> Unit,
    onSendCurrentLocation: (DirectChat, ChatLocationPoint) -> Unit,
    onStartLiveLocation: (DirectChat, Int, String, ChatLocationPoint) -> Unit,
    onLocationDetected: (ChatLocationPoint) -> Unit = {},
    onStopLiveLocation: () -> Unit,
    onShowMessage: (String) -> Unit,
    onAddPeerToGroup: (String, String, String) -> Unit = { _, _, _ -> },
    onDeleteChats: (List<String>) -> Unit = {},
    onBlockUser: (String, String) -> Unit,
    onSubmitReport: (String, String, String, ReportReason, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chatsList = (myChatsState as? UiState.Success)?.data.orEmpty()
    val activeChat = selectedChatId?.let { id -> chatsList.firstOrNull { it.id == id } }

    if (activeChat != null) {
        BackHandler { onCloseSelectedChatView() }
        DirectChatDetailScreen(
            currentUserId = currentUserId,
            currentZone = currentZone,
            myName = myName,
            myAvatarColor = myAvatarColor,
            myPhotoData = myPhotoData,
            myPhotoUrl = myPhotoUrl,
            chat = activeChat,
            messagesState = selectedChatMessagesState,
            peerVisualMap = peerVisualMap,
            liveLocationState = liveLocationState,
            onBack = onCloseSelectedChatView,
            onSendMessage = { text, replyId, replySender, replyPreview ->
                onSendDirectMessage(activeChat, text, replyId, replySender, replyPreview)
            },
            onEditMessage = { msgId, newText, isLatest ->
                onEditDirectMessage(activeChat, msgId, newText, isLatest)
            },
            onDeleteMessage = { msgId, fallbackLast ->
                onDeleteDirectMessage(activeChat, msgId, fallbackLast)
            },
            onSendImage = { uri -> onSendDirectImage(activeChat, uri) },
            onSendVoiceNote = { payload -> onSendVoiceNote(activeChat, payload) },
            onSendAudioUri = { uri -> onSendAudioUri(activeChat, uri) },
            onSendCurrentLocation = { point -> onSendCurrentLocation(activeChat, point) },
            onStartLiveLocation = { mins, comment, point -> onStartLiveLocation(activeChat, mins, comment, point) },
            onLocationDetected = onLocationDetected,
            onStopLiveLocation = onStopLiveLocation,
            onShowMessage = onShowMessage,
            onAddPeerToGroup = onAddPeerToGroup,
            onBlockUser = onBlockUser,
            onSubmitReport = onSubmitReport,
            modifier = modifier,
        )
    } else {
        ChatsListScreen(
            currentUserId = currentUserId,
            myChatsState = myChatsState,
            peerVisualMap = peerVisualMap,
            onOpenChat = onOpenChat,
            onDeleteChats = onDeleteChats,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatsListScreen(
    currentUserId: String,
    myChatsState: UiState<List<DirectChat>>,
    peerVisualMap: Map<String, PeerVisualInfo>,
    onOpenChat: (String) -> Unit,
    onDeleteChats: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectionMode by remember { mutableStateOf(false) }
    var selectedChatIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val availableChatIds = (myChatsState as? UiState.Success)?.data?.map { it.id }?.toSet().orEmpty()
    LaunchedEffect(availableChatIds) {
        val pruned = selectedChatIds.intersect(availableChatIds)
        if (pruned != selectedChatIds) {
            selectedChatIds = pruned
        }
        if (availableChatIds.isEmpty()) {
            selectionMode = false
        }
    }

    if (selectionMode) {
        BackHandler {
            selectionMode = false
            selectedChatIds = emptySet()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (myChatsState) {
            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = myChatsState.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is UiState.Success -> {
                val chats = myChatsState.data
                if (chats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(
                                modifier = Modifier.padding(26.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Forum,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp),
                                )
                                Text(
                                    text = "Tus conversaciones aparecerán aquí",
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = "Saluda a alguien desde el Radar o escribe en la Sala Local para iniciar un chat 1:1.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize(),
                    ) {
                        if (selectionMode) {
                            Surface(
                                tonalElevation = 3.dp,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    IconButton(
                                        onClick = {
                                            selectionMode = false
                                            selectedChatIds = emptySet()
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("chat_cancel_selection_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Cancelar selección",
                                        )
                                    }
                                    Text(
                                        text = if (selectedChatIds.isEmpty()) {
                                            "Selecciona conversaciones"
                                        } else {
                                            "${selectedChatIds.size} seleccionada(s)"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                    val allSelected = selectedChatIds.size == chats.size
                                    TextButton(
                                        onClick = {
                                            selectedChatIds = if (allSelected) {
                                                emptySet()
                                            } else {
                                                chats.map { it.id }.toSet()
                                            }
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("chat_select_all_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DoneAll,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (allSelected) "Ninguna" else "Todas")
                                    }
                                    IconButton(
                                        onClick = { showDeleteConfirmDialog = true },
                                        enabled = selectedChatIds.isNotEmpty(),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("chat_delete_selected_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Eliminar conversaciones seleccionadas",
                                            tint = if (selectedChatIds.isNotEmpty()) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 12.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Conversaciones (${chats.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(
                                    onClick = {
                                        selectionMode = true
                                        selectedChatIds = emptySet()
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("chat_select_mode_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Checklist,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Seleccionar")
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(chats, key = { it.id }) { chat ->
                                val peerId = chat.participantIds.firstOrNull { it != currentUserId } ?: ""
                                val peerName = chat.participantNames[peerId] ?: "Persona cercana"
                                val peerColor = AvatarColor.fromKey(chat.participantColors[peerId])
                                val purpose = SocialPurpose.fromRaw(chat.purpose)
                                val visual = peerVisualMap[peerId]
                                val isOnline = visual?.isOnline == true
                                val distText = visual?.distanceKm?.let {
                                    "A ${LocationHelper.formatCompactDistance(it)}"
                                }
                                val isSelected = chat.id in selectedChatIds

                                fun toggleSelection() {
                                    selectedChatIds = if (isSelected) {
                                        selectedChatIds - chat.id
                                    } else {
                                        selectedChatIds + chat.id
                                    }
                                }

                                Card(
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                    ),
                                    border = if (isSelected) {
                                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        null
                                    },
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (selectionMode) {
                                                    toggleSelection()
                                                } else {
                                                    onOpenChat(chat.id)
                                                }
                                            },
                                            onLongClick = {
                                                selectionMode = true
                                                selectedChatIds = selectedChatIds + chat.id
                                            },
                                        )
                                        .testTag("chat_row_${chat.id}"),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        if (selectionMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { toggleSelection() },
                                                modifier = Modifier.testTag("chat_checkbox_${chat.id}"),
                                            )
                                        }
                                        AvatarCircle(
                                            name = peerName,
                                            avatarColor = peerColor,
                                            size = 48.dp,
                                            verified = true,
                                            isOnline = isOnline,
                                            photoData = visual?.photoData,
                                            photoUrl = visual?.photoUrl,
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = peerName,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = chat.lastMessage.ifBlank { "Conversación abierta" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                PurposeBadge(purpose = purpose)
                                                if (isOnline) {
                                                    Text(
                                                        text = "En línea",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF16A34A),
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                }
                                                val metaText = distText ?: chat.zoneName.takeIf { it.isNotBlank() }
                                                if (!metaText.isNullOrBlank()) {
                                                    Text(
                                                        text = metaText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = formatRelativeTimestamp(chat.updatedAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog && selectedChatIds.isNotEmpty()) {
        val count = selectedChatIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    if (count == 1) "¿Eliminar conversación?"
                    else "¿Eliminar $count conversaciones?"
                )
            },
            text = {
                Text("Se eliminarán las conversaciones seleccionadas y todo su historial de mensajes.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = selectedChatIds.toList()
                        showDeleteConfirmDialog = false
                        selectionMode = false
                        selectedChatIds = emptySet()
                        onDeleteChats(toDelete)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.testTag("confirm_delete_chats_button"),
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun DirectChatDetailScreen(
    currentUserId: String,
    currentZone: ZoneLocation,
    myName: String,
    myAvatarColor: AvatarColor,
    myPhotoData: String?,
    myPhotoUrl: String?,
    chat: DirectChat,
    messagesState: UiState<List<DirectMessage>>,
    peerVisualMap: Map<String, PeerVisualInfo>,
    liveLocationState: SerafielViewModel.LiveLocationShareState,
    onBack: () -> Unit,
    onSendMessage: (String, String?, String?, String?) -> Unit,
    onEditMessage: (String, String, Boolean) -> Unit,
    onDeleteMessage: (String, String?) -> Unit,
    onSendImage: (Uri) -> Unit,
    onSendVoiceNote: (EncodedMediaPayload) -> Unit,
    onSendAudioUri: (Uri) -> Unit,
    onSendCurrentLocation: (ChatLocationPoint) -> Unit,
    onStartLiveLocation: (Int, String, ChatLocationPoint) -> Unit,
    onLocationDetected: (ChatLocationPoint) -> Unit = {},
    onStopLiveLocation: () -> Unit,
    onShowMessage: (String) -> Unit,
    onAddPeerToGroup: (String, String, String) -> Unit,
    onBlockUser: (String, String) -> Unit,
    onSubmitReport: (String, String, String, ReportReason, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val peerId = chat.participantIds.firstOrNull { it != currentUserId } ?: ""
    val peerName = chat.participantNames[peerId] ?: "Persona cercana"
    val peerColor = AvatarColor.fromKey(chat.participantColors[peerId])
    val peerVisual = peerVisualMap[peerId]

    var draft by remember { mutableStateOf("") }
    var replyDraft by remember(chat.id) { mutableStateOf<ReplyDraft?>(null) }
    var editingMessage by remember(chat.id) { mutableStateOf<DirectMessage?>(null) }
    var selectedMessageForActions by remember(chat.id) { mutableStateOf<DirectMessage?>(null) }
    var pendingDeleteMessage by remember(chat.id) { mutableStateOf<DirectMessage?>(null) }
    var inspectedLocationMessageId by remember(chat.id) { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showAttachmentsPanel by remember { mutableStateOf(false) }
    var showWhatsAppLocationPicker by remember { mutableStateOf(false) }
    var fullscreenImageBase64 by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val voiceRecorder = remember(context) { VoiceNoteRecorder(context) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableLongStateOf(0L) }

    val audioPlaybackState by ChatAudioPlayer.playbackState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            showAttachmentsPanel = false
            onSendImage(uri)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val capturedUri = pendingCameraUri
        pendingCameraUri = null
        if (success && capturedUri != null) {
            showAttachmentsPanel = false
            onSendImage(capturedUri)
        }
    }

    fun startCameraCapture() {
        showAttachmentsPanel = false
        val uri = ChatMediaHelper.createTempCameraImageUri(context)
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startCameraCapture()
        } else {
            onShowMessage("Activa el permiso de cámara para tomar y enviar fotos.")
        }
    }

    fun launchCameraFlow() {
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            startCameraCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            showAttachmentsPanel = false
            onSendAudioUri(uri)
        }
    }

    fun beginVoiceRecording() {
        val started = voiceRecorder.startRecording()
        if (started.isSuccess) {
            recordingSeconds = 0L
            isRecordingVoice = true
            showEmojiPicker = false
            showAttachmentsPanel = false
        } else {
            onShowMessage("No se pudo iniciar la grabación de voz.")
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            beginVoiceRecording()
        } else {
            onShowMessage("Activa el permiso de micrófono para enviar notas de voz.")
        }
    }

    var hasRequestedFineUpgrade by remember(chat.id) { mutableStateOf(false) }

    val chatLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val anyGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            LocationHelper.hasAnyLocationPermission(context)
        if (anyGranted) {
            showWhatsAppLocationPicker = true
        } else {
            onShowMessage("Activa el permiso de ubicación para enviar tu ubicación.")
        }
    }

    fun openWhatsAppLocationPicker() {
        showAttachmentsPanel = false
        val hasFine = LocationHelper.hasPreciseLocationPermission(context)
        val hasAny = LocationHelper.hasAnyLocationPermission(context)
        if (hasFine || (hasAny && hasRequestedFineUpgrade)) {
            showWhatsAppLocationPicker = true
        } else {
            if (hasAny) hasRequestedFineUpgrade = true
            chatLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            while (isRecordingVoice) {
                delay(1000L)
                recordingSeconds += 1L
                if (recordingSeconds >= 58L) {
                    isRecordingVoice = false
                    val payloadRes = voiceRecorder.finishAndEncode()
                    payloadRes.onSuccess { onSendVoiceNote(it) }
                        .onFailure { onShowMessage(it.localizedMessage ?: "No se pudo guardar la nota de voz.") }
                }
            }
        }
    }

    DisposableEffect(chat.id) {
        onDispose {
            voiceRecorder.cancelRecording()
            ChatAudioPlayer.stop()
        }
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
            // Peer Header & Safety Controls
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("chat_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                    val isPeerOnline = peerVisual?.isOnline == true
                    val distPart = peerVisual?.distanceKm?.let {
                        " · A ${LocationHelper.formatCompactDistance(it)}"
                    } ?: ""
                    val headerSubtitle = buildString {
                        if (isPeerOnline) {
                            append("En línea")
                        } else if (peerVisual?.lastSeenAt != null) {
                            append("Últ. vez ${formatRelativeTimestamp(peerVisual.lastSeenAt)}")
                        } else {
                            append(SocialPurpose.fromRaw(chat.purpose).labelEs)
                        }
                        if (distPart.isNotBlank()) {
                            append(distPart)
                        } else if (chat.zoneName.isNotBlank()) {
                            append(" · ${chat.zoneName}")
                        }
                    }

                    AvatarCircle(
                        name = peerName,
                        avatarColor = peerColor,
                        size = 40.dp,
                        verified = true,
                        isOnline = isPeerOnline,
                        photoData = peerVisual?.photoData,
                        photoUrl = peerVisual?.photoUrl,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = peerName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = headerSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPeerOnline) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_safety_menu"),
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
                            if (peerId.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text("Añadir a grupo") },
                                    leadingIcon = { Icon(Icons.Outlined.GroupAdd, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onAddPeerToGroup(peerId, peerName, peerColor.key)
                                    },
                                )
                            }
                            if (peerId.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text("Reportar") },
                                    leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        showReportDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Bloquear persona") },
                                    leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onBlockUser(peerId, peerName)
                                        onBack()
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (liveLocationState.isActive &&
                liveLocationState.targetType == "chat" &&
                liveLocationState.chatId == chat.id
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Compartiendo ubicación en tiempo real · ${DirectMessage.formatDuration(liveLocationState.remainingSec)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onStopLiveLocation,
                            modifier = Modifier.testTag("stop_live_location_banner_button"),
                        ) {
                            Text("Detener", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Message Thread
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (messagesState) {
                    is UiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    is UiState.Error -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = messagesState.message,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    is UiState.Success -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                val mine = msg.senderId == currentUserId
                                SwipeToReplyWrapper(
                                    onSwipeReply = {
                                        editingMessage = null
                                        replyDraft = ReplyDraft(
                                            messageId = msg.id,
                                            senderName = if (mine) "Tú" else msg.senderName.ifBlank { peerName },
                                            previewText = msg.previewLabel(),
                                        )
                                    },
                                ) {
                                    DirectMessageBubble(
                                        message = msg,
                                        isMine = mine,
                                        audioPlaybackState = audioPlaybackState,
                                        onToggleAudio = { target ->
                                            ChatAudioPlayer.togglePlay(context, target.id, target.mediaData)
                                        },
                                        onStopLiveLocation = onStopLiveLocation,
                                        onPreviewImage = { b64 -> fullscreenImageBase64 = b64 },
                                        onOpenLocation = { inspectedLocationMessageId = msg.id },
                                        onLongPressOrSelect = { selectedMessageForActions = msg },
                                        senderPhotoData = if (mine) myPhotoData else peerVisual?.photoData,
                                        senderPhotoUrl = if (mine) myPhotoUrl else peerVisual?.photoUrl,
                                        senderColor = if (mine) myAvatarColor else peerColor,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Composer
            Column(modifier = Modifier.fillMaxWidth()) {
                if (showAttachmentsPanel) {
                        ChatAttachmentOptionsBar(
                            onTakeCameraPhoto = { launchCameraFlow() },
                            onPickImage = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onPickAudio = {
                                audioPickerLauncher.launch(arrayOf("audio/*"))
                            },
                            onOpenLocationPicker = {
                                openWhatsAppLocationPicker()
                            },
                        )
                    }

                    if (showEmojiPicker) {
                        EmojiPickerPanel(
                            onEmojiSelected = { emoji ->
                                if (draft.length + emoji.length <= 1000) {
                                    draft += emoji
                                }
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
                                draft = ""
                            }
                        },
                    )

                    Surface(
                        tonalElevation = 3.dp,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isRecordingVoice) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error),
                                )
                                Text(
                                    text = "Grabando voz ${DirectMessage.formatDuration(recordingSeconds)} / 1:00",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        isRecordingVoice = false
                                        voiceRecorder.cancelRecording()
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("cancel_voice_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Cancelar grabación",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        isRecordingVoice = false
                                        scope.launch {
                                            val payloadRes = voiceRecorder.finishAndEncode()
                                            payloadRes.onSuccess { onSendVoiceNote(it) }
                                                .onFailure {
                                                    onShowMessage(it.localizedMessage ?: "No se pudo guardar el audio.")
                                                }
                                        }
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("send_voice_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "Enviar nota de voz",
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                IconButton(
                                    onClick = {
                                        showEmojiPicker = !showEmojiPicker
                                        if (showEmojiPicker) showAttachmentsPanel = false
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("toggle_emoji_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.EmojiEmotions,
                                        contentDescription = "Emojis",
                                        tint = if (showEmojiPicker) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        showAttachmentsPanel = !showAttachmentsPanel
                                        if (showAttachmentsPanel) showEmojiPicker = false
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("toggle_attachments_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AddCircleOutline,
                                        contentDescription = "Adjuntar imagen, audio o ubicación",
                                        tint = if (showAttachmentsPanel) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                                IconButton(
                                    onClick = { launchCameraFlow() },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("chat_camera_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PhotoCamera,
                                        contentDescription = "Tomar foto con cámara",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedTextField(
                                    value = draft,
                                    onValueChange = { draft = it.take(1000) },
                                    placeholder = { Text(if (editingMessage != null) "Editar mensaje..." else "Mensaje...") },
                                    maxLines = 4,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                    shape = RoundedCornerShape(22.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("direct_message_input"),
                                )
                                if (draft.trim().isEmpty()) {
                                    IconButton(
                                        onClick = {
                                            val hasMic = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO,
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasMic) {
                                                beginVoiceRecording()
                                            } else {
                                                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        ),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("record_voice_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Mic,
                                            contentDescription = "Grabar mensaje de voz",
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            val clean = draft.trim()
                                            if (clean.isNotEmpty()) {
                                                val editing = editingMessage
                                                if (editing != null) {
                                                    val isLatest = messages.lastOrNull()?.id == editing.id
                                                    onEditMessage(editing.id, clean, isLatest)
                                                    editingMessage = null
                                                } else {
                                                    val reply = replyDraft
                                                    onSendMessage(
                                                        clean,
                                                        reply?.messageId,
                                                        reply?.senderName,
                                                        reply?.previewText,
                                                    )
                                                    replyDraft = null
                                                }
                                                draft = ""
                                                showEmojiPicker = false
                                            }
                                        },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("direct_send_button"),
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
            }
        }

    if (showWhatsAppLocationPicker) {
        WhatsAppSendLocationDialog(
            fallbackZone = currentZone,
            myName = myName,
            myAvatarColor = myAvatarColor,
            myPhotoData = myPhotoData,
            myPhotoUrl = myPhotoUrl,
            onDismiss = { showWhatsAppLocationPicker = false },
            onSendCurrentLocation = { point ->
                showWhatsAppLocationPicker = false
                onSendCurrentLocation(point)
            },
            onSendLiveLocation = { mins, comment, point ->
                showWhatsAppLocationPicker = false
                onStartLiveLocation(mins, comment, point)
            },
            onLocationDetected = onLocationDetected,
        )
    }

    inspectedLocationMessageId?.let { targetId ->
        val liveMsg = messages.firstOrNull { it.id == targetId }
        if (liveMsg != null) {
            val mine = liveMsg.senderId == currentUserId
            InAppDynamicLocationDialog(
                message = liveMsg,
                viewerZone = currentZone,
                isMine = mine,
                senderPhotoData = if (mine) myPhotoData else peerVisual?.photoData,
                senderPhotoUrl = if (mine) myPhotoUrl else peerVisual?.photoUrl,
                senderColor = if (mine) myAvatarColor else peerColor,
                onStopLiveLocation = onStopLiveLocation,
                onDismiss = { inspectedLocationMessageId = null },
            )
        }
    }

    selectedMessageForActions?.let { msg ->
        val mine = msg.senderId == currentUserId
        MessageActionsDialog(
            senderName = if (mine) "Tú" else msg.senderName.ifBlank { peerName },
            previewText = msg.previewLabel(),
            canCopy = msg.text.isNotBlank(),
            canEdit = mine && msg.messageType == DirectMessage.TYPE_TEXT,
            canDelete = mine,
            onDismiss = { selectedMessageForActions = null },
            onReply = {
                editingMessage = null
                replyDraft = ReplyDraft(
                    messageId = msg.id,
                    senderName = if (mine) "Tú" else msg.senderName.ifBlank { peerName },
                    previewText = msg.previewLabel(),
                )
            },
            onCopy = {
                if (msg.text.isNotBlank()) {
                    clipboardManager.setText(AnnotatedString(msg.text))
                    onShowMessage("Texto copiado")
                }
            },
            onEdit = {
                replyDraft = null
                editingMessage = msg
                draft = msg.text
            },
            onDelete = { pendingDeleteMessage = msg },
        )
    }

    pendingDeleteMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMessage = null },
            title = { Text("¿Eliminar mensaje?") },
            text = { Text("Se borrará este mensaje para todos en la conversación.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteMessage = null
                        if (editingMessage?.id == msg.id) {
                            editingMessage = null
                            draft = ""
                        }
                        val isLatest = messages.lastOrNull()?.id == msg.id
                        val fallbackLast = if (isLatest) {
                            messages.filter { it.id != msg.id }.lastOrNull()?.previewLabel() ?: "Conversación abierta"
                        } else {
                            null
                        }
                        onDeleteMessage(msg.id, fallbackLast)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.testTag("confirm_delete_message_button"),
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

    fullscreenImageBase64?.let { b64 ->
        FullscreenImageDialog(
            base64Data = b64,
            onDismiss = { fullscreenImageBase64 = null },
        )
    }

    if (showReportDialog && peerId.isNotBlank()) {
        SafetyReportDialog(
            targetName = peerName,
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, details, alsoBlock ->
                showReportDialog = false
                onSubmitReport(peerId, peerName, chat.id, reason, details, alsoBlock)
                if (alsoBlock) onBack()
            },
        )
    }
}
