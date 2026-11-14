package com.example.ui.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toRect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.example.data.local.VideoEntity
import com.example.data.model.VideoSourceType
import com.example.player.PipHelper
import com.example.player.PipHelper.findActivity
import com.example.player.PlaybackUiState
import com.example.player.PlayerManager
import com.example.ui.components.CompactQueueVideoRow
import com.example.ui.navigation.PlayerSheetState
import com.example.ui.util.formatDurationMs
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun WatchPlayerOverlay(
    playerManager: PlayerManager,
    playbackState: PlaybackUiState,
    sheetState: PlayerSheetState,
    isFullscreen: Boolean,
    isInPipMode: Boolean,
    autoPlayNext: Boolean,
    autoPipEnabled: Boolean,
    bottomNavPadding: Dp,
    onMinimize: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onToggleFullscreen: (Boolean) -> Unit,
    onSelectVideo: (VideoEntity) -> Unit,
    onToggleBookmark: (VideoEntity) -> Unit,
    onShareHotspot: (VideoEntity) -> Unit,
    onToggleAutoPlayNext: (Boolean) -> Unit,
    onDefaultSpeedChange: (Float) -> Unit
) {
    val currentVideo = playbackState.currentVideo ?: return
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var gestureHudIsBrightness by remember { mutableStateOf(true) }
    var lastSourceRect by remember { mutableStateOf<android.graphics.Rect?>(null) }

    val shouldAutoEnterPip = autoPipEnabled &&
        playbackState.isPlaying &&
        sheetState == PlayerSheetState.EXPANDED

    // Keep Android PiP params synced with video aspect ratio and playback state
    LaunchedEffect(
        playbackState.isPlaying,
        playbackState.videoWidth,
        playbackState.videoHeight,
        shouldAutoEnterPip,
        lastSourceRect
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            val params = PipHelper.buildPipParams(
                context = context,
                isPlaying = playbackState.isPlaying,
                videoWidth = playbackState.videoWidth,
                videoHeight = playbackState.videoHeight,
                sourceRectHint = lastSourceRect,
                autoEnterEnabled = shouldAutoEnterPip
            )
            runCatching { activity.setPictureInPictureParams(params) }
        }
    }

    // Handle fullscreen orientation & system bars
    DisposableEffect(isFullscreen, isInPipMode) {
        if (activity != null && !isInPipMode) {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (activity != null && isFullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val window = activity.window
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Auto-hide controls while playing
    LaunchedEffect(controlsVisible, playbackState.isPlaying, sheetState, isLocked) {
        if (controlsVisible && playbackState.isPlaying && sheetState == PlayerSheetState.EXPANDED && !isLocked) {
            delay(3500L)
            controlsVisible = false
        }
    }

    LaunchedEffect(seekFeedbackText) {
        if (seekFeedbackText != null) {
            delay(850L)
            seekFeedbackText = null
        }
    }

    LaunchedEffect(gestureHudText) {
        if (gestureHudText != null) {
            delay(900L)
            gestureHudText = null
        }
    }

    if (sheetState == PlayerSheetState.EXPANDED && !isInPipMode) {
        BackHandler {
            if (isFullscreen) {
                onToggleFullscreen(false)
            } else {
                onMinimize()
            }
        }
    }

    val pipModifier = Modifier.onGloballyPositioned { coords ->
        lastSourceRect = coords.boundsInWindow().toAndroidRectF().toRect()
    }

    // In PiP mode, render ONLY the full-bleed video surface with zero UI chrome
    if (isInPipMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            ContentFrame(
                player = playerManager.exoPlayer,
                modifier = Modifier.fillMaxSize(),
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                contentScale = playbackState.resizeMode.contentScale
            )
        }
        return
    }

    // Mini-Player docked right above Bottom Navigation Bar
    if (sheetState == PlayerSheetState.MINI) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (bottomNavPadding > 0.dp) {
                            Modifier.padding(bottom = bottomNavPadding)
                        } else {
                            Modifier.navigationBarsPadding()
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("mini_player_bar")
                    .clickable(onClick = onExpand),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
                shadowElevation = 10.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(114.dp)
                                .height(64.dp)
                                .background(Color.Black)
                                .then(pipModifier)
                        ) {
                            ContentFrame(
                                player = playerManager.exoPlayer,
                                modifier = Modifier.fillMaxSize(),
                                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                                contentScale = playbackState.resizeMode.contentScale
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentVideo.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (playbackState.isBuffering) "Buffering..." else {
                                    "${formatDurationMs(playbackState.currentPositionMs)} / ${
                                        if (playbackState.durationMs > 0L) formatDurationMs(playbackState.durationMs) else "--:--"
                                    }"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { playerManager.togglePlayPause() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("mini_player_play_pause")
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play"
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("mini_player_close")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close mini player"
                            )
                        }
                    }
                    if (playbackState.durationMs > 0L) {
                        val progress = (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat())
                            .coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
        return
    }

    // Expanded Watch Screen (Portrait YouTube-style or Fullscreen Landscape)
    if (sheetState == PlayerSheetState.EXPANDED) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("expanded_watch_screen"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!isFullscreen) Modifier.statusBarsPadding() else Modifier)
            ) {
                val viewportModifier = if (isFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                }

                // Video Viewport + Gestures + Controls
                Box(
                    modifier = viewportModifier
                        .background(Color.Black)
                        .then(pipModifier)
                        .pointerInput(isLocked) {
                            detectTapGestures(
                                onTap = { controlsVisible = !controlsVisible },
                                onDoubleTap = { offset ->
                                    if (!isLocked) {
                                        val isLeft = offset.x < size.width / 2f
                                        if (isLeft) {
                                            playerManager.seekBy(-10_000L)
                                            seekFeedbackText = "-10s"
                                        } else {
                                            playerManager.seekBy(10_000L)
                                            seekFeedbackText = "+10s"
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(isLocked, activity) {
                            if (isLocked) return@pointerInput
                            var accumulatedDrag = 0f
                            detectVerticalDragGestures(
                                onDragStart = { accumulatedDrag = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val isLeft = change.position.x < size.width / 2f
                                    val deltaRatio = -dragAmount / size.height.coerceAtLeast(1)
                                    if (isLeft && activity != null) {
                                        val lp = activity.window.attributes
                                        val currentB = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                        val nextB = (currentB + deltaRatio * 1.4f).coerceIn(0.05f, 1f)
                                        lp.screenBrightness = nextB
                                        activity.window.attributes = lp
                                        gestureHudIsBrightness = true
                                        gestureHudText = "${(nextB * 100).roundToInt()}%"
                                    } else {
                                        accumulatedDrag += deltaRatio
                                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        val stepThreshold = 0.08f
                                        if (kotlin.math.abs(accumulatedDrag) >= stepThreshold) {
                                            val deltaSteps = (accumulatedDrag / stepThreshold).toInt()
                                            accumulatedDrag -= deltaSteps * stepThreshold
                                            val nextVol = (curVol + deltaSteps).coerceIn(0, maxVol)
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                                            val pct = ((nextVol.toFloat() / maxVol.coerceAtLeast(1)) * 100).roundToInt()
                                            gestureHudIsBrightness = false
                                            gestureHudText = "$pct%"
                                        } else {
                                            val pct = ((curVol.toFloat() / maxVol.coerceAtLeast(1)) * 100).roundToInt()
                                            gestureHudIsBrightness = false
                                            gestureHudText = "$pct%"
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    ContentFrame(
                        player = playerManager.exoPlayer,
                        modifier = Modifier.fillMaxSize(),
                        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                        contentScale = playbackState.resizeMode.contentScale
                    )

                    if (playbackState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Error overlay if stream fails
                    if (playbackState.errorMessage != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.85f)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = playbackState.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Double-tap seek feedback HUD
                    androidx.compose.animation.AnimatedVisibility(
                        visible = seekFeedbackText != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Black.copy(alpha = 0.72f)
                        ) {
                            Text(
                                text = seekFeedbackText.orEmpty(),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                    }

                    // Swipe Brightness / Volume HUD
                    androidx.compose.animation.AnimatedVisibility(
                        visible = gestureHudText != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 18.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.76f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (gestureHudIsBrightness) Icons.Rounded.BrightnessHigh else Icons.Rounded.VolumeUp,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = gestureHudText.orEmpty(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Overlay Controls
                    androidx.compose.animation.AnimatedVisibility(
                        visible = controlsVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.65f),
                                            Color.Black.copy(alpha = 0.25f),
                                            Color.Black.copy(alpha = 0.75f)
                                        )
                                    )
                                )
                        ) {
                            if (isLocked) {
                                IconButton(
                                    onClick = { isLocked = false },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = "Unlock player controls",
                                        tint = Color.White
                                    )
                                }
                            } else {
                                // Top bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (isFullscreen) onToggleFullscreen(false) else onMinimize()
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("player_minimize_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = "Minimize player",
                                            tint = Color.White
                                        )
                                    }
                                    Text(
                                        text = currentVideo.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { isLocked = true },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.LockOpen,
                                            contentDescription = "Lock controls",
                                            tint = Color.White
                                        )
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        IconButton(
                                            onClick = {
                                                if (activity != null) {
                                                    val params = PipHelper.buildPipParams(
                                                        context = context,
                                                        isPlaying = playbackState.isPlaying,
                                                        videoWidth = playbackState.videoWidth,
                                                        videoHeight = playbackState.videoHeight,
                                                        sourceRectHint = lastSourceRect,
                                                        autoEnterEnabled = autoPipEnabled
                                                    )
                                                    runCatching { activity.enterPictureInPictureMode(params) }
                                                }
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("player_pip_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PictureInPictureAlt,
                                                contentDescription = "Enter Picture-in-Picture",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }

                                // Center playback buttons
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { playerManager.playPrevious() },
                                        enabled = playbackState.hasPrevious || playbackState.currentPositionMs > 3000L,
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SkipPrevious,
                                            contentDescription = "Previous video",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { playerManager.seekBy(-10_000L) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Replay10,
                                            contentDescription = "Rewind 10 seconds",
                                            tint = Color.White,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                        modifier = Modifier
                                            .size(56.dp)
                                            .testTag("player_play_pause_button")
                                            .clickable { playerManager.togglePlayPause() }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                                tint = Color.White,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { playerManager.seekBy(10_000L) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Forward10,
                                            contentDescription = "Forward 10 seconds",
                                            tint = Color.White,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { playerManager.playNext() },
                                        enabled = playbackState.hasNext,
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SkipNext,
                                            contentDescription = "Next video",
                                            tint = if (playbackState.hasNext) Color.White else Color.White.copy(alpha = 0.38f),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                // Bottom bar: timecodes + seekbar + speed + resize + fullscreen
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    val duration = playbackState.durationMs.coerceAtLeast(1L)
                                    var isDragging by remember { mutableStateOf(false) }
                                    var dragFraction by remember { mutableFloatStateOf(0f) }
                                    val currentFraction = if (isDragging) {
                                        dragFraction
                                    } else {
                                        (playbackState.currentPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                                    }

                                    Slider(
                                        value = currentFraction,
                                        onValueChange = {
                                            isDragging = true
                                            dragFraction = it
                                        },
                                        onValueChangeFinished = {
                                            isDragging = false
                                            playerManager.seekTo((dragFraction * duration).toLong())
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(28.dp)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${formatDurationMs(playbackState.currentPositionMs)} / ${
                                                if (playbackState.durationMs > 0L) formatDurationMs(playbackState.durationMs) else "--:--"
                                            }",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.weight(1f))

                                        Box {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.White.copy(alpha = 0.16f),
                                                modifier = Modifier
                                                    .clickable { speedMenuOpen = true }
                                                    .testTag("player_speed_chip")
                                            ) {
                                                Text(
                                                    text = "${playbackState.playbackSpeed}x",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = speedMenuOpen,
                                                onDismissRequest = { speedMenuOpen = false }
                                            ) {
                                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                                    DropdownMenuItem(
                                                        text = { Text("${spd}x") },
                                                        onClick = {
                                                            speedMenuOpen = false
                                                            onDefaultSpeedChange(spd)
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = { playerManager.cycleResizeMode() },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AspectRatio,
                                                contentDescription = "Cycle video aspect resize (${playbackState.resizeMode.label})",
                                                tint = Color.White
                                            )
                                        }

                                        IconButton(
                                            onClick = { onToggleFullscreen(!isFullscreen) },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("player_fullscreen_button")
                                        ) {
                                            Icon(
                                                imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                                contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Portrait below-video details + YouTube-style Up Next queue
                if (!isFullscreen) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = currentVideo.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = buildList {
                                        add(
                                            when (currentVideo.sourceType) {
                                                VideoSourceType.DEVICE -> "Device Memory"
                                                VideoSourceType.WEB_LINK -> "Web Stream"
                                                VideoSourceType.HOTSPOT -> "Hotspot Stream"
                                            }
                                        )
                                        if (currentVideo.resolution.isNotBlank()) add(currentVideo.resolution)
                                        add("Resize: ${playbackState.resizeMode.label}")
                                    }.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    AssistChip(
                                        onClick = {
                                            if (activity != null) {
                                                val params = PipHelper.buildPipParams(
                                                    context = context,
                                                    isPlaying = playbackState.isPlaying,
                                                    videoWidth = playbackState.videoWidth,
                                                    videoHeight = playbackState.videoHeight,
                                                    sourceRectHint = lastSourceRect,
                                                    autoEnterEnabled = autoPipEnabled
                                                )
                                                runCatching { activity.enterPictureInPictureMode(params) }
                                            }
                                        },
                                        label = { Text("PiP Mode") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                    )
                                }
                                AssistChip(
                                    onClick = { onToggleBookmark(currentVideo) },
                                    label = { Text(if (currentVideo.isBookmarked) "Saved" else "Save") },
                                    leadingIcon = {
                                        Icon(
                                            if (currentVideo.isBookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                                AssistChip(
                                    onClick = { speedMenuOpen = true },
                                    label = { Text("Speed ${playbackState.playbackSpeed}x") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                                if (currentVideo.sourceType == VideoSourceType.DEVICE) {
                                    AssistChip(
                                        onClick = { onShareHotspot(currentVideo) },
                                        label = { Text("Hotspot Share") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.WifiTethering, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                    )
                                }
                            }
                        }

                        item {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Up Next Queue",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Auto-play next video in queue",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = autoPlayNext,
                                        onCheckedChange = onToggleAutoPlayNext
                                    )
                                }
                            }
                        }

                        items(playbackState.queue, key = { it.uri }) { item ->
                            CompactQueueVideoRow(
                                video = item,
                                isCurrent = item.uri == currentVideo.uri,
                                onClick = { onSelectVideo(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}
