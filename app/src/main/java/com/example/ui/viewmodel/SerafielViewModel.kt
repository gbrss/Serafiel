package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatAudioPlayer
import com.example.data.ChunkedMediaStore
import com.example.data.ChatLocationPoint
import com.example.data.ChatMediaHelper
import com.example.data.ChatMediaSettings
import com.example.data.ChatVideoHelper
import com.example.data.EncodedMediaPayload
import com.example.data.LocationHelper
import com.example.data.SerafielRepository
import com.example.data.unwrapFirestoreError
import com.example.model.AvatarColor
import com.example.model.DirectChat
import com.example.model.DirectMessage
import com.example.model.GroupCategory
import com.example.model.GroupMessage
import com.example.model.KnownContact
import com.example.model.PeerVisualInfo
import com.example.model.ProximityBeacon
import com.example.model.ReportReason
import com.example.model.SocialGroup
import com.example.model.SocialPurpose
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.ZoneLocation
import com.example.model.ZoneMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SerafielViewModel(
    private val repository: SerafielRepository,
    val currentUserId: String,
    val initialDisplayName: String,
    val initialPhotoUrl: String = "",
    initialZone: ZoneLocation = LocationHelper.UNRESOLVED_DEFAULT_ZONE,
) : ViewModel() {

    private val _currentZone = MutableStateFlow(initialZone)
    val currentZone: StateFlow<ZoneLocation> = _currentZone.asStateFlow()

    private val _selectedPurposeFilter = MutableStateFlow<SocialPurpose?>(null)
    val selectedPurposeFilter: StateFlow<SocialPurpose?> = _selectedPurposeFilter.asStateFlow()

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    val selectedGroupId: StateFlow<String?> = _selectedGroupId.asStateFlow()

    private val _selectedGroupCategoryFilter = MutableStateFlow<GroupCategory?>(null)
    val selectedGroupCategoryFilter: StateFlow<GroupCategory?> = _selectedGroupCategoryFilter.asStateFlow()

    private val _radiusKm = MutableStateFlow(DEFAULT_RADIUS_KM)
    val radiusKm: StateFlow<Int> = _radiusKm.asStateFlow()

    @Volatile
    private var hasUserEditedRadius = false

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _isRefreshingLocation = MutableStateFlow(false)
    val isRefreshingLocation: StateFlow<Boolean> = _isRefreshingLocation.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    /** Aviso con acción "Deshacer" (p. ej. al eliminar un mensaje). */
    data class UndoEvent(
        val message: String,
        val actionLabel: String,
        val onUndo: () -> Unit,
        val onTimeout: () -> Unit,
    )

    private val _undoEvents = MutableSharedFlow<UndoEvent>(extraBufferCapacity = 4)
    val undoEvents: SharedFlow<UndoEvent> = _undoEvents.asSharedFlow()

    /** Mensajes ocultos mientras se puede deshacer su eliminación. */
    private val _hiddenMessageIds = MutableStateFlow<Set<String>>(emptySet())

    data class LiveLocationShareState(
        val targetType: String = "chat",
        val chatId: String = "",
        val messageId: String = "",
        val expiresAtMs: Long = 0L,
        val remainingSec: Long = 0L,
        val isActive: Boolean = false,
    )

    private val _liveLocationState = MutableStateFlow(LiveLocationShareState())
    val liveLocationState: StateFlow<LiveLocationShareState> = _liveLocationState.asStateFlow()

    private var liveLocationJob: Job? = null
    private var locationRefreshJob: Job? = null

    @Volatile
    private var appPrefsContext: Context? = null
    private val _cachedBlockedProfiles = MutableStateFlow<Map<String, KnownContact>>(emptyMap())
    private val fetchedBlockedLookupAttempted = mutableSetOf<String>()

    fun attachAppContext(context: Context) {
        val appCtx = context.applicationContext
        if (appPrefsContext == null) {
            appPrefsContext = appCtx
            val loaded = BlockedProfilePrefs.load(appCtx, currentUserId)
            if (loaded.isNotEmpty()) {
                _cachedBlockedProfiles.value = loaded + _cachedBlockedProfiles.value
            }
        }
    }

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing profile", root)
                emit(UiState.Error(root.message ?: "No se pudo cargar tu perfil"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val myBeaconState: StateFlow<UiState<ProximityBeacon?>> =
        repository.observeMyBeacon(currentUserId)
            .onEach { beacon ->
                if (!hasUserEditedRadius && beacon != null) {
                    _radiusKm.value = beacon.radiusKm.toInt().coerceIn(MIN_RADIUS_KM, MAX_RADIUS_KM)
                }
                val current = _currentZone.value
                val beaconHasGpsOrigin = beacon != null &&
                    (beacon.approxLat != 0.0 || beacon.approxLon != 0.0) &&
                    (beacon.accuracyMeters != null || beacon.zoneKey != "plaza_central")
                if (!current.isFromGps && !current.isManualChoice && !current.hasValidCoords && beaconHasGpsOrigin) {
                    _currentZone.value = ZoneLocation(
                        zoneKey = beacon!!.zoneKey.ifBlank { LocationHelper.formatZoneKey(beacon.approxLat, beacon.approxLon) },
                        zoneName = beacon.zoneName.ifBlank { "Zona Abierta" },
                        approxLat = beacon.approxLat,
                        approxLon = beacon.approxLon,
                        isFromGps = false,
                        accuracyMeters = beacon.accuracyMeters,
                        isManualChoice = false,
                    )
                }
            }
            .map<ProximityBeacon?, UiState<ProximityBeacon?>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing own beacon", root)
                emit(UiState.Error(root.message ?: "No se pudo cargar tu baliza"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    private val rawBeaconsState: StateFlow<UiState<List<ProximityBeacon>>> =
        repository.observeActiveBeacons(currentUserId)
            .map<List<ProximityBeacon>, UiState<List<ProximityBeacon>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing beacons", root)
                emit(UiState.Error(root.message ?: "No se pudieron cargar las balizas cercanas"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val nearbyBeaconsState: StateFlow<UiState<List<ProximityBeacon>>> =
        combine(
            rawBeaconsState,
            userProfileState,
            _selectedPurposeFilter,
            _currentZone,
            _radiusKm,
        ) { beaconsUi, profileUi, filter, zone, radius ->
            when (beaconsUi) {
                is UiState.Loading -> UiState.Loading
                is UiState.Error -> beaconsUi
                is UiState.Success -> {
                    val blockedIds = (profileUi as? UiState.Success)?.data?.blockedUserIds.orEmpty().toSet()
                    val isGlobalSearch = radius > ProximityBeacon.MAX_LOCAL_RADIUS_KM
                    val filtered = beaconsUi.data
                        .asSequence()
                        .filter { beacon ->
                            beacon.userId != currentUserId &&
                                beacon.userId !in blockedIds &&
                                (filter == null || beacon.purpose == filter.name)
                        }
                        .map { beacon ->
                            val peerHasCoords = beacon.approxLat != 0.0 || beacon.approxLon != 0.0
                            val dist = if (zone.hasValidCoords && peerHasCoords) {
                                LocationHelper.distanceKm(
                                    zone.approxLat,
                                    zone.approxLon,
                                    beacon.approxLat,
                                    beacon.approxLon,
                                )
                            } else {
                                Double.MAX_VALUE
                            }
                            beacon to dist
                        }
                        .filter { (_, distanceKm) ->
                            isGlobalSearch || !zone.hasValidCoords || distanceKm <= radius.toDouble()
                        }
                        .sortedBy { (_, distanceKm) -> distanceKm }
                        .map { (beacon, _) -> beacon }
                        .toList()
                    UiState.Success(filtered)
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val outsideRadiusCountState: StateFlow<Int> =
        combine(
            rawBeaconsState,
            userProfileState,
            _selectedPurposeFilter,
            _currentZone,
            _radiusKm,
        ) { beaconsUi, profileUi, filter, zone, radius ->
            if (radius > ProximityBeacon.MAX_LOCAL_RADIUS_KM || !zone.hasValidCoords) {
                return@combine 0
            }
            val beacons = (beaconsUi as? UiState.Success)?.data ?: return@combine 0
            val blockedIds = (profileUi as? UiState.Success)?.data?.blockedUserIds.orEmpty().toSet()
            beacons.count { beacon ->
                val peerHasCoords = beacon.approxLat != 0.0 || beacon.approxLon != 0.0
                beacon.userId != currentUserId &&
                    beacon.userId !in blockedIds &&
                    (filter == null || beacon.purpose == filter.name) &&
                    (!peerHasCoords || LocationHelper.distanceKm(
                        zone.approxLat,
                        zone.approxLon,
                        beacon.approxLat,
                        beacon.approxLon,
                    ) > radius.toDouble())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0,
        )

    private val rawZoneMessagesState: StateFlow<UiState<List<ZoneMessage>>> =
        _currentZone
            .flatMapLatest { zone ->
                repository.observeZoneMessages(currentUserId, zone.zoneKey)
                    .map<List<ZoneMessage>, UiState<List<ZoneMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreError(error)
                        Log.w(TAG, "Error observing zone messages", root)
                        emit(UiState.Error(root.message ?: "No se pudo cargar la sala local"))
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val zoneMessagesState: StateFlow<UiState<List<ZoneMessage>>> =
        combine(rawZoneMessagesState, userProfileState) { messagesUi, profileUi ->
            when (messagesUi) {
                is UiState.Loading -> UiState.Loading
                is UiState.Error -> messagesUi
                is UiState.Success -> {
                    val blockedIds = (profileUi as? UiState.Success)?.data?.blockedUserIds.orEmpty().toSet()
                    UiState.Success(messagesUi.data.filter { it.senderId !in blockedIds })
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    private val rawChatsState: StateFlow<UiState<List<DirectChat>>> =
        repository.observeMyChats(currentUserId)
            .map<List<DirectChat>, UiState<List<DirectChat>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing chats", root)
                emit(UiState.Error(root.message ?: "No se pudieron cargar tus chats"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val myChatsState: StateFlow<UiState<List<DirectChat>>> =
        combine(rawChatsState, userProfileState) { chatsUi, profileUi ->
            when (chatsUi) {
                is UiState.Loading -> UiState.Loading
                is UiState.Error -> chatsUi
                is UiState.Success -> {
                    val blockedIds = (profileUi as? UiState.Success)?.data?.blockedUserIds.orEmpty().toSet()
                    UiState.Success(
                        chatsUi.data.filter { chat ->
                            val peerId = chat.participantIds.firstOrNull { it != currentUserId }
                            peerId == null || peerId !in blockedIds
                        }
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val selectedChatMessagesState: StateFlow<UiState<List<DirectMessage>>> =
        _selectedChatId
            .flatMapLatest { chatId ->
                if (chatId.isNullOrBlank()) {
                    flowOf(UiState.Success(emptyList()))
                } else {
                    repository.observeChatMessages(currentUserId, chatId)
                        .map<List<DirectMessage>, UiState<List<DirectMessage>>> { UiState.Success(it) }
                        .catch { error ->
                            val root = unwrapFirestoreError(error)
                            Log.w(TAG, "Error observing direct messages", root)
                            emit(UiState.Error(root.message ?: "No se pudieron cargar los mensajes"))
                        }
                }
            }
            .combine(_hiddenMessageIds) { state, hidden ->
                if (state is UiState.Success && hidden.isNotEmpty()) {
                    UiState.Success(state.data.filterNot { it.id in hidden })
                } else {
                    state
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val myGroupsState: StateFlow<UiState<List<SocialGroup>>> =
        repository.observeMyGroups(currentUserId)
            .map<List<SocialGroup>, UiState<List<SocialGroup>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing groups", root)
                emit(UiState.Error(root.message ?: "No se pudieron cargar tus grupos"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val selectedGroupMessagesState: StateFlow<UiState<List<GroupMessage>>> =
        _selectedGroupId
            .flatMapLatest { groupId ->
                if (groupId.isNullOrBlank()) {
                    flowOf(UiState.Success(emptyList()))
                } else {
                    repository.observeGroupMessages(currentUserId, groupId)
                        .map<List<GroupMessage>, UiState<List<GroupMessage>>> { UiState.Success(it) }
                        .catch { error ->
                            val root = unwrapFirestoreError(error)
                            Log.w(TAG, "Error observing group messages", root)
                            emit(UiState.Error(root.message ?: "No se pudieron cargar los mensajes del grupo"))
                        }
                }
            }
            .combine(_hiddenMessageIds) { state, hidden ->
                if (state is UiState.Success && hidden.isNotEmpty()) {
                    UiState.Success(state.data.filterNot { it.id in hidden })
                } else {
                    state
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val peerPhotosMapState: StateFlow<Map<String, Pair<String?, String?>>> =
        rawBeaconsState
            .map { state ->
                val list = (state as? UiState.Success)?.data.orEmpty()
                list.associate { it.userId to (it.photoData to it.photoUrl) }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyMap(),
            )

    val peerVisualMapState: StateFlow<Map<String, PeerVisualInfo>> =
        combine(rawBeaconsState, _currentZone) { state, zone ->
            val list = (state as? UiState.Success)?.data.orEmpty()
            val nowMs = System.currentTimeMillis()
            val hasMyCoords = zone.approxLat != 0.0 || zone.approxLon != 0.0
            list.associate { beacon ->
                val hasPeerCoords = beacon.approxLat != 0.0 || beacon.approxLon != 0.0
                val distKm = if (hasMyCoords && hasPeerCoords) {
                    LocationHelper.distanceKm(
                        zone.approxLat,
                        zone.approxLon,
                        beacon.approxLat,
                        beacon.approxLon,
                    )
                } else {
                    null
                }
                beacon.userId to PeerVisualInfo(
                    photoData = beacon.photoData,
                    photoUrl = beacon.photoUrl,
                    isOnline = beacon.isCurrentlyOnline(nowMs),
                    distanceKm = distKm,
                    lastSeenAt = beacon.lastSeenAt ?: beacon.updatedAt,
                    zoneName = beacon.zoneName,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyMap(),
        )

    private val liveKnownDirectoryState: StateFlow<Map<String, KnownContact>> =
        combine(
            rawBeaconsState,
            rawChatsState,
            myGroupsState,
            rawZoneMessagesState,
        ) { beaconsUi, chatsUi, groupsUi, zoneUi ->
            val map = linkedMapOf<String, KnownContact>()
            (zoneUi as? UiState.Success)?.data.orEmpty().forEach { msg ->
                if (msg.senderId.isNotBlank() && msg.senderId != currentUserId) {
                    map[msg.senderId] = KnownContact(
                        userId = msg.senderId,
                        displayName = msg.senderName.ifBlank { "Usuario Serafiel" },
                        avatarColor = msg.senderColor,
                        subtitle = _currentZone.value.zoneName.ifBlank { "Sala local" },
                    )
                }
            }
            (groupsUi as? UiState.Success)?.data.orEmpty().forEach { group ->
                group.memberIds.forEach { memberId ->
                    if (memberId.isNotBlank() && memberId != currentUserId) {
                        val existing = map[memberId]
                        val name = group.memberNames[memberId]?.takeIf { it.isNotBlank() }
                            ?: existing?.displayName ?: "Miembro de grupo"
                        val color = group.memberColors[memberId]
                            ?: existing?.avatarColor ?: AvatarColor.AMBER.key
                        map[memberId] = (existing ?: KnownContact(memberId, name, color)).copy(
                            displayName = name,
                            avatarColor = color,
                            subtitle = group.name.ifBlank { "Grupo" },
                        )
                    }
                }
            }
            (chatsUi as? UiState.Success)?.data.orEmpty().forEach { chat ->
                val peerId = chat.participantIds.firstOrNull { it != currentUserId }
                if (!peerId.isNullOrBlank()) {
                    val existing = map[peerId]
                    val name = chat.participantNames[peerId]?.takeIf { it.isNotBlank() }
                        ?: existing?.displayName ?: "Contacto de chat"
                    val color = chat.participantColors[peerId]
                        ?: existing?.avatarColor ?: AvatarColor.AMBER.key
                    map[peerId] = (existing ?: KnownContact(peerId, name, color)).copy(
                        displayName = name,
                        avatarColor = color,
                        subtitle = chat.zoneName.ifBlank { "Chat 1:1" },
                    )
                }
            }
            (beaconsUi as? UiState.Success)?.data.orEmpty().forEach { beacon ->
                if (beacon.userId.isNotBlank() && beacon.userId != currentUserId) {
                    val existing = map[beacon.userId]
                    map[beacon.userId] = KnownContact(
                        userId = beacon.userId,
                        displayName = beacon.displayName.ifBlank { existing?.displayName ?: "Viajero Serafiel" },
                        avatarColor = beacon.avatarColor,
                        photoData = beacon.photoData?.takeIf { it.isNotBlank() } ?: existing?.photoData,
                        photoUrl = beacon.photoUrl?.takeIf { it.isNotBlank() } ?: existing?.photoUrl,
                        subtitle = beacon.zoneName.ifBlank { "Radar cercano" },
                    )
                }
            }
            map
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyMap(),
        )

    val blockedUsersState: StateFlow<List<KnownContact>> =
        combine(
            userProfileState,
            liveKnownDirectoryState,
            _cachedBlockedProfiles,
        ) { profileUi, directory, cachedMap ->
            val blockedIds = (profileUi as? UiState.Success)?.data?.blockedUserIds.orEmpty()
            blockedIds.map { uid ->
                val cached = cachedMap[uid]
                val live = directory[uid]
                KnownContact(
                    userId = uid,
                    displayName = live?.displayName?.takeIf { it.isNotBlank() }
                        ?: cached?.displayName?.takeIf { it.isNotBlank() }
                        ?: "Usuario ${uid.take(8)}...",
                    avatarColor = live?.avatarColor?.takeIf { it.isNotBlank() }
                        ?: cached?.avatarColor?.takeIf { it.isNotBlank() }
                        ?: AvatarColor.AMBER.key,
                    photoData = live?.photoData?.takeIf { it.isNotBlank() }
                        ?: cached?.photoData?.takeIf { it.isNotBlank() },
                    photoUrl = live?.photoUrl?.takeIf { it.isNotBlank() }
                        ?: cached?.photoUrl?.takeIf { it.isNotBlank() },
                    subtitle = live?.subtitle?.takeIf { it.isNotBlank() }
                        ?: cached?.subtitle?.takeIf { it.isNotBlank() }
                        ?: "Persona bloqueada",
                )
            }
        }
            .onEach { resolved ->
                persistEnrichedBlockedProfiles(resolved)
                fetchMissingBlockedProfiles(resolved)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList(),
            )

    val knownContactsState: StateFlow<List<KnownContact>> =
        combine(rawBeaconsState, myChatsState, userProfileState) { beaconsUi, chatsUi, profileUi ->
            val blockedIds = (profileUi as? UiState.Success)?.data?.blockedUserIds.orEmpty().toSet()
            val contacts = linkedMapOf<String, KnownContact>()
            (beaconsUi as? UiState.Success)?.data.orEmpty().forEach { beacon ->
                if (beacon.userId.isNotBlank() &&
                    beacon.userId != currentUserId &&
                    beacon.userId !in blockedIds
                ) {
                    contacts[beacon.userId] = KnownContact(
                        userId = beacon.userId,
                        displayName = beacon.displayName.ifBlank { "Viajero Serafiel" },
                        avatarColor = beacon.avatarColor,
                        photoData = beacon.photoData,
                        photoUrl = beacon.photoUrl,
                        subtitle = beacon.zoneName.ifBlank { "Radar cercano" },
                    )
                }
            }
            (chatsUi as? UiState.Success)?.data.orEmpty().forEach { chat ->
                val peerId = chat.participantIds.firstOrNull { it != currentUserId }
                if (!peerId.isNullOrBlank() && peerId !in blockedIds && peerId !in contacts) {
                    contacts[peerId] = KnownContact(
                        userId = peerId,
                        displayName = chat.participantNames[peerId] ?: "Contacto de chat",
                        avatarColor = chat.participantColors[peerId] ?: AvatarColor.AMBER.key,
                        subtitle = chat.zoneName.ifBlank { "Chat 1:1" },
                    )
                }
            }
            contacts.values.sortedBy { it.displayName.lowercase() }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    fun setPurposeFilter(purpose: SocialPurpose?) {
        _selectedPurposeFilter.value = purpose
    }

    fun selectChat(chatId: String?) {
        _selectedChatId.value = chatId
    }

    fun selectGroup(groupId: String?) {
        _selectedGroupId.value = groupId
    }

    fun setGroupCategoryFilter(category: GroupCategory?) {
        _selectedGroupCategoryFilter.value = category
    }

    fun refreshDeviceLocation(context: Context, silent: Boolean = false) {
        if (silent && _isRefreshingLocation.value) return
        val appContext = context.applicationContext
        if (!silent) {
            locationRefreshJob?.cancel()
        }
        locationRefreshJob = viewModelScope.launch {
            _isRefreshingLocation.value = true
            try {
                if (!LocationHelper.hasAnyLocationPermission(appContext)) {
                    if (!silent) {
                        _snackbarEvents.tryEmit("Activa el permiso de ubicación para obtener tu GPS real.")
                    }
                    return@launch
                }
                if (!LocationHelper.isSystemLocationEnabled(appContext)) {
                    if (!silent) {
                        _snackbarEvents.tryEmit("El GPS del teléfono está desactivado. Actívalo en los ajustes rápidos.")
                    }
                    return@launch
                }
                val detected = LocationHelper.fetchApproximateZone(appContext, forceFresh = !silent)
                if (detected != null && detected.hasValidCoords) {
                    LocationHelper.saveCachedGpsZone(appContext, detected)
                    _currentZone.value = detected
                    syncBeaconZoneIfExists(detected)
                } else if (!silent) {
                    _snackbarEvents.tryEmit("No se pudo fijar señal GPS ahora mismo. Intenta acercarte a una ventana.")
                }
            } finally {
                _isRefreshingLocation.value = false
            }
        }
    }

    fun onForegroundHighAccuracyPoint(context: Context, point: ChatLocationPoint) {
        if (!point.isFromGps || (point.latitude == 0.0 && point.longitude == 0.0)) return
        val current = _currentZone.value
        if (current.isManualChoice) return

        val currentAcc = current.accuracyMeters ?: Int.MAX_VALUE
        val improvedAccuracy = point.accuracyMeters + 15 < currentAcc
        val movedMeters = if (current.hasValidCoords) {
            LocationHelper.distanceKm(current.approxLat, current.approxLon, point.latitude, point.longitude) * 1000.0
        } else {
            Double.MAX_VALUE
        }
        val shouldUpdate = !current.hasValidCoords ||
            !current.isFromGps ||
            improvedAccuracy ||
            movedMeters >= 15.0

        if (!shouldUpdate) return

        val appContext = context.applicationContext
        val resolvedName = point.label.substringBefore('\n').takeIf { it.isNotBlank() }
            ?: current.zoneName.takeIf { current.hasValidCoords && it.isNotBlank() }
            ?: "Zona GPS"
        val zone = ZoneLocation(
            zoneKey = LocationHelper.formatZoneKey(point.latitude, point.longitude),
            zoneName = resolvedName,
            approxLat = point.latitude,
            approxLon = point.longitude,
            isFromGps = true,
            accuracyMeters = point.accuracyMeters,
            isManualChoice = false,
        )
        LocationHelper.saveCachedGpsZone(appContext, zone)
        _currentZone.value = zone
        viewModelScope.launch {
            syncBeaconZoneIfExists(zone)
        }
    }

    fun updateZoneFromChatPoint(context: Context, point: ChatLocationPoint) {
        if (!point.isFromGps || (point.latitude == 0.0 && point.longitude == 0.0)) return
        val appContext = context.applicationContext
        val zone = ZoneLocation(
            zoneKey = LocationHelper.formatZoneKey(point.latitude, point.longitude),
            zoneName = point.label.substringBefore('\n').ifBlank { "Zona GPS" },
            approxLat = point.latitude,
            approxLon = point.longitude,
            isFromGps = true,
            accuracyMeters = point.accuracyMeters,
            isManualChoice = false,
        )
        LocationHelper.saveCachedGpsZone(appContext, zone)
        _currentZone.value = zone
        viewModelScope.launch {
            syncBeaconZoneIfExists(zone)
        }
    }

    fun selectManualZone(zone: ZoneLocation) {
        val manualZone = zone.copy(isManualChoice = true)
        _currentZone.value = manualZone
        viewModelScope.launch {
            syncBeaconZoneIfExists(manualZone)
            _snackbarEvents.tryEmit("Explorando ${manualZone.zoneName}")
        }
    }

    fun setBeaconRadiusKm(km: Int, persist: Boolean = true) {
        val clamped = km.coerceIn(MIN_RADIUS_KM, MAX_RADIUS_KM)
        hasUserEditedRadius = true
        _radiusKm.value = clamped
        if (persist) {
            viewModelScope.launch {
                val result = repository.updateBeaconRadius(currentUserId, clamped)
                if (result.isFailure) {
                    _snackbarEvents.tryEmit("No se pudo guardar el radio de la baliza")
                }
            }
        }
    }

    private suspend fun syncBeaconZoneIfExists(zone: ZoneLocation) {
        val existingBeacon = (myBeaconState.value as? UiState.Success)?.data ?: return
        val profile = (userProfileState.value as? UiState.Success)?.data
        val isOnline = (profile?.showOnline ?: true)
        repository.upsertMyBeacon(
            userId = currentUserId,
            displayName = existingBeacon.displayName,
            purpose = SocialPurpose.fromRaw(existingBeacon.purpose),
            statusText = existingBeacon.statusText,
            avatarColor = AvatarColor.fromKey(existingBeacon.avatarColor),
            languages = existingBeacon.languages,
            zone = zone,
            active = existingBeacon.active,
            radiusKm = _radiusKm.value,
            photoData = existingBeacon.photoData,
            photoUrl = existingBeacon.photoUrl,
            online = isOnline,
        )
    }

    fun updateOnlinePresence(inForeground: Boolean) {
        viewModelScope.launch {
            val profile = (userProfileState.value as? UiState.Success)?.data
            val showOnline = profile?.showOnline ?: true
            repository.setUserOnlineStatus(
                userId = currentUserId,
                online = inForeground && showOnline,
            )
        }
    }

    fun setShowOnlinePreference(enabled: Boolean) {
        viewModelScope.launch {
            val res = repository.setUserOnlineStatus(
                userId = currentUserId,
                online = enabled,
                showOnlineOpt = enabled,
            )
            if (res.isSuccess) {
                _snackbarEvents.tryEmit(
                    if (enabled) "Estado en línea activado (círculo verde visible)"
                    else "Estado en línea ocultado"
                )
            } else {
                _snackbarEvents.tryEmit("No se pudo actualizar tu estado en línea")
            }
        }
    }

    fun saveOnboardingOrProfile(
        displayName: String,
        bio: String,
        avatarColor: AvatarColor,
        preferredPurposes: List<SocialPurpose>,
        beaconPurpose: SocialPurpose,
        beaconStatus: String,
        languages: List<String>,
        beaconActive: Boolean,
        radiusKm: Int = _radiusKm.value,
        photoData: String? = null,
        photoUrl: String? = null,
        showOnline: Boolean? = null,
    ) {
        val clampedRadius = radiusKm.coerceIn(MIN_RADIUS_KM, MAX_RADIUS_KM)
        hasUserEditedRadius = true
        _radiusKm.value = clampedRadius
        val existingProfile = (userProfileState.value as? UiState.Success)?.data
        val existingBeacon = (myBeaconState.value as? UiState.Success)?.data
        val resolvedPhotoData = photoData ?: existingProfile?.photoData ?: existingBeacon?.photoData ?: ""
        val resolvedPhotoUrl = photoUrl ?: existingProfile?.photoUrl ?: existingBeacon?.photoUrl ?: initialPhotoUrl
        val resolvedShowOnline = showOnline ?: existingProfile?.showOnline ?: true

        viewModelScope.launch {
            _isBusy.value = true
            val profileResult = repository.saveUserProfile(
                userId = currentUserId,
                displayName = displayName,
                bio = bio,
                avatarColor = avatarColor,
                preferredPurposes = preferredPurposes.ifEmpty { listOf(beaconPurpose) },
                photoData = resolvedPhotoData,
                photoUrl = resolvedPhotoUrl,
                online = resolvedShowOnline,
                showOnline = resolvedShowOnline,
            )
            if (profileResult.isFailure) {
                _isBusy.value = false
                _snackbarEvents.tryEmit(
                    profileResult.exceptionOrNull()?.localizedMessage ?: "Error al guardar tu perfil"
                )
                return@launch
            }

            val effectiveZone = when {
                _currentZone.value.hasValidCoords -> _currentZone.value
                existingBeacon != null && (existingBeacon.approxLat != 0.0 || existingBeacon.approxLon != 0.0) -> ZoneLocation(
                    zoneKey = existingBeacon.zoneKey,
                    zoneName = existingBeacon.zoneName,
                    approxLat = existingBeacon.approxLat,
                    approxLon = existingBeacon.approxLon,
                    isFromGps = false,
                    accuracyMeters = existingBeacon.accuracyMeters,
                )
                else -> LocationHelper.fallbackZones.first()
            }

            val beaconResult = repository.upsertMyBeacon(
                userId = currentUserId,
                displayName = displayName,
                purpose = beaconPurpose,
                statusText = beaconStatus,
                avatarColor = avatarColor,
                languages = languages.ifEmpty { listOf("Español") },
                zone = effectiveZone,
                active = beaconActive,
                radiusKm = clampedRadius,
                photoData = resolvedPhotoData,
                photoUrl = resolvedPhotoUrl,
                online = resolvedShowOnline,
            )
            _isBusy.value = false
            if (beaconResult.isSuccess) {
                _snackbarEvents.tryEmit("Tu perfil y baliza se han actualizado")
            } else {
                _snackbarEvents.tryEmit(
                    beaconResult.exceptionOrNull()?.localizedMessage ?: "Error al actualizar tu baliza"
                )
            }
        }
    }

    fun updateProfilePhotoFromUri(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isBusy.value = true
            val encodedRes = ChatMediaHelper.compressAvatarToBase64(appContext, uri)
            if (encodedRes.isFailure) {
                _isBusy.value = false
                _snackbarEvents.tryEmit(
                    encodedRes.exceptionOrNull()?.localizedMessage ?: "No se pudo procesar la foto de perfil"
                )
                return@launch
            }
            val base64 = encodedRes.getOrThrow()
            val updateRes = repository.updateProfilePhoto(
                userId = currentUserId,
                photoData = base64,
                photoUrl = "",
            )
            _isBusy.value = false
            if (updateRes.isSuccess) {
                _snackbarEvents.tryEmit("Foto de perfil actualizada")
            } else {
                _snackbarEvents.tryEmit("No se pudo guardar la foto de perfil")
            }
        }
    }

    fun useGoogleProfilePhoto() {
        if (initialPhotoUrl.isBlank()) {
            _snackbarEvents.tryEmit("Tu cuenta Google no tiene imagen pública disponible")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateProfilePhoto(
                userId = currentUserId,
                photoData = "",
                photoUrl = initialPhotoUrl,
            )
            _isBusy.value = false
            if (res.isSuccess) {
                _snackbarEvents.tryEmit("Usando imagen predeterminada de tu cuenta Google")
            } else {
                _snackbarEvents.tryEmit("No se pudo restaurar la foto de Google")
            }
        }
    }

    fun clearProfilePhoto() {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateProfilePhoto(
                userId = currentUserId,
                photoData = "",
                photoUrl = "",
            )
            _isBusy.value = false
            if (res.isSuccess) {
                _snackbarEvents.tryEmit("Foto retirada: ahora usas tu halo de iniciales")
            }
        }
    }

    // ---------- Privacidad: precisión y modo fantasma ----------

    val ghostUntilMs: StateFlow<Long> = com.example.data.PrivacySettings.ghostUntilMs
    val locationPrecision: StateFlow<com.example.data.PrivacySettings.Precision> =
        com.example.data.PrivacySettings.precisionState

    private var ghostJob: Job? = null

    private fun scheduleGhostRestore() {
        ghostJob?.cancel()
        val until = com.example.data.PrivacySettings.ghostUntilMs.value
        if (until <= 0L) return
        ghostJob = viewModelScope.launch {
            val wait = until - System.currentTimeMillis()
            if (wait > 0) kotlinx.coroutines.delay(wait)
            if (com.example.data.PrivacySettings.ghostUntilMs.value == until) {
                com.example.data.PrivacySettings.clearGhost()
                repository.setBeaconActive(currentUserId, true)
                _snackbarEvents.tryEmit("Modo fantasma terminado: vuelves a ser visible")
            }
        }
    }

    /** Se invoca al iniciar: si el modo fantasma quedó pendiente, lo reanuda o lo termina. */
    fun resumeGhostIfNeeded() = scheduleGhostRestore()

    /** [durationMs] = null desactiva el modo fantasma. */
    fun setGhostMode(context: Context, durationMs: Long?) {
        viewModelScope.launch {
            if (durationMs == null) {
                ghostJob?.cancel()
                com.example.data.PrivacySettings.clearGhost()
                repository.setBeaconActive(currentUserId, true)
                _snackbarEvents.tryEmit("Modo fantasma desactivado: vuelves a ser visible")
            } else {
                com.example.data.PrivacySettings.setGhostUntil(context, System.currentTimeMillis() + durationMs)
                repository.setBeaconActive(currentUserId, false)
                scheduleGhostRestore()
                _snackbarEvents.tryEmit("Modo fantasma activado: nadie te ve en el radar")
            }
        }
    }

    fun setLocationPrecision(context: Context, value: com.example.data.PrivacySettings.Precision) {
        com.example.data.PrivacySettings.setPrecision(context, value)
        viewModelScope.launch {
            // Republica la baliza con la nueva precisión.
            syncBeaconZoneIfExists(_currentZone.value)
        }
    }

    fun toggleBeaconVisibility(active: Boolean) {
        viewModelScope.launch {
            val currentBeacon = (myBeaconState.value as? UiState.Success)?.data
            val profile = (userProfileState.value as? UiState.Success)?.data
            val result = if (currentBeacon == null) {
                val name = profile?.displayName?.takeIf { it.length >= 2 }
                    ?: initialDisplayName.ifBlank { "Viajero Serafiel" }
                val color = AvatarColor.fromKey(profile?.avatarColor)
                val purpose = SocialPurpose.fromRaw(profile?.preferredPurposes?.firstOrNull())
                val photoData = profile?.photoData ?: ""
                val photoUrl = profile?.photoUrl ?: initialPhotoUrl
                val effectiveZone = if (_currentZone.value.hasValidCoords) {
                    _currentZone.value
                } else {
                    LocationHelper.fallbackZones.first()
                }
                repository.upsertMyBeacon(
                    userId = currentUserId,
                    displayName = name,
                    purpose = purpose,
                    statusText = "Abierto a conectar cerca",
                    avatarColor = color,
                    languages = listOf("Español"),
                    zone = effectiveZone,
                    active = active,
                    radiusKm = _radiusKm.value,
                    photoData = photoData,
                    photoUrl = photoUrl,
                )
            } else {
                repository.setBeaconActive(currentUserId, active)
            }

            if (result.isSuccess) {
                _snackbarEvents.tryEmit(
                    if (active) "Tu baliza ahora es visible en tu zona"
                    else "Modo pausa activado: eres invisible en el radar"
                )
            } else {
                _snackbarEvents.tryEmit("No se pudo cambiar la visibilidad")
            }
        }
    }

    fun sendZoneMessage(
        text: String,
        purpose: SocialPurpose,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToPreview: String? = null,
    ) {
        val profile = (userProfileState.value as? UiState.Success)?.data
        val beacon = (myBeaconState.value as? UiState.Success)?.data
        val senderName = profile?.displayName?.trim()?.takeIf { it.length >= 2 }
            ?: beacon?.displayName?.trim()?.takeIf { it.length >= 2 }
            ?: initialDisplayName.trim().takeIf { it.length >= 2 }
            ?: "Usuario Serafiel"
        val senderColor = AvatarColor.fromKey(profile?.avatarColor ?: beacon?.avatarColor)

        viewModelScope.launch {
            val result = repository.sendZoneMessage(
                userId = currentUserId,
                zoneKey = _currentZone.value.zoneKey,
                senderName = senderName,
                senderColor = senderColor,
                purpose = purpose,
                text = text,
                replyToMessageId = replyToMessageId,
                replyToSenderName = replyToSenderName,
                replyToPreview = replyToPreview,
            )
            if (result.isFailure) {
                _snackbarEvents.tryEmit(
                    result.exceptionOrNull()?.localizedMessage ?: "No se pudo enviar el mensaje"
                )
            }
        }
    }

    fun editZoneMessage(messageId: String, newText: String) {
        viewModelScope.launch {
            val res = repository.editZoneMessage(
                userId = currentUserId,
                zoneKey = _currentZone.value.zoneKey,
                messageId = messageId,
                newText = newText,
            )
            if (res.isFailure) {
                _snackbarEvents.tryEmit("No se pudo editar el mensaje")
            }
        }
    }

    fun archiveOwnZoneMessage(messageId: String) {
        viewModelScope.launch {
            val result = repository.archiveZoneMessage(currentUserId, _currentZone.value.zoneKey, messageId)
            if (result.isSuccess) {
                _snackbarEvents.tryEmit("Mensaje eliminado de la sala local")
            } else {
                _snackbarEvents.tryEmit("No se pudo eliminar el mensaje")
            }
        }
    }

    fun startDirectChat(
        peerId: String,
        peerName: String,
        peerColor: AvatarColor,
        purpose: SocialPurpose,
        initialGreeting: String,
        sendGreetingIfExists: Boolean = false,
        onChatReady: (String) -> Unit,
    ) {
        val profile = (userProfileState.value as? UiState.Success)?.data
        val beacon = (myBeaconState.value as? UiState.Success)?.data
        val myName = profile?.displayName?.trim()?.takeIf { it.length >= 2 }
            ?: beacon?.displayName?.trim()?.takeIf { it.length >= 2 }
            ?: initialDisplayName.trim().takeIf { it.length >= 2 }
            ?: "Usuario Serafiel"
        val myColor = AvatarColor.fromKey(profile?.avatarColor ?: beacon?.avatarColor)

        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.startOrOpenDirectChat(
                userId = currentUserId,
                myName = myName,
                myColor = myColor,
                peerId = peerId,
                peerName = peerName,
                peerColor = peerColor,
                purpose = purpose,
                zoneName = _currentZone.value.zoneName,
                initialGreeting = initialGreeting,
                sendGreetingIfExists = sendGreetingIfExists,
            )
            _isBusy.value = false
            result.onSuccess { chatId ->
                _selectedChatId.value = chatId
                onChatReady(chatId)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "No se pudo abrir la conversación")
            }
        }
    }

    private fun resolveDirectSenderName(chat: DirectChat): String {
        val profile = (userProfileState.value as? UiState.Success)?.data
        return profile?.displayName?.trim()?.takeIf { it.length >= 2 }
            ?: chat.participantNames[currentUserId]?.trim()?.takeIf { it.length >= 2 }
            ?: initialDisplayName.trim().takeIf { it.length >= 2 }
            ?: "Usuario Serafiel"
    }

    // ---------- Mensaje fijado ----------

    fun setPinnedMessage(isGroup: Boolean, parentId: String, messageId: String?, preview: String?) {
        viewModelScope.launch {
            repository.setPinnedMessage(currentUserId, isGroup, parentId, messageId, preview)
                .onSuccess { _snackbarEvents.tryEmit(if (messageId == null) "Mensaje desfijado" else "Mensaje fijado") }
                .onFailure { _snackbarEvents.tryEmit("No se pudo cambiar el mensaje fijado") }
        }
    }

    // ---------- Reenviar mensajes ----------

    sealed interface ForwardTarget {
        val id: String
        val title: String
        val colorKey: String

        data class Chat(val chat: DirectChat, val peerId: String) : ForwardTarget {
            override val id get() = "chat:${chat.id}"
            override val title get() = chat.participantNames[peerId]?.takeIf { it.isNotBlank() } ?: "Persona cercana"
            override val colorKey get() = chat.participantColors[peerId] ?: AvatarColor.AMBER.key
        }

        data class Group(val group: SocialGroup) : ForwardTarget {
            override val id get() = "group:${group.id}"
            override val title get() = group.name
            override val colorKey get() = group.colorKey
        }
    }

    private val _forwardRequest = MutableStateFlow<DirectMessage?>(null)
    val forwardRequest: StateFlow<DirectMessage?> = _forwardRequest.asStateFlow()

    fun requestForward(message: DirectMessage) { _forwardRequest.value = message }
    fun cancelForward() { _forwardRequest.value = null }

    /** Chats activos y grupos a los que se puede reenviar. */
    val forwardTargets: StateFlow<List<ForwardTarget>> = combine(myChatsState, myGroupsState) { chats, groups ->
        val chatTargets = ((chats as? UiState.Success)?.data.orEmpty())
            .filter { it.status == "active" }
            .mapNotNull { chat ->
                val peer = chat.participantIds.firstOrNull { it != currentUserId } ?: return@mapNotNull null
                ForwardTarget.Chat(chat, peer)
            }
        val groupTargets = ((groups as? UiState.Success)?.data.orEmpty()).map { ForwardTarget.Group(it) }
        (chatTargets + groupTargets)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun forwardMessage(context: Context, message: DirectMessage, targets: List<ForwardTarget>) {
        val appContext = context.applicationContext
        _forwardRequest.value = null
        if (targets.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            val isMedia = message.messageType in listOf(
                DirectMessage.TYPE_IMAGE, DirectMessage.TYPE_VOICE, DirectMessage.TYPE_AUDIO, DirectMessage.TYPE_VIDEO,
            )
            val type = if (message.messageType == DirectMessage.TYPE_LIVE_LOCATION) DirectMessage.TYPE_LOCATION else message.messageType
            var source: java.io.File? = null
            if (isMedia && message.chunkCount > 0L) {
                source = ChunkedMediaStore.resolveFullMedia(appContext, message)
                if (source == null) {
                    _isBusy.value = false
                    _snackbarEvents.tryEmit("No se pudo preparar el archivo para reenviar")
                    return@launch
                }
            }
            val preview = DirectMessage(
                messageType = type, text = message.text, durationSec = message.durationSec, mediaName = message.mediaName,
            ).previewLabel()
            var failures = 0
            for (target in targets.take(5)) {
                // Cada envío consume (borra) su archivo, así que se le entrega una copia propia.
                val copy = source?.let { src ->
                    runCatching {
                        java.io.File(appContext.cacheDir, "fwd_${System.nanoTime()}.tmp").also { src.copyTo(it, overwrite = true) }
                    }.getOrNull()
                }
                val res = when (target) {
                    is ForwardTarget.Chat -> repository.sendRichDirectMessage(
                        userId = currentUserId,
                        chatId = target.chat.id,
                        participantIds = target.chat.participantIds,
                        senderName = resolveDirectSenderName(target.chat),
                        messageType = type,
                        text = message.text,
                        mediaData = message.mediaData?.ifBlank { null },
                        mediaMimeType = message.mediaMimeType,
                        mediaName = message.mediaName,
                        durationSec = message.durationSec.takeIf { it > 0 },
                        latitude = message.latitude,
                        longitude = message.longitude,
                        locationLabel = message.locationLabel,
                        chatPreview = preview,
                        chunkFile = copy,
                        waveform = message.waveform.ifBlank { null },
                        forwarded = true,
                    )
                    is ForwardTarget.Group -> repository.sendGroupRichMessage(
                        userId = currentUserId,
                        groupId = target.group.id,
                        memberIds = target.group.memberIds,
                        senderName = resolveGroupSenderName(target.group),
                        messageType = type,
                        text = message.text,
                        mediaData = message.mediaData?.ifBlank { null },
                        mediaMimeType = message.mediaMimeType,
                        mediaName = message.mediaName,
                        durationSec = message.durationSec.takeIf { it > 0 },
                        latitude = message.latitude,
                        longitude = message.longitude,
                        locationLabel = message.locationLabel,
                        groupPreview = preview,
                        chunkFile = copy,
                        waveform = message.waveform.ifBlank { null },
                        forwarded = true,
                    )
                }
                if (res.isFailure) failures++
            }
            _isBusy.value = false
            _snackbarEvents.tryEmit(
                if (failures == 0) {
                    if (targets.size == 1) "Mensaje reenviado" else "Mensaje reenviado a ${targets.size} chats"
                } else {
                    "No se pudo reenviar a $failures de ${targets.size} destinos"
                }
            )
        }
    }

    fun emitMessage(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    fun sendDirectMessage(
        chat: DirectChat,
        text: String,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToPreview: String? = null,
    ) {
        val myName = resolveDirectSenderName(chat)
        viewModelScope.launch {
            val result = repository.sendDirectMessage(
                userId = currentUserId,
                chatId = chat.id,
                participantIds = chat.participantIds,
                senderName = myName,
                text = text,
                replyToMessageId = replyToMessageId,
                replyToSenderName = replyToSenderName,
                replyToPreview = replyToPreview,
            )
            if (result.isFailure) {
                _snackbarEvents.tryEmit("No se pudo enviar el mensaje privado")
            }
        }
    }

    fun editDirectMessage(chat: DirectChat, messageId: String, newText: String, isLatest: Boolean) {
        viewModelScope.launch {
            val res = repository.editDirectMessage(
                userId = currentUserId,
                chatId = chat.id,
                messageId = messageId,
                newText = newText,
                isLatestMessage = isLatest,
            )
            if (res.isFailure) {
                _snackbarEvents.tryEmit("No se pudo editar el mensaje")
            }
        }
    }

    /**
     * Oculta el mensaje y muestra "Mensaje eliminado · Deshacer". Solo se borra de
     * verdad si el aviso se cierra sin pulsar Deshacer.
     */
    fun deleteDirectMessage(chat: DirectChat, messageId: String, fallbackLastMessage: String?) {
        hideWithUndo(messageId) { commitDeleteDirectMessage(chat, messageId, fallbackLastMessage) }
    }

    fun deleteGroupMessage(group: SocialGroup, messageId: String, fallbackLastMessage: String?) {
        hideWithUndo(messageId) { commitDeleteGroupMessage(group, messageId, fallbackLastMessage) }
    }

    private fun hideWithUndo(messageId: String, commit: () -> Unit) {
        _hiddenMessageIds.value = _hiddenMessageIds.value + messageId
        var settled = false
        val emitted = _undoEvents.tryEmit(
            UndoEvent(
                message = "Mensaje eliminado",
                actionLabel = "Deshacer",
                onUndo = {
                    if (!settled) {
                        settled = true
                        _hiddenMessageIds.value = _hiddenMessageIds.value - messageId
                    }
                },
                onTimeout = {
                    if (!settled) {
                        settled = true
                        commit()
                    }
                },
            )
        )
        if (!emitted) {
            settled = true
            commit()
        }
    }

    private fun commitDeleteDirectMessage(chat: DirectChat, messageId: String, fallbackLastMessage: String?) {
        viewModelScope.launch {
            val res = repository.deleteDirectMessage(
                userId = currentUserId,
                chatId = chat.id,
                messageId = messageId,
                fallbackLastMessage = fallbackLastMessage,
            )
            _hiddenMessageIds.value = _hiddenMessageIds.value - messageId
            if (res.isFailure) {
                _snackbarEvents.tryEmit("No se pudo eliminar el mensaje")
            }
        }
    }

    fun sendDirectImage(context: Context, chat: DirectChat, uri: Uri, caption: String = "") {
        val myName = resolveDirectSenderName(chat)
        val appContext = context.applicationContext
        if (ChatVideoHelper.isVideo(appContext, uri)) {
            sendVideo(appContext, uri, caption) { payload, preview ->
                repository.sendRichDirectMessage(
                    userId = currentUserId,
                    chatId = chat.id,
                    participantIds = chat.participantIds,
                    senderName = myName,
                    messageType = DirectMessage.TYPE_VIDEO,
                    text = caption,
                    mediaData = payload.base64Data.ifBlank { null },
                    mediaMimeType = payload.mimeType,
                    mediaName = payload.displayName,
                    durationSec = payload.durationSec,
                    chatPreview = preview,
                    chunkFile = payload.fullFile,
                ).map { }
            }
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val encodedResult = ChatMediaHelper.compressImageToBase64(
                appContext,
                uri,
                hd = ChatMediaSettings.isSendHd(appContext),
            )
            if (encodedResult.isFailure) {
                _isBusy.value = false
                _snackbarEvents.tryEmit(
                    encodedResult.exceptionOrNull()?.localizedMessage ?: "No se pudo procesar la imagen"
                )
                return@launch
            }
            val media = encodedResult.getOrThrow()
            val preview = if (caption.isNotBlank()) "📷 ${caption.trim()}" else "📷 Imagen"
            val sendRes = repository.sendRichDirectMessage(
                userId = currentUserId,
                chatId = chat.id,
                participantIds = chat.participantIds,
                senderName = myName,
                messageType = DirectMessage.TYPE_IMAGE,
                text = caption,
                mediaData = media.base64Data,
                mediaMimeType = media.mimeType,
                mediaName = media.displayName,
                chatPreview = preview,
                chunkFile = media.fullFile,
            )
            _isBusy.value = false
            if (sendRes.isFailure) {
                _snackbarEvents.tryEmit("No se pudo enviar la imagen")
            }
        }
    }

    fun sendVoiceNotePayload(chat: DirectChat, payload: EncodedMediaPayload) {
        val myName = resolveDirectSenderName(chat)
        viewModelScope.launch {
            _isBusy.value = true
            val durText = DirectMessage.formatDuration(payload.durationSec)
            val sendRes = repository.sendRichDirectMessage(
                userId = currentUserId,
                chatId = chat.id,
                participantIds = chat.participantIds,
                senderName = myName,
                messageType = DirectMessage.TYPE_VOICE,
                text = "",
                mediaData = payload.base64Data.ifBlank { null },
                mediaMimeType = payload.mimeType,
                mediaName = payload.displayName,
                durationSec = payload.durationSec,
                chatPreview = "🎤 Mensaje de voz ($durText)",
                chunkFile = payload.fullFile,
                waveform = payload.waveform,
            )
            _isBusy.value = false
            if (sendRes.isFailure) {
                _snackbarEvents.tryEmit("No se pudo enviar el mensaje de voz")
            }
        }
    }

    fun sendDirectAudioUri(context: Context, chat: DirectChat, uri: Uri) {
        val myName = resolveDirectSenderName(chat)
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isBusy.value = true
            val encodedResult = ChatMediaHelper.readAudioToBase64(appContext, uri)
            if (encodedResult.isFailure) {
                _isBusy.value = false
                _snackbarEvents.tryEmit(
                    encodedResult.exceptionOrNull()?.localizedMessage ?: "No se pudo adjuntar el audio"
                )
                return@launch
            }
            val media = encodedResult.getOrThrow()
            val sendRes = repository.sendRichDirectMessage(
                userId = currentUserId,
                chatId = chat.id,
                participantIds = chat.participantIds,
                senderName = myName,
                messageType = DirectMessage.TYPE_AUDIO,
                text = "",
                mediaData = media.base64Data.ifBlank { null },
                mediaMimeType = media.mimeType,
                mediaName = media.displayName,
                durationSec = media.durationSec,
                chatPreview = "🎵 ${media.displayName}",
                chunkFile = media.fullFile,
            )
            _isBusy.value = false
            if (sendRes.isFailure) {
                _snackbarEvents.tryEmit("No se pudo enviar el archivo de audio")
            }
        }
    }

    private val _mediaUploadLabel = MutableStateFlow<String?>(null)
    /** Texto de progreso mientras se comprime/sube un video ("Comprimiendo video...", etc.). */
    val mediaUploadLabel: StateFlow<String?> = _mediaUploadLabel.asStateFlow()

    private fun sendVideo(
        appContext: Context,
        uri: Uri,
        caption: String,
        send: suspend (EncodedMediaPayload, String) -> Result<Unit>,
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val hd = ChatMediaSettings.isSendHd(appContext)
            _mediaUploadLabel.value = if (hd) "Comprimiendo video en HD..." else "Comprimiendo video..."
            val prepared = ChatVideoHelper.prepare(appContext, uri, hd)
            if (prepared.isFailure) {
                _isBusy.value = false
                _mediaUploadLabel.value = null
                _snackbarEvents.tryEmit(prepared.exceptionOrNull()?.localizedMessage ?: "No se pudo procesar el video")
                return@launch
            }
            val video = prepared.getOrThrow()
            if (video.wasTrimmed) {
                val maxSec = if (hd) ChatVideoHelper.HD_MAX_SEC else ChatVideoHelper.SD_MAX_SEC
                _snackbarEvents.tryEmit("El video se recortó a ${DirectMessage.formatDuration(maxSec)}")
            }
            _mediaUploadLabel.value = "Enviando video..."
            val preview = if (caption.isNotBlank()) "🎬 ${caption.trim()}" else "🎬 Video"
            val res = send(video.payload, preview)
            video.payload.fullFile?.delete()
            _isBusy.value = false
            _mediaUploadLabel.value = null
            if (res.isFailure) {
                _snackbarEvents.tryEmit("No se pudo enviar el video")
            }
        }
    }

    fun reactToDirectMessage(chat: DirectChat, messageId: String, emoji: String?) {
        viewModelScope.launch {
            repository.setMessageReaction(currentUserId, DirectMessage.SOURCE_CHATS, chat.id, messageId, emoji)
                .onFailure { _snackbarEvents.tryEmit("No se pudo reaccionar al mensaje") }
        }
    }

    fun reactToGroupMessage(group: SocialGroup, messageId: String, emoji: String?) {
        viewModelScope.launch {
            repository.setMessageReaction(currentUserId, DirectMessage.SOURCE_GROUPS, group.id, messageId, emoji)
                .onFailure { _snackbarEvents.tryEmit("No se pudo reaccionar al mensaje") }
        }
    }

    private var lastTypingPulseMs = 0L
    private var lastTypingChatId: String? = null
    private var typingStopJob: Job? = null

    /**
     * Llamar en cada cambio del texto del compositor. Envía un pulso de "escribiendo"
     * como mucho cada 4 s y lo borra tras 5 s sin teclear.
     */
    fun onComposerTyping(chat: DirectChat, hasText: Boolean) {
        if (!hasText) {
            stopTyping(chat)
            return
        }
        val now = System.currentTimeMillis()
        if (lastTypingChatId != chat.id || now - lastTypingPulseMs > 4_000L) {
            lastTypingPulseMs = now
            lastTypingChatId = chat.id
            viewModelScope.launch { repository.setTyping(currentUserId, chat.id, true) }
        }
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(5_000L)
            stopTyping(chat)
        }
    }

    fun stopTyping(chat: DirectChat) {
        typingStopJob?.cancel()
        if (lastTypingChatId == chat.id && lastTypingPulseMs != 0L) {
            lastTypingPulseMs = 0L
            viewModelScope.launch { repository.setTyping(currentUserId, chat.id, false) }
        }
    }

    private var lastReadMarkKey: String? = null

    /** Marca como vistos los mensajes del otro participante hasta [latestPeerMessageId]. */
    fun markChatRead(chat: DirectChat, latestPeerMessageId: String) {
        val key = "${chat.id}:$latestPeerMessageId"
        if (lastReadMarkKey == key) return
        lastReadMarkKey = key
        viewModelScope.launch {
            repository.markChatRead(currentUserId, chat.id).onFailure { lastReadMarkKey = null }
        }
    }

    fun sendCurrentLocation(
        context: Context,
        chat: DirectChat,
        customPoint: ChatLocationPoint? = null,
    ) {
        val myName = resolveDirectSenderName(chat)
        val appContext = context.applicationContext
        val fallbackZone = _currentZone.value
        viewModelScope.launch {
            _isBusy.value = true
            val point = customPoint ?: LocationHelper.fetchChatLocation(appContext, fallbackZone)
            val sendRes = repository.sendRichDirectMessage(
                userId = currentUserId,
                chatId = chat.id,
                participantIds = chat.participantIds,
                senderName = myName,
                messageType = DirectMessage.TYPE_LOCATION,
                text = "",
                latitude = point.latitude,
                longitude = point.longitude,
                locationLabel = point.label,
                liveActive = false,
                chatPreview = "📍 Ubicación: ${point.label.substringBefore('\n')}",
            )
            _isBusy.value = false
            if (sendRes.isFailure) {
                _snackbarEvents.tryEmit("No se pudo enviar la ubicación actual")
            }
        }
    }

    fun startLiveLocation(
        context: Context,
        chat: DirectChat,
        durationMinutes: Int = 60,
        comment: String = "",
        initialPoint: ChatLocationPoint? = null,
    ) {
        val myName = resolveDirectSenderName(chat)
        val appContext = context.applicationContext
        val fallbackZone = _currentZone.value
        val safeMins = durationMinutes.coerceIn(5, 480)
        val cleanComment = comment.trim().take(200)

        stopLiveLocation(notifyUser = false)

        liveLocationJob = viewModelScope.launch {
            _isBusy.value = true
            val resolvedPoint = initialPoint?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }
                ?: LocationHelper.fetchChatLocation(appContext, fallbackZone, forceFresh = true)
            val expiresAtMs = System.currentTimeMillis() + safeMins * 60_000L
            val durLabel = if (safeMins >= 60 && safeMins % 60 == 0) "${safeMins / 60} h" else "$safeMins min"

            val createRes = repository.sendRichDirectMessage(
                userId = currentUserId,
                chatId = chat.id,
                participantIds = chat.participantIds,
                senderName = myName,
                messageType = DirectMessage.TYPE_LIVE_LOCATION,
                text = cleanComment,
                latitude = resolvedPoint.latitude,
                longitude = resolvedPoint.longitude,
                locationLabel = resolvedPoint.label,
                liveActive = true,
                liveExpiresAtMs = expiresAtMs,
                chatPreview = if (cleanComment.isNotBlank()) "📍 En tiempo real: $cleanComment" else "📍 Ubicación en tiempo real ($durLabel)",
            )
            _isBusy.value = false

            val messageId = createRes.getOrNull()
            if (messageId == null) {
                _snackbarEvents.tryEmit("No se pudo iniciar la ubicación en tiempo real")
                return@launch
            }

            _liveLocationState.value = LiveLocationShareState(
                targetType = "chat",
                chatId = chat.id,
                messageId = messageId,
                expiresAtMs = expiresAtMs,
                remainingSec = ((expiresAtMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L),
                isActive = true,
            )
            _snackbarEvents.tryEmit("Compartiendo ubicación en tiempo real ($durLabel)")

            var lastKnownPoint = resolvedPoint
            var lastPushMs = System.currentTimeMillis()
            val trackerJob = launch {
                LocationHelper.observeHighAccuracyLocationFlow(appContext)
                    .conflate()
                    .collect { point ->
                        lastKnownPoint = point
                        val now = System.currentTimeMillis()
                        if (now < expiresAtMs && now - lastPushMs >= 4000L) {
                            lastPushMs = now
                            repository.updateLiveLocationMessage(
                                userId = currentUserId,
                                chatId = chat.id,
                                messageId = messageId,
                                latitude = point.latitude,
                                longitude = point.longitude,
                                locationLabel = point.label,
                                liveActive = true,
                            )
                        }
                    }
            }

            while (System.currentTimeMillis() < expiresAtMs) {
                delay(1000L)
                val remaining = ((expiresAtMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                _liveLocationState.value = _liveLocationState.value.copy(remainingSec = remaining)
            }
            trackerJob.cancel()

            repository.updateLiveLocationMessage(
                userId = currentUserId,
                chatId = chat.id,
                messageId = messageId,
                latitude = lastKnownPoint.latitude,
                longitude = lastKnownPoint.longitude,
                locationLabel = lastKnownPoint.label,
                liveActive = false,
            )
            _liveLocationState.value = LiveLocationShareState()
        }
    }

    fun stopLiveLocation(notifyUser: Boolean = true) {
        val current = _liveLocationState.value
        liveLocationJob?.cancel()
        liveLocationJob = null
        _liveLocationState.value = LiveLocationShareState()
        if (current.isActive && current.chatId.isNotBlank() && current.messageId.isNotBlank()) {
            viewModelScope.launch {
                val zone = _currentZone.value
                if (current.targetType == "group") {
                    repository.updateGroupLiveLocationMessage(
                        userId = currentUserId,
                        groupId = current.chatId,
                        messageId = current.messageId,
                        latitude = zone.approxLat,
                        longitude = zone.approxLon,
                        locationLabel = "Ubicación finalizada",
                        liveActive = false,
                    )
                } else {
                    repository.updateLiveLocationMessage(
                        userId = currentUserId,
                        chatId = current.chatId,
                        messageId = current.messageId,
                        latitude = zone.approxLat,
                        longitude = zone.approxLon,
                        locationLabel = "Ubicación finalizada",
                        liveActive = false,
                    )
                }
                if (notifyUser) {
                    _snackbarEvents.tryEmit("Has detenido la ubicación en tiempo real")
                }
            }
        }
    }

    // --- GROUPS ACTIONS ---

    private fun resolveMyNameAndColor(): Pair<String, AvatarColor> {
        val profile = (userProfileState.value as? UiState.Success)?.data
        val beacon = (myBeaconState.value as? UiState.Success)?.data
        val myName = profile?.displayName?.trim()?.takeIf { it.length >= 2 }
            ?: beacon?.displayName?.trim()?.takeIf { it.length >= 2 }
            ?: initialDisplayName.trim().takeIf { it.length >= 2 }
            ?: "Usuario Serafiel"
        val myColor = AvatarColor.fromKey(profile?.avatarColor ?: beacon?.avatarColor)
        return myName to myColor
    }

    fun createGroup(
        name: String,
        category: GroupCategory,
        color: AvatarColor,
        initialMembers: List<KnownContact>,
        onCreated: (String) -> Unit = {},
    ) {
        val (myName, myColor) = resolveMyNameAndColor()
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createGroup(
                userId = currentUserId,
                myName = myName,
                myColor = myColor,
                name = name,
                category = category,
                color = color,
                initialMembers = initialMembers,
            )
            _isBusy.value = false
            res.onSuccess { groupId ->
                _selectedGroupId.value = groupId
                _snackbarEvents.tryEmit("Grupo \"$name\" creado")
                onCreated(groupId)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "No se pudo crear el grupo")
            }
        }
    }

    fun updateGroup(
        group: SocialGroup,
        name: String,
        category: GroupCategory,
        color: AvatarColor,
        selectedMembers: List<KnownContact>,
    ) {
        val (myName, myColor) = resolveMyNameAndColor()
        val memberIds = linkedSetOf(group.creatorId)
        val memberNames = linkedMapOf<String, String>()
        val memberColors = linkedMapOf<String, String>()

        memberNames[group.creatorId] = if (group.creatorId == currentUserId) myName
        else group.memberNames[group.creatorId] ?: "Creador"
        memberColors[group.creatorId] = if (group.creatorId == currentUserId) myColor.key
        else group.memberColors[group.creatorId] ?: AvatarColor.AMBER.key

        selectedMembers.take(49).forEach { contact ->
            if (contact.userId.isNotBlank() && contact.userId != group.creatorId) {
                memberIds.add(contact.userId)
                memberNames[contact.userId] = contact.displayName
                memberColors[contact.userId] = contact.avatarColor
            }
        }

        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateGroup(
                userId = currentUserId,
                groupId = group.id,
                name = name,
                category = category,
                color = color,
                memberIds = memberIds.toList(),
                memberNames = memberNames,
                memberColors = memberColors,
            )
            _isBusy.value = false
            if (res.isSuccess) {
                _snackbarEvents.tryEmit("Grupo actualizado")
            } else {
                _snackbarEvents.tryEmit("No se pudo actualizar el grupo")
            }
        }
    }

    fun addContactToExistingGroup(group: SocialGroup, contact: KnownContact) {
        if (contact.userId in group.memberIds) {
            _selectedGroupId.value = group.id
            _snackbarEvents.tryEmit("${contact.displayName} ya está en ${group.name}")
            return
        }
        val newIds = (group.memberIds + contact.userId).distinct().take(50)
        val newNames = group.memberNames.toMutableMap().apply {
            this[contact.userId] = contact.displayName
        }
        val newColors = group.memberColors.toMutableMap().apply {
            this[contact.userId] = contact.avatarColor
        }
        viewModelScope.launch {
            val res = repository.updateGroup(
                userId = currentUserId,
                groupId = group.id,
                name = group.name,
                category = GroupCategory.fromKey(group.category),
                color = AvatarColor.fromKey(group.colorKey),
                memberIds = newIds,
                memberNames = newNames,
                memberColors = newColors,
            )
            if (res.isSuccess) {
                _selectedGroupId.value = group.id
                _snackbarEvents.tryEmit("${contact.displayName} añadido a ${group.name}")
            } else {
                _snackbarEvents.tryEmit("Solo el creador del grupo puede añadir miembros")
            }
        }
    }

    fun leaveOrDeleteGroup(group: SocialGroup) {
        viewModelScope.launch {
            val isCreator = group.creatorId == currentUserId
            val res = if (isCreator) {
                repository.deleteGroup(currentUserId, group.id)
            } else {
                repository.leaveGroup(currentUserId, group)
            }
            if (res.isSuccess) {
                if (_selectedGroupId.value == group.id) _selectedGroupId.value = null
                _snackbarEvents.tryEmit(if (isCreator) "Grupo eliminado" else "Saliste del grupo")
            } else {
                _snackbarEvents.tryEmit("No se pudo completar la acción")
            }
        }
    }

    private fun resolveGroupSenderName(group: SocialGroup): String {
        val profile = (userProfileState.value as? UiState.Success)?.data
        return profile?.displayName?.trim()?.takeIf { it.length >= 2 }
            ?: group.memberNames[currentUserId]?.trim()?.takeIf { it.length >= 2 }
            ?: initialDisplayName.trim().takeIf { it.length >= 2 }
            ?: "Usuario Serafiel"
    }

    fun sendGroupPoll(group: SocialGroup, question: String, options: List<String>) {
        val q = question.trim().take(200)
        val opts = options.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(6)
        if (q.isEmpty() || opts.size < 2) return
        val myName = resolveGroupSenderName(group)
        viewModelScope.launch {
            val res = repository.sendGroupRichMessage(
                userId = currentUserId,
                groupId = group.id,
                memberIds = group.memberIds,
                senderName = myName,
                messageType = DirectMessage.TYPE_POLL,
                text = q,
                groupPreview = "📊 $q",
                pollOptions = opts,
            )
            if (res.isFailure) _snackbarEvents.tryEmit("No se pudo crear la encuesta")
        }
    }

    fun voteGroupPoll(group: SocialGroup, messageId: String, optionIndex: Int?) {
        viewModelScope.launch {
            repository.setPollVote(currentUserId, group.id, messageId, optionIndex)
                .onFailure { _snackbarEvents.tryEmit("No se pudo registrar tu voto") }
        }
    }

    fun sendGroupTextMessage(
        group: SocialGroup,
        text: String,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToPreview: String? = null,
    ) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val myName = resolveGroupSenderName(group)
        viewModelScope.launch {
            val res = repository.sendGroupRichMessage(
                userId = currentUserId,
                groupId = group.id,
                memberIds = group.memberIds,
                senderName = myName,
                messageType = DirectMessage.TYPE_TEXT,
                text = clean,
                replyToMessageId = replyToMessageId,
                replyToSenderName = replyToSenderName,
                replyToPreview = replyToPreview,
                groupPreview = clean,
            )
            if (res.isFailure) {
                _snackbarEvents.tryEmit("No se pudo enviar el mensaje al grupo")
            }
        }
    }

    fun editGroupMessage(group: SocialGroup, messageId: String, newText: String, isLatest: Boolean) {
        val myName = resolveGroupSenderName(group)
        viewModelScope.launch {
            val res = repository.editGroupMessage(
                userId = currentUserId,
                groupId = group.id,
                messageId = messageId,
                senderName = myName,
                newText = newText,
                isLatestMessage = isLatest,
            )
            if (res.isFailure) {
                _snackbarEvents.tryEmit("No se pudo editar el mensaje del grupo")
            }
        }
    }

    private fun commitDeleteGroupMessage(group: SocialGroup, messageId: String, fallbackLastMessage: String?) {
        viewModelScope.launch {
            val res = repository.deleteGroupMessage(
                userId = currentUserId,
                groupId = group.id,
                messageId = messageId,
                fallbackLastMessage = fallbackLastMessage,
            )
            _hiddenMessageIds.value = _hiddenMessageIds.value - messageId
            if (res.isFailure) {
                _snackbarEvents.tryEmit("No se pudo eliminar el mensaje")
            }
        }
    }

    fun sendGroupImage(context: Context, group: SocialGroup, uri: Uri, caption: String = "") {
        val myName = resolveGroupSenderName(group)
        val appContext = context.applicationContext
        if (ChatVideoHelper.isVideo(appContext, uri)) {
            sendVideo(appContext, uri, caption) { payload, preview ->
                repository.sendGroupRichMessage(
                    userId = currentUserId,
                    groupId = group.id,
                    memberIds = group.memberIds,
                    senderName = myName,
                    messageType = DirectMessage.TYPE_VIDEO,
                    text = caption,
                    mediaData = payload.base64Data.ifBlank { null },
                    mediaMimeType = payload.mimeType,
                    mediaName = payload.displayName,
                    durationSec = payload.durationSec,
                    groupPreview = preview,
                    chunkFile = payload.fullFile,
                ).map { }
            }
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val encodedRes = ChatMediaHelper.compressImageToBase64(
                appContext,
                uri,
                hd = ChatMediaSettings.isSendHd(appContext),
            )
            if (encodedRes.isFailure) {
                _isBusy.value = false
                _snackbarEvents.tryEmit(
                    encodedRes.exceptionOrNull()?.localizedMessage ?: "No se pudo procesar la imagen"
                )
                return@launch
            }
            val media = encodedRes.getOrThrow()
            val preview = if (caption.isNotBlank()) "📷 ${caption.trim()}" else "📷 Imagen"
            val res = repository.sendGroupRichMessage(
                userId = currentUserId,
                groupId = group.id,
                memberIds = group.memberIds,
                senderName = myName,
                messageType = DirectMessage.TYPE_IMAGE,
                text = caption,
                mediaData = media.base64Data,
                mediaMimeType = media.mimeType,
                mediaName = media.displayName,
                groupPreview = preview,
                chunkFile = media.fullFile,
            )
            _isBusy.value = false
            if (res.isFailure) {
                _snackbarEvents.tryEmit("No se pudo enviar la imagen al grupo")
            }
        }
    }

    fun sendGroupVoiceNotePayload(group: SocialGroup, payload: EncodedMediaPayload) {
        val myName = resolveGroupSenderName(group)
        viewModelScope.launch {
            _isBusy.value = true
            val durText = DirectMessage.formatDuration(payload.durationSec)
            val res = repository.sendGroupRichMessage(
                userId = currentUserId,
                groupId = group.id,
                memberIds = group.memberIds,
                senderName = myName,
                messageType = DirectMessage.TYPE_VOICE,
                text = "",
                mediaData = payload.base64Data.ifBlank { null },
                mediaMimeType = payload.mimeType,
                mediaName = payload.displayName,
                durationSec = payload.durationSec,
                groupPreview = "🎤 Mensaje de voz ($durText)",
                chunkFile = payload.fullFile,
                waveform = payload.waveform,
            )
            _isBusy.value = false
            if (res.isFailure) {
                _snackbarEvents.tryEmit("No se pudo enviar la nota de voz")
            }
        }
    }

    fun sendGroupAudioUri(context: Context, group: SocialGroup, uri: Uri) {
        val myName = resolveGroupSenderName(group)
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isBusy.value = true
            val encodedRes = ChatMediaHelper.readAudioToBase64(appContext, uri)
            if (encodedRes.isFailure) {
                _isBusy.value = false
                _snackbarEvents.tryEmit(
                    encodedRes.exceptionOrNull()?.localizedMessage ?: "No se pudo adjuntar el audio"
                )
                return@launch
            }
            val media = encodedRes.getOrThrow()
            val res = repository.sendGroupRichMessage(
                userId = currentUserId,
                groupId = group.id,
                memberIds = group.memberIds,
                senderName = myName,
                messageType = DirectMessage.TYPE_AUDIO,
                text = "",
                mediaData = media.base64Data.ifBlank { null },
                mediaMimeType = media.mimeType,
                mediaName = media.displayName,
                durationSec = media.durationSec,
                groupPreview = "🎵 ${media.displayName}",
                chunkFile = media.fullFile,
            )
            _isBusy.value = false
            if (res.isFailure) {
                _snackbarEvents.tryEmit("No se pudo enviar el archivo de audio")
            }
        }
    }

    fun sendGroupCurrentLocation(
        context: Context,
        group: SocialGroup,
        customPoint: ChatLocationPoint? = null,
    ) {
        val myName = resolveGroupSenderName(group)
        val appContext = context.applicationContext
        val fallbackZone = _currentZone.value
        viewModelScope.launch {
            _isBusy.value = true
            val point = customPoint ?: LocationHelper.fetchChatLocation(appContext, fallbackZone)
            val res = repository.sendGroupRichMessage(
                userId = currentUserId,
                groupId = group.id,
                memberIds = group.memberIds,
                senderName = myName,
                messageType = DirectMessage.TYPE_LOCATION,
                text = "",
                latitude = point.latitude,
                longitude = point.longitude,
                locationLabel = point.label,
                liveActive = false,
                groupPreview = "📍 Ubicación: ${point.label.substringBefore('\n')}",
            )
            _isBusy.value = false
            if (res.isFailure) {
                _snackbarEvents.tryEmit("No se pudo compartir tu ubicación")
            }
        }
    }

    fun startGroupLiveLocation(
        context: Context,
        group: SocialGroup,
        durationMinutes: Int = 60,
        comment: String = "",
        initialPoint: ChatLocationPoint? = null,
    ) {
        val myName = resolveGroupSenderName(group)
        val appContext = context.applicationContext
        val fallbackZone = _currentZone.value
        val safeMins = durationMinutes.coerceIn(5, 480)
        val cleanComment = comment.trim().take(200)

        stopLiveLocation(notifyUser = false)

        liveLocationJob = viewModelScope.launch {
            _isBusy.value = true
            val resolvedPoint = initialPoint?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }
                ?: LocationHelper.fetchChatLocation(appContext, fallbackZone, forceFresh = true)
            val expiresAtMs = System.currentTimeMillis() + safeMins * 60_000L
            val durLabel = if (safeMins >= 60 && safeMins % 60 == 0) "${safeMins / 60} h" else "$safeMins min"

            val createRes = repository.sendGroupRichMessage(
                userId = currentUserId,
                groupId = group.id,
                memberIds = group.memberIds,
                senderName = myName,
                messageType = DirectMessage.TYPE_LIVE_LOCATION,
                text = cleanComment,
                latitude = resolvedPoint.latitude,
                longitude = resolvedPoint.longitude,
                locationLabel = resolvedPoint.label,
                liveActive = true,
                liveExpiresAtMs = expiresAtMs,
                groupPreview = if (cleanComment.isNotBlank()) "📍 En tiempo real: $cleanComment" else "📍 Ubicación en tiempo real ($durLabel)",
            )
            _isBusy.value = false

            val messageId = createRes.getOrNull()
            if (messageId == null) {
                _snackbarEvents.tryEmit("No se pudo iniciar la ubicación en tiempo real")
                return@launch
            }

            _liveLocationState.value = LiveLocationShareState(
                targetType = "group",
                chatId = group.id,
                messageId = messageId,
                expiresAtMs = expiresAtMs,
                remainingSec = ((expiresAtMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L),
                isActive = true,
            )
            _snackbarEvents.tryEmit("Compartiendo ubicación en el grupo ($durLabel)")

            var lastKnownPoint = resolvedPoint
            var lastPushMs = System.currentTimeMillis()
            val trackerJob = launch {
                LocationHelper.observeHighAccuracyLocationFlow(appContext)
                    .conflate()
                    .collect { point ->
                        lastKnownPoint = point
                        val now = System.currentTimeMillis()
                        if (now < expiresAtMs && now - lastPushMs >= 4000L) {
                            lastPushMs = now
                            repository.updateGroupLiveLocationMessage(
                                userId = currentUserId,
                                groupId = group.id,
                                messageId = messageId,
                                latitude = point.latitude,
                                longitude = point.longitude,
                                locationLabel = point.label,
                                liveActive = true,
                            )
                        }
                    }
            }

            while (System.currentTimeMillis() < expiresAtMs) {
                delay(1000L)
                val remaining = ((expiresAtMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                _liveLocationState.value = _liveLocationState.value.copy(remainingSec = remaining)
            }
            trackerJob.cancel()

            repository.updateGroupLiveLocationMessage(
                userId = currentUserId,
                groupId = group.id,
                messageId = messageId,
                latitude = lastKnownPoint.latitude,
                longitude = lastKnownPoint.longitude,
                locationLabel = lastKnownPoint.label,
                liveActive = false,
            )
            _liveLocationState.value = LiveLocationShareState()
        }
    }

    override fun onCleared() {
        liveLocationJob?.cancel()
        ChatAudioPlayer.stop()
        super.onCleared()
    }

    private fun snapshotAndStoreBlockedUser(targetUserId: String, fallbackName: String) {
        if (targetUserId.isBlank()) return
        val live = liveKnownDirectoryState.value[targetUserId]
        val existing = _cachedBlockedProfiles.value[targetUserId]
        val snapshot = KnownContact(
            userId = targetUserId,
            displayName = live?.displayName?.takeIf { it.isNotBlank() }
                ?: fallbackName.trim().takeIf { it.isNotBlank() }
                ?: existing?.displayName
                ?: "Usuario ${targetUserId.take(8)}...",
            avatarColor = live?.avatarColor ?: existing?.avatarColor ?: AvatarColor.AMBER.key,
            photoData = live?.photoData?.takeIf { it.isNotBlank() } ?: existing?.photoData,
            photoUrl = live?.photoUrl?.takeIf { it.isNotBlank() } ?: existing?.photoUrl,
            subtitle = live?.subtitle?.takeIf { it.isNotBlank() } ?: existing?.subtitle ?: "Persona bloqueada",
        )
        val updated = _cachedBlockedProfiles.value + (targetUserId to snapshot)
        _cachedBlockedProfiles.value = updated
        appPrefsContext?.let { ctx ->
            BlockedProfilePrefs.save(ctx, currentUserId, updated)
        }
    }

    private fun persistEnrichedBlockedProfiles(resolved: List<KnownContact>) {
        if (resolved.isEmpty()) return
        val currentMap = _cachedBlockedProfiles.value.toMutableMap()
        var changed = false
        resolved.forEach { contact ->
            val fallbackPrefix = "Usuario ${contact.userId.take(8)}"
            val hasMeaningfulInfo = !contact.displayName.startsWith(fallbackPrefix) ||
                !contact.photoData.isNullOrBlank() ||
                !contact.photoUrl.isNullOrBlank()
            if (hasMeaningfulInfo) {
                val prev = currentMap[contact.userId]
                if (prev == null ||
                    (prev.displayName.startsWith(fallbackPrefix) && !contact.displayName.startsWith(fallbackPrefix)) ||
                    (prev.photoData.isNullOrBlank() && !contact.photoData.isNullOrBlank()) ||
                    (prev.photoUrl.isNullOrBlank() && !contact.photoUrl.isNullOrBlank())
                ) {
                    currentMap[contact.userId] = contact
                    changed = true
                }
            }
        }
        if (changed) {
            _cachedBlockedProfiles.value = currentMap
            appPrefsContext?.let { ctx ->
                BlockedProfilePrefs.save(ctx, currentUserId, currentMap)
            }
        }
    }

    private fun fetchMissingBlockedProfiles(resolved: List<KnownContact>) {
        val missing = resolved.filter { contact ->
            contact.userId !in fetchedBlockedLookupAttempted &&
                (contact.photoData.isNullOrBlank() && contact.photoUrl.isNullOrBlank() ||
                    contact.displayName.startsWith("Usuario ${contact.userId.take(8)}"))
        }
        if (missing.isEmpty()) return
        missing.forEach { fetchedBlockedLookupAttempted.add(it.userId) }
        viewModelScope.launch {
            var updatedMap = _cachedBlockedProfiles.value
            var changed = false
            missing.forEach { entry ->
                val beacon = repository.fetchActiveBeaconSnapshot(entry.userId)
                if (beacon != null) {
                    val current = updatedMap[entry.userId] ?: entry
                    val merged = current.copy(
                        displayName = beacon.displayName.ifBlank { current.displayName },
                        avatarColor = beacon.avatarColor.ifBlank { current.avatarColor },
                        photoData = beacon.photoData?.takeIf { it.isNotBlank() } ?: current.photoData,
                        photoUrl = beacon.photoUrl?.takeIf { it.isNotBlank() } ?: current.photoUrl,
                        subtitle = beacon.zoneName.ifBlank { current.subtitle },
                    )
                    updatedMap = updatedMap + (entry.userId to merged)
                    changed = true
                }
            }
            if (changed) {
                _cachedBlockedProfiles.value = updatedMap
                appPrefsContext?.let { ctx ->
                    BlockedProfilePrefs.save(ctx, currentUserId, updatedMap)
                }
            }
        }
    }

    fun deleteDirectChats(chatIds: List<String>) {
        val cleanIds = chatIds.distinct().filter { it.isNotBlank() }
        if (cleanIds.isEmpty()) return
        if (_liveLocationState.value.isActive &&
            _liveLocationState.value.targetType == "chat" &&
            _liveLocationState.value.chatId in cleanIds
        ) {
            stopLiveLocation(notifyUser = false)
        }
        if (_selectedChatId.value in cleanIds) {
            _selectedChatId.value = null
        }
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteDirectChats(currentUserId, cleanIds)
            _isBusy.value = false
            if (result.isSuccess) {
                val count = result.getOrDefault(cleanIds.size)
                _snackbarEvents.tryEmit(
                    if (count == 1) "Conversación eliminada" else "$count conversaciones eliminadas"
                )
            } else {
                _snackbarEvents.tryEmit("No se pudieron eliminar las conversaciones seleccionadas")
            }
        }
    }

    fun blockUser(targetUserId: String, targetName: String) {
        snapshotAndStoreBlockedUser(targetUserId, targetName)
        viewModelScope.launch {
            val result = repository.setUserBlocked(currentUserId, targetUserId, blocked = true)
            if (result.isSuccess) {
                _snackbarEvents.tryEmit("$targetName fue bloqueado. Ya no verás sus mensajes ni baliza.")
            } else {
                _snackbarEvents.tryEmit("No se pudo bloquear al usuario")
            }
        }
    }

    fun unblockUser(targetUserId: String) {
        val updated = _cachedBlockedProfiles.value - targetUserId
        _cachedBlockedProfiles.value = updated
        appPrefsContext?.let { ctx ->
            BlockedProfilePrefs.save(ctx, currentUserId, updated)
        }
        viewModelScope.launch {
            val result = repository.setUserBlocked(currentUserId, targetUserId, blocked = false)
            if (result.isSuccess) {
                _snackbarEvents.tryEmit("Usuario desbloqueado")
            }
        }
    }

    fun submitSafetyReport(
        reportedUserId: String,
        reportedUserName: String,
        targetType: String,
        targetId: String,
        reason: ReportReason,
        details: String,
        alsoBlock: Boolean,
    ) {
        if (alsoBlock) {
            snapshotAndStoreBlockedUser(reportedUserId, reportedUserName)
        }
        viewModelScope.launch {
            val reportRes = repository.submitSafetyReport(
                userId = currentUserId,
                reportedUserId = reportedUserId,
                targetType = targetType,
                targetId = targetId,
                reason = reason,
                details = details,
            )
            if (alsoBlock) {
                repository.setUserBlocked(currentUserId, reportedUserId, blocked = true)
            }
            if (reportRes.isSuccess) {
                _snackbarEvents.tryEmit("Reporte enviado. Gracias por cuidar la comunidad de Serafiel.")
            } else {
                _snackbarEvents.tryEmit("No se pudo enviar el reporte")
            }
        }
    }

    private companion object {
        const val TAG = "SerafielVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val MIN_RADIUS_KM = 1
        const val MAX_RADIUS_KM = ProximityBeacon.GLOBAL_RADIUS_KM
        const val DEFAULT_RADIUS_KM = 50
    }
}

internal object BlockedProfilePrefs {
    private const val PREFS_NAME = "serafiel_blocked_profiles_v1"

    fun load(context: Context, ownerUserId: String): Map<String, KnownContact> {
        if (ownerUserId.isBlank()) return emptyMap()
        return try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString("blocked_$ownerUserId", null) ?: return emptyMap()
            decodeJson(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun save(context: Context, ownerUserId: String, entries: Map<String, KnownContact>) {
        if (ownerUserId.isBlank()) return
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString("blocked_$ownerUserId", encodeJson(entries)).apply()
        } catch (_: Exception) {
            // Ignore preference persistence errors
        }
    }

    fun encodeJson(entries: Map<String, KnownContact>): String {
        val arr = org.json.JSONArray()
        entries.values.forEach { contact ->
            val obj = org.json.JSONObject().apply {
                put("userId", contact.userId)
                put("displayName", contact.displayName)
                put("avatarColor", contact.avatarColor)
                if (!contact.photoData.isNullOrBlank()) put("photoData", contact.photoData)
                if (!contact.photoUrl.isNullOrBlank()) put("photoUrl", contact.photoUrl)
                if (contact.subtitle.isNotBlank()) put("subtitle", contact.subtitle)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decodeJson(raw: String): Map<String, KnownContact> {
        val map = linkedMapOf<String, KnownContact>()
        val arr = org.json.JSONArray(raw)
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val uid = obj.optString("userId", "").trim()
            if (uid.isBlank()) continue
            map[uid] = KnownContact(
                userId = uid,
                displayName = obj.optString("displayName", ""),
                avatarColor = obj.optString("avatarColor", AvatarColor.AMBER.key),
                photoData = obj.optString("photoData", "").takeIf { it.isNotBlank() },
                photoUrl = obj.optString("photoUrl", "").takeIf { it.isNotBlank() },
                subtitle = obj.optString("subtitle", ""),
            )
        }
        return map
    }
}
