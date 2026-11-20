package com.example.ui.screens

import com.example.R
import com.example.i18n.tr

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.ChatMediaHelper
import com.example.model.AvatarColor
import com.example.model.ProximityBeacon
import com.example.model.ReportReason
import com.example.model.SocialPurpose
import com.example.model.UserProfile
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

fun SocialPurpose.icon(): ImageVector = when (this) {
    SocialPurpose.FRIENDSHIP -> Icons.Outlined.FavoriteBorder
    SocialPurpose.NETWORKING -> Icons.Outlined.Groups
    SocialPurpose.TOURISM -> Icons.Outlined.Explore
    SocialPurpose.HELP -> Icons.Outlined.Handshake
    SocialPurpose.CASUAL -> Icons.Outlined.ChatBubbleOutline
}

@Composable
fun AvatarCircle(
    name: String,
    avatarColor: AvatarColor,
    size: Dp = 44.dp,
    verified: Boolean = false,
    isOnline: Boolean = false,
    photoData: String? = null,
    photoUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val initials = remember(name) {
        name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifBlank { "S" }
    }
    val decodedBitmap = remember(photoData) {
        if (!photoData.isNullOrBlank()) {
            ChatMediaHelper.decodeBase64Bitmap(photoData)?.asImageBitmap()
        } else {
            null
        }
    }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(avatarColor.bgColor),
            contentAlignment = Alignment.Center,
        ) {
            if (decodedBitmap != null) {
                Image(
                    bitmap = decodedBitmap,
                    contentDescription = tr(R.string.s_foto_de_perfil_de, name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (!photoUrl.isNullOrBlank()) {
                Text(
                    text = initials,
                    color = avatarColor.contentColor,
                    style = if (size >= 52.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                AsyncImage(
                    model = photoUrl,
                    contentDescription = tr(R.string.s_foto_de_perfil_de, name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = initials,
                    color = avatarColor.contentColor,
                    style = if (size >= 52.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (isOnline) {
            val dotSize = (size * 0.28f).coerceIn(9.dp, 16.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color(0xFF22C55E))
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .testTag("online_indicator_dot"),
            )
        }
        if (verified) {
            val badgeSize = (size * 0.30f).coerceIn(12.dp, 16.dp)
            Box(
                modifier = Modifier
                    .align(if (isOnline) Alignment.TopEnd else Alignment.BottomEnd)
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = tr(R.string.s_perfil_verificado),
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(badgeSize * 0.62f),
                )
            }
        }
    }
}

@Composable
fun PurposeBadge(
    purpose: SocialPurpose,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = purpose.icon(),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = purpose.labelEs,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Hora de envío HH:mm; si el servidor aún no puso la marca de tiempo, usa la hora actual. */
fun formatClockTime(timestamp: Timestamp?): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(timestamp?.toDate() ?: java.util.Date())

fun isSameDay(a: Timestamp?, b: Timestamp?): Boolean {
    val ca = java.util.Calendar.getInstance().apply { time = a?.toDate() ?: java.util.Date() }
    val cb = java.util.Calendar.getInstance().apply { time = b?.toDate() ?: java.util.Date() }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}

/** Etiqueta de día estilo WhatsApp: Hoy, Ayer, día de la semana (esta semana) o fecha. */
/** Fecha con el orden y los textos propios del idioma activo (p. ej. "20 de noviembre", "November 20"). */
private fun localizedDate(skeleton: String, date: java.util.Date): String {
    val locale = Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    return SimpleDateFormat(pattern, locale).format(date)
}

fun formatDayLabel(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: java.util.Date()
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { time = date }
    fun startOfDay(c: java.util.Calendar) = (c.clone() as java.util.Calendar).apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val days = ((startOfDay(now) - startOfDay(then)) / 86_400_000L).toInt()
    return when {
        days <= 0 -> tr(R.string.s_hoy)
        days == 1 -> tr(R.string.s_ayer)
        days < 7 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
            .replaceFirstChar { it.uppercase() }
        now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) ->
            localizedDate("dMMMM", date)
        else -> localizedDate("dMMMMyyyy", date)
    }
}

@androidx.compose.runtime.Composable
fun DaySeparator(timestamp: Timestamp?) {
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        ) {
            androidx.compose.material3.Text(
                text = formatDayLabel(timestamp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

fun formatRelativeTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return tr(R.string.s_ahora)
    val diffMs = System.currentTimeMillis() - timestamp.toDate().time
    val mins = diffMs / 60_000L
    return when {
        mins < 1 -> tr(R.string.s_ahora)
        mins < 60 -> tr(R.string.s_hace_m, mins)
        mins < 1440 -> tr(R.string.s_hace_h, mins / 60)
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(timestamp.toDate())
    }
}

/**
 * "Últ. vez" estilo WhatsApp: hoy/ayer con hora, día de la semana con hora si fue
 * esta semana, y fecha con hora si fue hace más de una semana.
 */
fun formatLastSeen(timestamp: Timestamp?): String {
    if (timestamp == null) return tr(R.string.s_hace_un_momento)
    val date = timestamp.toDate()
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { time = date }
    val diffMs = now.timeInMillis - then.timeInMillis
    if (diffMs < 60_000L) return tr(R.string.s_hace_un_momento)
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    fun dayIndex(c: java.util.Calendar) = c.get(java.util.Calendar.YEAR) * 400 + c.get(java.util.Calendar.DAY_OF_YEAR)
    val daysAgo = run {
        val a = now.clone() as java.util.Calendar
        var d = 0
        while (dayIndex(a) > dayIndex(then) && d < 8) {
            a.add(java.util.Calendar.DAY_OF_YEAR, -1)
            d++
        }
        d
    }
    return when {
        daysAgo == 0 -> tr(R.string.s_hoy_a_las, time)
        daysAgo == 1 -> tr(R.string.s_ayer_a_las, time)
        daysAgo < 7 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(date) + tr(R.string.s_a_las, time)
        now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) ->
            localizedDate("dMMM", date) + tr(R.string.s_a_las, time)
        else -> localizedDate("dMMMyyyy", date) + tr(R.string.s_a_las, time)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeaconEditorSheet(
    initialName: String,
    googlePhotoUrl: String = "",
    existingProfile: UserProfile?,
    existingBeacon: ProximityBeacon?,
    currentRadiusKm: Int = 50,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        displayName: String,
        bio: String,
        avatarColor: AvatarColor,
        preferredPurposes: List<SocialPurpose>,
        beaconPurpose: SocialPurpose,
        beaconStatus: String,
        languages: List<String>,
        beaconActive: Boolean,
        radiusKm: Int,
        photoData: String,
        photoUrl: String,
        showOnline: Boolean,
    ) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var displayName by remember {
        mutableStateOf(
            existingBeacon?.displayName?.ifBlank { null }
                ?: existingProfile?.displayName?.ifBlank { null }
                ?: initialName
        )
    }
    var bio by remember { mutableStateOf(existingProfile?.bio ?: "") }
    var selectedColor by remember {
        mutableStateOf(AvatarColor.fromKey(existingBeacon?.avatarColor ?: existingProfile?.avatarColor))
    }
    var editedPhotoData by remember {
        mutableStateOf(existingProfile?.photoData ?: existingBeacon?.photoData ?: "")
    }
    var editedPhotoUrl by remember {
        mutableStateOf(existingProfile?.photoUrl ?: existingBeacon?.photoUrl ?: googlePhotoUrl)
    }
    var photoStatusMessage by remember { mutableStateOf<String?>(null) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val res = ChatMediaHelper.compressAvatarToBase64(context, uri)
                res.onSuccess { b64 ->
                    editedPhotoData = b64
                    editedPhotoUrl = ""
                    photoStatusMessage = tr(R.string.s_foto_personalizada_lista_para_guardar)
                }.onFailure { err ->
                    photoStatusMessage = err.localizedMessage ?: tr(R.string.s_no_se_pudo_procesar_la)
                }
            }
        }
    }

    var selectedPurpose by remember {
        mutableStateOf(SocialPurpose.fromRaw(existingBeacon?.purpose))
    }
    var statusText by remember {
        mutableStateOf(existingBeacon?.statusText ?: tr(R.string.s_abierto_a_saludar_y_compartir))
    }
    var languagesText by remember {
        mutableStateOf((existingBeacon?.languages ?: listOf(tr(R.string.s_espanol))).joinToString(", "))
    }
    var isActive by remember { mutableStateOf(existingBeacon?.active ?: true) }
    var showOnlineState by remember {
        mutableStateOf(existingProfile?.showOnline ?: existingBeacon?.online ?: true)
    }
    var radiusKm by remember {
        mutableIntStateOf((existingBeacon?.radiusKm?.toInt() ?: currentRadiusKm).coerceIn(1, ProximityBeacon.GLOBAL_RADIUS_KM))
    }
    var nameError by remember { mutableStateOf<String?>(null) }

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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AvatarCircle(
                    name = displayName.ifBlank { "S" },
                    avatarColor = selectedColor,
                    size = 60.dp,
                    verified = true,
                    isOnline = showOnlineState,
                    photoData = editedPhotoData,
                    photoUrl = editedPhotoUrl,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (existingBeacon == null) tr(R.string.s_enciende_tu_baliza) else tr(R.string.s_edita_tu_perfil_y_baliza),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = tr(R.string.s_personaliza_tu_foto_nombre_y),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = tr(R.string.s_imagen_de_perfil),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                avatarPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("upload_avatar_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(tr(R.string.s_subir_editar_foto))
                        }
                        if (googlePhotoUrl.isNotBlank()) {
                            FilterChip(
                                selected = editedPhotoData.isBlank() && editedPhotoUrl == googlePhotoUrl,
                                onClick = {
                                    editedPhotoData = ""
                                    editedPhotoUrl = googlePhotoUrl
                                    photoStatusMessage = tr(R.string.s_usando_foto_predeterminada_de_google)
                                },
                                label = { Text(tr(R.string.s_foto_de_google)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                modifier = Modifier.testTag("use_google_photo_chip"),
                            )
                        }
                        FilterChip(
                            selected = editedPhotoData.isBlank() && editedPhotoUrl.isBlank(),
                            onClick = {
                                editedPhotoData = ""
                                editedPhotoUrl = ""
                                photoStatusMessage = tr(R.string.s_usando_solo_iniciales_y_halo)
                            },
                            label = { Text(tr(R.string.s_solo_iniciales)) },
                            modifier = Modifier.testTag("use_initials_chip"),
                        )
                    }
                    if (!photoStatusMessage.isNullOrBlank()) {
                        Text(
                            text = photoStatusMessage!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = {
                    displayName = it.take(40)
                    nameError = null
                },
                label = { Text(tr(R.string.s_nombre_visible)) },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("beacon_name_input"),
            )

            Text(
                text = tr(R.string.s_color_de_tu_halo),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AvatarColor.entries.forEach { colorOption ->
                    val selected = colorOption == selectedColor
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colorOption.bgColor)
                            .then(
                                if (selected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { selectedColor = colorOption }
                            .testTag("avatar_color_${colorOption.key}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = colorOption.labelEs,
                                tint = colorOption.contentColor,
                            )
                        }
                    }
                }
            }

            Text(
                text = tr(R.string.s_proposito_actual),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SocialPurpose.entries.forEach { purpose ->
                    FilterChip(
                        selected = purpose == selectedPurpose,
                        onClick = { selectedPurpose = purpose },
                        label = { Text(purpose.labelEs) },
                        leadingIcon = {
                            Icon(
                                imageVector = purpose.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.testTag("beacon_purpose_${purpose.name.lowercase()}"),
                    )
                }
            }

            OutlinedTextField(
                value = statusText,
                onValueChange = { statusText = it.take(160) },
                label = { Text(tr(R.string.s_mensaje_de_estado_opcional)) },
                placeholder = { Text(tr(R.string.s_ej_tomando_un_cafe_cerca)) },
                minLines = 2,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("beacon_status_input"),
            )

            OutlinedTextField(
                value = languagesText,
                onValueChange = { languagesText = it.take(60) },
                label = { Text(tr(R.string.s_idiomas_separados_por_coma)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it.take(200) },
                label = { Text(tr(R.string.s_sobre_ti_opcional)) },
                minLines = 2,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                val isGlobalRadius = radiusKm > ProximityBeacon.MAX_LOCAL_RADIUS_KM
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = tr(R.string.s_radio_de_alcance_de_tu),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = if (isGlobalRadius) tr(R.string.s_1000_km_todo_el_mundo) else "$radiusKm km",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = tr(R.string.s_amplia_hasta_1000_km_o),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = radiusKm.coerceIn(1, ProximityBeacon.MAX_LOCAL_RADIUS_KM).toFloat(),
                        onValueChange = { radiusKm = it.roundToInt().coerceIn(1, 1000) },
                        valueRange = 1f..1000f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_radius_slider"),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(10, 50, 250, 500, 1000).forEach { preset ->
                            FilterChip(
                                selected = !isGlobalRadius && radiusKm == preset,
                                onClick = { radiusKm = preset },
                                label = { Text("$preset km") },
                                modifier = Modifier.testTag("editor_radius_preset_$preset"),
                            )
                        }
                        FilterChip(
                            selected = isGlobalRadius,
                            onClick = { radiusKm = ProximityBeacon.GLOBAL_RADIUS_KM },
                            label = { Text(tr(R.string.s_1000_km_todo_el_mundo)) },
                            modifier = Modifier.testTag("editor_radius_preset_global"),
                        )
                    }
                }
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr(R.string.s_baliza_visible_en_el_radar),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = tr(R.string.s_muestra_tu_perfil_y_tu),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        modifier = Modifier.testTag("beacon_active_switch"),
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (showOnlineState) androidx.compose.ui.graphics.Color(0xFF22C55E)
                                else MaterialTheme.colorScheme.outline
                            ),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr(R.string.s_mostrar_estado_en_linea),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = tr(R.string.s_muestra_un_circulo_verde_en),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = showOnlineState,
                        onCheckedChange = { showOnlineState = it },
                        modifier = Modifier.testTag("beacon_show_online_switch"),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Text(tr(R.string.s_cancelar))
                }
                Button(
                    onClick = {
                        val trimmed = displayName.trim()
                        if (trimmed.length < 2) {
                            nameError = tr(R.string.s_escribe_al_menos_2_caracteres)
                        } else {
                            val parsedLangs = languagesText.split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .take(5)
                                .ifEmpty { listOf(tr(R.string.s_espanol)) }
                            onSave(
                                trimmed,
                                bio.trim(),
                                selectedColor,
                                listOf(selectedPurpose),
                                selectedPurpose,
                                statusText.trim(),
                                parsedLangs,
                                isActive,
                                radiusKm,
                                editedPhotoData,
                                editedPhotoUrl,
                                showOnlineState,
                            )
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("save_beacon_button"),
                ) {
                    Text(tr(R.string.s_guardar_baliza))
                }
            }
        }
    }
}

@Composable
fun SafetyReportDialog(
    targetName: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: ReportReason, details: String, alsoBlock: Boolean) -> Unit,
) {
    var selectedReason by remember { mutableStateOf(ReportReason.HARASSMENT) }
    var details by remember { mutableStateOf("") }
    var alsoBlock by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(tr(R.string.s_reportar_a, targetName)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = tr(R.string.s_serafiel_es_un_espacio_seguro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ReportReason.entries.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                        )
                        Text(
                            text = reason.labelEs,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(500) },
                    label = { Text(tr(R.string.s_detalles_adicionales_opcional)) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { alsoBlock = !alsoBlock }
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = alsoBlock,
                        onCheckedChange = { alsoBlock = it },
                    )
                    Text(
                        text = tr(R.string.s_bloquear_tambien_a_esta_persona),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, details, alsoBlock) },
                modifier = Modifier.testTag("confirm_report_button"),
            ) {
                Text(tr(R.string.s_enviar_reporte))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(R.string.s_cancelar))
            }
        },
    )
}

@Composable
fun CommunityGuidelinesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        title = { Text(tr(R.string.s_convivencia_y_seguridad_en_serafiel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = tr(R.string.s_1_espacio_abierto_y_no),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = tr(R.string.s_2_app_abierta_y_transparente),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = tr(R.string.s_3_control_total_puedes_activar),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(tr(R.string.s_entendido))
            }
        },
    )
}
