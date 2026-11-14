package com.example.player

import android.content.Context
import android.net.Uri
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.local.VideoEntity
import com.example.data.repository.VideoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class VideoResizeMode(val label: String, val contentScale: ContentScale) {
    FIT("Fit", ContentScale.Fit),
    CROP("Zoom", ContentScale.Crop),
    FILL("Stretch", ContentScale.FillBounds)
}

data class PlaybackUiState(
    val currentVideo: VideoEntity? = null,
    val queue: List<VideoEntity> = emptyList(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val videoWidth: Int = 16,
    val videoHeight: Int = 9,
    val resizeMode: VideoResizeMode = VideoResizeMode.FIT,
    val errorMessage: String? = null
) {
    val hasNext: Boolean
        get() {
            val cur = currentVideo ?: return false
            val idx = queue.indexOfFirst { it.uri == cur.uri }
            return idx in 0 until (queue.size - 1)
        }

    val hasPrevious: Boolean
        get() {
            val cur = currentVideo ?: return false
            val idx = queue.indexOfFirst { it.uri == cur.uri }
            return idx > 0
        }
}

class PlayerManager(
    context: Context,
    private val repository: VideoRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    var autoPlayNext: Boolean = true
    private var progressJob: Job? = null
    private var lastSavedPositionMs = -1L

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressTicker()
            } else {
                saveProgressNow()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            val duration = exoPlayer.duration.takeIf { it > 0 } ?: _state.value.durationMs
            _state.update {
                it.copy(
                    isBuffering = buffering,
                    durationMs = duration,
                    bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                )
            }
            if (playbackState == Player.STATE_ENDED) {
                saveProgressNow(resetToZero = true)
                if (autoPlayNext && _state.value.hasNext) {
                    playNext()
                }
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                _state.update {
                    it.copy(videoWidth = videoSize.width, videoHeight = videoSize.height)
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    errorMessage = error.localizedMessage ?: "Unable to play this video source"
                )
            }
        }
    }

    init {
        exoPlayer.addListener(listener)
    }

    fun playVideo(video: VideoEntity, queue: List<VideoEntity> = emptyList(), speed: Float? = null) {
        val normalizedQueue = if (queue.any { it.uri == video.uri }) queue else listOf(video) + queue
        val targetSpeed = speed ?: _state.value.playbackSpeed

        _state.update {
            it.copy(
                currentVideo = video,
                queue = normalizedQueue,
                currentPositionMs = video.lastPositionMs,
                durationMs = video.durationMs,
                playbackSpeed = targetSpeed,
                errorMessage = null,
                isBuffering = true
            )
        }

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(video.uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(video.title)
                    .build()
            )
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.setPlaybackSpeed(targetSpeed)
        if (video.lastPositionMs > 3000L && (video.durationMs <= 0L || video.lastPositionMs < video.durationMs - 5000L)) {
            exoPlayer.seekTo(video.lastPositionMs)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        scope.launch {
            repository.updatePlaybackProgress(video.uri, video.lastPositionMs, video.durationMs)
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekTo(0L)
            exoPlayer.play()
        } else if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, (exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
        exoPlayer.seekTo(clamped)
        _state.update { it.copy(currentPositionMs = clamped) }
    }

    fun seekBy(deltaMs: Long) {
        seekTo(exoPlayer.currentPosition + deltaMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    fun cycleResizeMode() {
        val modes = VideoResizeMode.entries
        val next = modes[(modes.indexOf(_state.value.resizeMode) + 1) % modes.size]
        _state.update { it.copy(resizeMode = next) }
    }

    fun playNext() {
        val cur = _state.value.currentVideo ?: return
        val q = _state.value.queue
        val idx = q.indexOfFirst { it.uri == cur.uri }
        if (idx in 0 until (q.size - 1)) {
            playVideo(q[idx + 1], q)
        }
    }

    fun playPrevious() {
        if (exoPlayer.currentPosition > 5000L) {
            seekTo(0L)
            return
        }
        val cur = _state.value.currentVideo ?: return
        val q = _state.value.queue
        val idx = q.indexOfFirst { it.uri == cur.uri }
        if (idx > 0) {
            playVideo(q[idx - 1], q)
        } else {
            seekTo(0L)
        }
    }

    fun stopAndClear() {
        saveProgressNow()
        progressJob?.cancel()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _state.value = PlaybackUiState(playbackSpeed = _state.value.playbackSpeed)
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration.takeIf { it > 0 } ?: _state.value.durationMs
                val buf = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                _state.update {
                    it.copy(
                        currentPositionMs = pos,
                        durationMs = dur,
                        bufferedPositionMs = buf
                    )
                }
                if (kotlin.math.abs(pos - lastSavedPositionMs) >= 4000L) {
                    saveProgressNow()
                }
                delay(400L)
            }
        }
    }

    private fun saveProgressNow(resetToZero: Boolean = false) {
        val video = _state.value.currentVideo ?: return
        val pos = if (resetToZero) 0L else exoPlayer.currentPosition.coerceAtLeast(0L)
        val dur = exoPlayer.duration.takeIf { it > 0 } ?: video.durationMs
        lastSavedPositionMs = pos
        scope.launch {
            repository.updatePlaybackProgress(video.uri, pos, dur)
        }
    }

    fun release() {
        saveProgressNow()
        progressJob?.cancel()
        exoPlayer.removeListener(listener)
        exoPlayer.release()
    }
}
