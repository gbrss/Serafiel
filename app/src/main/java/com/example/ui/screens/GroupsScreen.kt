package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
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
import com.example.model.DirectMessage
import com.example.model.GroupCategory
import com.example.model.GroupMessage
import com.example.model.KnownContact
import com.example.model.PeerVisualInfo
import com.example.model.SocialGroup
import com.example.model.UiState
import com.example.model.ZoneLocation
import com.example.ui.viewmodel.SerafielViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GroupsTabScreen(
    currentUserId: String,
    currentZone: ZoneLocation,
    myName: String,
    myAvatarColor: AvatarColor,
    myPhotoData: String?,
    myPhotoUrl: String?,
    myGroupsState: UiState<List<SocialGroup>>,
    selectedGroupId: String?,
    selectedGroupMessagesState: UiState<List<GroupMessage>>,
    selectedCategoryFilter: GroupCategory?,
    knownContacts: List<KnownContact>,
    peerVisualMap: Map<String, PeerVisualInfo> = emptyMap(),
    liveLocationState: SerafielViewModel.LiveLocationShareState,
    onSelectCategoryFilter: (GroupCategory?) -> Unit,
    onOpenGroup: (String) -> Unit,
    onCloseGroupView: () -> Unit,
    onCreateGroup: (String, GroupCategory, AvatarColor, List<KnownContact>) -> Unit,
    onUpdateGroup: (SocialGroup, String, GroupCategory, AvatarColor, List<KnownContact>) -> Unit,
    onLeaveOrDeleteGroup: (SocialGroup) -> Unit,
    onSendGroupMessage: (SocialGroup, String, String?, String?, String?) -> Unit,
    onEditGroupMessage: (SocialGroup, String, String, Boolean) -> Unit,
    onDeleteGroupMessage: (SocialGroup, String, String?) -> Unit,
    onSendGroupImage: (SocialGroup, Uri) -> Unit,
    onSendGroupVoiceNote: (SocialGroup, EncodedMediaPayload) -> Unit,
    onSendGroupAudioUri: (SocialGroup, Uri) -> Unit,
    onSendGroupCurrentLocation: (SocialGroup, ChatLocationPoint) -> Unit,
    onStartGroupLiveLocation: (SocialGroup, Int, String, ChatLocationPoint) -> Unit,
    onLocationDetected: (ChatLocationPoint) -> Unit = {},
    onStopLiveLocation: () -> Unit,
    onShowMessage: (String) -> Unit,
    onReactToGroupMessage: (SocialGroup, String, String?) -> Unit = { _, _, _ -> },
    mediaUploadLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val groupsList = (myGroupsState as? UiState.Success)?.data.orEmpty()
    val activeGroup = selectedGroupId?.let { id -> groupsList.firstOrNull { it.id == id } }
    var showCreateDialog by remember { mutableStateOf(false) }
    var groupToEdit by remember { mutableStateOf<SocialGroup?>(null) }

    if (activeGroup != null) {
        BackHandler { onCloseGroupView() }
        GroupDetailScreen(
            currentUserId = currentUserId,
            currentZone = currentZone,
            myName = myName,
            myAvatarColor = myAvatarColor,
            myPhotoData = myPhotoData,
            myPhotoUrl = myPhotoUrl,
            group = activeGroup,
            messagesState = selectedGroupMessagesState,
            peerVisualMap = peerVisualMap,
            liveLocationState = liveLocationState,
            onBack = onCloseGroupView,
            onEditGroup = { groupToEdit = activeGroup },
            onLeaveOrDeleteGroup = { onLeaveOrDeleteGroup(activeGroup) },
            onSendMessage = { text, replyId, replySender, replyPreview ->
                onSendGroupMessage(activeGroup, text, replyId, replySender, replyPreview)
            },
            onEditMessage = { msgId, newText, isLatest ->
                onEditGroupMessage(activeGroup, msgId, newText, isLatest)
            },
            onDeleteMessage = { msgId, fallbackLast ->
                onDeleteGroupMessage(activeGroup, msgId, fallbackLast)
            },
            onSendImage = { uri -> onSendGroupImage(activeGroup, uri) },
            onSendVoiceNote = { payload -> onSendGroupVoiceNote(activeGroup, payload) },
            onSendAudioUri = { uri -> onSendGroupAudioUri(activeGroup, uri) },
            onSendCurrentLocation = { point -> onSendGroupCurrentLocation(activeGroup, point) },
            onStartLiveLocation = { mins, comment, point -> onStartGroupLiveLocation(activeGroup, mins, comment, point) },
            onLocationDetected = onLocationDetected,
            onStopLiveLocation = onStopLiveLocation,
            onShowMessage = onShowMessage,
            onReactToMessage = { msgId, emoji -> onReactToGroupMessage(activeGroup, msgId, emoji) },
            mediaUploadLabel = mediaUploadLabel,
            modifier = modifier,
        )
    } else {
        GroupsListScreen(
            currentUserId = currentUserId,
            myGroupsState = myGroupsState,
            selectedCategoryFilter = selectedCategoryFilter,
            onSelectCategoryFilter = onSelectCategoryFilter,
            onOpenGroup = onOpenGroup,
            onOpenCreateDialog = { showCreateDialog = true },
            modifier = modifier,
        )
    }

    if (showCreateDialog) {
        CreateOrEditGroupDialog(
            title = "Crear grupo",
            confirmLabel = "Crear grupo",
            knownContacts = knownContacts,
            peerVisualMap = peerVisualMap,
            initialName = "",
            initialCategory = GroupCategory.FRIENDS,
            initialColor = AvatarColor.AMBER,
            initialMemberIds = emptySet(),
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, category, color, selectedMembers ->
                showCreateDialog = false
                onCreateGroup(name, category, color, selectedMembers)
            },
        )
    }

    groupToEdit?.let { target ->
        CreateOrEditGroupDialog(
            title = "Editar grupo y miembros",
            confirmLabel = "Guardar cambios",
            knownContacts = buildMergedContactList(knownContacts, target, currentUserId, peerVisualMap),
            peerVisualMap = peerVisualMap,
            initialName = target.name,
            initialCategory = GroupCategory.fromKey(target.category),
            initialColor = AvatarColor.fromKey(target.colorKey),
            initialMemberIds = target.memberIds.filter { it != target.creatorId }.toSet(),
            onDismiss = { groupToEdit = null },
            onConfirm = { name, category, color, selectedMembers ->
                groupToEdit = null
                onUpdateGroup(target, name, category, color, selectedMembers)
            },
        )
    }
}

