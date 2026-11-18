package com.example.ui.map

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Estilos vectoriales públicos de OpenFreeMap (https://openfreemap.org).
 * No requieren API key ni registro. Datos © OpenStreetMap contributors.
 */
object OpenFreeMap {
    const val STYLE_LIBERTY = "https://tiles.openfreemap.org/styles/liberty"
    const val STYLE_BRIGHT = "https://tiles.openfreemap.org/styles/bright"
    const val STYLE_POSITRON = "https://tiles.openfreemap.org/styles/positron"
    const val ATTRIBUTION = "© OpenFreeMap © OpenStreetMap"
}

private fun safeLatLng(lat: Double, lon: Double) =
    LatLng(lat.coerceIn(-85.0, 85.0), lon.coerceIn(-180.0, 180.0))

/**
 * Estado de cámara observable desde Compose para un mapa MapLibre.
 * Las lecturas de [cameraVersion] dentro de layout/draw provocan solo
 * re-posicionado / re-dibujado de los overlays al mover el mapa.
 */
@Stable
class MapLibreCameraState(initialLat: Double, initialLon: Double, initialZoom: Double) {
    internal var map: MapLibreMap? by mutableStateOf(null)

    var cameraVersion by mutableIntStateOf(0)
        private set
    var targetLat by mutableDoubleStateOf(initialLat)
        private set
    var targetLon by mutableDoubleStateOf(initialLon)
        private set
    var zoom by mutableDoubleStateOf(initialZoom)
        private set
    var isMoving by mutableStateOf(false)
        internal set

    internal fun syncFrom(m: MapLibreMap) {
        val pos = m.cameraPosition
        pos.target?.let {
            targetLat = it.latitude
            targetLon = it.longitude
        }
        zoom = pos.zoom
        cameraVersion++
    }

    internal fun attach(m: MapLibreMap) {
        map = m
        // Aplica cualquier movimiento pedido antes de que el mapa estuviera listo.
        m.moveCamera(CameraUpdateFactory.newLatLngZoom(safeLatLng(targetLat, targetLon), zoom))
        syncFrom(m)
    }

    fun moveTo(lat: Double, lon: Double, zoom: Double? = null) {
        if (lat == 0.0 && lon == 0.0) return
        val z = zoom ?: this.zoom
        val m = map
        if (m == null) {
            targetLat = lat
            targetLon = lon
            this.zoom = z
            return
        }
        m.moveCamera(CameraUpdateFactory.newLatLngZoom(safeLatLng(lat, lon), z))
    }

    fun animateTo(lat: Double, lon: Double, zoom: Double? = null, durationMs: Int = 450) {
        if (lat == 0.0 && lon == 0.0) return
        val z = zoom ?: this.zoom
        val m = map
        if (m == null) {
            moveTo(lat, lon, z)
            return
        }
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(safeLatLng(lat, lon), z), durationMs)
    }

    fun zoomIn() {
        map?.animateCamera(CameraUpdateFactory.zoomIn(), 250)
    }

    fun zoomOut() {
        map?.animateCamera(CameraUpdateFactory.zoomOut(), 250)
    }

    /** Proyecta lat/lon a píxeles dentro de la vista del mapa. */
    fun toScreen(lat: Double, lon: Double): Offset? {
        @Suppress("UNUSED_VARIABLE")
        val version = cameraVersion // suscribe al estado
        val m = map ?: return null
        val p = m.projection.toScreenLocation(safeLatLng(lat, lon))
        return Offset(p.x, p.y)
    }
}

@Composable
fun rememberMapLibreCameraState(
    key: Any? = null,
    initialLat: Double,
    initialLon: Double,
    initialZoom: Double = 16.0,
): MapLibreCameraState = remember(key) {
    MapLibreCameraState(initialLat, initialLon, initialZoom)
}

/**
 * Mapa MapLibre interactivo con estilo OpenFreeMap. Los hijos en [content]
 * se dibujan encima (marcadores Compose, círculos, etc.).
 */
@Composable
fun MapLibreMapView(
    state: MapLibreCameraState,
    modifier: Modifier = Modifier,
    styleUrl: String = OpenFreeMap.STYLE_LIBERTY,
    gesturesEnabled: Boolean = true,
    onGestureMove: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onMapClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnGestureMove by rememberUpdatedState(onGestureMove)
    val currentOnMapClick by rememberUpdatedState(onMapClick)

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        val options = MapLibreMapOptions.createFromAttributes(context)
            // TextureView: se compone bien con overlays, clips y diálogos de Compose.
            .textureMode(true)
            .camera(
                CameraPosition.Builder()
                    .target(safeLatLng(state.targetLat, state.targetLon))
                    .zoom(state.zoom)
                    .build()
            )
            .logoEnabled(false)
            .compassEnabled(false)
            // Atribución propia en Compose (el diálogo nativo exige tema AppCompat).
            .attributionEnabled(false)
            .rotateGesturesEnabled(gesturesEnabled)
            .tiltGesturesEnabled(false)
            .scrollGesturesEnabled(gesturesEnabled)
            .zoomGesturesEnabled(gesturesEnabled)
            .doubleTapGesturesEnabled(gesturesEnabled)
        MapView(context, options).apply { onCreate(null) }
    }

    DisposableEffect(mapView) {
        var gestureMove = false
        mapView.getMapAsync(object : OnMapReadyCallback {
            override fun onMapReady(map: MapLibreMap) {
                map.setStyle(Style.Builder().fromUri(styleUrl))
                map.addOnCameraMoveStartedListener(object : MapLibreMap.OnCameraMoveStartedListener {
                    override fun onCameraMoveStarted(reason: Int) {
                        gestureMove = reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE
                        state.isMoving = true
                    }
                })
                map.addOnCameraMoveListener(object : MapLibreMap.OnCameraMoveListener {
                    override fun onCameraMove() {
                        state.syncFrom(map)
                        if (gestureMove) currentOnGestureMove(state.targetLat, state.targetLon)
                    }
                })
                map.addOnCameraIdleListener(object : MapLibreMap.OnCameraIdleListener {
                    override fun onCameraIdle() {
                        state.syncFrom(map)
                        if (gestureMove) currentOnGestureMove(state.targetLat, state.targetLon)
                        gestureMove = false
                        state.isMoving = false
                    }
                })
                map.addOnMapClickListener(object : MapLibreMap.OnMapClickListener {
                    override fun onMapClick(point: LatLng): Boolean {
                        val cb = currentOnMapClick ?: return false
                        cb()
                        return true
                    }
                })
                state.attach(map)
            }
        })
        onDispose { }
    }

    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            state.map = null
            mapView.onDestroy()
        }
    }

    Box(modifier = modifier.clipToBounds().background(Color(0xFFECE9E1))) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        content()
        MapAttribution(modifier = Modifier.align(Alignment.BottomStart))
    }
}

