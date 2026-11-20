package com.example.ui.screens

import com.example.i18n.tr

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.LocationHelper
import com.example.model.AvatarColor
import com.example.model.PeerVisualInfo
import com.example.model.ProximityBeacon
import com.example.model.ReportReason
import com.example.model.SocialPurpose
import com.example.model.UiState
import com.example.model.ZoneLocation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun RadarScreen(
    currentZone: ZoneLocation,
    hasLocationPermission: Boolean,
    hasNotificationPermission: Boolean,
    isRefreshingLocation: Boolean,
    myBeacon: ProximityBeacon?,
    radiusKm: Int,
    outsideRadiusCount: Int,
    nearbyBeaconsState: UiState<List<ProximityBeacon>>,
    peerVisualMap: Map<String, PeerVisualInfo> = emptyMap(),
    selectedPurposeFilter: SocialPurpose?,
    onRequestLocationRefresh: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSelectManualZone: (ZoneLocation) -> Unit,
    onToggleBeaconActive: (Boolean) -> Unit,
    onRadiusChange: (Int, Boolean) -> Unit,
    onOpenBeaconEditor: () -> Unit,
    onSelectPurposeFilter: (SocialPurpose?) -> Unit,
    onSendGreeting: (ProximityBeacon, String) -> Unit,
    onAddToGroup: (ProximityBeacon) -> Unit = {},
    onBlockUser: (String, String) -> Unit,
    onSubmitReport: (ProximityBeacon, ReportReason, String, Boolean) -> Unit,
    onGoToZoneRoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var greetingTarget by remember { mutableStateOf<ProximityBeacon?>(null) }
    var inspectTarget by remember { mutableStateOf<ProximityBeacon?>(null) }
    var reportTarget by remember { mutableStateOf<ProximityBeacon?>(null) }
    var showZonePickerDialog by remember { mutableStateOf(false) }

    val allBeacons = (nearbyBeaconsState as? UiState.Success)?.data.orEmpty()
    var onlyAvailableNow by remember { mutableStateOf(false) }
    var languageFilter by remember { mutableStateOf<String?>(null) }
    val availableLanguages = remember(allBeacons) {
        allBeacons.flatMap { it.languages }.map { it.trim() }.filter { it.isNotEmpty() }
            .groupingBy { it.lowercase() }.eachCount().entries.sortedByDescending { it.value }.take(8)
            .map { e -> allBeacons.flatMap { it.languages }.first { l -> l.trim().lowercase() == e.key }.trim() }
    }
    val beaconsList = allBeacons.filter { b ->
        (!onlyAvailableNow || b.isCurrentlyOnline()) &&
            (languageFilter == null || b.languages.any { it.trim().equals(languageFilter, ignoreCase = true) })
    }
    val myBeaconActive = myBeacon?.active ?: false

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Zone & Privacy Header Card
            item {
                ZoneHeaderCard(
                    currentZone = currentZone,
                    hasLocationPermission = hasLocationPermission,
                    isRefreshingLocation = isRefreshingLocation,
                    onRefreshGps = onRequestLocationRefresh,
                    onPickZone = { showZonePickerDialog = true },
                )
            }

            if (!hasNotificationPermission) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr(R.string.s_activa_las_notificaciones),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = tr(R.string.s_recibe_avisos_de_mensajes_nuevos),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                )
                            }
                            Button(
                                onClick = onRequestNotificationPermission,
                                modifier = Modifier.testTag("enable_notifications_banner_button"),
                            ) {
                                Text(tr(R.string.s_activar))
                            }
                        }
                    }
                }
            }

            // 2. Animated Proximity Radar Canvas + Own Beacon Status + Radius Control
            item {
                RadarVisualizerCard(
                    myBeacon = myBeacon,
                    myBeaconActive = myBeaconActive,
                    radiusKm = radiusKm,
                    nearbyBeacons = beaconsList,
                    currentZone = currentZone,
                    onToggleActive = onToggleBeaconActive,
                    onRadiusChange = onRadiusChange,
                    onEditBeacon = onOpenBeaconEditor,
                    onInspectBeacon = { beacon -> inspectTarget = beacon },
                )
            }

            // 3. Purpose Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val isGlobal = radiusKm > ProximityBeacon.MAX_LOCAL_RADIUS_KM
                        Text(
                            text = if (isGlobal) {
                                tr(R.string.s_personas_en_todo_el_mundo, beaconsList.size)
                            } else {
                                tr(R.string.s_personas_activas_en_km, radiusKm, beaconsList.size)
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (outsideRadiusCount > 0) {
                            Text(
                                text = tr(R.string.s_mas_lejos, outsideRadiusCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { onRadiusChange(ProximityBeacon.GLOBAL_RADIUS_KM, true) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedPurposeFilter == null,
                            onClick = { onSelectPurposeFilter(null) },
                            label = { Text(tr(R.string.s_todos)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            modifier = Modifier.testTag("filter_all"),
                        )
                        SocialPurpose.entries.forEach { purpose ->
                            FilterChip(
                                selected = selectedPurposeFilter == purpose,
                                onClick = {
                                    onSelectPurposeFilter(
                                        if (selectedPurposeFilter == purpose) null else purpose
                                    )
                                },
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
                                modifier = Modifier.testTag("filter_${purpose.name.lowercase()}"),
                            )
                        }
                    }
                }
            }

            // 3b. Filtros extra: disponible ahora e idioma
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = onlyAvailableNow,
                        onClick = { onlyAvailableNow = !onlyAvailableNow },
                        label = { Text(tr(R.string.radar_filter_available_now)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.testTag("filter_available_now"),
                    )
                    availableLanguages.forEach { lang ->
                        FilterChip(
                            selected = languageFilter.equals(lang, ignoreCase = true),
                            onClick = { languageFilter = if (languageFilter.equals(lang, ignoreCase = true)) null else lang },
                            label = { Text(lang) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            modifier = Modifier.testTag("filter_lang_${lang.lowercase()}"),
                        )
                    }
                }
            }

            // 4. Nearby Beacons Feed
            when (nearbyBeaconsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = nearbyBeaconsState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    if (beaconsList.isEmpty()) {
                        item {
                            EmptyRadarCard(
                                hasBeacon = myBeacon != null && myBeacon.active,
                                radiusKm = radiusKm,
                                outsideRadiusCount = outsideRadiusCount,
                                onExpandRadiusToMax = { onRadiusChange(ProximityBeacon.GLOBAL_RADIUS_KM, true) },
                                onEditBeacon = onOpenBeaconEditor,
                                onGoToZoneRoom = onGoToZoneRoom,
                            )
                        }
                    } else {
                        items(beaconsList, key = { it.userId }) { beacon ->
                            val peerHasCoords = beacon.approxLat != 0.0 || beacon.approxLon != 0.0
                            val distanceLabel = if (currentZone.hasValidCoords && peerHasCoords) {
                                val km = LocationHelper.distanceKm(
                                    currentZone.approxLat,
                                    currentZone.approxLon,
                                    beacon.approxLat,
                                    beacon.approxLon,
                                )
                                LocationHelper.formatApproximateDistance(
                                    km = km,
                                    sameZone = beacon.zoneKey == currentZone.zoneKey,
                                )
                            } else if (!currentZone.hasValidCoords) {
                                tr(R.string.s_calculando_distancia)
                            } else {
                                tr(R.string.s_ubicacion_pendiente)
                            }
                            val isPeerOnline = peerVisualMap[beacon.userId]?.isOnline ?: beacon.isCurrentlyOnline()
                            BeaconCard(
                                beacon = beacon,
                                isOnline = isPeerOnline,
                                distanceLabel = distanceLabel,
                                onInspect = { inspectTarget = beacon },
                                onGreet = { greetingTarget = beacon },
                                onAddToGroup = { onAddToGroup(beacon) },
                                onReport = { reportTarget = beacon },
                                onBlock = { onBlockUser(beacon.userId, beacon.displayName) },
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    inspectTarget?.let { peer ->
        val peerHasCoords = peer.approxLat != 0.0 || peer.approxLon != 0.0
        val distanceLabel = if (currentZone.hasValidCoords && peerHasCoords) {
            val km = LocationHelper.distanceKm(
                currentZone.approxLat,
                currentZone.approxLon,
                peer.approxLat,
                peer.approxLon,
            )
            LocationHelper.formatApproximateDistance(
                km = km,
                sameZone = peer.zoneKey == currentZone.zoneKey,
            )
        } else if (!currentZone.hasValidCoords) {
            tr(R.string.s_calculando_distancia)
        } else {
            tr(R.string.s_ubicacion_pendiente)
        }
        val isPeerOnline = peerVisualMap[peer.userId]?.isOnline ?: peer.isCurrentlyOnline()
        ActivePersonDetailDialog(
            beacon = peer,
            isOnline = isPeerOnline,
            distanceLabel = distanceLabel,
            onDismiss = { inspectTarget = null },
            onGreet = {
                inspectTarget = null
                greetingTarget = peer
            },
            onAddToGroup = {
                inspectTarget = null
                onAddToGroup(peer)
            },
            onReport = {
                inspectTarget = null
                reportTarget = peer
            },
            onBlock = {
                inspectTarget = null
                onBlockUser(peer.userId, peer.displayName)
            },
        )
    }

    greetingTarget?.let { peer ->
        GreetingDialog(
            beacon = peer,
            onDismiss = { greetingTarget = null },
            onSend = { msg ->
                greetingTarget = null
                onSendGreeting(peer, msg)
            },
        )
    }

    reportTarget?.let { peer ->
        SafetyReportDialog(
            targetName = peer.displayName,
            onDismiss = { reportTarget = null },
            onSubmit = { reason, details, alsoBlock ->
                reportTarget = null
                onSubmitReport(peer, reason, details, alsoBlock)
            },
        )
    }

    if (showZonePickerDialog) {
        ZonePickerDialog(
            currentZone = currentZone,
            onDismiss = { showZonePickerDialog = false },
            onSelectZone = { zone ->
                showZonePickerDialog = false
                onSelectManualZone(zone)
            },
        )
    }
}

@Composable
private fun ZoneHeaderCard(
    currentZone: ZoneLocation,
    hasLocationPermission: Boolean,
    isRefreshingLocation: Boolean,
    onRefreshGps: () -> Unit,
    onPickZone: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPickZone() },
            ) {
                Text(
                    text = currentZone.zoneName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (!currentZone.hasValidCoords) {
                        if (isRefreshingLocation) {
                            tr(R.string.s_localizando_gps_de_alta_precision)
                        } else if (!hasLocationPermission) {
                            tr(R.string.s_activa_el_gps_para_medir)
                        } else {
                            tr(R.string.s_toca_el_icono_gps_para)
                        }
                    } else if (currentZone.isFromGps) {
                        val accText = currentZone.accuracyMeters?.let { acc ->
                            if (acc >= 300) tr(R.string.s_precision_aprox_m_toca_gps, acc)
                            else tr(R.string.s_gps_preciso_m_distancia_en, acc)
                        } ?: tr(R.string.s_gps_activo_distancia_en_m)
                        tr(R.string.s_toca_para_cambiar, accText)
                    } else {
                        tr(R.string.s_zona_seleccionada_distancia_abierta_toca)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onRefreshGps,
                enabled = !isRefreshingLocation,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("refresh_location_button"),
            ) {
                if (isRefreshingLocation) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.MyLocation,
                        contentDescription = stringResource(R.string.cd_refresh_location),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarVisualizerCard(
    myBeacon: ProximityBeacon?,
    myBeaconActive: Boolean,
    radiusKm: Int,
    nearbyBeacons: List<ProximityBeacon>,
    currentZone: ZoneLocation,
    onToggleActive: (Boolean) -> Unit,
    onRadiusChange: (Int, Boolean) -> Unit,
    onEditBeacon: () -> Unit,
    onInspectBeacon: (ProximityBeacon) -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radarPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseProgress",
    )

    val isGlobalRadius = radiusKm > ProximityBeacon.MAX_LOCAL_RADIUS_KM
    var sliderRadiusKm by remember(radiusKm) {
        mutableIntStateOf(radiusKm.coerceIn(1, ProximityBeacon.MAX_LOCAL_RADIUS_KM))
    }
    val presetRadii = remember { listOf(5, 25, 100, 500, 1000) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(235.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = if (myBeaconActive) 0.16f else 0.06f),
                                secondaryColor.copy(alpha = 0.07f),
                                Color.Transparent,
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(nearbyBeacons, sliderRadiusKm, isGlobalRadius, currentZone) {
                            detectTapGestures { tapOffset ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val centerX = w / 2f
                                val centerY = h / 2f
                                val maxRadius = minOf(w, h) * 0.42f
                                val effectiveRadiusKm = if (isGlobalRadius) {
                                    20_000.0
                                } else {
                                    sliderRadiusKm.toDouble().coerceAtLeast(1.0)
                                }
                                val hit = nearbyBeacons.take(12).mapIndexedNotNull { idx, beacon ->
                                    val angleRad = ((abs(beacon.userId.hashCode()) % 360) + idx * 31) * (PI / 180.0)
                                    val peerHasCoords = beacon.approxLat != 0.0 || beacon.approxLon != 0.0
                                    val distFraction = if (currentZone.hasValidCoords && peerHasCoords) {
                                        val km = LocationHelper.distanceKm(
                                            currentZone.approxLat,
                                            currentZone.approxLon,
                                            beacon.approxLat,
                                            beacon.approxLon,
                                        )
                                        (0.28f + (km.coerceIn(0.0, effectiveRadiusKm) / effectiveRadiusKm).toFloat() * 0.64f)
                                    } else {
                                        0.55f
                                    }
                                    val bx = centerX + (maxRadius * distFraction * cos(angleRad)).toFloat()
                                    val by = centerY + (maxRadius * distFraction * sin(angleRad)).toFloat()
                                    val distPx = hypot((tapOffset.x - bx).toDouble(), (tapOffset.y - by).toDouble())
                                    if (distPx <= 56.0) beacon to distPx else null
                                }.minByOrNull { it.second }?.first
                                if (hit != null) {
                                    onInspectBeacon(hit)
                                }
                            }
                        }
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = (minOf(size.width, size.height) * 0.42f)

                    // Subtle concentric radar rings
                    listOf(0.36f, 0.68f, 1.0f).forEach { fraction ->
                        drawCircle(
                            color = outlineColor.copy(alpha = 0.28f),
                            radius = maxRadius * fraction,
                            center = center,
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }

                    // Animated warm pulse wave when visible
                    if (myBeaconActive) {
                        val waveRadius = maxRadius * (0.18f + 0.82f * pulse)
                        drawCircle(
                            color = primaryColor.copy(alpha = (1f - pulse) * 0.45f),
                            radius = waveRadius,
                            center = center,
                            style = Stroke(width = 2.5.dp.toPx()),
                        )
                    }

                    // Center user beacon
                    drawCircle(
                        color = (if (myBeaconActive) primaryColor else tertiaryColor).copy(alpha = 0.22f),
                        radius = 22.dp.toPx(),
                        center = center,
                    )
                    drawCircle(
                        color = if (myBeaconActive) primaryColor else outlineColor,
                        radius = 10.dp.toPx(),
                        center = center,
                    )

                    // Nearby beacons with visible name + distance labels right on the radar
                    val effectiveRadiusKm = if (isGlobalRadius) {
                        20_000.0
                    } else {
                        sliderRadiusKm.toDouble().coerceAtLeast(1.0)
                    }
                    nearbyBeacons.take(12).forEachIndexed { idx, beacon ->
                        val angleRad = ((abs(beacon.userId.hashCode()) % 360) + idx * 31) * (PI / 180.0)
                        val peerHasCoords = beacon.approxLat != 0.0 || beacon.approxLon != 0.0
                        val km = if (currentZone.hasValidCoords && peerHasCoords) {
                            LocationHelper.distanceKm(
                                currentZone.approxLat,
                                currentZone.approxLon,
                                beacon.approxLat,
                                beacon.approxLon,
                            )
                        } else {
                            null
                        }
                        val distFraction = if (km != null) {
                            (0.28f + (km.coerceIn(0.0, effectiveRadiusKm) / effectiveRadiusKm).toFloat() * 0.64f)
                        } else {
                            0.55f
                        }
                        val bx = center.x + (maxRadius * distFraction * cos(angleRad)).toFloat()
                        val by = center.y + (maxRadius * distFraction * sin(angleRad)).toFloat()
                        val peerColor = AvatarColor.fromKey(beacon.avatarColor).bgColor

                        drawCircle(
                            color = peerColor.copy(alpha = 0.24f),
                            radius = 15.dp.toPx(),
                            center = Offset(bx, by),
                        )
                        drawCircle(
                            color = peerColor,
                            radius = 7.5.dp.toPx(),
                            center = Offset(bx, by),
                        )

                        val shortName = beacon.displayName.trim().substringBefore(" ").take(10)
                        val shortDist = km?.let { LocationHelper.formatCompactDistance(it) } ?: tr(R.string.s_gps)
                        val badgeText = "$shortName · $shortDist"
                        val measured = textMeasurer.measure(
                            text = badgeText,
                            style = labelStyle.copy(color = onSurfaceColor),
                        )
                        val padH = 6.dp.toPx()
                        val padV = 2.dp.toPx()
                        val boxW = measured.size.width + padH * 2f
                        val boxH = measured.size.height + padV * 2f
                        val rawLeft = bx - boxW / 2f
                        val rawTop = if (by > center.y) by + 10.dp.toPx() else by - boxH - 10.dp.toPx()
                        val clampedLeft = rawLeft.coerceIn(6.dp.toPx(), (size.width - boxW - 6.dp.toPx()).coerceAtLeast(6.dp.toPx()))
                        val clampedTop = rawTop.coerceIn(6.dp.toPx(), (size.height - boxH - 6.dp.toPx()).coerceAtLeast(6.dp.toPx()))

                        drawRoundRect(
                            color = surfaceColor.copy(alpha = 0.90f),
                            topLeft = Offset(clampedLeft, clampedTop),
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        )
                        drawRoundRect(
                            color = peerColor.copy(alpha = 0.65f),
                            topLeft = Offset(clampedLeft, clampedTop),
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(clampedLeft + padH, clampedTop + padV),
                        )
                    }
                }

                // Active radius badge in top-left corner
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Radar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = if (isGlobalRadius) tr(R.string.s_radio_1000_km_mundo) else tr(R.string.s_radio_km, sliderRadiusKm),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // Tappable quick-inspect badge in corner
                if (nearbyBeacons.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clickable { onInspectBeacon(nearbyBeacons.first()) },
                    ) {
                        Text(
                            text = tr(R.string.s_toca_una_persona_para_ver),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            if (nearbyBeacons.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = tr(R.string.s_ubicacion_en_el_radar, nearbyBeacons.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        nearbyBeacons.forEach { beacon ->
                            val peerHasCoords = beacon.approxLat != 0.0 || beacon.approxLon != 0.0
                            val compactDist = if (currentZone.hasValidCoords && peerHasCoords) {
                                val km = LocationHelper.distanceKm(
                                    currentZone.approxLat,
                                    currentZone.approxLon,
                                    beacon.approxLat,
                                    beacon.approxLon,
                                )
                                LocationHelper.formatCompactDistance(km)
                            } else {
                                tr(R.string.s_gps)
                            }
                            val dotColor = AvatarColor.fromKey(beacon.avatarColor).bgColor
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .clickable { onInspectBeacon(beacon) }
                                    .testTag("radar_peer_chip_${beacon.userId}"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (beacon.isCurrentlyOnline()) Color(0xFF22C55E) else dotColor
                                            ),
                                    )
                                    Text(
                                        text = "${beacon.displayName.substringBefore(" ")} · $compactDist (${beacon.zoneName})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Visibility + Own Beacon Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = if (myBeaconActive) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint = if (myBeaconActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column {
                        Text(
                            text = stringResource(
                                if (myBeaconActive) R.string.beacon_visible_on else R.string.beacon_visible_off
                            ),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = myBeacon?.statusText?.ifBlank { tr(R.string.s_sin_estado_actual) }
                                ?: tr(R.string.s_personaliza_tu_proposito_y_estado),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Switch(
                    checked = myBeaconActive,
                    onCheckedChange = onToggleActive,
                    modifier = Modifier.testTag("radar_visibility_switch"),
                )
            }

            // Adjustable Beacon Radius Control (1 km - 1000 km)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tr(R.string.s_alcance_de_la_baliza),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = if (isGlobalRadius) tr(R.string.s_1000_km_mundo) else "$sliderRadiusKm km",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("beacon_radius_label"),
                            )
                        }
                    }

                    Slider(
                        value = sliderRadiusKm.toFloat(),
                        onValueChange = { raw ->
                            val snapped = raw.roundToInt().coerceIn(1, 1000)
                            sliderRadiusKm = snapped
                            onRadiusChange(snapped, false)
                        },
                        onValueChangeFinished = {
                            onRadiusChange(sliderRadiusKm, true)
                        },
                        valueRange = 1f..1000f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("beacon_radius_slider"),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        presetRadii.forEach { preset ->
                            FilterChip(
                                selected = !isGlobalRadius && sliderRadiusKm == preset,
                                onClick = {
                                    sliderRadiusKm = preset
                                    onRadiusChange(preset, true)
                                },
                                label = { Text("$preset km") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                                modifier = Modifier.testTag("radius_preset_$preset"),
                            )
                        }
                        FilterChip(
                            selected = isGlobalRadius,
                            onClick = {
                                sliderRadiusKm = 1000
                                onRadiusChange(ProximityBeacon.GLOBAL_RADIUS_KM, true)
                            },
                            label = { Text(tr(R.string.s_1000_km_todo_el_mundo)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                            modifier = Modifier.testTag("radius_preset_global"),
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onEditBeacon,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("edit_beacon_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (myBeacon == null) tr(R.string.s_configurar_mi_baliza) else tr(R.string.s_editar_mi_baliza_y_proposito))
            }
        }
    }
}

@Composable
private fun BeaconCard(
    beacon: ProximityBeacon,
    isOnline: Boolean,
    distanceLabel: String,
    onInspect: () -> Unit,
    onGreet: () -> Unit,
    onAddToGroup: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val purpose = SocialPurpose.fromRaw(beacon.purpose)
    val avatarColor = AvatarColor.fromKey(beacon.avatarColor)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInspect() }
            .testTag("beacon_card_${beacon.userId}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AvatarCircle(
                    name = beacon.displayName,
                    avatarColor = avatarColor,
                    size = 48.dp,
                    verified = beacon.verifiedGoogle,
                    isOnline = isOnline,
                    photoData = beacon.photoData,
                    photoUrl = beacon.photoUrl,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = beacon.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (isOnline) {
                            tr(R.string.s_en_linea_2, beacon.zoneName.ifBlank { tr(R.string.s_zona_abierta_2) })
                        } else {
                            beacon.zoneName.ifBlank { tr(R.string.s_zona_abierta_2) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOnline) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PurposeBadge(purpose = purpose)
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("beacon_safety_menu_${beacon.userId}"),
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
                            text = { Text(tr(R.string.s_anadir_a_grupo)) },
                            leadingIcon = { Icon(Icons.Outlined.GroupAdd, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onAddToGroup()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(tr(R.string.s_reportar_perfil)) },
                            leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onReport()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(tr(R.string.s_bloquear_persona)) },
                            leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onBlock()
                            },
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "$distanceLabel · ${beacon.zoneName.ifBlank { tr(R.string.s_zona_abierta) }}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (beacon.statusText.isNotBlank()) {
                Text(
                    text = beacon.statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = beacon.languages.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: tr(R.string.s_espanol),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onGreet,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("greet_button_${beacon.userId}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WavingHand,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(tr(R.string.s_saludar))
                }
            }
        }
    }
}

@Composable
private fun EmptyRadarCard(
    hasBeacon: Boolean,
    radiusKm: Int,
    outsideRadiusCount: Int,
    onExpandRadiusToMax: () -> Unit,
    onEditBeacon: () -> Unit,
    onGoToZoneRoom: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_radar_empty),
                contentDescription = stringResource(R.string.cd_empty_radar),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(MaterialTheme.shapes.large),
            )
            Text(
                text = if (outsideRadiusCount > 0) {
                    tr(R.string.s_no_hay_balizas_dentro_de, radiusKm)
                } else {
                    stringResource(R.string.empty_radar_title)
                },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (outsideRadiusCount > 0) {
                    tr(R.string.s_hay_persona_s_activa_s, outsideRadiusCount)
                } else {
                    stringResource(R.string.empty_radar_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (radiusKm <= ProximityBeacon.MAX_LOCAL_RADIUS_KM) {
                OutlinedButton(
                    onClick = onExpandRadiusToMax,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("expand_radius_1000_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Radar,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(tr(R.string.s_ampliar_a_1000_km_todo))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!hasBeacon) {
                    OutlinedButton(
                        onClick = onEditBeacon,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(tr(R.string.s_encender_baliza))
                    }
                }
                Button(
                    onClick = onGoToZoneRoom,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("empty_go_zone_room_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Forum,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(tr(R.string.s_ir_a_sala_local))
                }
            }
        }
    }
}

@Composable
private fun ActivePersonDetailDialog(
    beacon: ProximityBeacon,
    isOnline: Boolean,
    distanceLabel: String,
    onDismiss: () -> Unit,
    onGreet: () -> Unit,
    onAddToGroup: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    val purpose = SocialPurpose.fromRaw(beacon.purpose)
    val avatarColor = AvatarColor.fromKey(beacon.avatarColor)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AvatarCircle(
                    name = beacon.displayName,
                    avatarColor = avatarColor,
                    size = 48.dp,
                    verified = beacon.verifiedGoogle,
                    isOnline = isOnline,
                    photoData = beacon.photoData,
                    photoUrl = beacon.photoUrl,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = beacon.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isOnline) {
                        Text(
                            text = tr(R.string.s_en_linea_ahora),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF16A34A),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    PurposeBadge(purpose = purpose)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column {
                            Text(
                                text = beacon.zoneName.ifBlank { tr(R.string.s_zona_abierta) },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            val hasCoords = beacon.approxLat != 0.0 || beacon.approxLon != 0.0
                            Text(
                                text = if (hasCoords) {
                                    "$distanceLabel (${String.format(java.util.Locale.US, "%.5f, %.5f", beacon.approxLat, beacon.approxLon)})"
                                } else {
                                    distanceLabel
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
                if (beacon.statusText.isNotBlank()) {
                    Text(
                        text = beacon.statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = tr(R.string.s_idiomas, beacon.languages.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: tr(R.string.s_espanol)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onAddToGroup,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr(R.string.s_anadir_a_un_grupo))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onReport) {
                        Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr(R.string.s_reportar))
                    }
                    TextButton(onClick = onBlock) {
                        Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr(R.string.s_bloquear))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onGreet,
                modifier = Modifier.testTag("detail_greet_button_${beacon.userId}"),
            ) {
                Icon(Icons.Rounded.WavingHand, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr(R.string.s_saludar))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(R.string.s_cerrar))
            }
        },
    )
}

@Composable
private fun GreetingDialog(
    beacon: ProximityBeacon,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    val purpose = SocialPurpose.fromRaw(beacon.purpose)
    var message by remember {
        mutableStateOf(tr(R.string.s_hola_vi_tu_baliza_cerca, beacon.displayName, purpose.labelEs))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.s_saludar_a, beacon.displayName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PurposeBadge(purpose = purpose)
                Text(
                    text = tr(R.string.s_envia_un_primer_mensaje_amable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(500) },
                    label = { Text(tr(R.string.s_tu_saludo)) },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("greeting_message_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(message) },
                enabled = message.trim().isNotEmpty(),
                modifier = Modifier.testTag("send_greeting_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(tr(R.string.s_abrir_chat))
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
fun ZonePickerDialog(
    currentZone: ZoneLocation,
    onDismiss: () -> Unit,
    onSelectZone: (ZoneLocation) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.s_elegir_zona_de_exploracion)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr(R.string.s_puedes_usar_tu_ubicacion_aproximada),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LocationHelper.fallbackZones.forEach { zone ->
                    val selected = zone.zoneKey == currentZone.zoneKey
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectZone(zone) },
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = zone.zoneName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(R.string.s_cerrar))
            }
        },
    )
}
