package com.example.data

import com.example.R
import com.example.i18n.tr

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.example.BuildConfig
import com.example.model.NearbyPlaceSuggestion
import com.example.model.ZoneLocation
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object LocationHelper {

    private const val PREF_NAME = "serafiel_gps_cache"
    private const val KEY_ZONE_KEY = "gps_zone_key"
    private const val KEY_ZONE_NAME = "gps_zone_name"
    private const val KEY_LAT = "gps_lat"
    private const val KEY_LON = "gps_lon"
    private const val KEY_ACCURACY = "gps_accuracy"

    val UNRESOLVED_DEFAULT_ZONE = ZoneLocation(
        zoneKey = "plaza_central",
        zoneName = tr(R.string.s_obteniendo_ubicacion),
        approxLat = 0.0,
        approxLon = 0.0,
        isFromGps = false,
        isManualChoice = false,
    )

    val fallbackZones: List<ZoneLocation> = listOf(
        ZoneLocation(
            zoneKey = "plaza_central",
            zoneName = tr(R.string.s_zona_abierta),
            approxLat = 40.42,
            approxLon = -3.70,
            isFromGps = false,
            isManualChoice = true,
        ),
        ZoneLocation(
            zoneKey = "barrio_cultural",
            zoneName = tr(R.string.s_barrio_cultural_y_cafes),
            approxLat = 40.41,
            approxLon = -3.71,
            isFromGps = false,
            isManualChoice = true,
        ),
        ZoneLocation(
            zoneKey = "campus_innovacion",
            zoneName = tr(R.string.s_distrito_universitario),
            approxLat = 40.44,
            approxLon = -3.73,
            isFromGps = false,
            isManualChoice = true,
        ),
        ZoneLocation(
            zoneKey = "parque_mirador",
            zoneName = tr(R.string.s_parque_y_mirador),
            approxLat = 40.42,
            approxLon = -3.68,
            isFromGps = false,
            isManualChoice = true,
        ),
    )

    fun saveCachedGpsZone(context: Context, zone: ZoneLocation) {
        if (!zone.isFromGps || !zone.hasValidCoords) return
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ZONE_KEY, zone.zoneKey)
            .putString(KEY_ZONE_NAME, zone.zoneName)
            .putString(KEY_LAT, zone.approxLat.toString())
            .putString(KEY_LON, zone.approxLon.toString())
            .putInt(KEY_ACCURACY, zone.accuracyMeters ?: -1)
            .apply()
    }

    fun loadCachedGpsZone(context: Context): ZoneLocation? {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lat = prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString(KEY_LON, null)?.toDoubleOrNull() ?: return null
        if (lat == 0.0 && lon == 0.0) return null
        val key = prefs.getString(KEY_ZONE_KEY, null)?.takeIf { it.isNotBlank() } ?: formatZoneKey(lat, lon)
        val name = prefs.getString(KEY_ZONE_NAME, null)?.takeIf { it.isNotBlank() } ?: tr(R.string.s_zona_gps)
        val acc = prefs.getInt(KEY_ACCURACY, -1).takeIf { it > 0 }
        return ZoneLocation(
            zoneKey = key,
            zoneName = name,
            approxLat = lat,
            approxLon = lon,
            isFromGps = true,
            accuracyMeters = acc,
            isManualChoice = false,
        )
    }

    fun hasPreciseLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    fun hasAnyLocationPermission(context: Context): Boolean {
        val fine = hasPreciseLocationPermission(context)
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasCoarseOnlyLocationPermission(context: Context): Boolean =
        hasAnyLocationPermission(context) && !hasPreciseLocationPermission(context)

    fun hasConfiguredGoogleMapsKey(): Boolean {
        val key = BuildConfig.MAPS_API_KEY.trim()
        return key.isNotBlank() &&
            key != "MY_MAPS_API_KEY" &&
            !key.equals("null", ignoreCase = true)
    }

    fun isSystemLocationEnabled(context: Context): Boolean {
        val lm = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    private fun preferredGpsPriority(context: Context): Int =
        if (hasPreciseLocationPermission(context)) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

    private fun preferredGranularity(context: Context): Int =
        if (hasPreciseLocationPermission(context)) {
            Granularity.GRANULARITY_FINE
        } else {
            Granularity.GRANULARITY_COARSE
        }

    private fun Location?.isUsable(): Boolean =
        this != null && (latitude != 0.0 || longitude != 0.0) && latitude in -89.9..89.9 && longitude in -180.0..180.0

    private fun pickBetterFix(current: Location?, candidate: Location?): Location? {
        if (!candidate.isUsable()) return current
        if (!current.isUsable()) return candidate
        val cAcc = if (candidate!!.hasAccuracy()) candidate.accuracy else 9999f
        val curAcc = if (current!!.hasAccuracy()) current.accuracy else 9999f
        return when {
            cAcc + 5f < curAcc -> candidate
            curAcc + 5f < cAcc -> current
            candidate.time >= current.time -> candidate
            else -> current
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocationManagerFix(appContext: Context): Location? {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val hasFine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val providers = buildList {
            if (hasFine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        }
        for (provider in providers) {
            val fix = withTimeoutOrNull(4500L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        LocationManagerCompat.getCurrentLocation(
                            lm,
                            provider,
                            signal,
                            ContextCompat.getMainExecutor(appContext),
                        ) { loc ->
                            if (cont.isActive) cont.resume(loc?.takeIf { it.isUsable() })
                        }
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            if (fix.isUsable()) return fix
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(context: Context, forceFresh: Boolean = false): Location? {
        val appContext = context.applicationContext
        if (!hasAnyLocationPermission(appContext)) return null
        if (!isSystemLocationEnabled(appContext)) return null

        val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
        val hasFine = hasPreciseLocationPermission(appContext)
        val priority = preferredGpsPriority(appContext)
        val granularity = preferredGranularity(appContext)

        // 1. High-accuracy current location request (force maxUpdateAgeMillis=0 on explicit refresh)
        val currentReq = CurrentLocationRequest.Builder()
            .setPriority(priority)
            .setGranularity(granularity)
            .setMaxUpdateAgeMillis(if (forceFresh) 0L else 5_000L)
            .setDurationMillis(6_500L)
            .build()

        var bestFix = withTimeoutOrNull(6_800L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }
                fusedClient.getCurrentLocation(currentReq, cts.token)
                    .addOnSuccessListener { loc ->
                        if (cont.isActive) cont.resume(loc?.takeIf { it.isUsable() })
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }
        }
        if (bestFix.isUsable() && bestFix!!.hasAccuracy() && bestFix.accuracy <= 35f) {
            return bestFix
        }

        // 2. If directFix wasn't precise enough (e.g. 2000m cell tower fix) or null,
        // listen actively to Fused HIGH_ACCURACY + GPS_PROVIDER until <= 30m or timeout.
        val activeFix = withTimeoutOrNull(if (hasFine) 7_000L else 4_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                var bestSoFar: Location? = bestFix
                val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                var gpsListener: android.location.LocationListener? = null
                var fusedCallback: LocationCallback? = null

                fun cleanup() {
                    fusedCallback?.let { fusedClient.removeLocationUpdates(it) }
                    gpsListener?.let { listener ->
                        runCatching { lm?.removeUpdates(listener) }
                    }
                }

                fun consider(candidate: Location?) {
                    bestSoFar = pickBetterFix(bestSoFar, candidate)
                    val cur = bestSoFar
                    val threshold = if (hasFine) 30f else 150f
                    if (cur.isUsable() && cur!!.hasAccuracy() && cur.accuracy <= threshold && cont.isActive) {
                        cleanup()
                        cont.resume(cur)
                    }
                }

                fusedCallback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        val bestInBatch = result.locations
                            .filter { it.isUsable() }
                            .minByOrNull { if (it.hasAccuracy()) it.accuracy else 9999f }
                        consider(bestInBatch)
                    }
                }

                val req = LocationRequest.Builder(priority, 600L)
                    .setMinUpdateIntervalMillis(300L)
                    .setGranularity(granularity)
                    .setWaitForAccurateLocation(false)
                    .setMaxUpdates(12)
                    .build()

                fusedClient.requestLocationUpdates(req, fusedCallback, Looper.getMainLooper())
                    .addOnFailureListener {
                        if (!hasFine && cont.isActive) {
                            cleanup()
                            cont.resume(bestSoFar)
                        }
                    }

                if (hasFine && lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    val listener = android.location.LocationListener { loc ->
                        if (loc.isUsable()) consider(loc)
                    }
                    gpsListener = listener
                    runCatching {
                        lm.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            500L,
                            0f,
                            listener,
                            Looper.getMainLooper(),
                        )
                    }
                }

                cont.invokeOnCancellation { cleanup() }
            }
        }
        bestFix = pickBetterFix(bestFix, activeFix)
        if (bestFix.isUsable() && (!hasFine || (bestFix!!.hasAccuracy() && bestFix.accuracy <= 100f))) {
            return bestFix
        }

        // 3. Direct LocationManager active single-fix (GPS_PROVIDER first)
        val platformFix = requestCurrentLocationManagerFix(appContext)
        bestFix = pickBetterFix(bestFix, platformFix)
        if (bestFix.isUsable()) return bestFix

        // 4. Fallback to Fused lastLocation
        val fusedLast = suspendCancellableCoroutine<Location?> { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { loc ->
                    if (cont.isActive) cont.resume(loc?.takeIf { it.isUsable() })
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        }
        bestFix = pickBetterFix(bestFix, fusedLast)
        if (bestFix.isUsable()) return bestFix

        // 5. Final fallback to Android LocationManager cached providers (prefer fine/accurate fix)
        return try {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val gpsLoc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.takeIf { it.isUsable() }
            val netLoc = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.takeIf { it.isUsable() }
            pickBetterFix(gpsLoc, netLoc)
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchApproximateZone(context: Context, forceFresh: Boolean = false): ZoneLocation? {
        val rawLocation = requestFreshLocation(context, forceFresh = forceFresh) ?: return null
        val exactLat = rawLocation.latitude
        val exactLon = rawLocation.longitude
        val zoneKey = formatZoneKey(exactLat, exactLon)
        val zoneName = resolveFriendlyZoneName(context, exactLat, exactLon)
        val accuracyMeters = if (rawLocation.hasAccuracy()) {
            rawLocation.accuracy.roundToInt().coerceIn(1, 50_000)
        } else {
            null
        }
        return ZoneLocation(
            zoneKey = zoneKey,
            zoneName = zoneName,
            approxLat = exactLat,
            approxLon = exactLon,
            isFromGps = true,
            accuracyMeters = accuracyMeters,
            isManualChoice = false,
        )
    }

    @SuppressLint("MissingPermission")
    fun observeHighAccuracyLocationFlow(context: Context): Flow<ChatLocationPoint> = callbackFlow {
        val appContext = context.applicationContext
        if (!hasAnyLocationPermission(appContext)) {
            close()
            return@callbackFlow
        }
        val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
        val hasFine = hasPreciseLocationPermission(appContext)
        val priority = preferredGpsPriority(appContext)
        val granularity = preferredGranularity(appContext)
        val request = LocationRequest.Builder(priority, 2500L)
            .setMinUpdateIntervalMillis(1500L)
            .setMinUpdateDistanceMeters(1f)
            .setGranularity(granularity)
            .build()

        var lastGeocodedLat = 0.0
        var lastGeocodedLon = 0.0
        var lastGeocodedLabel = ""
        var bestRecentAccuracy = Float.MAX_VALUE
        var bestRecentTimeMs = 0L

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.locations
                    .filter { it.isUsable() }
                    .minByOrNull { if (it.hasAccuracy()) it.accuracy else 9999f }
                    ?: return
                val accFloat = if (loc.hasAccuracy()) loc.accuracy else 15f
                val now = System.currentTimeMillis()
                // Avoid overwriting a recent fine fix with a sudden 2000m coarse cell blip
                if (hasFine &&
                    bestRecentAccuracy <= 60f &&
                    now - bestRecentTimeMs < 30_000L &&
                    accFloat > 200f
                ) {
                    return
                }
                if (accFloat <= bestRecentAccuracy || now - bestRecentTimeMs >= 30_000L) {
                    bestRecentAccuracy = accFloat
                    bestRecentTimeMs = now
                }

                val lat = loc.latitude
                val lon = loc.longitude
                val acc = accFloat.roundToInt().coerceIn(1, 50_000)
                val coarseDiag = if (!hasFine && acc >= 300) ChatLocationPoint.STATUS_COARSE_ONLY else null

                launch {
                    val movedMeters = if (lastGeocodedLabel.isBlank()) Double.MAX_VALUE
                    else distanceKm(lastGeocodedLat, lastGeocodedLon, lat, lon) * 1000.0
                    val label = if (movedMeters >= 25.0) {
                        resolveFriendlyZoneName(appContext, lat, lon).also {
                            lastGeocodedLat = lat
                            lastGeocodedLon = lon
                            lastGeocodedLabel = it
                        }
                    } else {
                        lastGeocodedLabel
                    }
                    trySend(
                        ChatLocationPoint(
                            latitude = lat,
                            longitude = lon,
                            label = label,
                            isFromGps = true,
                            accuracyMeters = acc,
                            diagnosticCode = coarseDiag,
                        )
                    )
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            close()
        }

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }.flowOn(Dispatchers.IO)

    fun formatZoneKey(approxLat: Double, approxLon: Double): String {
        val latInt = round(approxLat * 100.0).toInt()
        val lonInt = round(approxLon * 100.0).toInt()
        return "${latInt}_${lonInt}"
    }

    suspend fun resolveFriendlyZoneName(
        context: Context,
        lat: Double,
        lon: Double,
    ): String = withContext(Dispatchers.IO) {
        if (lat == 0.0 && lon == 0.0) return@withContext tr(R.string.s_obteniendo_ubicacion)
        try {
            if (!Geocoder.isPresent()) return@withContext tr(R.string.s_zona_gps_2, formatCoordsShort(lat, lon))
            val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lon, 1) { list ->
                        if (cont.isActive) cont.resume(list)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1) ?: emptyList()
            }
            val addr = addresses.firstOrNull()
            val street = addr?.thoroughfare?.takeIf { it.isNotBlank() }
            val area = addr?.subLocality ?: addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea
            val country = addr?.countryCode
            when {
                !street.isNullOrBlank() && !area.isNullOrBlank() && !country.isNullOrBlank() ->
                    "$street, $area, $country"
                !area.isNullOrBlank() && !country.isNullOrBlank() -> "$area, $country"
                !area.isNullOrBlank() -> area
                else -> tr(R.string.s_zona, formatCoordsShort(lat, lon))
            }
        } catch (_: Exception) {
            tr(R.string.s_zona, formatCoordsShort(lat, lon))
        }
    }

    suspend fun fetchNearbyPlaces(
        context: Context,
        lat: Double,
        lon: Double,
    ): List<NearbyPlaceSuggestion> = withContext(Dispatchers.IO) {
        if (lat == 0.0 && lon == 0.0) return@withContext emptyList()
        val results = mutableListOf<NearbyPlaceSuggestion>()

        // 1. If Google Maps API key is configured, query Google Places commercial POIs first
        if (hasConfiguredGoogleMapsKey()) {
            val googlePlaces = fetchGooglePlacesCommercialPois(lat, lon, BuildConfig.MAPS_API_KEY)
            results.addAll(googlePlaces)
        }

        // 2. Supplement with real commercial businesses / landmarks from OpenStreetMap Overpass API
        val overpassPois = fetchOverpassCommercialPois(lat, lon)
        overpassPois.forEach { poi ->
            if (results.none { it.title.equals(poi.title, ignoreCase = true) }) {
                results.add(poi)
            }
        }

        // 2. Supplement with Android Geocoder nearby addresses / landmarks
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lon, 6) { list ->
                            if (cont.isActive) cont.resume(list)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lon, 6) ?: emptyList()
                }
                addresses.forEachIndexed { idx, addr ->
                    val title = addr.featureName?.takeIf { it.isNotBlank() && it != addr.locality }
                        ?: addr.thoroughfare?.takeIf { it.isNotBlank() }
                        ?: addr.subLocality
                        ?: addr.locality
                        ?: tr(R.string.s_punto_cercano, idx + 1)
                    val line0 = (0..addr.maxAddressLineIndex)
                        .mapNotNull { i -> addr.getAddressLine(i)?.takeIf { it.isNotBlank() } }
                        .joinToString(", ")
                    val address = line0.ifBlank {
                        listOfNotNull(addr.thoroughfare, addr.subLocality, addr.locality, addr.countryName)
                            .distinct()
                            .joinToString(", ")
                    }
                    val pLat = if (addr.hasLatitude()) addr.latitude else lat
                    val pLon = if (addr.hasLongitude()) addr.longitude else lon
                    val distMeters = (distanceKm(lat, lon, pLat, pLon) * 1000.0).roundToInt().coerceAtLeast(1)
                    if (title.isNotBlank() && results.none { it.title.equals(title, ignoreCase = true) }) {
                        results.add(
                            NearbyPlaceSuggestion(
                                id = "geo_$idx",
                                title = title.take(80),
                                address = address.ifBlank { formatCoordsShort(pLat, pLon) }.take(150),
                                latitude = pLat,
                                longitude = pLon,
                                categoryLabel = tr(R.string.s_direccion),
                                distanceMeters = distMeters,
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }

        if (results.isEmpty()) {
            val cityFallback = resolveFriendlyZoneName(context, lat, lon)
            results.add(
                NearbyPlaceSuggestion(
                    id = "zone_center",
                    title = cityFallback,
                    address = "Lat ${formatCoordsShort(lat, lon)}",
                    latitude = lat,
                    longitude = lon,
                    categoryLabel = tr(R.string.s_zona_2),
                    distanceMeters = 0,
                )
            )
        }
        results.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
    }

    suspend fun searchPlacesByName(
        context: Context,
        query: String,
        biasLat: Double,
        biasLon: Double,
    ): List<NearbyPlaceSuggestion> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2 || !Geocoder.isPresent()) return@withContext emptyList()
        try {
            val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(clean, 8) { list ->
                        if (cont.isActive) cont.resume(list)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(clean, 8) ?: emptyList()
            }
            addresses.mapIndexedNotNull { idx, addr ->
                if (!addr.hasLatitude() || !addr.hasLongitude()) return@mapIndexedNotNull null
                val title = addr.featureName?.takeIf { it.isNotBlank() }
                    ?: addr.thoroughfare?.takeIf { it.isNotBlank() }
                    ?: addr.locality
                    ?: clean
                val line0 = (0..addr.maxAddressLineIndex)
                    .mapNotNull { i -> addr.getAddressLine(i)?.takeIf { it.isNotBlank() } }
                    .joinToString(", ")
                val distMeters = if (biasLat != 0.0 || biasLon != 0.0) {
                    (distanceKm(biasLat, biasLon, addr.latitude, addr.longitude) * 1000.0).roundToInt()
                } else null
                NearbyPlaceSuggestion(
                    id = "search_$idx",
                    title = title.take(80),
                    address = line0.ifBlank { formatCoordsShort(addr.latitude, addr.longitude) }.take(150),
                    latitude = addr.latitude,
                    longitude = addr.longitude,
                    categoryLabel = tr(R.string.s_busqueda),
                    distanceMeters = distMeters,
                )
            }.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchGooglePlacesCommercialPois(
        lat: Double,
        lon: Double,
        apiKey: String,
    ): List<NearbyPlaceSuggestion> {
        return try {
            val latStr = String.format(Locale.US, "%.6f", lat)
            val lonStr = String.format(Locale.US, "%.6f", lon)
            val urlStr =
                "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=$latStr,$lonStr&radius=1200&language=${java.util.Locale.getDefault().language}&key=${
                    URLEncoder.encode(apiKey, "UTF-8")
                }"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4500
                readTimeout = 5500
                setRequestProperty("User-Agent", "SerafielAndroidApp/1.0")
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return emptyList()
            }
            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            conn.disconnect()
            val root = JSONObject(body)
            if (root.optString("status") !in setOf("OK", "ZERO_RESULTS")) return emptyList()
            val arr = root.optJSONArray("results") ?: return emptyList()
            val list = mutableListOf<NearbyPlaceSuggestion>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val name = item.optString("name").trim()
                if (name.length < 2) continue
                val loc = item.optJSONObject("geometry")?.optJSONObject("location") ?: continue
                val pLat = loc.optDouble("lat", Double.NaN)
                val pLon = loc.optDouble("lng", Double.NaN)
                if (pLat.isNaN() || pLon.isNaN()) continue
                val vicinity = item.optString("vicinity").trim()
                val distMeters = (distanceKm(lat, lon, pLat, pLon) * 1000.0).roundToInt().coerceAtLeast(1)
                val addr = listOfNotNull(
                    vicinity.takeIf { it.isNotBlank() },
                    "a ${formatCompactDistance(distMeters / 1000.0)}",
                ).joinToString(" · ")
                list.add(
                    NearbyPlaceSuggestion(
                        id = "gplace_${item.optString("place_id").ifBlank { i.toString() }}",
                        title = name.take(80),
                        address = addr.ifBlank { formatCoordsShort(pLat, pLon) }.take(150),
                        latitude = pLat,
                        longitude = pLon,
                        categoryLabel = "Google Maps",
                        distanceMeters = distMeters,
                    )
                )
            }
            list.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }.take(20)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchOverpassCommercialPois(lat: Double, lon: Double): List<NearbyPlaceSuggestion> {
        val latStr = String.format(Locale.US, "%.6f", lat)
        val lonStr = String.format(Locale.US, "%.6f", lon)
        val query = """
            [out:json][timeout:6];
            (
              node["name"]["shop"](around:1000,$latStr,$lonStr);
              way["name"]["shop"](around:1000,$latStr,$lonStr);
              node["name"]["amenity"](around:1000,$latStr,$lonStr);
              way["name"]["amenity"](around:1000,$latStr,$lonStr);
              node["name"]["tourism"](around:1000,$latStr,$lonStr);
              node["name"]["leisure"](around:1000,$latStr,$lonStr);
              node["name"]["office"](around:1000,$latStr,$lonStr);
              node["name"]["healthcare"](around:1000,$latStr,$lonStr);
            );
            out center 35;
        """.trimIndent()

        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
        )
        for (endpoint in endpoints) {
            try {
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4500
                    readTimeout = 6000
                    doOutput = true
                    setRequestProperty("User-Agent", "SerafielAndroidApp/1.0")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write("data=${URLEncoder.encode(query, "UTF-8")}")
                }
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                    conn.disconnect()
                    val parsed = parseOverpassJson(body, lat, lon)
                    if (parsed.isNotEmpty()) return parsed
                } else {
                    conn.disconnect()
                }
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }

    private fun parseOverpassJson(
        rawJson: String,
        originLat: Double,
        originLon: Double,
    ): List<NearbyPlaceSuggestion> {
        val root = JSONObject(rawJson)
        val elements = root.optJSONArray("elements") ?: return emptyList()
        val deduped = linkedMapOf<String, NearbyPlaceSuggestion>()
        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            val tags = el.optJSONObject("tags") ?: continue
            val name = tags.optString("name").trim()
            if (name.length < 2) continue

            val centerObj = el.optJSONObject("center")
            val pLat = centerObj?.optDouble("lat", Double.NaN) ?: el.optDouble("lat", Double.NaN)
            val pLon = centerObj?.optDouble("lon", Double.NaN) ?: el.optDouble("lon", Double.NaN)
            if (pLat.isNaN() || pLon.isNaN()) continue

            val category = resolvePoiCategoryEs(tags)
            val street = tags.optString("addr:street").trim()
            val number = tags.optString("addr:housenumber").trim()
            val city = tags.optString("addr:city").trim().ifBlank { tags.optString("addr:suburb").trim() }
            val addrText = buildString {
                if (category.isNotBlank()) append(category)
                val streetFull = listOf(street, number).filter { it.isNotBlank() }.joinToString(" ")
                if (streetFull.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(streetFull)
                }
                if (city.isNotBlank()) {
                    if (isNotEmpty()) append(", ")
                    append(city)
                }
            }.ifBlank { formatCoordsShort(pLat, pLon) }

            val distMeters = (distanceKm(originLat, originLon, pLat, pLon) * 1000.0).roundToInt().coerceAtLeast(1)
            val key = name.lowercase(Locale.getDefault())
            val existing = deduped[key]
            if (existing == null || distMeters < (existing.distanceMeters ?: Int.MAX_VALUE)) {
                deduped[key] = NearbyPlaceSuggestion(
                    id = "poi_${el.optString("type")}_${el.optLong("id")}",
                    title = name.take(80),
                    address = "$addrText · a ${formatCompactDistance(distMeters / 1000.0)}".take(150),
                    latitude = pLat,
                    longitude = pLon,
                    categoryLabel = category,
                    distanceMeters = distMeters,
                )
            }
        }
        return deduped.values
            .sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
            .take(25)
    }

    private fun resolvePoiCategoryEs(tags: JSONObject): String {
        val shop = tags.optString("shop")
        val amenity = tags.optString("amenity")
        val tourism = tags.optString("tourism")
        val leisure = tags.optString("leisure")
        val healthcare = tags.optString("healthcare")
        return when {
            amenity == "cafe" -> tr(R.string.s_cafeteria)
            amenity == "restaurant" -> tr(R.string.s_restaurante)
            amenity == "fast_food" -> tr(R.string.s_comida_rapida)
            amenity == "bar" || amenity == "pub" -> tr(R.string.s_bar)
            amenity == "pharmacy" -> tr(R.string.s_farmacia)
            amenity == "hospital" || amenity == "clinic" || healthcare.isNotBlank() -> tr(R.string.s_centro_de_salud)
            amenity == "bank" || amenity == "atm" -> tr(R.string.s_banco)
            amenity == "fuel" -> tr(R.string.s_gasolinera)
            amenity == "school" || amenity == "university" || amenity == "college" -> tr(R.string.s_educacion)
            amenity == "parking" -> tr(R.string.s_estacionamiento)
            shop == "supermarket" || shop == "convenience" -> tr(R.string.s_supermercado)
            shop == "bakery" -> tr(R.string.s_panaderia)
            shop == "mall" || shop == "department_store" -> tr(R.string.s_centro_comercial)
            shop == "clothes" || shop == "shoes" -> tr(R.string.s_tienda_de_ropa)
            shop == "hardware" -> tr(R.string.s_ferreteria)
            shop == "electronics" || shop == "mobile_phone" -> tr(R.string.s_tecnologia)
            shop.isNotBlank() -> tr(R.string.s_comercio)
            tourism == "hotel" || tourism == "hostel" -> tr(R.string.s_alojamiento)
            tourism.isNotBlank() -> tr(R.string.s_sitio_de_interes)
            leisure == "park" -> tr(R.string.s_parque)
            leisure == "fitness_centre" || leisure == "sports_centre" -> tr(R.string.s_gimnasio)
            leisure.isNotBlank() -> tr(R.string.s_ocio)
            tags.optString("office").isNotBlank() -> tr(R.string.s_oficina)
            else -> tr(R.string.s_lugar_comercial)
        }
    }

    fun formatCoordsShort(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lon)

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }

    suspend fun fetchChatLocation(
        context: Context,
        fallbackZone: ZoneLocation,
        forceFresh: Boolean = false,
    ): ChatLocationPoint {
        val appContext = context.applicationContext
        if (!hasAnyLocationPermission(appContext)) {
            val cached = loadCachedGpsZone(appContext)
            val effectiveZone = if (fallbackZone.hasValidCoords) fallbackZone else (cached ?: fallbackZone)
            return ChatLocationPoint(
                latitude = effectiveZone.approxLat,
                longitude = effectiveZone.approxLon,
                label = if (effectiveZone.hasValidCoords) effectiveZone.zoneName else tr(R.string.s_permiso_de_ubicacion_necesario),
                isFromGps = false,
                accuracyMeters = effectiveZone.accuracyMeters ?: 40,
                diagnosticCode = ChatLocationPoint.STATUS_NO_PERMISSION,
            )
        }
        if (!isSystemLocationEnabled(appContext)) {
            val cached = loadCachedGpsZone(appContext)
            val effectiveZone = if (fallbackZone.hasValidCoords) fallbackZone else (cached ?: fallbackZone)
            return ChatLocationPoint(
                latitude = effectiveZone.approxLat,
                longitude = effectiveZone.approxLon,
                label = if (effectiveZone.hasValidCoords) effectiveZone.zoneName else tr(R.string.s_gps_del_dispositivo_desactivado),
                isFromGps = false,
                accuracyMeters = effectiveZone.accuracyMeters ?: 40,
                diagnosticCode = ChatLocationPoint.STATUS_GPS_DISABLED,
            )
        }

        val rawLocation = requestFreshLocation(appContext, forceFresh = forceFresh)
        if (rawLocation != null) {
            val lat = rawLocation.latitude
            val lon = rawLocation.longitude
            val label = resolveFriendlyZoneName(appContext, lat, lon)
            val acc = if (rawLocation.hasAccuracy()) rawLocation.accuracy.roundToInt().coerceIn(1, 50_000) else 15
            val coarseDiag = if (hasCoarseOnlyLocationPermission(appContext) && acc >= 300) {
                ChatLocationPoint.STATUS_COARSE_ONLY
            } else {
                null
            }
            saveCachedGpsZone(
                appContext,
                ZoneLocation(
                    zoneKey = formatZoneKey(lat, lon),
                    zoneName = label,
                    approxLat = lat,
                    approxLon = lon,
                    isFromGps = true,
                    accuracyMeters = acc,
                    isManualChoice = false,
                )
            )
            return ChatLocationPoint(
                latitude = lat,
                longitude = lon,
                label = label,
                isFromGps = true,
                accuracyMeters = acc,
                diagnosticCode = coarseDiag,
            )
        }

        val cached = loadCachedGpsZone(appContext)
        val effectiveZone = if (fallbackZone.hasValidCoords) fallbackZone else (cached ?: fallbackZone)
        return ChatLocationPoint(
            latitude = effectiveZone.approxLat,
            longitude = effectiveZone.approxLon,
            label = if (effectiveZone.hasValidCoords) effectiveZone.zoneName else tr(R.string.s_senal_gps_debil_en_interiores),
            isFromGps = effectiveZone.isFromGps,
            accuracyMeters = effectiveZone.accuracyMeters ?: 40,
            diagnosticCode = ChatLocationPoint.STATUS_UNAVAILABLE,
        )
    }

    fun formatCompactDistance(km: Double): String {
        val safeKm = km.coerceAtLeast(0.0)
        return when {
            safeKm < 1.0 -> "${round(safeKm * 1000.0).toInt()} m"
            safeKm < 10.0 -> String.format(Locale.getDefault(), "%.1f km", safeKm)
            else -> String.format(Locale.getDefault(), "%d km", round(safeKm).toInt())
        }
    }

    fun formatApproximateDistance(km: Double, sameZone: Boolean): String {
        val numeric = formatCompactDistance(km)
        return if (sameZone) {
            tr(R.string.s_a_misma_zona, numeric)
        } else {
            "A $numeric"
        }
    }
}

data class ChatLocationPoint(
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val isFromGps: Boolean,
    val accuracyMeters: Int = 20,
    val diagnosticCode: String? = null,
) {
    companion object {
        const val STATUS_NO_PERMISSION = "NO_PERMISSION"
        const val STATUS_COARSE_ONLY = "COARSE_ONLY"
        const val STATUS_GPS_DISABLED = "GPS_DISABLED"
        const val STATUS_UNAVAILABLE = "UNAVAILABLE"
    }
}
