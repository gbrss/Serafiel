package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.graphics.Rect
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.ChatLocationPoint
import com.example.data.LocationHelper
import com.example.model.AvatarColor
import com.example.model.DirectMessage
import com.example.model.NearbyPlaceSuggestion
import com.example.model.ZoneLocation
import com.example.ui.map.MapAccuracyCircle
import com.example.ui.map.MapLibreCameraState
import com.example.ui.map.MapLibreMapView
import com.example.ui.map.MapLibreStaticPreview
import com.example.ui.map.MapMarker
import com.example.ui.map.rememberMapLibreCameraState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val WhatsAppHeaderTeal = Color(0xFF753506)
private val WhatsAppAccentTeal = Color(0xFFC99200)
private val WhatsAppSendGreen = Color(0xFFFFC928)
private val OnYellowInk = Color(0xFF2A1E00)
private val WhatsAppLiveRed = Color(0xFFD32F2F)

private enum class WhatsAppPickerMode {
    MENU,
    LIVE_CONFIG,
}

@Composable
private fun rememberPreloadedAvatarImageBitmap(
    photoData: String?,
    photoUrl: String?,
): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(photoData, photoUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photoData, photoUrl) {
        bitmap = withContext(Dispatchers.IO) {
            if (!photoData.isNullOrBlank()) {
                runCatching {
                    val bytes = Base64.decode(photoData, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            } else if (!photoUrl.isNullOrBlank()) {
                runCatching {
                    val loader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(photoUrl)
                        .allowHardware(false)
                        .size(128)
                        .build()
                    val result = loader.execute(request) as? SuccessResult
                    (result?.drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
                }.getOrNull()
            } else {
                null
            }
        }
    }
    return bitmap
}

@Composable
private fun MapAvatarPinContent(
    name: String,
    avatarColor: AvatarColor,
    bitmap: ImageBitmap?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White)
                .padding(2.5.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(avatarColor.bgColor),
                    contentAlignment = Alignment.Center,
                ) {
                    val initials = remember(name) {
                        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                        when {
                            parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
                            parts.size == 1 -> parts[0].take(2).uppercase()
                            else -> "S"
                        }
                    }
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleSmall,
                        color = avatarColor.contentColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, WhatsAppAccentTeal, CircleShape),
        )
    }
}

