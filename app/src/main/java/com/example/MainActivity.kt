package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.LocationHelper
import com.example.data.SerafielRepository
import com.example.model.AvatarColor
import com.example.model.KnownContact
import com.example.model.PeerVisualInfo
import com.example.model.SocialPurpose
import com.example.model.UiState
import com.example.notifications.MessageListenerService
import com.example.notifications.NotificationSessionState
import com.example.notifications.SerafielNotificationHelper
import com.example.ui.screens.ForwardDialog
import com.example.ui.screens.LocalForwardHandler
import com.example.ui.screens.AddToGroupDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.AvatarCircle
import com.example.ui.screens.BeaconEditorSheet
import com.example.ui.screens.ChatsTabScreen
import com.example.ui.screens.GroupsTabScreen
import com.example.ui.screens.MentionCandidate
import com.example.ui.screens.ProfileSafetySheet
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.ZoneRoomScreen
import com.example.ui.theme.SerafielTheme
import com.example.ui.viewmodel.SerafielViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

const val ROUTE_RADAR = "radar"
const val ROUTE_ZONE_ROOM = "zone_room"
const val ROUTE_CHATS = "chats"
const val ROUTE_GROUPS = "groups"

data class PendingNotificationNav(
    val route: String,
    val chatId: String? = null,
    val groupId: String? = null,
)

