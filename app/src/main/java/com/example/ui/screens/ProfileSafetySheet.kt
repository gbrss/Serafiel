package com.example.ui.screens

import com.example.R
import com.example.i18n.tr

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
import androidx.compose.runtime.LaunchedEffect
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
    ghostUntilMs: Long = 0L,
    locationPrecision: com.example.data.PrivacySettings.Precision = com.example.data.PrivacySettings.Precision.APPROX,
    onSetGhost: (Long?) -> Unit = {},
    onSetPrecision: (com.example.data.PrivacySettings.Precision) -> Unit = {},
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
                    displayName = tr(R.string.s_usuario_2, uid.take(8)),
                    subtitle = tr(R.string.s_persona_bloqueada),
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
                            text = tr(R.string.s_cuenta_google_autenticada, currentZone.zoneName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    label = { Text(tr(R.string.s_subir_foto)) },
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
                        label = { Text(tr(R.string.s_usar_foto_google)) },
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
                        label = { Text(tr(R.string.s_usar_iniciales)) },
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
                Text(tr(R.string.s_editar_perfil_y_baliza))
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
                            text = if (showOnline) tr(R.string.s_en_linea_circulo_verde_activo) else tr(R.string.s_estado_en_linea_oculto),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = tr(R.string.s_cuando_estas_activo_los_demas),
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

            LocationPrivacyCard(
                precision = locationPrecision,
                ghostUntilMs = ghostUntilMs,
                onSetPrecision = onSetPrecision,
                onSetGhost = onSetGhost,
            )
            // Las tarjetas pesadas se componen tras el primer frame para que el panel abra al instante
            var heavyReady by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; heavyReady = true }
            if (heavyReady) {
                AppearanceCard()
                LanguageCard()
                AppLockCard()
                MediaQualitySettingsCard()
                AppUpdateCard()
            }

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
                                tr(R.string.s_notificaciones_activas)
                            } else {
                                tr(R.string.s_notificaciones_desactivadas)
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = if (hasNotificationPermission) {
                                tr(R.string.s_recibiras_avisos_de_mensajes_nuevos)
                            } else {
                                tr(R.string.s_activa_el_permiso_para_enterarte)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.testTag("profile_notification_button"),
                    ) {
                        Text(if (hasNotificationPermission) tr(R.string.s_ajustes) else tr(R.string.s_activar))
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
                Text(tr(R.string.s_guia_de_convivencia_y_uso))
            }

            HorizontalDivider()

            Text(
                text = tr(R.string.s_personas_bloqueadas, resolvedBlockedProfiles.size),
                style = MaterialTheme.typography.titleSmall,
            )
            if (resolvedBlockedProfiles.isEmpty()) {
                Text(
                    text = tr(R.string.s_no_tienes_usuarios_bloqueados_actualmente),
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
                                    text = blocked.subtitle.ifBlank { tr(R.string.s_persona_bloqueada) },
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
                                Text(tr(R.string.s_desbloquear))
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
                Text(tr(R.string.s_cerrar_sesion))
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
            Text(tr(R.string.s_calidad_de_fotos_y_videos), style = MaterialTheme.typography.titleSmall)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr(R.string.s_enviar_en_hd), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = tr(R.string.s_fotos_hasta_2048_px_y),
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
                    Text(tr(R.string.s_recibir_en_hd_automaticamente), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = tr(R.string.s_descarga_sola_la_version_hd),
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
            Text(tr(R.string.s_actualizaciones), style = MaterialTheme.typography.titleSmall)
            Text(
                text = tr(R.string.s_version_instalada, com.example.data.AppUpdater.currentVersionName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (update.status) {
                com.example.data.AppUpdater.Status.CHECKING ->
                    Text(tr(R.string.s_buscando_actualizaciones), style = MaterialTheme.typography.bodyMedium)
                com.example.data.AppUpdater.Status.UP_TO_DATE ->
                    Text(tr(R.string.s_tienes_la_ultima_version), style = MaterialTheme.typography.bodyMedium)
                com.example.data.AppUpdater.Status.AVAILABLE, com.example.data.AppUpdater.Status.READY -> {
                    val info = update.info
                    Text(
                        text = tr(R.string.s_hay_una_version_nueva, info?.build?.let { "1.0.$it" } ?: "?") +
                            (info?.sizeBytes?.takeIf { it > 0 }?.let { tr(R.string.s_mb, it / (1024 * 1024)) } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                com.example.data.AppUpdater.Status.DOWNLOADING -> {
                    Text(tr(R.string.s_descargando_2, (update.progress * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
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
                        Text(if (update.status == com.example.data.AppUpdater.Status.READY) tr(R.string.s_instalar) else tr(R.string.s_descargar_e_instalar))
                    }
                com.example.data.AppUpdater.Status.DOWNLOADING, com.example.data.AppUpdater.Status.CHECKING -> {}
                else ->
                    OutlinedButton(
                        onClick = { com.example.data.AppUpdater.check() },
                        modifier = Modifier.testTag("settings_check_update_button"),
                    ) {
                        Text(tr(R.string.s_buscar_actualizaciones))
                    }
            }
        }
    }
}


/** Precisión de la ubicación en el radar y modo fantasma temporal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationPrivacyCard(
    precision: com.example.data.PrivacySettings.Precision,
    ghostUntilMs: Long,
    onSetPrecision: (com.example.data.PrivacySettings.Precision) -> Unit,
    onSetGhost: (Long?) -> Unit,
) {
    val now = androidx.compose.runtime.produceState(System.currentTimeMillis(), ghostUntilMs) {
        while (true) {
            value = System.currentTimeMillis()
            kotlinx.coroutines.delay(30_000L)
        }
    }.value
    val ghostActive = ghostUntilMs > now
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(tr(R.string.s_privacidad_de_ubicacion), style = MaterialTheme.typography.titleSmall)
            Text(
                text = tr(R.string.s_precision_con_la_que_apareces),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                com.example.data.PrivacySettings.Precision.entries.forEach { p ->
                    androidx.compose.material3.FilterChip(
                        selected = p == precision,
                        onClick = { onSetPrecision(p) },
                        label = { Text(p.label) },
                        modifier = Modifier.testTag("privacy_precision_${p.level}"),
                    )
                }
            }
            Text(
                text = precision.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            Text(tr(R.string.s_modo_fantasma), style = MaterialTheme.typography.titleSmall)
            if (ghostActive) {
                val minsLeft = ((ghostUntilMs - now) / 60_000L).coerceAtLeast(1)
                val label = if (minsLeft >= 60) tr(R.string.s_h_min, minsLeft / 60, minsLeft % 60) else "$minsLeft min"
                Text(
                    text = tr(R.string.s_nadie_te_ve_en_el, label),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { onSetGhost(null) },
                    modifier = Modifier.testTag("ghost_off_button"),
                ) { Text(tr(R.string.s_volver_a_ser_visible)) }
            } else {
                Text(
                    text = tr(R.string.s_desaparece_del_radar_por_un),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    listOf(tr(R.string.s_1_hora) to 1L, tr(R.string.s_8_horas) to 8L, tr(R.string.s_24_horas) to 24L).forEach { (label, hours) ->
                        OutlinedButton(
                            onClick = { onSetGhost(hours * 3_600_000L) },
                            modifier = Modifier.testTag("ghost_${hours}h_button"),
                        ) { Text(label) }
                    }
                }
            }
        }
    }
}


@Composable
private fun LanguageCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
        .filterIsInstance<android.app.Activity>().firstOrNull()
    val current = com.example.i18n.Lang.saved(context)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(R.string.s_idioma), style = MaterialTheme.typography.titleSmall)
            Text(
                text = tr(R.string.s_la_app_se_reiniciara_para),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                val options = listOf(com.example.i18n.Lang.AUTO to tr(R.string.s_automatico)) + com.example.i18n.Lang.supported
                options.forEach { (code, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = code == current,
                        onClick = { if (code != current) activity?.let { com.example.i18n.Lang.change(it, code) } },
                        label = { Text(label) },
                        modifier = Modifier.testTag("language_$code"),
                    )
                }
            }
        }
    }
}