@Composable
private fun InteractiveMapBox(
    cameraState: MapLibreCameraState,
    markerLat: Double,
    markerLon: Double,
    accuracyMeters: Int,
    showAccuracyCircle: Boolean,
    showCenterCrosshairPin: Boolean,
    showStaticMarkerPin: Boolean,
    avatarName: String?,
    avatarColor: AvatarColor,
    avatarPhotoData: String?,
    avatarPhotoUrl: String?,
    onGestureMove: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasValidMarker = markerLat != 0.0 || markerLon != 0.0
    val avatarBitmap = if (avatarName != null) {
        rememberPreloadedAvatarImageBitmap(photoData = avatarPhotoData, photoUrl = avatarPhotoUrl)
    } else {
        null
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        MapLibreMapView(
            state = cameraState,
            onGestureMove = onGestureMove,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (hasValidMarker && showAccuracyCircle) {
                MapAccuracyCircle(
                    state = cameraState,
                    lat = markerLat,
                    lon = markerLon,
                    radiusMeters = accuracyMeters.coerceIn(10, 5_000).toDouble(),
                )
                if (avatarName == null) {
                    MapMarker(
                        state = cameraState,
                        lat = markerLat,
                        lon = markerLon,
                        anchor = Offset(0.5f, 0.5f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E88E5)),
                            )
                        }
                    }
                }
            }

            if (hasValidMarker && avatarName != null) {
                MapMarker(
                    state = cameraState,
                    lat = markerLat,
                    lon = markerLon,
                    anchor = Offset(0.5f, 0.85f),
                ) {
                    MapAvatarPinContent(
                        name = avatarName,
                        avatarColor = avatarColor,
                        bitmap = avatarBitmap,
                    )
                }
            } else if (hasValidMarker && showStaticMarkerPin) {
                MapMarker(
                    state = cameraState,
                    lat = markerLat,
                    lon = markerLon,
                    anchor = Offset(0.5f, 0.9f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = "Ubicación marcada",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
        }

        if (showCenterCrosshairPin) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = "Punto seleccionado en el mapa",
                tint = Color(0xFFE53935),
                modifier = Modifier
                    .size(42.dp)
                    .offset(y = (-16).dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 3.dp,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                IconButton(
                    onClick = { cameraState.zoomIn() },
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Acercar mapa",
                        tint = Color(0xFF374151),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 3.dp,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                IconButton(
                    onClick = { cameraState.zoomOut() },
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Remove,
                        contentDescription = "Alejar mapa",
                        tint = Color(0xFF374151),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private data class VisibleFramePadding(val topPx: Int, val bottomPx: Int)

/**
 * Calcula cuánto del contenedor queda tapado arriba/abajo comparando su posición
 * en pantalla con getWindowVisibleDisplayFrame (que excluye barras del sistema y
 * teclado). Se recalcula en cada layout global, p. ej. al abrir el teclado.
 */
@Composable
private fun rememberVisibleFramePadding(view: View): VisibleFramePadding {
    var padding by remember(view) { mutableStateOf(VisibleFramePadding(0, 0)) }
    DisposableEffect(view) {
        val root = view.rootView
        val frame = Rect()
        val loc = IntArray(2)
        fun recompute() {
            root.getWindowVisibleDisplayFrame(frame)
            root.getLocationOnScreen(loc)
            if (frame.height() <= 0 || root.height <= 0) return
            val top = (frame.top - loc[1]).coerceIn(0, root.height / 3)
            val bottom = (loc[1] + root.height - frame.bottom).coerceIn(0, root.height * 2 / 3)
            val next = VisibleFramePadding(top, bottom)
            if (next != padding) padding = next
        }
        val listener = ViewTreeObserver.OnGlobalLayoutListener { recompute() }
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
        root.post { recompute() }
        onDispose {
            if (root.viewTreeObserver.isAlive) {
                root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
        }
    }
    return padding
}

@Composable
fun WhatsAppSendLocationDialog(
    fallbackZone: ZoneLocation,
    myName: String,
    myAvatarColor: AvatarColor,
    myPhotoData: String?,
    myPhotoUrl: String?,
    onDismiss: () -> Unit,
    onSendCurrentLocation: (ChatLocationPoint) -> Unit,
    onSendLiveLocation: (durationMinutes: Int, comment: String, initialPoint: ChatLocationPoint) -> Unit,
    onLocationDetected: (ChatLocationPoint) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickerMode by remember { mutableStateOf(WhatsAppPickerMode.MENU) }
    var isLoadingLocation by remember { mutableStateOf(true) }
    var isLoadingPlaces by remember { mutableStateOf(false) }
    var diagnosticCode by remember { mutableStateOf<String?>(null) }
    var refreshJob by remember { mutableStateOf<Job?>(null) }

    var gpsPoint by remember {
        mutableStateOf(
            ChatLocationPoint(
                latitude = fallbackZone.approxLat,
                longitude = fallbackZone.approxLon,
                label = fallbackZone.zoneName,
                isFromGps = fallbackZone.isFromGps,
                accuracyMeters = fallbackZone.accuracyMeters ?: 26,
            )
        )
    }

    var mapCenterLat by remember { mutableDoubleStateOf(fallbackZone.approxLat) }
    var mapCenterLon by remember { mutableDoubleStateOf(fallbackZone.approxLon) }
    var userHasPanned by remember { mutableStateOf(false) }

    val hasFallbackCoords = fallbackZone.approxLat != 0.0 || fallbackZone.approxLon != 0.0
    val cameraState = rememberMapLibreCameraState(
        initialLat = if (hasFallbackCoords) fallbackZone.approxLat else -33.4489,
        initialLon = if (hasFallbackCoords) fallbackZone.approxLon else -70.6693,
        initialZoom = 16.0,
    )

    var nearbyPlaces by remember { mutableStateOf<List<NearbyPlaceSuggestion>>(emptyList()) }
    var remoteSearchPlaces by remember { mutableStateOf<List<NearbyPlaceSuggestion>>(emptyList()) }
    var isMapExpanded by remember { mutableStateOf(false) }
    var showSearchField by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var selectedLiveMinutes by remember { mutableIntStateOf(60) }
    var liveComment by remember { mutableStateOf("") }

    fun centerMapOn(lat: Double, lon: Double, zoom: Double? = null, animate: Boolean = true) {
        if (lat == 0.0 && lon == 0.0) return
        val targetZoom = zoom ?: cameraState.zoom.coerceIn(14.0, 18.0)
        if (animate) {
            cameraState.animateTo(lat, lon, targetZoom, durationMs = 450)
        } else {
            cameraState.moveTo(lat, lon, targetZoom)
        }
    }

    fun loadPlacesAt(lat: Double, lon: Double) {
        if (lat == 0.0 && lon == 0.0) return
        scope.launch {
            isLoadingPlaces = true
            nearbyPlaces = LocationHelper.fetchNearbyPlaces(context, lat, lon)
            isLoadingPlaces = false
        }
    }

    fun loadGpsAndPlaces(forceFresh: Boolean = true) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            isLoadingLocation = true
            val point = LocationHelper.fetchChatLocation(
                context = context,
                fallbackZone = fallbackZone,
                forceFresh = forceFresh,
            )
            diagnosticCode = point.diagnosticCode
            gpsPoint = point
            if (point.latitude != 0.0 || point.longitude != 0.0) {
                mapCenterLat = point.latitude
                mapCenterLon = point.longitude
                centerMapOn(point.latitude, point.longitude, zoom = 16.0, animate = true)
            }
            userHasPanned = false
            isLoadingLocation = false
            if (point.isFromGps) {
                onLocationDetected(point)
            }
            loadPlacesAt(point.latitude, point.longitude)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            LocationHelper.hasAnyLocationPermission(context)
        if (granted) {
            loadGpsAndPlaces(forceFresh = true)
        }
    }

    LaunchedEffect(Unit) {
        loadGpsAndPlaces(forceFresh = true)
    }

    // Refine live GPS accuracy continuously while picker is open
    LaunchedEffect(Unit) {
        LocationHelper.observeHighAccuracyLocationFlow(context)
            .conflate()
            .collect { refined ->
                diagnosticCode = refined.diagnosticCode
                val prevHasCoords = gpsPoint.latitude != 0.0 || gpsPoint.longitude != 0.0
                val movedKm = if (prevHasCoords) {
                    LocationHelper.distanceKm(gpsPoint.latitude, gpsPoint.longitude, refined.latitude, refined.longitude)
                } else {
                    1.0
                }
                gpsPoint = refined
                onLocationDetected(refined)
                if (!userHasPanned) {
                    mapCenterLat = refined.latitude
                    mapCenterLon = refined.longitude
                    centerMapOn(refined.latitude, refined.longitude, animate = prevHasCoords)
                }
                if (!prevHasCoords || movedKm > 0.04) {
                    if (!userHasPanned) {
                        loadPlacesAt(refined.latitude, refined.longitude)
                    }
                }
            }
    }

    // Debounced commercial place search when search query changes
    LaunchedEffect(searchQuery, mapCenterLat, mapCenterLon) {
        val clean = searchQuery.trim()
        if (clean.length < 2) {
            remoteSearchPlaces = emptyList()
            return@LaunchedEffect
        }
        delay(320L)
        remoteSearchPlaces = LocationHelper.searchPlacesByName(context, clean, mapCenterLat, mapCenterLon)
    }

    val pannedDistanceMeters = remember(gpsPoint.latitude, gpsPoint.longitude, mapCenterLat, mapCenterLon) {
        if ((gpsPoint.latitude == 0.0 && gpsPoint.longitude == 0.0) ||
            (mapCenterLat == 0.0 && mapCenterLon == 0.0)
        ) {
            0
        } else {
            (LocationHelper.distanceKm(gpsPoint.latitude, gpsPoint.longitude, mapCenterLat, mapCenterLon) * 1000.0).roundToInt()
        }
    }

    val filteredPlaces = remember(nearbyPlaces, remoteSearchPlaces, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) {
            nearbyPlaces
        } else {
            val localMatches = nearbyPlaces.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.address.contains(q, ignoreCase = true) ||
                    it.categoryLabel.contains(q, ignoreCase = true)
            }
            val merged = linkedMapOf<String, NearbyPlaceSuggestion>()
            localMatches.forEach { merged[it.title.lowercase()] = it }
            remoteSearchPlaces.forEach { item ->
                merged.putIfAbsent(item.title.lowercase(), item)
            }
            merged.values.toList()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        val density = LocalDensity.current

        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                WindowInsetsControllerCompat(window, dialogView).isAppearanceLightStatusBars = false
                @Suppress("DEPRECATION")
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            onDispose {}
        }

        // Margen medido contra la zona realmente visible de la pantalla (sin barra de
        // estado, barra de navegación ni teclado). En algunos equipos el Dialog
        // arranca bajo la barra de estado pero mide toda la pantalla, y los insets
        // llegan en 0 o duplicados; medir la geometría real evita ambos casos.
        val visiblePadding = rememberVisibleFramePadding(dialogView)
        val topStatusInsetDp = with(density) { visiblePadding.topPx.toDp() }
        val bottomClearanceDp = with(density) { visiblePadding.bottomPx.toDp() }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("whatsapp_location_dialog"),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomClearanceDp),
            ) {
                // WhatsApp-style top app bar
                Surface(
                    color = WhatsAppHeaderTeal,
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = topStatusInsetDp)
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                if (pickerMode == WhatsAppPickerMode.LIVE_CONFIG) {
                                    pickerMode = WhatsAppPickerMode.MENU
                                } else {
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.testTag("whatsapp_location_back"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Volver",
                                tint = Color.White,
                            )
                        }
                        if (showSearchField) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Buscar comercio o lugar...", color = Color.White.copy(alpha = 0.7f)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color.White,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                searchQuery = ""
                                showSearchField = false
                            }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Cerrar búsqueda", tint = Color.White)
                            }
                        } else {
                            Text(
                                text = "Enviar ubicación",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 6.dp),
                            )
                            if (pickerMode == WhatsAppPickerMode.MENU) {
                                IconButton(onClick = { showSearchField = true }) {
                                    Icon(Icons.Outlined.Search, contentDescription = "Buscar lugar", tint = Color.White)
                                }
                            }
                            IconButton(
                                onClick = { loadGpsAndPlaces(forceFresh = true) },
                                modifier = Modifier.testTag("whatsapp_refresh_location_button"),
                            ) {
                                if (isLoadingLocation) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                    )
                                } else {
                                    Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar ubicación", tint = Color.White)
                                }
                            }
                        }
                    }
                }

                // Interactive MapLibre View (OpenFreeMap)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (pickerMode == WhatsAppPickerMode.LIVE_CONFIG) {
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 140.dp)
                            } else {
                                Modifier.height(if (isMapExpanded) 330.dp else 230.dp)
                            }
                        ),
                ) {
                    InteractiveMapBox(
                        cameraState = cameraState,
                        markerLat = gpsPoint.latitude,
                        markerLon = gpsPoint.longitude,
                        accuracyMeters = gpsPoint.accuracyMeters,
                        showAccuracyCircle = pickerMode == WhatsAppPickerMode.MENU,
                        showCenterCrosshairPin = pickerMode == WhatsAppPickerMode.MENU && userHasPanned && pannedDistanceMeters > 12,
                        showStaticMarkerPin = false,
                        avatarName = if (pickerMode == WhatsAppPickerMode.LIVE_CONFIG) myName else null,
                        avatarColor = myAvatarColor,
                        avatarPhotoData = myPhotoData,
                        avatarPhotoUrl = myPhotoUrl,
                        onGestureMove = { nextLat, nextLon ->
                            userHasPanned = true
                            mapCenterLat = nextLat
                            mapCenterLon = nextLon
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (pickerMode == WhatsAppPickerMode.MENU) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.94f),
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .minimumInteractiveComponentSize(),
                        ) {
                            IconButton(
                                onClick = { isMapExpanded = !isMapExpanded },
                                modifier = Modifier.size(42.dp),
                            ) {
                                Icon(
                                    imageVector = if (isMapExpanded) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                    contentDescription = "Ampliar mapa",
                                    tint = Color(0xFF455A64),
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.94f),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .minimumInteractiveComponentSize(),
                    ) {
                        IconButton(
                            onClick = { loadGpsAndPlaces(forceFresh = true) },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("whatsapp_center_gps_button"),
                        ) {
                            if (isLoadingLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = WhatsAppAccentTeal,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.MyLocation,
                                    contentDescription = "Centrar en mi ubicación",
                                    tint = Color(0xFF455A64),
                                )
                            }
                        }
                    }

                    // GPS Status / Permission / Settings Diagnostic Pill
                    if (diagnosticCode != null) {
                        val (pillText, onPillAction) = when (diagnosticCode) {
                            ChatLocationPoint.STATUS_NO_PERMISSION -> "Permiso GPS necesario · Toca para conceder" to {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                )
                            }
                            ChatLocationPoint.STATUS_COARSE_ONLY -> "Ubicación aproximada · Activa precisión exacta" to {
                                runCatching {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    )
                                    context.startActivity(intent)
                                }
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                )
                            }
                            ChatLocationPoint.STATUS_GPS_DISABLED -> "GPS del móvil desactivado · Toca para activar" to {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                }
                                Unit
                            }
                            else -> "GPS débil en interiores · Toca para reintentar" to {
                                loadGpsAndPlaces(forceFresh = true)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f),
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp, start = 62.dp, end = 62.dp)
                                .clickable { onPillAction() },
                        ) {
                            Text(
                                text = pillText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }

                    if (pickerMode == WhatsAppPickerMode.MENU && userHasPanned && pannedDistanceMeters > 18) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.95f),
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                                .clickable { loadPlacesAt(mapCenterLat, mapCenterLon) }
                                .testTag("search_panned_area_pill"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (isLoadingPlaces) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = WhatsAppAccentTeal,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = null,
                                        tint = WhatsAppAccentTeal,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Text(
                                    text = "Buscar lugares aquí",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = WhatsAppAccentTeal,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                // Bottom Section
                if (pickerMode == WhatsAppPickerMode.MENU) {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { pickerMode = WhatsAppPickerMode.LIVE_CONFIG }
                                    .padding(horizontal = 18.dp, vertical = 16.dp)
                                    .testTag("whatsapp_live_location_option"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(WhatsAppAccentTeal),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Sensors,
                                        contentDescription = null,
                                        tint = OnYellowInk,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Text(
                                    text = "Ubicación en tiempo real",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        item {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Lugares cercanos",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = WhatsAppAccentTeal,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (isLoadingPlaces) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = WhatsAppAccentTeal,
                                    )
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSendCurrentLocation(gpsPoint) }
                                    .padding(horizontal = 18.dp, vertical = 12.dp)
                                    .testTag("whatsapp_send_current_location_option"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, WhatsAppAccentTeal, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, WhatsAppAccentTeal, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(WhatsAppAccentTeal),
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Enviar mi ubicación actual",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Normal,
                                    )
                                    Text(
                                        text = "Exacto a ${gpsPoint.accuracyMeters} metros",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        if (userHasPanned && pannedDistanceMeters > 12) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                val zoneLabel = LocationHelper.resolveFriendlyZoneName(
                                                    context,
                                                    mapCenterLat,
                                                    mapCenterLon,
                                                )
                                                onSendCurrentLocation(
                                                    ChatLocationPoint(
                                                        latitude = mapCenterLat,
                                                        longitude = mapCenterLon,
                                                        label = "$zoneLabel\n${LocationHelper.formatCoordsShort(mapCenterLat, mapCenterLon)}",
                                                        isFromGps = false,
                                                        accuracyMeters = gpsPoint.accuracyMeters,
                                                    )
                                                )
                                            }
                                        }
                                        .padding(horizontal = 18.dp, vertical = 12.dp)
                                        .testTag("whatsapp_send_panned_location_option"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFEE2E2)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.PinDrop,
                                            contentDescription = null,
                                            tint = WhatsAppLiveRed,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Enviar punto marcado en el mapa",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Normal,
                                        )
                                        Text(
                                            text = "A ${LocationHelper.formatCompactDistance(pannedDistanceMeters / 1000.0)} · ${LocationHelper.formatCoordsShort(mapCenterLat, mapCenterLon)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        items(filteredPlaces, key = { it.id }) { place ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSendCurrentLocation(
                                            ChatLocationPoint(
                                                latitude = place.latitude,
                                                longitude = place.longitude,
                                                label = "${place.title}\n${place.address}",
                                                isFromGps = gpsPoint.isFromGps,
                                                accuracyMeters = gpsPoint.accuracyMeters,
                                            )
                                        )
                                    }
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFDCE1E4)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Apartment,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = place.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = place.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        if (searchQuery.isNotBlank()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val clean = searchQuery.trim().take(120)
                                            if (clean.isNotEmpty()) {
                                                onSendCurrentLocation(
                                                    ChatLocationPoint(
                                                        latitude = mapCenterLat,
                                                        longitude = mapCenterLon,
                                                        label = "$clean\n${gpsPoint.label}",
                                                        isFromGps = gpsPoint.isFromGps,
                                                        accuracyMeters = gpsPoint.accuracyMeters,
                                                    )
                                                )
                                            }
                                        }
                                        .padding(horizontal = 18.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(WhatsAppAccentTeal.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.LocationOn,
                                            contentDescription = null,
                                            tint = WhatsAppAccentTeal,
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Enviar con nombre \"$searchQuery\"", style = MaterialTheme.typography.bodyLarge)
                                        Text("Usar coordenadas del mapa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // "Ubicación en tiempo real" compact footer sitting safely above system nav bar & IME
                    Surface(
                        tonalElevation = 4.dp,
                        shadowElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Ubicación en tiempo real",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (gpsPoint.label.isNotBlank()) {
                                    Text(
                                        text = "${gpsPoint.label.substringBefore('\n')} · Exacto a ${gpsPoint.accuracyMeters} m",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            val durations = listOf(
                                15 to "15 minutos",
                                60 to "1 hora",
                                480 to "8 horas",
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                durations.forEach { (mins, label) ->
                                    val selected = selectedLiveMinutes == mins
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) WhatsAppAccentTeal else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clickable { selectedLiveMinutes = mins }
                                            .testTag("whatsapp_live_duration_$mins"),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (selected) OnYellowInk else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedTextField(
                                    value = liveComment,
                                    onValueChange = { liveComment = it.take(200) },
                                    placeholder = { Text("Añade un comentario") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("whatsapp_live_comment_input"),
                                )
                                FloatingActionButton(
                                    onClick = { onSendLiveLocation(selectedLiveMinutes, liveComment, gpsPoint) },
                                    shape = CircleShape,
                                    containerColor = WhatsAppSendGreen,
                                    contentColor = OnYellowInk,
                                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                                    modifier = Modifier
                                        .size(54.dp)
                                        .testTag("whatsapp_send_live_location_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "Enviar ubicación en tiempo real",
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WhatsAppLocationMessageCard(
    message: DirectMessage,
    isMine: Boolean,
    senderPhotoData: String?,
    senderPhotoUrl: String?,
    senderColor: AvatarColor,
    onStopLiveLocation: () -> Unit,
    onOpenInAppLocation: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isLiveType = message.messageType == DirectMessage.TYPE_LIVE_LOCATION
    val stillWithinWindow = !isLiveType ||
        (message.liveActive && (message.liveExpiresAtMs == 0L || System.currentTimeMillis() < message.liveExpiresAtMs))
    val lat = message.latitude ?: 0.0
    val lon = message.longitude ?: 0.0
    val hasValidCoords = lat != 0.0 || lon != 0.0
    val rawLabel = message.locationLabel?.takeIf { it.isNotBlank() } ?: "Ubicación compartida"
    val labelLines = rawLabel.split('\n').filter { it.isNotBlank() }
    val titleLine = labelLines.firstOrNull() ?: rawLabel
    val subtitleLine = labelLines.getOrNull(1)
        ?: String.format(Locale.US, "Lat %.5f · Lon %.5f", lat, lon)

    val expiresFormatted = remember(message.liveExpiresAtMs) {
        if (message.liveExpiresAtMs > 0L) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.liveExpiresAtMs))
        } else {
            ""
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onOpenInAppLocation,
                onLongClick = onLongPress,
            )
            .testTag("whatsapp_location_card_${message.id}"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .background(Color(0xFFECE9E1)),
                contentAlignment = Alignment.Center,
            ) {
                if (hasValidCoords) {
                    MapLibreStaticPreview(
                        lat = lat,
                        lon = lon,
                        zoom = 15.5,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (isLiveType) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(2.5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AvatarCircle(
                                name = message.senderName.ifBlank { "Usuario" },
                                avatarColor = senderColor,
                                size = 43.dp,
                                verified = false,
                                isOnline = false,
                                photoData = senderPhotoData,
                                photoUrl = senderPhotoUrl,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(2.dp, WhatsAppAccentTeal, CircleShape),
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = "Ubicación marcada",
                        tint = Color(0xFFE53935),
                        modifier = Modifier
                            .size(40.dp)
                            .offset(y = (-14).dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isLiveType) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sensors,
                            contentDescription = null,
                            tint = if (stillWithinWindow) WhatsAppAccentTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = when {
                                stillWithinWindow && expiresFormatted.isNotBlank() ->
                                    "Compartiendo hasta las $expiresFormatted"
                                stillWithinWindow -> "Compartiendo ubicación en tiempo real"
                                else -> "Ubicación en tiempo real finalizada"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = "$titleLine · Toca para ver mapa en la app",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = titleLine,
                        style = MaterialTheme.typography.titleSmall,
                        color = WhatsAppAccentTeal,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitleLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Toca para explorar en el mapa",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (isMine && isLiveType && stillWithinWindow) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStopLiveLocation() }
                        .padding(vertical = 10.dp)
                        .testTag("whatsapp_stop_live_bubble_button"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Dejar de compartir",
                        style = MaterialTheme.typography.titleSmall,
                        color = WhatsAppLiveRed,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun InAppDynamicLocationDialog(
    message: DirectMessage,
    viewerZone: ZoneLocation,
    isMine: Boolean,
    senderPhotoData: String?,
    senderPhotoUrl: String?,
    senderColor: AvatarColor,
    onStopLiveLocation: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isLiveType = message.messageType == DirectMessage.TYPE_LIVE_LOCATION
    val stillWithinWindow = !isLiveType ||
        (message.liveActive && (message.liveExpiresAtMs == 0L || System.currentTimeMillis() < message.liveExpiresAtMs))

    val targetLat = message.latitude ?: 0.0
    val targetLon = message.longitude ?: 0.0

    val animatedMarkerLat by animateFloatAsState(
        targetValue = targetLat.toFloat(),
        animationSpec = tween(durationMillis = 550),
        label = "liveLatAnim",
    )
    val animatedMarkerLon by animateFloatAsState(
        targetValue = targetLon.toFloat(),
        animationSpec = tween(durationMillis = 550),
        label = "liveLonAnim",
    )

    var followMarker by remember(message.id) { mutableStateOf(true) }
    val cameraState = rememberMapLibreCameraState(
        key = "dialog_${message.id}",
        initialLat = targetLat,
        initialLon = targetLon,
        initialZoom = 16.0,
    )

    LaunchedEffect(targetLat, targetLon, followMarker) {
        if (followMarker && (targetLat != 0.0 || targetLon != 0.0)) {
            cameraState.animateTo(
                lat = targetLat,
                lon = targetLon,
                zoom = cameraState.zoom.coerceIn(13.0, 18.0),
                durationMs = 500,
            )
        }
    }

    val rawLabel = message.locationLabel?.takeIf { it.isNotBlank() } ?: "Ubicación compartida"
    val labelLines = rawLabel.split('\n').filter { it.isNotBlank() }
    val titleLine = labelLines.firstOrNull() ?: rawLabel
    val subtitleLine = labelLines.getOrNull(1)
        ?: LocationHelper.formatCoordsShort(targetLat, targetLon)

    val distText = if (viewerZone.hasValidCoords && (targetLat != 0.0 || targetLon != 0.0)) {
        val km = LocationHelper.distanceKm(viewerZone.approxLat, viewerZone.approxLon, targetLat, targetLon)
        "A ${LocationHelper.formatCompactDistance(km)} de ti"
    } else {
        null
    }

    val expiresFormatted = remember(message.liveExpiresAtMs) {
        if (message.liveExpiresAtMs > 0L) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.liveExpiresAtMs))
        } else {
            ""
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .testTag("inapp_dynamic_location_dialog"),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WhatsAppHeaderTeal)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AvatarCircle(
                        name = message.senderName.ifBlank { "Usuario" },
                        avatarColor = senderColor,
                        size = 40.dp,
                        isOnline = isLiveType && stillWithinWindow,
                        photoData = senderPhotoData,
                        photoUrl = senderPhotoUrl,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLiveType) "Ubicación en tiempo real" else titleLine,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = when {
                                isLiveType && stillWithinWindow && expiresFormatted.isNotBlank() ->
                                    "${message.senderName.ifBlank { "Usuario" }} · Hasta las $expiresFormatted"
                                isLiveType && !stillWithinWindow -> "Finalizada"
                                distText != null -> distText
                                else -> subtitleLine
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_inapp_location_dialog"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cerrar mapa",
                            tint = Color.White,
                        )
                    }
                }

                // Dynamic Interactive MapLibre Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                ) {
                    InteractiveMapBox(
                        cameraState = cameraState,
                        markerLat = animatedMarkerLat.toDouble(),
                        markerLon = animatedMarkerLon.toDouble(),
                        accuracyMeters = 20,
                        showAccuracyCircle = false,
                        showCenterCrosshairPin = false,
                        showStaticMarkerPin = !isLiveType,
                        avatarName = if (isLiveType) message.senderName.ifBlank { "Usuario" } else null,
                        avatarColor = senderColor,
                        avatarPhotoData = senderPhotoData,
                        avatarPhotoUrl = senderPhotoUrl,
                        onGestureMove = { _, _ ->
                            followMarker = false
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (isLiveType && stillWithinWindow) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = WhatsAppSendGreen.copy(alpha = 0.94f),
                            shadowElevation = 2.dp,
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
                                    imageVector = Icons.Rounded.Sensors,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "En vivo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    if (!followMarker) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.95f),
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .minimumInteractiveComponentSize(),
                        ) {
                            IconButton(
                                onClick = { followMarker = true },
                                modifier = Modifier.size(42.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MyLocation,
                                    contentDescription = "Centrar en marcador",
                                    tint = WhatsAppAccentTeal,
                                )
                            }
                        }
                    }
                }

                // Footer info + Actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = titleLine,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = buildString {
                            append(subtitleLine)
                            if (distText != null) append(" · $distText")
                            val ts = message.updatedAt ?: message.createdAt
                            if (ts != null) append(" · ${formatRelativeTimestamp(ts)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isMine && isLiveType && stillWithinWindow) {
                            Button(
                                onClick = {
                                    onStopLiveLocation()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WhatsAppLiveRed,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Text("Dejar de compartir")
                            }
                        }
                        TextButton(onClick = onDismiss) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }
    }
}