class MainActivity : ComponentActivity() {
    private val pendingNotificationNav = MutableStateFlow<PendingNotificationNav?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.data.PrivacySettings.init(this)
        SerafielNotificationHelper.ensureChannels(this)
        SerafielNotificationHelper.clearLegacyForegroundNotification(this)
        handleNotificationIntent(intent)
        setContent {
            SerafielTheme {
                AppNavigation(
                    pendingNotificationNav = pendingNotificationNav,
                    onConsumedNotificationNav = { pendingNotificationNav.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val route = intent?.getStringExtra(SerafielNotificationHelper.EXTRA_OPEN_ROUTE) ?: return
        val chatId = intent.getStringExtra(SerafielNotificationHelper.EXTRA_OPEN_CHAT_ID)
        val groupId = intent.getStringExtra(SerafielNotificationHelper.EXTRA_OPEN_GROUP_ID)
        pendingNotificationNav.value = PendingNotificationNav(
            route = route,
            chatId = chatId,
            groupId = groupId,
        )
    }
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth = Firebase.auth,
    pendingNotificationNav: StateFlow<PendingNotificationNav?> = MutableStateFlow(null),
    onConsumedNotificationNav: () -> Unit = {},
) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            SerafielAuthenticatedShell(
                currentUserId = user.uid,
                initialDisplayName = user.displayName.orEmpty().ifBlank { "Viajero Serafiel" },
                initialPhotoUrl = user.photoUrl?.toString().orEmpty(),
                pendingNotificationNav = pendingNotificationNav,
                onConsumedNotificationNav = onConsumedNotificationNav,
            )
        }
    }
}

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SerafielAuthenticatedShell(
    currentUserId: String,
    initialDisplayName: String,
    initialPhotoUrl: String,
    pendingNotificationNav: StateFlow<PendingNotificationNav?>,
    onConsumedNotificationNav: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()

    val viewModel: SerafielViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SerafielViewModel(
                    repository = SerafielRepository(db),
                    currentUserId = currentUserId,
                    initialDisplayName = initialDisplayName,
                    initialPhotoUrl = initialPhotoUrl,
                    initialZone = LocationHelper.loadCachedGpsZone(app)
                        ?: LocationHelper.UNRESOLVED_DEFAULT_ZONE,
                )
            }
        }
    )

    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val myBeaconState by viewModel.myBeaconState.collectAsStateWithLifecycle()
    val nearbyBeaconsState by viewModel.nearbyBeaconsState.collectAsStateWithLifecycle()
    val zoneMessagesState by viewModel.zoneMessagesState.collectAsStateWithLifecycle()
    val myChatsState by viewModel.myChatsState.collectAsStateWithLifecycle()
    val selectedChatId by viewModel.selectedChatId.collectAsStateWithLifecycle()
    val selectedChatMessagesState by viewModel.selectedChatMessagesState.collectAsStateWithLifecycle()
    val myGroupsState by viewModel.myGroupsState.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val selectedGroupMessagesState by viewModel.selectedGroupMessagesState.collectAsStateWithLifecycle()
    val selectedGroupCategoryFilter by viewModel.selectedGroupCategoryFilter.collectAsStateWithLifecycle()
    val peerPhotosMap by viewModel.peerPhotosMapState.collectAsStateWithLifecycle()
    val peerVisualMap by viewModel.peerVisualMapState.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsersState.collectAsStateWithLifecycle()
    val knownContacts by viewModel.knownContactsState.collectAsStateWithLifecycle()
    val currentZone by viewModel.currentZone.collectAsStateWithLifecycle()
    val radiusKm by viewModel.radiusKm.collectAsStateWithLifecycle()
    val outsideRadiusCount by viewModel.outsideRadiusCountState.collectAsStateWithLifecycle()
    val selectedPurposeFilter by viewModel.selectedPurposeFilter.collectAsStateWithLifecycle()
    val isRefreshingLocation by viewModel.isRefreshingLocation.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val mediaUploadLabel by viewModel.mediaUploadLabel.collectAsStateWithLifecycle()
    val liveLocationState by viewModel.liveLocationState.collectAsStateWithLifecycle()
    val pendingNav by pendingNotificationNav.collectAsStateWithLifecycle()
    val ghostUntilMs by viewModel.ghostUntilMs.collectAsStateWithLifecycle()
    val locationPrecision by viewModel.locationPrecision.collectAsStateWithLifecycle()
    val appUpdate by com.example.data.AppUpdater.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { com.example.data.AppUpdater.check(silent = true) }
    LaunchedEffect(Unit) { viewModel.resumeGhostIfNeeded() }
    LaunchedEffect(appUpdate.status, appUpdate.info?.build) {
        if (appUpdate.status == com.example.data.AppUpdater.Status.AVAILABLE) {
            viewModel.emitMessage("Hay una versión nueva de Serafiel. Instálala desde Perfil → Actualizaciones")
        }
    }

    var hasLocationPermission by remember {
        mutableStateOf(LocationHelper.hasAnyLocationPermission(context))
    }
    var hasPreciseLocationPermission by remember {
        mutableStateOf(LocationHelper.hasPreciseLocationPermission(context))
    }

    var hasNotificationPermission by remember {
        mutableStateOf(SerafielNotificationHelper.hasNotificationPermission(context))
    }
    var hasPromptedNotificationPermission by remember { mutableStateOf(false) }
    var hasAskedRuntimeNotifOnce by remember { mutableStateOf(false) }
    var hasPromptedLocationPermission by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val granted = fineGranted || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted || LocationHelper.hasAnyLocationPermission(context)
        hasPreciseLocationPermission = fineGranted || LocationHelper.hasPreciseLocationPermission(context)
        if (hasLocationPermission) {
            viewModel.refreshDeviceLocation(context, silent = true)
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAskedRuntimeNotifOnce = true
        hasNotificationPermission = SerafielNotificationHelper.hasNotificationPermission(context)
        if (granted) {
            MessageListenerService.start(context)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Permiso de notificaciones denegado. Actívalo desde Ajustes para recibir avisos.")
            }
        }
        if (!hasLocationPermission && !hasPromptedLocationPermission) {
            hasPromptedLocationPermission = true
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    fun requestOrOpenNotificationSettings() {
        if (hasNotificationPermission) {
            SerafielNotificationHelper.openAppNotificationSettings(context)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !SerafielNotificationHelper.hasRuntimePermissionOnly(context)
        ) {
            val activity = context as? Activity
            val shouldShowRationale = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
            if (!hasAskedRuntimeNotifOnce || shouldShowRationale) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                SerafielNotificationHelper.openAppNotificationSettings(context)
            }
        } else {
            SerafielNotificationHelper.openAppNotificationSettings(context)
        }
    }

    DisposableEffect(lifecycleOwner, currentUserId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    NotificationSessionState.isAppInForeground = true
                    hasNotificationPermission = SerafielNotificationHelper.hasNotificationPermission(context)
                    hasLocationPermission = LocationHelper.hasAnyLocationPermission(context)
                    hasPreciseLocationPermission = LocationHelper.hasPreciseLocationPermission(context)
                    if (hasNotificationPermission) {
                        MessageListenerService.start(context)
                    }
                    if (hasLocationPermission && !currentZone.isFromGps) {
                        viewModel.refreshDeviceLocation(context, silent = true)
                    }
                    viewModel.updateOnlinePresence(true)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    NotificationSessionState.isAppInForeground = false
                    viewModel.updateOnlinePresence(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            NotificationSessionState.isAppInForeground = false
            viewModel.updateOnlinePresence(false)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(currentUserId) {
        viewModel.attachAppContext(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !SerafielNotificationHelper.hasRuntimePermissionOnly(context) &&
            !hasPromptedNotificationPermission
        ) {
            hasPromptedNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (hasNotificationPermission) {
                MessageListenerService.start(context)
            }
            if (!hasLocationPermission && !hasPromptedLocationPermission) {
                hasPromptedLocationPermission = true
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            }
        }
    }

    LaunchedEffect(hasLocationPermission, hasPreciseLocationPermission) {
        if (hasLocationPermission) {
            viewModel.refreshDeviceLocation(context, silent = true)
            LocationHelper.observeHighAccuracyLocationFlow(context)
                .conflate()
                .collect { point ->
                    viewModel.onForegroundHighAccuracyPoint(context, point)
                }
        }
    }

    LaunchedEffect(currentZone) {
        NotificationSessionState.updateZone(currentZone.zoneKey, currentZone.zoneName)
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.undoEvents.collect { event ->
            // Cada aviso con "Deshacer" se resuelve en su propia corrutina para no
            // bloquear los siguientes.
            launch {
                val result = snackbarHostState.showSnackbar(
                    message = event.message,
                    actionLabel = event.actionLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) event.onUndo() else event.onTimeout()
            }
        }
    }

    var showBeaconEditor by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var hasPromptedOnboarding by remember { mutableStateOf(false) }
    var contactToAddToGroup by remember { mutableStateOf<KnownContact?>(null) }

    val profile = (profileState as? UiState.Success)?.data
    val myBeacon = (myBeaconState as? UiState.Success)?.data
    val myEffectivePhotoData = profile?.photoData ?: myBeacon?.photoData
    val myEffectivePhotoUrl = profile?.photoUrl ?: myBeacon?.photoUrl ?: initialPhotoUrl
    val myShowOnline = profile?.showOnline ?: true
    val effectiveVisualMap = remember(
        peerVisualMap,
        currentUserId,
        myEffectivePhotoData,
        myEffectivePhotoUrl,
        myShowOnline,
        currentZone,
    ) {
        peerVisualMap + (
            currentUserId to PeerVisualInfo(
                photoData = myEffectivePhotoData,
                photoUrl = myEffectivePhotoUrl,
                isOnline = myShowOnline,
                distanceKm = 0.0,
                zoneName = currentZone.zoneName,
            )
        )
    }

    // Offer gentle first-time beacon setup if neither profile nor beacon exists yet
    LaunchedEffect(profileState, myBeaconState) {
        if (!hasPromptedOnboarding &&
            profileState is UiState.Success &&
            myBeaconState is UiState.Success &&
            (profileState as UiState.Success).data == null &&
            (myBeaconState as UiState.Success).data == null
        ) {
            hasPromptedOnboarding = true
            showBeaconEditor = true
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_RADAR

    LaunchedEffect(currentRoute, selectedChatId, selectedGroupId) {
        NotificationSessionState.currentRoute = currentRoute
        NotificationSessionState.currentlyOpenChatId =
            if (currentRoute == ROUTE_CHATS) selectedChatId else null
        NotificationSessionState.currentlyOpenGroupId =
            if (currentRoute == ROUTE_GROUPS) selectedGroupId else null
    }

    // Mensajes sin leer (no chats): suma de unreadCount propio en cada conversación.
    val unreadMessagesCount = ((myChatsState as? UiState.Success)?.data ?: emptyList())
        .sumOf { (it.unreadCount[currentUserId] ?: 0L).coerceAtLeast(0L) }

    val destinations = remember {
        listOf(
            NavDestination(
                route = ROUTE_RADAR,
                labelRes = R.string.nav_radar,
                selectedIcon = Icons.Rounded.Radar,
                unselectedIcon = Icons.Outlined.Radar,
                testTag = "nav_radar",
            ),
            NavDestination(
                route = ROUTE_ZONE_ROOM,
                labelRes = R.string.nav_zone_room,
                selectedIcon = Icons.Rounded.Forum,
                unselectedIcon = Icons.Outlined.Forum,
                testTag = "nav_zone_room",
            ),
            NavDestination(
                route = ROUTE_CHATS,
                labelRes = R.string.nav_chats,
                selectedIcon = Icons.Rounded.ChatBubble,
                unselectedIcon = Icons.Outlined.ChatBubbleOutline,
                testTag = "nav_chats",
            ),
            NavDestination(
                route = ROUTE_GROUPS,
                labelRes = R.string.nav_groups,
                selectedIcon = Icons.Rounded.Groups,
                unselectedIcon = Icons.Outlined.Groups,
                testTag = "nav_groups",
            ),
        )
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(pendingNav) {
        val nav = pendingNav ?: return@LaunchedEffect
        if (!nav.chatId.isNullOrBlank()) {
            viewModel.selectChat(nav.chatId)
        }
        if (!nav.groupId.isNullOrBlank()) {
            viewModel.selectGroup(nav.groupId)
        }
        navigateToTab(nav.route)
        onConsumedNotificationNav()
    }

    val displayName = profile?.displayName?.ifBlank { null }
        ?: myBeacon?.displayName?.ifBlank { null }
        ?: initialDisplayName
    val avatarColor = AvatarColor.fromKey(profile?.avatarColor ?: myBeacon?.avatarColor)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        androidx.compose.runtime.CompositionLocalProvider(
            LocalForwardHandler provides { msg -> viewModel.requestForward(msg) },
        ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {},
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.WbSunny,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    },
                    actions = {
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(48.dp)
                                .clickable { showProfileSheet = true }
                                .testTag("profile_avatar_button"),
                            contentAlignment = Alignment.Center,
                        ) {
                            AvatarCircle(
                                name = displayName,
                                avatarColor = avatarColor,
                                size = 38.dp,
                                verified = true,
                                isOnline = myShowOnline,
                                photoData = myEffectivePhotoData,
                                photoUrl = myEffectivePhotoUrl,
                            )
                        }
                    },
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (dest.route == ROUTE_CHATS) {
                                        viewModel.selectChat(null)
                                    } else if (dest.route == ROUTE_GROUPS) {
                                        viewModel.selectGroup(null)
                                    }
                                    navigateToTab(dest.route)
                                },
                                icon = {
                                    val iconVector = if (selected) dest.selectedIcon else dest.unselectedIcon
                                    if (dest.route == ROUTE_CHATS && unreadMessagesCount > 0) {
                                        BadgedBox(badge = { UnreadBadge(unreadMessagesCount) }) {
                                            Icon(iconVector, contentDescription = null)
                                        }
                                    } else {
                                        Icon(iconVector, contentDescription = null)
                                    }
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (dest.route == ROUTE_CHATS) {
                                        viewModel.selectChat(null)
                                    } else if (dest.route == ROUTE_GROUPS) {
                                        viewModel.selectGroup(null)
                                    }
                                    navigateToTab(dest.route)
                                },
                                icon = {
                                    val iconVector = if (selected) dest.selectedIcon else dest.unselectedIcon
                                    if (dest.route == ROUTE_CHATS && unreadMessagesCount > 0) {
                                        BadgedBox(badge = { UnreadBadge(unreadMessagesCount) }) {
                                            Icon(iconVector, contentDescription = null)
                                        }
                                    } else {
                                        Icon(iconVector, contentDescription = null)
                                    }
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_RADAR,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                    composable(ROUTE_RADAR) {
                        RadarScreen(
                            currentZone = currentZone,
                            hasLocationPermission = hasLocationPermission,
                            hasNotificationPermission = hasNotificationPermission,
                            isRefreshingLocation = isRefreshingLocation,
                            myBeacon = myBeacon,
                            radiusKm = radiusKm,
                            outsideRadiusCount = outsideRadiusCount,
                            nearbyBeaconsState = nearbyBeaconsState,
                            peerVisualMap = effectiveVisualMap,
                            selectedPurposeFilter = selectedPurposeFilter,
                            onRequestLocationRefresh = {
                                if (!hasLocationPermission) {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        )
                                    )
                                } else {
                                    if (!hasPreciseLocationPermission) {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                            )
                                        )
                                    }
                                    viewModel.refreshDeviceLocation(context)
                                }
                            },
                            onRequestNotificationPermission = ::requestOrOpenNotificationSettings,
                            onSelectManualZone = { zone -> viewModel.selectManualZone(zone) },
                            onToggleBeaconActive = { active -> viewModel.toggleBeaconVisibility(active) },
                            onRadiusChange = { km, persist -> viewModel.setBeaconRadiusKm(km, persist) },
                            onOpenBeaconEditor = { showBeaconEditor = true },
                            onSelectPurposeFilter = { filter -> viewModel.setPurposeFilter(filter) },
                            onSendGreeting = { peer, greeting ->
                                viewModel.startDirectChat(
                                    peerId = peer.userId,
                                    peerName = peer.displayName,
                                    peerColor = AvatarColor.fromKey(peer.avatarColor),
                                    purpose = SocialPurpose.fromRaw(peer.purpose),
                                    initialGreeting = greeting,
                                    sendGreetingIfExists = true,
                                    onChatReady = { navigateToTab(ROUTE_CHATS) },
                                )
                            },
                            onAddToGroup = { peer ->
                                contactToAddToGroup = KnownContact(
                                    userId = peer.userId,
                                    displayName = peer.displayName,
                                    avatarColor = peer.avatarColor,
                                    photoData = peer.photoData,
                                    photoUrl = peer.photoUrl,
                                    subtitle = peer.zoneName,
                                )
                            },
                            onBlockUser = { uid, name -> viewModel.blockUser(uid, name) },
                            onSubmitReport = { peer, reason, details, alsoBlock ->
                                viewModel.submitSafetyReport(
                                    reportedUserId = peer.userId,
                                    reportedUserName = peer.displayName,
                                    targetType = "beacon",
                                    targetId = peer.userId,
                                    reason = reason,
                                    details = details,
                                    alsoBlock = alsoBlock,
                                )
                            },
                            onGoToZoneRoom = { navigateToTab(ROUTE_ZONE_ROOM) },
                        )
                    }

                    composable(ROUTE_ZONE_ROOM) {
                        val nearbyBeacons = (nearbyBeaconsState as? UiState.Success)?.data.orEmpty()
                        val zoneMentionCandidates = remember(
                            nearbyBeacons,
                            knownContacts,
                            effectiveVisualMap,
                            currentUserId,
                        ) {
                            val map = linkedMapOf<String, MentionCandidate>()
                            nearbyBeacons.forEach { beacon ->
                                if (beacon.userId != currentUserId) {
                                    val visual = effectiveVisualMap[beacon.userId]
                                    map[beacon.userId] = MentionCandidate(
                                        userId = beacon.userId,
                                        displayName = beacon.displayName,
                                        avatarColorKey = beacon.avatarColor,
                                        isOnline = visual?.isOnline ?: beacon.isCurrentlyOnline(),
                                        photoData = visual?.photoData ?: beacon.photoData,
                                        photoUrl = visual?.photoUrl ?: beacon.photoUrl,
                                        subtitle = visual?.distanceKm?.let { "A ${LocationHelper.formatCompactDistance(it)}" }
                                            ?: beacon.zoneName,
                                    )
                                }
                            }
                            knownContacts.forEach { c ->
                                if (c.userId != currentUserId && c.userId !in map) {
                                    val visual = effectiveVisualMap[c.userId]
                                    map[c.userId] = MentionCandidate(
                                        userId = c.userId,
                                        displayName = c.displayName,
                                        avatarColorKey = c.avatarColor,
                                        isOnline = visual?.isOnline == true,
                                        photoData = visual?.photoData ?: c.photoData,
                                        photoUrl = visual?.photoUrl ?: c.photoUrl,
                                        subtitle = c.subtitle,
                                    )
                                }
                            }
                            map.values.sortedBy { it.displayName.lowercase() }
                        }
                        ZoneRoomScreen(
                            currentUserId = currentUserId,
                            currentZone = currentZone,
                            zoneMessagesState = zoneMessagesState,
                            peerVisualMap = effectiveVisualMap,
                            mentionCandidates = zoneMentionCandidates,
                            onSendMessage = { text, purpose, replyId, replySender, replyPreview ->
                                viewModel.sendZoneMessage(
                                    text = text,
                                    purpose = purpose,
                                    replyToMessageId = replyId,
                                    replyToSenderName = replySender,
                                    replyToPreview = replyPreview,
                                )
                            },
                            onEditOwnMessage = { msgId, newText ->
                                viewModel.editZoneMessage(msgId, newText)
                            },
                            onArchiveOwnMessage = { msgId -> viewModel.archiveOwnZoneMessage(msgId) },
                            onStartDirectChat = { msg ->
                                viewModel.startDirectChat(
                                    peerId = msg.senderId,
                                    peerName = msg.senderName,
                                    peerColor = AvatarColor.fromKey(msg.senderColor),
                                    purpose = SocialPurpose.fromRaw(msg.purpose),
                                    initialGreeting = "¡Hola ${msg.senderName}! Vi tu mensaje en la sala de ${currentZone.zoneName}.",
                                    onChatReady = { navigateToTab(ROUTE_CHATS) },
                                )
                            },
                            onBlockUser = { uid, name -> viewModel.blockUser(uid, name) },
                            onSubmitReport = { msg, reason, details, alsoBlock ->
                                viewModel.submitSafetyReport(
                                    reportedUserId = msg.senderId,
                                    reportedUserName = msg.senderName,
                                    targetType = "zone_message",
                                    targetId = msg.id.ifBlank { currentZone.zoneKey },
                                    reason = reason,
                                    details = details,
                                    alsoBlock = alsoBlock,
                                )
                            },
                        )
                    }

                    composable(ROUTE_CHATS) {
                        ChatsTabScreen(
                            currentUserId = currentUserId,
                            currentZone = currentZone,
                            myName = displayName,
                            myAvatarColor = avatarColor,
                            myPhotoData = myEffectivePhotoData,
                            myPhotoUrl = myEffectivePhotoUrl,
                            myChatsState = myChatsState,
                            selectedChatId = selectedChatId,
                            selectedChatMessagesState = selectedChatMessagesState,
                            peerVisualMap = effectiveVisualMap,
                            liveLocationState = liveLocationState,
                            onOpenChat = { chatId -> viewModel.selectChat(chatId) },
                            onCloseSelectedChatView = { viewModel.selectChat(null) },
                            onSendDirectMessage = { chat, text, replyId, replySender, replyPreview ->
                                viewModel.sendDirectMessage(
                                    chat = chat,
                                    text = text,
                                    replyToMessageId = replyId,
                                    replyToSenderName = replySender,
                                    replyToPreview = replyPreview,
                                )
                            },
                            onEditDirectMessage = { chat, msgId, newText, isLatest ->
                                viewModel.editDirectMessage(chat, msgId, newText, isLatest)
                            },
                            onDeleteDirectMessage = { chat, msgId, fallbackLast ->
                                viewModel.deleteDirectMessage(chat, msgId, fallbackLast)
                            },
                            onSendDirectImage = { chat, uri -> viewModel.sendDirectImage(context, chat, uri) },
                            onSendVoiceNote = { chat, payload -> viewModel.sendVoiceNotePayload(chat, payload) },
                            onSendAudioUri = { chat, uri -> viewModel.sendDirectAudioUri(context, chat, uri) },
                            onSendCurrentLocation = { chat, point -> viewModel.sendCurrentLocation(context, chat, point) },
                            onStartLiveLocation = { chat, mins, comment, point ->
                                viewModel.startLiveLocation(context, chat, mins, comment, point)
                            },
                            onLocationDetected = { point ->
                                viewModel.updateZoneFromChatPoint(context, point)
                            },
                            onStopLiveLocation = { viewModel.stopLiveLocation() },
                            onShowMessage = { msg -> viewModel.emitMessage(msg) },
                            onAddPeerToGroup = { peerId, peerName, peerColorKey ->
                                val photos = peerPhotosMap[peerId]
                                contactToAddToGroup = KnownContact(
                                    userId = peerId,
                                    displayName = peerName,
                                    avatarColor = peerColorKey,
                                    photoData = photos?.first,
                                    photoUrl = photos?.second,
                                    subtitle = "Chat 1:1",
                                )
                            },
                            onDeleteChats = { chatIds -> viewModel.deleteDirectChats(chatIds) },
                            onReactToMessage = { chat, msgId, emoji -> viewModel.reactToDirectMessage(chat, msgId, emoji) },
                            onComposerTyping = { chat, hasText -> viewModel.onComposerTyping(chat, hasText) },
                            onMarkChatRead = { chat, latestId -> viewModel.markChatRead(chat, latestId) },
                            mediaUploadLabel = mediaUploadLabel,
                            onBlockUser = { uid, name -> viewModel.blockUser(uid, name) },
                            onSubmitReport = { peerId, peerName, chatId, reason, details, alsoBlock ->
                                viewModel.submitSafetyReport(
                                    reportedUserId = peerId,
                                    reportedUserName = peerName,
                                    targetType = "chat",
                                    targetId = chatId,
                                    reason = reason,
                                    details = details,
                                    alsoBlock = alsoBlock,
                                )
                            },
                        )
                    }

                    composable(ROUTE_GROUPS) {
                        GroupsTabScreen(
                            currentUserId = currentUserId,
                            currentZone = currentZone,
                            myName = displayName,
                            myAvatarColor = avatarColor,
                            myPhotoData = myEffectivePhotoData,
                            myPhotoUrl = myEffectivePhotoUrl,
                            myGroupsState = myGroupsState,
                            selectedGroupId = selectedGroupId,
                            selectedGroupMessagesState = selectedGroupMessagesState,
                            selectedCategoryFilter = selectedGroupCategoryFilter,
                            knownContacts = knownContacts,
                            peerVisualMap = effectiveVisualMap,
                            liveLocationState = liveLocationState,
                            onSelectCategoryFilter = { cat -> viewModel.setGroupCategoryFilter(cat) },
                            onOpenGroup = { groupId -> viewModel.selectGroup(groupId) },
                            onCloseGroupView = { viewModel.selectGroup(null) },
                            onCreateGroup = { name, cat, color, members ->
                                viewModel.createGroup(name, cat, color, members)
                            },
                            onUpdateGroup = { group, name, cat, color, members ->
                                viewModel.updateGroup(group, name, cat, color, members)
                            },
                            onLeaveOrDeleteGroup = { group -> viewModel.leaveOrDeleteGroup(group) },
                            onSendGroupMessage = { group, text, replyId, replySender, replyPreview ->
                                viewModel.sendGroupTextMessage(
                                    group = group,
                                    text = text,
                                    replyToMessageId = replyId,
                                    replyToSenderName = replySender,
                                    replyToPreview = replyPreview,
                                )
                            },
                            onEditGroupMessage = { group, msgId, newText, isLatest ->
                                viewModel.editGroupMessage(group, msgId, newText, isLatest)
                            },
                            onDeleteGroupMessage = { group, msgId, fallbackLast ->
                                viewModel.deleteGroupMessage(group, msgId, fallbackLast)
                            },
                            onSendGroupImage = { group, uri -> viewModel.sendGroupImage(context, group, uri) },
                            onSendGroupVoiceNote = { group, payload -> viewModel.sendGroupVoiceNotePayload(group, payload) },
                            onSendGroupAudioUri = { group, uri -> viewModel.sendGroupAudioUri(context, group, uri) },
                            onSendGroupCurrentLocation = { group, point -> viewModel.sendGroupCurrentLocation(context, group, point) },
                            onStartGroupLiveLocation = { group, mins, comment, point ->
                                viewModel.startGroupLiveLocation(context, group, mins, comment, point)
                            },
                            onLocationDetected = { point ->
                                viewModel.updateZoneFromChatPoint(context, point)
                            },
                            onStopLiveLocation = { viewModel.stopLiveLocation() },
                            onShowMessage = { msg -> viewModel.emitMessage(msg) },
                            onReactToGroupMessage = { group, msgId, emoji -> viewModel.reactToGroupMessage(group, msgId, emoji) },
                            mediaUploadLabel = mediaUploadLabel,
                        )
                    }
                }
            }
        }
        }

        val forwardRequest by viewModel.forwardRequest.collectAsStateWithLifecycle()
        val forwardTargets by viewModel.forwardTargets.collectAsStateWithLifecycle()
        forwardRequest?.let { req ->
            ForwardDialog(
                message = req,
                targets = forwardTargets,
                onDismiss = { viewModel.cancelForward() },
                onForward = { chosen -> viewModel.forwardMessage(context, req, chosen) },
            )
        }

        // Avisos flotantes: centrados y compactos.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(18.dp),
                containerColor = androidx.compose.ui.graphics.Color(0xF02B2520),
                contentColor = androidx.compose.ui.graphics.Color.White,
                actionColor = androidx.compose.ui.graphics.Color(0xFFFFD54F),
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
    }

    if (showBeaconEditor) {
        BeaconEditorSheet(
            initialName = initialDisplayName,
            googlePhotoUrl = initialPhotoUrl,
            existingProfile = profile,
            existingBeacon = myBeacon,
            currentRadiusKm = radiusKm,
            isBusy = isBusy,
            onDismiss = { showBeaconEditor = false },
            onSave = { name, bio, color, preferredPurposes, beaconPurpose, statusText, languages, active, editedRadiusKm, photoData, photoUrl, showOnline ->
                viewModel.saveOnboardingOrProfile(
                    displayName = name,
                    bio = bio,
                    avatarColor = color,
                    preferredPurposes = preferredPurposes,
                    beaconPurpose = beaconPurpose,
                    beaconStatus = statusText,
                    languages = languages,
                    beaconActive = active,
                    radiusKm = editedRadiusKm,
                    photoData = photoData,
                    photoUrl = photoUrl,
                    showOnline = showOnline,
                )
                showBeaconEditor = false
            },
        )
    }

    if (showProfileSheet) {
        ProfileSafetySheet(
            displayName = displayName,
            googlePhotoUrl = initialPhotoUrl,
            profile = profile,
            beacon = myBeacon,
            blockedProfiles = blockedUsers,
            currentZone = currentZone,
            showOnline = myShowOnline,
            onToggleShowOnline = { enabled -> viewModel.setShowOnlinePreference(enabled) },
            ghostUntilMs = ghostUntilMs,
            locationPrecision = locationPrecision,
            onSetGhost = { ms -> viewModel.setGhostMode(context, ms) },
            onSetPrecision = { p -> viewModel.setLocationPrecision(context, p) },
            hasNotificationPermission = hasNotificationPermission,
            onRequestNotificationPermission = ::requestOrOpenNotificationSettings,
            onUploadPhoto = { uri -> viewModel.updateProfilePhotoFromUri(context, uri) },
            onUseGooglePhoto = { viewModel.useGoogleProfilePhoto() },
            onClearPhoto = { viewModel.clearProfilePhoto() },
            onDismiss = { showProfileSheet = false },
            onEditProfileBeacon = { showBeaconEditor = true },
            onUnblockUser = { blockedUid -> viewModel.unblockUser(blockedUid) },
            onSignOut = {
                showProfileSheet = false
                viewModel.updateOnlinePresence(false)
                MessageListenerService.stop(context)
                signOut(credentialManager = credentialManager, scope = scope)
            },
        )
    }

    contactToAddToGroup?.let { contact ->
        val groupsList = (myGroupsState as? UiState.Success)?.data.orEmpty()
        AddToGroupDialog(
            contact = contact,
            currentUserId = currentUserId,
            groups = groupsList,
            onDismiss = { contactToAddToGroup = null },
            onAddToExistingGroup = { group ->
                contactToAddToGroup = null
                viewModel.addContactToExistingGroup(group, contact)
                navigateToTab(ROUTE_GROUPS)
            },
            onCreateNewGroupWithContact = { name, category ->
                contactToAddToGroup = null
                viewModel.createGroup(
                    name = name,
                    category = category,
                    color = AvatarColor.fromKey(contact.avatarColor),
                    initialMembers = listOf(contact),
                    onCreated = { navigateToTab(ROUTE_GROUPS) },
                )
            },
        )
    }
}


/** Círculo rojo con el número de mensajes sin leer (99+ como máximo). */
@Composable
private fun UnreadBadge(count: Long) {
    Badge(
        containerColor = androidx.compose.ui.graphics.Color(0xFFE53935),
        contentColor = androidx.compose.ui.graphics.Color.White,
    ) {
        Text(if (count > 99) "99+" else count.toString())
    }
}