/**
 * Coloca un composable anclado a una coordenada del mapa.
 * [anchor] (0..1) indica qué punto del contenido toca la coordenada.
 */
@Composable
fun MapMarker(
    state: MapLibreCameraState,
    lat: Double,
    lon: Double,
    anchor: Offset = Offset(0.5f, 1f),
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val w = placeables.maxOfOrNull { it.width } ?: 0
        val h = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, constraints.maxHeight) {
            val p = state.toScreen(lat, lon) ?: return@layout
            val x = (p.x - w * anchor.x).roundToInt()
            val y = (p.y - h * anchor.y).roundToInt()
            placeables.forEach { it.place(x, y) }
        }
    }
}

/** Círculo de precisión en metros reales, se reescala con el zoom. */
@Composable
fun MapAccuracyCircle(
    state: MapLibreCameraState,
    lat: Double,
    lon: Double,
    radiusMeters: Double,
    fillColor: Color = Color(0x2E2196F3),
    strokeColor: Color = Color(0x992196F3),
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = state.toScreen(lat, lon) ?: return@Canvas
        val edge = state.toScreen(lat + radiusMeters / 111_320.0, lon) ?: return@Canvas
        val r = (edge - center).getDistance().coerceAtMost(size.maxDimension * 2f)
        if (r < 1f) return@Canvas
        drawCircle(color = fillColor, radius = r, center = center)
        drawCircle(color = strokeColor, radius = r, center = center, style = Stroke(width = 3f))
    }
}

private object MapSnapshotCache {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

/**
 * Vista previa estática (sustituye a liteMode de Google Maps). Renderiza una
 * sola imagen con MapSnapshotter y la cachea; ideal para burbujas de chat.
 */
@Composable
fun MapLibreStaticPreview(
    lat: Double,
    lon: Double,
    modifier: Modifier = Modifier,
    zoom: Double = 15.5,
    styleUrl: String = OpenFreeMap.STYLE_LIBERTY,
) {
    val context = LocalContext.current
    BoxWithConstraints(modifier = modifier.clipToBounds().background(Color(0xFFECE9E1))) {
        val density = LocalDensity.current.density
        val widthDp = maxWidth.value.roundToInt().coerceAtLeast(1)
        val heightDp = maxHeight.value.roundToInt().coerceAtLeast(1)
        val key = String.format(Locale.US, "%.5f,%.5f,%.1f,%d,%d,%s", lat, lon, zoom, widthDp, heightDp, styleUrl)
        var bitmap by remember(key) { mutableStateOf(MapSnapshotCache.get(key)) }

        DisposableEffect(key) {
            if (bitmap != null || (lat == 0.0 && lon == 0.0) || maxWidth.value <= 0f || maxHeight.value <= 0f) {
                return@DisposableEffect onDispose { }
            }
            MapLibre.getInstance(context.applicationContext)
            val options = MapSnapshotter.Options(widthDp, heightDp)
                .withStyleBuilder(Style.Builder().fromUri(styleUrl))
                .withCameraPosition(
                    CameraPosition.Builder().target(safeLatLng(lat, lon)).zoom(zoom).build()
                )
                .withLogo(false)
                .withPixelRatio(density)
            val snapshotter = MapSnapshotter(context.applicationContext, options)
            snapshotter.start(
                object : MapSnapshotter.SnapshotReadyCallback {
                    override fun onSnapshotReady(snapshot: MapSnapshot) {
                        val bmp = snapshot.bitmap
                        MapSnapshotCache.put(key, bmp)
                        bitmap = bmp
                    }
                },
                object : MapSnapshotter.ErrorHandler {
                    override fun onError(error: String) = Unit
                },
            )
            onDispose { snapshotter.cancel() }
        }

        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        MapAttribution(modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun MapAttribution(modifier: Modifier = Modifier) {
    Text(
        text = OpenFreeMap.ATTRIBUTION,
        fontSize = 8.sp,
        color = Color(0xFF4B5563),
        modifier = modifier
            .background(Color.White.copy(alpha = 0.7f))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}
