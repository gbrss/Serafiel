package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AvatarColor
import com.example.model.KnownContact
import com.example.model.ProximityBeacon
import com.example.model.UserProfile
import com.example.model.ZoneLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSafetySheet(
    displayName: String,
    googlePhotoUrl: String = "",
    profile: UserProfile?,
    beacon: ProximityBeacon?,
    blockedProfiles: List<KnownContact> = emptyList(),
    currentZone: ZoneLocation,
    showOnline: Boolean = profile?.showOnline ?: true,
    onToggleShowOnline: (Boolean) -> Unit = {},
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onUploadPhoto: (Uri) -> Unit,
    onUseGooglePhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    onDismiss: () -> Unit,
    onEditProfileBeacon: () -> Unit,
    onUnblockUser: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showGuidelines by remember { mutableStateOf(false) }

    val resolvedName = profile?.displayName?.ifBlank { null }
        ?: beacon?.displayName?.ifBlank { null }
        ?: displayName
    val resolvedColor = AvatarColor.fromKey(profile?.avatarColor ?: beacon?.avatarColor)
    val resolvedPhotoData = profile?.photoData ?: beacon?.photoData
    val resolvedPhotoUrl = profile?.photoUrl ?: beacon?.photoUrl ?: googlePhotoUrl
    val blockedIds = profile?.blockedUserIds.orEmpty()
    val resolvedBlockedProfiles = remember(blockedIds, blockedProfiles) {
        val byId = blockedProfiles.associateBy { it.userId }
        if (blockedIds.isEmpty()) {
            blockedProfiles
        } else {
            blockedIds.map { uid ->
                byId[uid] ?: KnownContact(
                    userId = uid,
                    displayName = "Usuario ${uid.take(8)}...",
                    subtitle = "Persona bloqueada",
                )
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            onUploadPhoto(uri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AvatarCircle(
                    name = resolvedName,
                    avatarColor = resolvedColor,
                    size = 64.dp,
                    verified = true,
                    isOnline = showOnline,
                    photoData = resolvedPhotoData,
                    photoUrl = resolvedPhotoUrl,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = resolvedName,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.height(16.dp),
                        )
                        Text(
                            text = "Cuenta Google autenticada · ${currentZone.zoneName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    label = { Text("Subir foto") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.testTag("profile_upload_photo_chip"),
                )
                if (googlePhotoUrl.isNotBlank() &&
                    (!resolvedPhotoData.isNullOrBlank() || resolvedPhotoUrl != googlePhotoUrl)
                ) {
                    AssistChip(
                        onClick = onUseGooglePhoto,
                        label = { Text("Usar foto Google") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        modifier = Modifier.testTag("profile_google_photo_chip"),
                    )
                }
                if (!resolvedPhotoData.isNullOrBlank() || !resolvedPhotoUrl.isNullOrBlank()) {
                    AssistChip(
                        onClick = onClearPhoto,
                        label = { Text("Usar iniciales") },
                        modifier = Modifier.testTag("profile_clear_photo_chip"),
                    )
                }
            }

            if (!profile?.bio.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = profile!!.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            Button(
                onClick = {
                    onDismiss()
                    onEditProfileBeacon()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("profile_edit_beacon_button"),
            ) {
                Icon(imageVector = Icons.Outlined.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Editar perfil y baliza")
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (showOnline) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline
                            ),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (showOnline) "En línea (círculo verde activo)" else "Estado en línea oculto",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Cuando estás activo, los demás ven un círculo verde en tu foto de perfil.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = showOnline,
                        onCheckedChange = onToggleShowOnline,
                        modifier = Modifier.testTag("profile_online_switch"),
                    )
                }
            }

            MediaQualitySettingsCard()
            AppUpdateCard()

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (hasNotificationPermission) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = if (hasNotificationPermission) {
                            Icons.Outlined.NotificationsActive
                        } else {
                            Icons.Outlined.NotificationsOff
                        },
                        contentDescription = null,
                        tint = if (hasNotificationPermission) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasNotificationPermission) {
                                "Notificaciones activas"
                            } else {
                                "Notificaciones desactivadas"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = if (hasNotificationPermission) {
                                "Recibirás avisos de mensajes nuevos aunque la app esté minimizada."
                            } else {
                                "Activa el permiso para enterarte al instante de nuevos mensajes."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.testTag("profile_notification_button"),
                    ) {
                        Text(if (hasNotificationPermission) "Ajustes" else "Activar")
                    }
                }
            }

            OutlinedButton(
                onClick = { showGuidelines = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Icon(imageVector = Icons.Outlined.Shield, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Guía de convivencia y uso")
            }

            HorizontalDivider()

            Text(
                text = "Personas bloqueadas (${resolvedBlockedProfiles.size})",
                style = MaterialTheme.typography.titleSmall,
            )
            if (resolvedBlockedProfiles.isEmpty()) {
                Text(
                    text = "No tienes usuarios bloqueados actualmente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                resolvedBlockedProfiles.forEach { blocked ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blocked_user_card_${blocked.userId}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AvatarCircle(
                                name = blocked.displayName,
                                avatarColor = AvatarColor.fromKey(blocked.avatarColor),
                                size = 44.dp,
                                verified = false,
                                isOnline = false,
                                photoData = blocked.photoData,
                                photoUrl = blocked.photoUrl,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = blocked.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = blocked.subtitle.ifBlank { "Persona bloqueada" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(
                                onClick = { onUnblockUser(blocked.userId) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("unblock_user_button_${blocked.userId}"),
                            ) {
                                Text("Desbloquear")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button"),
            ) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cerrar sesión")
            }
        }
    }

    if (showGuidelines) {
        CommunityGuidelinesDialog(onDismiss = { showGuidelines = false })
    }
}


/** Calidad de fotos y videos: enviar en HD y descargar en HD automáticamente. */
@Composable
private fun MediaQualitySettingsCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var sendHd by remember { mutableStateOf(com.example.data.ChatMediaSettings.isSendHd(context)) }
    var autoHd by remember { mutableStateOf(com.example.data.ChatMediaSettings.isAutoDownloadHd(context)) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Calidad de fotos y videos", style = MaterialTheme.typography.titleSmall)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enviar en HD", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Fotos hasta 2048 px y videos 720p (máx. 1 min). Usa más datos y tarda más en enviarse.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = sendHd,
                    onCheckedChange = {
                        sendHd = it
                        com.example.data.ChatMediaSettings.setSendHd(context, it)
                    },
                    modifier = Modifier.testTag("settings_send_hd_switch"),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recibir en HD automáticamente", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Descarga sola la versión HD de las fotos que te envían. Si lo apagas, se ve la versión ligera y la HD se baja al abrirla.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoHd,
                    onCheckedChange = {
                        autoHd = it
                        com.example.data.ChatMediaSettings.setAutoDownloadHd(context, it)
                    },
                    modifier = Modifier.testTag("settings_auto_hd_switch"),
                )
            }
        }
    }
}


/** Actualización dentro de la app (última release publicada en GitHub). */
@Composable
private fun AppUpdateCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val update by com.example.data.AppUpdater.state.collectAsStateWithLifecycle()
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Actualizaciones", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Versión instalada: ${com.example.data.AppUpdater.currentVersionName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (update.status) {
                com.example.data.AppUpdater.Status.CHECKING ->
                    Text("Buscando actualizaciones…", style = MaterialTheme.typography.bodyMedium)
                com.example.data.AppUpdater.Status.UP_TO_DATE ->
                    Text("Tienes la última versión.", style = MaterialTheme.typography.bodyMedium)
                com.example.data.AppUpdater.Status.AVAILABLE, com.example.data.AppUpdater.Status.READY -> {
                    val info = update.info
                    Text(
                        text = "Hay una versión nueva (#${info?.build ?: "?"})" +
                            (info?.sizeBytes?.takeIf { it > 0 }?.let { " · ${it / (1024 * 1024)} MB" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                com.example.data.AppUpdater.Status.DOWNLOADING -> {
                    Text("Descargando… ${(update.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { update.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {}
            }
            update.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (update.status) {
                com.example.data.AppUpdater.Status.AVAILABLE, com.example.data.AppUpdater.Status.READY ->
                    Button(
                        onClick = { com.example.data.AppUpdater.downloadAndInstall(context) },
                        modifier = Modifier.testTag("settings_install_update_button"),
                    ) {
                        Text(if (update.status == com.example.data.AppUpdater.Status.READY) "Instalar" else "Descargar e instalar")
                    }
                com.example.data.AppUpdater.Status.DOWNLOADING, com.example.data.AppUpdater.Status.CHECKING -> {}
                else ->
                    OutlinedButton(
                        onClick = { com.example.data.AppUpdater.check() },
                        modifier = Modifier.testTag("settings_check_update_button"),
                    ) {
                        Text("Buscar actualizaciones")
                    }
            }
        }
    }
}