private fun buildMergedContactList(
    knownContacts: List<KnownContact>,
    group: SocialGroup,
    currentUserId: String,
    peerVisualMap: Map<String, PeerVisualInfo>,
): List<KnownContact> {
    val merged = linkedMapOf<String, KnownContact>()
    knownContacts.forEach { merged[it.userId] = it }
    group.memberIds.forEach { uid ->
        if (uid != currentUserId && uid != group.creatorId && uid !in merged) {
            val visual = peerVisualMap[uid]
            merged[uid] = KnownContact(
                userId = uid,
                displayName = group.memberNames[uid] ?: "Miembro",
                avatarColor = group.memberColors[uid] ?: AvatarColor.AMBER.key,
                photoData = visual?.photoData,
                photoUrl = visual?.photoUrl,
                subtitle = "Miembro del grupo",
            )
        }
    }
    return merged.values.sortedBy { it.displayName.lowercase() }
}

@Composable
private fun GroupsListScreen(
    currentUserId: String,
    myGroupsState: UiState<List<SocialGroup>>,
    selectedCategoryFilter: GroupCategory?,
    onSelectCategoryFilter: (GroupCategory?) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenCreateDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (myGroupsState) {
            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = myGroupsState.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is UiState.Success -> {
                val allGroups = myGroupsState.data
                val filteredGroups = if (selectedCategoryFilter == null) {
                    allGroups
                } else {
                    allGroups.filter { it.category == selectedCategoryFilter.key }
                }

                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Tus grupos (${allGroups.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                TextButton(
                                    onClick = onOpenCreateDialog,
                                    modifier = Modifier.testTag("top_create_group_button"),
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Nuevo grupo")
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = selectedCategoryFilter == null,
                                    onClick = { onSelectCategoryFilter(null) },
                                    label = { Text("Todos") },
                                    modifier = Modifier.testTag("group_filter_all"),
                                )
                                GroupCategory.entries.forEach { cat ->
                                    FilterChip(
                                        selected = selectedCategoryFilter == cat,
                                        onClick = {
                                            onSelectCategoryFilter(
                                                if (selectedCategoryFilter == cat) null else cat
                                            )
                                        },
                                        label = { Text(cat.labelEs) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        ),
                                        modifier = Modifier.testTag("group_filter_${cat.key.lowercase()}"),
                                    )
                                }
                            }
                        }
                    }

                    if (filteredGroups.isEmpty()) {
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(26.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Groups,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp),
                                    )
                                    Text(
                                        text = if (allGroups.isEmpty()) {
                                            "Crea grupos para Familia, Amigos o Comunidad"
                                        } else {
                                            "No hay grupos en esta categoría"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        textAlign = TextAlign.Center,
                                    )
                                    Text(
                                        text = "Organiza personas cercanas o tus contactos habituales y chatea con todos compartiendo emojis, fotos, audio y ubicación.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                    Button(
                                        onClick = onOpenCreateDialog,
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("empty_create_group_button"),
                                    ) {
                                        Icon(Icons.Outlined.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Crear mi primer grupo")
                                    }
                                }
                            }
                        }
                    } else {
                        items(filteredGroups, key = { it.id }) { group ->
                            val category = GroupCategory.fromKey(group.category)
                            val groupColor = AvatarColor.fromKey(group.colorKey)
                            val isCreator = group.creatorId == currentUserId

                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenGroup(group.id) }
                                    .testTag("group_card_${group.id}"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    AvatarCircle(
                                        name = group.name,
                                        avatarColor = groupColor,
                                        size = 48.dp,
                                        verified = false,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = group.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                            ) {
                                                Text(
                                                    text = category.labelEs,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                        Text(
                                            text = group.lastMessage.ifBlank { "Sin mensajes aún" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${group.memberIds.size} miembro${if (group.memberIds.size == 1) "" else "s"}" +
                                                if (isCreator) " · Creado por ti" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Text(
                                        text = formatRelativeTimestamp(group.updatedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(76.dp)) }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onOpenCreateDialog,
            icon = { Icon(Icons.Outlined.GroupAdd, contentDescription = null) },
            text = { Text("Crear grupo") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_create_group"),
        )
    }
}

@Composable
fun CreateOrEditGroupDialog(
    title: String,
    confirmLabel: String,
    knownContacts: List<KnownContact>,
    peerVisualMap: Map<String, PeerVisualInfo> = emptyMap(),
    initialName: String,
    initialCategory: GroupCategory,
    initialColor: AvatarColor,
    initialMemberIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, GroupCategory, AvatarColor, List<KnownContact>) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedIds by remember { mutableStateOf(initialMemberIds) }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(50)
                        nameError = null
                    },
                    label = { Text("Nombre del grupo") },
                    placeholder = { Text("Ej: Familia, Amigos del Café...") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_name_input"),
                )

                Text(
                    text = "Categoría",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GroupCategory.entries.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category.labelEs) },
                            modifier = Modifier.testTag("group_category_${category.key.lowercase()}"),
                        )
                    }
                }

                Text(
                    text = "Color del grupo",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AvatarColor.entries.forEach { colorOption ->
                        val selected = colorOption == selectedColor
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colorOption.bgColor)
                                .then(
                                    if (selected) {
                                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { selectedColor = colorOption },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = colorOption.labelEs,
                                    tint = colorOption.contentColor,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Personas a incluir (${selectedIds.size})",
                    style = MaterialTheme.typography.labelLarge,
                )
                if (knownContacts.isEmpty()) {
                    Text(
                        text = "Puedes crear el grupo ahora y añadir personas más tarde desde el Radar o tus Chats.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    knownContacts.forEach { contact ->
                        val checked = contact.userId in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedIds = if (checked) selectedIds - contact.userId
                                    else selectedIds + contact.userId
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedIds = if (isChecked) selectedIds + contact.userId
                                    else selectedIds - contact.userId
                                },
                            )
                            AvatarCircle(
                                name = contact.displayName,
                                avatarColor = AvatarColor.fromKey(contact.avatarColor),
                                size = 34.dp,
                                isOnline = peerVisualMap[contact.userId]?.isOnline == true,
                                photoData = contact.photoData,
                                photoUrl = contact.photoUrl,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (contact.subtitle.isNotBlank()) {
                                    Text(
                                        text = contact.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = name.trim()
                    if (clean.length < 2) {
                        nameError = "Escribe al menos 2 caracteres"
                    } else {
                        val chosen = knownContacts.filter { it.userId in selectedIds }
                        onConfirm(clean, selectedCategory, selectedColor, chosen)
                    }
                },
                modifier = Modifier.testTag("confirm_create_group_button"),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
fun AddToGroupDialog(
    contact: KnownContact,
    currentUserId: String,
    groups: List<SocialGroup>,
    onDismiss: () -> Unit,
    onAddToExistingGroup: (SocialGroup) -> Unit,
    onCreateNewGroupWithContact: (String, GroupCategory) -> Unit,
) {
    val editableGroups = remember(groups, currentUserId) {
        groups.filter { it.creatorId == currentUserId }
    }
    var newGroupName by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf(GroupCategory.FRIENDS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.GroupAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("Añadir a ${contact.displayName} a un grupo") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (editableGroups.isNotEmpty()) {
                    Text(
                        text = "Tus grupos",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    editableGroups.forEach { group ->
                        val alreadyIn = contact.userId in group.memberIds
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (alreadyIn) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAddToExistingGroup(group) },
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AvatarCircle(
                                    name = group.name,
                                    avatarColor = AvatarColor.fromKey(group.colorKey),
                                    size = 34.dp,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(group.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${GroupCategory.fromKey(group.category).labelEs} · ${group.memberIds.size} miembro(s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = if (alreadyIn) "Incluido" else "Añadir",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                Text(
                    text = if (editableGroups.isEmpty()) "Crear un grupo nuevo" else "O crear un grupo nuevo",
                    style = MaterialTheme.typography.labelLarge,
                )
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it.take(50) },
                    label = { Text("Nombre del nuevo grupo") },
                    placeholder = { Text("Ej: Familia, Amigos...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GroupCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = newCategory == cat,
                            onClick = { newCategory = cat },
                            label = { Text(cat.labelEs) },
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        val clean = newGroupName.trim()
                        if (clean.length >= 2) {
                            onCreateNewGroupWithContact(clean, newCategory)
                        }
                    },
                    enabled = newGroupName.trim().length >= 2,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Crear y añadir a ${contact.displayName.substringBefore(" ")}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}

@Composable
private fun GroupDetailScreen(
    currentUserId: String,
    currentZone: ZoneLocation,
    myName: String,
    myAvatarColor: AvatarColor,
    myPhotoData: String?,
    myPhotoUrl: String?,
    group: SocialGroup,
    messagesState: UiState<List<GroupMessage>>,
    peerVisualMap: Map<String, PeerVisualInfo>,
    liveLocationState: SerafielViewModel.LiveLocationShareState,
    onBack: () -> Unit,
    onEditGroup: () -> Unit,
    onLeaveOrDeleteGroup: () -> Unit,
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
    onReactToMessage: (String, String?) -> Unit,
    mediaUploadLabel: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val isCreator = group.creatorId == currentUserId
    val groupColor = AvatarColor.fromKey(group.colorKey)
    val category = GroupCategory.fromKey(group.category)

    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var replyDraft by remember(group.id) { mutableStateOf<ReplyDraft?>(null) }
    var editingMessage by remember(group.id) { mutableStateOf<GroupMessage?>(null) }
    var selectedMessageForActions by remember(group.id) { mutableStateOf<GroupMessage?>(null) }
    var pendingDeleteMessage by remember(group.id) { mutableStateOf<GroupMessage?>(null) }
    var inspectedLocationMessageId by remember(group.id) { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showAttachmentsPanel by remember { mutableStateOf(false) }
    var showWhatsAppLocationPicker by remember { mutableStateOf(false) }
    var fullscreenImageBase64 by remember { mutableStateOf<String?>(null) }
    var fullscreenImageMessage by remember { mutableStateOf<DirectMessage?>(null) }
    var openVideoMessage by remember(group.id) { mutableStateOf<DirectMessage?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val voiceController = rememberVoiceNoteController(
        key = group.id,
        onSend = onSendVoiceNote,
        onShowMessage = onShowMessage,
    )

    val audioPlaybackState by ChatAudioPlayer.playbackState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(messages) { ChatAudioPlayer.updatePlaylist(messages.map { it.toDirectMessageView() }) }

    var searchActive by remember(group.id) { mutableStateOf(false) }
    var searchQuery by remember(group.id) { mutableStateOf("") }
    var searchPos by remember(group.id) { mutableStateOf(0) }
    var jumpToMessageId by remember(group.id) { mutableStateOf<String?>(null) }
    val searchResults = remember(messages, searchQuery, searchActive) {
        if (!searchActive || searchQuery.isBlank()) {
            emptyList()
        } else {
            messages.mapIndexedNotNull { i, m -> if (m.toDirectMessageView().matchesSearch(searchQuery)) i else null }
        }
    }
    LaunchedEffect(searchQuery, searchResults.size) { searchPos = searchResults.lastIndex.coerceAtLeast(0) }
    LaunchedEffect(searchPos, searchResults) {
        searchResults.getOrNull(searchPos)?.let { listState.animateScrollToItem(it) }
    }
    LaunchedEffect(jumpToMessageId) {
        val target = jumpToMessageId ?: return@LaunchedEffect
        val idx = messages.indexOfFirst { it.id == target }
        if (idx >= 0) listState.animateScrollToItem(idx)
        jumpToMessageId = null
    }
    val searchHighlightIndex = searchResults.getOrNull(searchPos) ?: -1
    val pinHandlerState = LocalPinHandler.current

    val mentionCandidates = remember(
        group.id,
        group.memberIds,
        group.memberNames,
        group.memberColors,
        peerVisualMap,
        currentUserId,
    ) {
        group.memberIds
            .filter { it != currentUserId }
            .map { memberId ->
                val name = group.memberNames[memberId]?.takeIf { it.isNotBlank() } ?: "Miembro"
                val colorKey = group.memberColors[memberId] ?: AvatarColor.AMBER.key
                val visual = peerVisualMap[memberId]
                MentionCandidate(
                    userId = memberId,
                    displayName = name,
                    avatarColorKey = colorKey,
                    isOnline = visual?.isOnline == true,
                    photoData = visual?.photoData,
                    photoUrl = visual?.photoUrl,
                    subtitle = visual?.distanceKm?.let { "A ${LocationHelper.formatCompactDistance(it)}" }
                        ?: if (visual?.isOnline == true) "En línea" else "En el grupo",
                )
            }
            .sortedBy { it.displayName.lowercase() }
    }
    val knownMentionNames = remember(group.memberNames, myName) {
        (group.memberNames.values + myName).filter { it.isNotBlank() }.distinct()
    }

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

    var hasRequestedFineUpgrade by remember(group.id) { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val anyGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            LocationHelper.hasAnyLocationPermission(context)
        if (anyGranted) {
            showWhatsAppLocationPicker = true
        } else {
            onShowMessage("Activa el permiso de ubicación para compartir tu ubicación.")
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
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    DisposableEffect(group.id) {
        onDispose {
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
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("group_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                        AvatarCircle(
                            name = group.name,
                            avatarColor = groupColor,
                            size = 40.dp,
                            verified = false,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${category.labelEs} · ${group.memberIds.size} miembro${if (group.memberIds.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("group_menu_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "Opciones del grupo",
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Buscar en el chat") },
                                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        searchActive = true
                                    },
                                )
                                if (isCreator) {
                                    DropdownMenuItem(
                                        text = { Text("Editar grupo y miembros") },
                                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onEditGroup()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(if (isCreator) "Eliminar grupo" else "Salir del grupo") },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onLeaveOrDeleteGroup()
                                    },
                                )
                            }
                        }
                    }

                    // Horizontal member chips strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        group.memberIds.forEach { memberId ->
                            val isSelf = memberId == currentUserId
                            val rawMemberName = group.memberNames[memberId] ?: "Miembro"
                            val memberName = if (isSelf) "Tú" else rawMemberName
                            val memberColor = AvatarColor.fromKey(group.memberColors[memberId])
                            val photos = peerVisualMap[memberId]
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .then(
                                        if (!isSelf) {
                                            Modifier.clickable {
                                                val prefixNeedSpace = draft.text.isNotEmpty() && !draft.text.last().isWhitespace()
                                                val snippet = (if (prefixNeedSpace) " " else "") + "@$rawMemberName "
                                                draft = appendTextAtSelection(draft, snippet, maxLength = 1000)
                                            }
                                        } else {
                                            Modifier
                                        }
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    AvatarCircle(
                                        name = memberName,
                                        avatarColor = memberColor,
                                        size = 22.dp,
                                        isOnline = photos?.isOnline == true,
                                        photoData = photos?.photoData,
                                        photoUrl = photos?.photoUrl,
                                    )
                                    Text(
                                        text = memberName,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (searchActive) {
                ChatSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    resultCount = searchResults.size,
                    position = searchPos,
                    onPrev = { if (searchPos > 0) searchPos-- },
                    onNext = { if (searchPos < searchResults.size - 1) searchPos++ },
                    onClose = { searchActive = false; searchQuery = "" },
                )
            }
            if (group.pinnedMessageId.isNotBlank() && !searchActive) {
                PinnedMessageBanner(
                    preview = group.pinnedPreview,
                    onJump = { jumpToMessageId = group.pinnedMessageId },
                    onUnpin = { pinHandlerState(true, group.id, null, null) },
                )
            }

            if (liveLocationState.isActive &&
                liveLocationState.targetType == "group" &&
                liveLocationState.chatId == group.id
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
                            text = "Ubicación en vivo en el grupo · ${DirectMessage.formatDuration(liveLocationState.remainingSec)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onStopLiveLocation) {
                            Text("Detener", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

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
                        if (messages.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Envía el primer mensaje, foto, audio o ubicación a ${group.name}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                itemsIndexed(messages, key = { _, m -> m.id }) { index, msg ->
                                Column(
                                    modifier = if (index == searchHighlightIndex) {
                                        Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                                    } else {
                                        Modifier
                                    },
                                ) {
                                    if (index == 0 || !isSameDay(messages[index - 1].createdAt, msg.createdAt)) {
                                        DaySeparator(msg.createdAt)
                                    }

                                    val mine = msg.senderId == currentUserId
                                    val senderVisual = peerVisualMap[msg.senderId]
                                    val senderColor = AvatarColor.fromKey(group.memberColors[msg.senderId])
                                    SwipeToReplyWrapper(
                                        onSwipeReply = {
                                            editingMessage = null
                                            replyDraft = ReplyDraft(
                                                messageId = msg.id,
                                                senderName = if (mine) "Tú" else msg.senderName,
                                                previewText = msg.previewLabel(),
                                            )
                                        },
                                    ) {
                                        val msgView = msg.toDirectMessageView()
                                        DirectMessageBubble(
                                            message = msgView,
                                            isMine = mine,
                                            audioPlaybackState = audioPlaybackState,
                                            onToggleAudio = { target ->
                                                ChatAudioPlayer.togglePlayMessage(context, target)
                                            },
                                            onStopLiveLocation = onStopLiveLocation,
                                            onPreviewImage = { b64 ->
                                                fullscreenImageBase64 = b64
                                                fullscreenImageMessage = msgView
                                            },
                                            myUserId = currentUserId,
                                            onToggleReaction = { emoji ->
                                                val mineNow = msg.reactions[currentUserId]
                                                onReactToMessage(msg.id, if (mineNow == emoji) null else emoji)
                                            },
                                            onOpenVideo = { openVideoMessage = msgView },
                                            onOpenLocation = { inspectedLocationMessageId = msg.id },
                                            onLongPressOrSelect = { selectedMessageForActions = msg },
                                            senderLabel = msg.senderName,
                                            senderPhotoData = if (mine) myPhotoData else senderVisual?.photoData,
                                            senderPhotoUrl = if (mine) myPhotoUrl else senderVisual?.photoUrl,
                                            senderColor = if (mine) myAvatarColor else senderColor,
                                            mentionNames = knownMentionNames,
                                        )
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                if (showAttachmentsPanel) {
                    ChatAttachmentOptionsBar(
                        onTakeCameraPhoto = { launchCameraFlow() },
                        onPickImage = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
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
                            draft = appendTextAtSelection(draft, emoji, maxLength = 1000)
                        },
                    )
                }

                val activeMention = remember(draft.text, draft.selection.end) {
                    findActiveMentionQuery(draft.text, draft.selection.end)
                }
                if (activeMention != null) {
                    MentionSuggestionsPanel(
                        candidates = mentionCandidates,
                        query = activeMention.second,
                        onSelect = { candidate ->
                            draft = insertMention(draft, activeMention.first, candidate.displayName)
                        },
                    )
                }

                if (mediaUploadLabel != null) {
                    MediaUploadBanner(mediaUploadLabel)
                }

                ReplyOrEditComposerBanner(
                    replyDraft = replyDraft,
                    editingTextPreview = editingMessage?.text,
                    onCancel = {
                        replyDraft = null
                        if (editingMessage != null) {
                            editingMessage = null
                            draft = TextFieldValue("")
                        }
                    },
                )

                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (voiceController.state == VoiceRecState.LOCKED) {
                        VoiceLockedBar(controller = voiceController)
                    } else {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                          if (voiceController.state == VoiceRecState.HOLDING) {
                            VoiceHoldingContent(voiceController)
                          } else {
                            IconButton(
                                onClick = {
                                    showEmojiPicker = !showEmojiPicker
                                    if (showEmojiPicker) showAttachmentsPanel = false
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("group_toggle_emoji_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.EmojiEmotions,
                                    contentDescription = "Emojis",
                                    tint = if (showEmojiPicker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    showAttachmentsPanel = !showAttachmentsPanel
                                    if (showAttachmentsPanel) showEmojiPicker = false
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("group_toggle_attachments_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AddCircleOutline,
                                    contentDescription = "Adjuntar",
                                    tint = if (showAttachmentsPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { launchCameraFlow() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("group_camera_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = "Tomar foto con cámara",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { incoming ->
                                    draft = if (incoming.text.length <= 1000) incoming else incoming.copy(text = incoming.text.take(1000))
                                },
                                placeholder = { Text(if (editingMessage != null) "Editar mensaje..." else "Mensaje al grupo (@ para etiquetar)...") },
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("group_message_input"),
                            )
                          }
                            if (draft.text.trim().isEmpty() || voiceController.isActive) {
                                VoiceMicButton(controller = voiceController, testTag = "group_record_voice_button")
                            } else {
                                IconButton(
                                    onClick = {
                                        val clean = draft.text.trim()
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
                                            draft = TextFieldValue("")
                                            showEmojiPicker = false
                                        }
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("group_send_button"),
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
            val senderVisual = peerVisualMap[liveMsg.senderId]
            val senderColor = AvatarColor.fromKey(group.memberColors[liveMsg.senderId])
            InAppDynamicLocationDialog(
                message = liveMsg.toDirectMessageView(),
                viewerZone = currentZone,
                isMine = mine,
                senderPhotoData = if (mine) myPhotoData else senderVisual?.photoData,
                senderPhotoUrl = if (mine) myPhotoUrl else senderVisual?.photoUrl,
                senderColor = if (mine) myAvatarColor else senderColor,
                onStopLiveLocation = onStopLiveLocation,
                onDismiss = { inspectedLocationMessageId = null },
                otherLives = activeLiveParticipants(messages.map { it.toDirectMessageView() }) { senderId, senderName ->
                    val isMe = senderId == currentUserId
                    val visual = peerVisualMap[senderId]
                    LiveMapParticipant(
                        userId = senderId,
                        name = if (isMe) myName else senderName.ifBlank { "Usuario" },
                        lat = 0.0,
                        lon = 0.0,
                        color = if (isMe) myAvatarColor else AvatarColor.fromKey(group.memberColors[senderId]),
                        photoData = if (isMe) myPhotoData else visual?.photoData,
                        photoUrl = if (isMe) myPhotoUrl else visual?.photoUrl,
                        isMine = isMe,
                    )
                },
            )
        }
    }

    selectedMessageForActions?.let { msg ->
        val mine = msg.senderId == currentUserId
        val forwardHandler = LocalForwardHandler.current
        MessageActionsDialog(
            onForward = { forwardHandler(msg.toDirectMessageView()) },
            onPin = { pinHandlerState(true, group.id, if (group.pinnedMessageId == msg.id) null else msg.id, msg.previewLabel()) },
            isPinned = group.pinnedMessageId == msg.id,
            senderName = if (mine) "Tú" else msg.senderName,
            previewText = msg.previewLabel(),
            canCopy = msg.text.isNotBlank(),
            canEdit = mine && msg.messageType == DirectMessage.TYPE_TEXT,
            canDelete = mine,
            onDismiss = { selectedMessageForActions = null },
            onReply = {
                editingMessage = null
                replyDraft = ReplyDraft(
                    messageId = msg.id,
                    senderName = if (mine) "Tú" else msg.senderName,
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
                draft = TextFieldValue(msg.text, TextRange(msg.text.length))
            },
            onDelete = { pendingDeleteMessage = msg },
            myReaction = msg.reactions[currentUserId],
            onReact = { emoji -> onReactToMessage(msg.id, emoji) },
        )
    }

    pendingDeleteMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMessage = null },
            title = { Text("¿Eliminar mensaje?") },
            text = { Text("Se eliminará este mensaje para todo el grupo.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteMessage = null
                        if (editingMessage?.id == msg.id) {
                            editingMessage = null
                            draft = TextFieldValue("")
                        }
                        val isLatest = messages.lastOrNull()?.id == msg.id
                        val fallbackLast = if (isLatest) {
                            messages.filter { it.id != msg.id }.lastOrNull()?.let { prev ->
                                "${prev.senderName}: ${prev.previewLabel()}"
                            } ?: "Sin mensajes aún"
                        } else {
                            null
                        }
                        onDeleteMessage(msg.id, fallbackLast)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.testTag("confirm_delete_group_message_button"),
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
            onDismiss = {
                fullscreenImageBase64 = null
                fullscreenImageMessage = null
            },
            hdMessage = fullscreenImageMessage,
        )
    }

    openVideoMessage?.let { videoMsg ->
        VideoPlayerDialog(message = videoMsg, onDismiss = { openVideoMessage = null })
    }
}
