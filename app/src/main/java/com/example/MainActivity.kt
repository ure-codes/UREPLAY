package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.VideoSourceType
import com.example.player.PipHelper
import com.example.ui.VeloPlayViewModel
import com.example.ui.navigation.BrowseRoute
import com.example.ui.navigation.HotspotRoute
import com.example.ui.navigation.LibraryRoute
import com.example.ui.navigation.LinksRoute
import com.example.ui.navigation.PlayerSheetState
import com.example.ui.player.WatchPlayerOverlay
import com.example.ui.screens.BrowseScreen
import com.example.ui.screens.HotspotScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.LinksScreen
import com.example.ui.theme.VeloPlayTheme

private data class NavTab(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopTabs = listOf(
    NavTab(LibraryRoute, R.string.nav_library, Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary, "tab_library"),
    NavTab(BrowseRoute, R.string.nav_browse, Icons.Rounded.Language, Icons.Outlined.Language, "tab_browse"),
    NavTab(LinksRoute, R.string.nav_links, Icons.Rounded.Link, Icons.Outlined.Link, "tab_links"),
    NavTab(HotspotRoute, R.string.nav_hotspot, Icons.Rounded.WifiTethering, Icons.Outlined.WifiTethering, "tab_hotspot")
)

class MainActivity : ComponentActivity() {

    private val viewModel: VeloPlayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            viewModel.handleIncomingIntent(intent)
        }

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            // Observe Android PiP Mode transitions
            DisposableEffect(this) {
                val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
                    viewModel.setInPipMode(info.isInPictureInPictureMode)
                }
                addOnPictureInPictureModeChangedListener(listener)
                onDispose {
                    removeOnPictureInPictureModeChangedListener(listener)
                }
            }

            // Pre-Android 12 automatic PiP on leaving app while playing
            DisposableEffect(this) {
                val leaveHintListener = Runnable {
                    val state = viewModel.playbackState.value
                    val shouldPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                        viewModel.settings.value.autoPipEnabled &&
                        state.isPlaying &&
                        viewModel.playerSheetState.value == PlayerSheetState.EXPANDED
                    if (shouldPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val params = PipHelper.buildPipParams(
                            context = this@MainActivity,
                            isPlaying = state.isPlaying,
                            videoWidth = state.videoWidth,
                            videoHeight = state.videoHeight,
                            sourceRectHint = null,
                            autoEnterEnabled = false
                        )
                        runCatching { enterPictureInPictureMode(params) }
                    }
                }
                addOnUserLeaveHintListener(leaveHintListener)
                onDispose {
                    removeOnUserLeaveHintListener(leaveHintListener)
                }
            }

            // Receive PiP remote action button clicks (Rewind, Play/Pause, Forward)
            DisposableEffect(this) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action != PipHelper.ACTION_PIP_CONTROL) return
                        when (intent.getIntExtra(PipHelper.EXTRA_CONTROL_TYPE, -1)) {
                            PipHelper.CONTROL_REWIND -> viewModel.playerManager.seekBy(-10_000L)
                            PipHelper.CONTROL_PLAY_PAUSE -> viewModel.playerManager.togglePlayPause()
                            PipHelper.CONTROL_FORWARD -> viewModel.playerManager.seekBy(10_000L)
                        }
                    }
                }
                val filter = IntentFilter(PipHelper.ACTION_PIP_CONTROL)
                ContextCompat.registerReceiver(
                    this@MainActivity,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                onDispose {
                    unregisterReceiver(receiver)
                }
            }

            VeloPlayTheme(darkTheme = settings.darkTheme) {
                VeloPlayApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIncomingIntent(intent)
    }
}

@Composable
fun VeloPlayApp(viewModel: VeloPlayViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val libraryVideos by viewModel.filteredLibraryVideos.collectAsStateWithLifecycle()
    val deviceVideos by viewModel.deviceVideos.collectAsStateWithLifecycle()
    val linkVideos by viewModel.linkVideos.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val playerSheetState by viewModel.playerSheetState.collectAsStateWithLifecycle()
    val isFullscreen by viewModel.isFullscreen.collectAsStateWithLifecycle()
    val isInPipMode by viewModel.isInPipMode.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val detectedBrowserVideos by viewModel.detectedBrowserVideos.collectAsStateWithLifecycle()
    val hotspotState by viewModel.hotspotState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val miniPlayerClearance = if (playerSheetState == PlayerSheetState.MINI) 78.dp else 0.dp
    var navBarHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navBarHeightDp = with(density) { navBarHeightPx.toDp() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.navigationBars,
            bottomBar = {
                if (!useNavRail && !isInPipMode && !(playerSheetState == PlayerSheetState.EXPANDED && isFullscreen)) {
                    NavigationBar(
                        modifier = Modifier.onSizeChanged { navBarHeightPx = it.height }
                    ) {
                        TopTabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && !isInPipMode && !(playerSheetState == PlayerSheetState.EXPANDED && isFullscreen)) {
                    NavigationRail {
                        TopTabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = LibraryRoute,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable<LibraryRoute> {
                            LibraryScreen(
                                videos = libraryVideos,
                                selectedFilter = selectedFilter,
                                searchQuery = searchQuery,
                                currentPlayingUri = playbackState.currentVideo?.uri,
                                darkTheme = settings.darkTheme,
                                bottomExtraPadding = miniPlayerClearance,
                                onSelectFilter = viewModel::setFilter,
                                onSearchQueryChange = viewModel::setSearchQuery,
                                onToggleTheme = viewModel::toggleDarkTheme,
                                onImportUris = { uris -> viewModel.importLocalVideos(uris, autoPlayFirst = true) },
                                onPlayVideo = { video, expand -> viewModel.playVideo(video, expand) },
                                onPlayStreamUrl = { url, title ->
                                    viewModel.playStreamUrl(url, title, VideoSourceType.WEB_LINK)
                                },
                                onSaveStreamUrl = { url, title ->
                                    viewModel.saveStreamUrl(url, title, VideoSourceType.WEB_LINK)
                                },
                                onToggleBookmark = viewModel::toggleBookmark,
                                onShareHotspot = { video ->
                                    viewModel.startHotspotShare(video)
                                    navController.navigate(HotspotRoute) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onDeleteVideo = viewModel::deleteVideo
                            )
                        }

                        composable<BrowseRoute> {
                            BrowseScreen(
                                detectedVideos = detectedBrowserVideos,
                                bottomExtraPadding = miniPlayerClearance,
                                onPageStarted = viewModel::onBrowserPageStarted,
                                onVideoDetected = viewModel::addDetectedBrowserVideo,
                                onPlayDetectedVideo = { item, expand ->
                                    viewModel.playStreamUrl(
                                        rawUrl = item.url,
                                        title = item.title,
                                        sourceType = VideoSourceType.WEB_LINK,
                                        sourcePageUrl = item.sourcePageUrl,
                                        openExpanded = expand
                                    )
                                },
                                onSaveDetectedVideo = { item ->
                                    viewModel.saveStreamUrl(
                                        rawUrl = item.url,
                                        title = item.title,
                                        sourceType = VideoSourceType.WEB_LINK,
                                        sourcePageUrl = item.sourcePageUrl
                                    )
                                },
                                onClearDetectedVideos = viewModel::clearDetectedBrowserVideos
                            )
                        }

                        composable<LinksRoute> {
                            LinksScreen(
                                linkVideos = linkVideos,
                                currentPlayingUri = playbackState.currentVideo?.uri,
                                bottomExtraPadding = miniPlayerClearance,
                                onPlayUrl = { url, title ->
                                    viewModel.playStreamUrl(url, title, VideoSourceType.WEB_LINK)
                                },
                                onSaveUrl = { url, title ->
                                    viewModel.saveStreamUrl(url, title, VideoSourceType.WEB_LINK)
                                },
                                onPlayVideo = { video, expand -> viewModel.playVideo(video, expand) },
                                onToggleBookmark = viewModel::toggleBookmark,
                                onDeleteVideo = viewModel::deleteVideo
                            )
                        }

                        composable<HotspotRoute> {
                            HotspotScreen(
                                deviceVideos = deviceVideos,
                                hotspotState = hotspotState,
                                bottomExtraPadding = miniPlayerClearance,
                                onStartHotspotShare = viewModel::startHotspotShare,
                                onStopHotspotShare = viewModel::stopHotspotShare,
                                onPlayHotspotUrl = { url ->
                                    viewModel.playStreamUrl(url, "Hotspot Stream", VideoSourceType.HOTSPOT)
                                },
                                onImportDeviceVideos = { uris ->
                                    viewModel.importLocalVideos(uris, autoPlayFirst = false)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Persistent Mini-Player & Fullscreen/PiP Watch Experience layered over Scaffold
        if (playerSheetState != PlayerSheetState.HIDDEN && playbackState.currentVideo != null) {
            val miniBottomPadding = if (useNavRail) 0.dp else navBarHeightDp
            WatchPlayerOverlay(
                playerManager = viewModel.playerManager,
                playbackState = playbackState,
                sheetState = playerSheetState,
                isFullscreen = isFullscreen,
                isInPipMode = isInPipMode,
                autoPlayNext = settings.autoPlayNext,
                autoPipEnabled = settings.autoPipEnabled,
                bottomNavPadding = miniBottomPadding,
                onMinimize = viewModel::minimizePlayer,
                onExpand = viewModel::expandPlayer,
                onClose = viewModel::closePlayer,
                onToggleFullscreen = viewModel::setFullscreen,
                onSelectVideo = { video -> viewModel.playVideo(video, expandPlayer = true) },
                onToggleBookmark = viewModel::toggleBookmark,
                onShareHotspot = { video ->
                    viewModel.startHotspotShare(video)
                    viewModel.minimizePlayer()
                    navController.navigate(HotspotRoute) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onToggleAutoPlayNext = viewModel::setAutoPlayNext,
                onDefaultSpeedChange = viewModel::setDefaultSpeed
            )
        }
    }
}
