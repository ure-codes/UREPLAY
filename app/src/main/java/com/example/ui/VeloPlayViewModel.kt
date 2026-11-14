package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.MediaMetadataHelper
import com.example.data.local.PlayerSettings
import com.example.data.local.SettingsDataStore
import com.example.data.local.VeloPlayDatabase
import com.example.data.local.VideoEntity
import com.example.data.model.VideoSourceType
import com.example.data.repository.VideoRepository
import com.example.network.HotspotServerState
import com.example.network.HotspotStreamServer
import com.example.player.PlaybackUiState
import com.example.player.PlayerManager
import com.example.ui.navigation.LibraryFilter
import com.example.ui.navigation.PlayerSheetState
import com.example.ui.util.extractFirstUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetectedBrowserVideo(
    val url: String,
    val title: String,
    val sourcePageUrl: String,
    val formatLabel: String
)

class VeloPlayViewModel(application: Application) : AndroidViewModel(application) {

    private val database = VeloPlayDatabase.getInstance(application)
    private val metadataHelper = MediaMetadataHelper(application)
    private val settingsDataStore = SettingsDataStore(application)
    private val repository = VideoRepository(
        videoDao = database.videoDao(),
        metadataHelper = metadataHelper,
        settingsDataStore = settingsDataStore
    )

    val playerManager = PlayerManager(application, repository)
    private val hotspotServer = HotspotStreamServer(application)

    val playbackState: StateFlow<PlaybackUiState> = playerManager.state
    val hotspotState: StateFlow<HotspotServerState> = hotspotServer.state

    val settings: StateFlow<PlayerSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerSettings())

    val allVideos: StateFlow<List<VideoEntity>> = repository.allVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deviceVideos: StateFlow<List<VideoEntity>> = repository.deviceVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val linkVideos: StateFlow<List<VideoEntity>> = repository.linkVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedFilter = MutableStateFlow(LibraryFilter.ALL)
    val selectedFilter: StateFlow<LibraryFilter> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredLibraryVideos: StateFlow<List<VideoEntity>> = combine(
        repository.allVideos,
        _selectedFilter,
        _searchQuery
    ) { videos, filter, query ->
        val byFilter = when (filter) {
            LibraryFilter.ALL -> videos
            LibraryFilter.DEVICE -> videos.filter { it.sourceType == VideoSourceType.DEVICE }
            LibraryFilter.LINKS -> videos.filter {
                it.sourceType == VideoSourceType.WEB_LINK || it.sourceType == VideoSourceType.HOTSPOT
            }
            LibraryFilter.HISTORY -> videos.filter { it.lastPlayedAt != null }
                .sortedByDescending { it.lastPlayedAt ?: 0L }
            LibraryFilter.SAVED -> videos.filter { it.isBookmarked }
        }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            byFilter
        } else {
            byFilter.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.uri.contains(trimmed, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _playerSheetState = MutableStateFlow(PlayerSheetState.HIDDEN)
    val playerSheetState: StateFlow<PlayerSheetState> = _playerSheetState.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    private val _detectedBrowserVideos = MutableStateFlow<List<DetectedBrowserVideo>>(emptyList())
    val detectedBrowserVideos: StateFlow<List<DetectedBrowserVideo>> = _detectedBrowserVideos.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            settings.collect { s ->
                playerManager.autoPlayNext = s.autoPlayNext
            }
        }
    }

    fun setFilter(filter: LibraryFilter) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun importLocalVideos(uris: List<Uri>, autoPlayFirst: Boolean = true) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = repository.importDeviceVideos(uris)
            if (imported.isNotEmpty()) {
                _statusMessage.value = if (imported.size == 1) {
                    "Added \"${imported.first().title}\""
                } else {
                    "Added ${imported.size} videos to Library"
                }
                if (autoPlayFirst) {
                    playVideo(imported.first())
                }
            }
        }
    }

    fun playVideo(video: VideoEntity, expandPlayer: Boolean = true) {
        val queue = allVideos.value.ifEmpty { listOf(video) }
        playerManager.playVideo(video, queue, settings.value.defaultSpeed)
        _playerSheetState.value = if (expandPlayer) PlayerSheetState.EXPANDED else PlayerSheetState.MINI
    }

    fun playStreamUrl(
        rawUrl: String,
        title: String? = null,
        sourceType: VideoSourceType = VideoSourceType.WEB_LINK,
        sourcePageUrl: String? = null,
        openExpanded: Boolean = true
    ) {
        val normalized = normalizeStreamUrl(rawUrl)
        if (normalized == null) {
            _statusMessage.value = "Enter a valid http:// or https:// video link"
            return
        }
        viewModelScope.launch {
            val video = repository.saveOrPrepareStreamVideo(
                url = normalized,
                title = title,
                sourceType = sourceType,
                sourcePageUrl = sourcePageUrl,
                bookmark = true
            )
            playVideo(video, expandPlayer = openExpanded)
        }
    }

    fun saveStreamUrl(
        rawUrl: String,
        title: String? = null,
        sourceType: VideoSourceType = VideoSourceType.WEB_LINK,
        sourcePageUrl: String? = null
    ) {
        val normalized = normalizeStreamUrl(rawUrl)
        if (normalized == null) {
            _statusMessage.value = "Enter a valid http:// or https:// video link"
            return
        }
        viewModelScope.launch {
            val video = repository.saveOrPrepareStreamVideo(
                url = normalized,
                title = title,
                sourceType = sourceType,
                sourcePageUrl = sourcePageUrl,
                bookmark = true
            )
            _statusMessage.value = "Saved \"${video.title}\""
        }
    }

    fun minimizePlayer() {
        _isFullscreen.value = false
        if (playbackState.value.currentVideo != null) {
            _playerSheetState.value = PlayerSheetState.MINI
        }
    }

    fun expandPlayer() {
        if (playbackState.value.currentVideo != null) {
            _playerSheetState.value = PlayerSheetState.EXPANDED
        }
    }

    fun closePlayer() {
        _isFullscreen.value = false
        _playerSheetState.value = PlayerSheetState.HIDDEN
        playerManager.stopAndClear()
    }

    fun setFullscreen(enabled: Boolean) {
        _isFullscreen.value = enabled
    }

    fun setInPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
        if (inPip && playbackState.value.currentVideo != null) {
            _playerSheetState.value = PlayerSheetState.EXPANDED
        }
    }

    fun onBrowserPageStarted() {
        _detectedBrowserVideos.value = emptyList()
    }

    fun addDetectedBrowserVideo(url: String, pageTitle: String, pageUrl: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) return

        val lower = trimmed.lowercase()
        val formatLabel = when {
            ".m3u8" in lower -> "HLS"
            ".mpd" in lower -> "DASH"
            ".webm" in lower -> "WEBM"
            ".mkv" in lower -> "MKV"
            ".mov" in lower -> "MOV"
            else -> "MP4"
        }
        val derivedTitle = Uri.parse(trimmed).lastPathSegment
            ?.substringBefore('?')
            ?.takeIf { it.length > 3 }
            ?: pageTitle.ifBlank { "Web Video" }

        _detectedBrowserVideos.update { current ->
            if (current.any { it.url == trimmed }) current
            else (listOf(DetectedBrowserVideo(trimmed, derivedTitle, pageUrl, formatLabel)) + current).take(20)
        }
    }

    fun clearDetectedBrowserVideos() {
        _detectedBrowserVideos.value = emptyList()
    }

    fun startHotspotShare(video: VideoEntity) {
        if (video.sourceType != VideoSourceType.DEVICE) {
            _statusMessage.value = "Select a local device video to share over Hotspot"
            return
        }
        hotspotServer.startSharing(
            uri = Uri.parse(video.uri),
            title = video.title,
            mimeType = video.mimeType
        )
    }

    fun stopHotspotShare() {
        hotspotServer.stopSharing()
    }

    fun toggleBookmark(video: VideoEntity) {
        viewModelScope.launch {
            repository.toggleBookmark(video)
        }
    }

    fun deleteVideo(video: VideoEntity) {
        viewModelScope.launch {
            if (playbackState.value.currentVideo?.uri == video.uri) {
                closePlayer()
            }
            repository.deleteVideo(video)
        }
    }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            repository.setDarkTheme(!settings.value.darkTheme)
        }
    }

    fun setAutoPlayNext(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoPlayNext(enabled)
        }
    }

    fun setAutoPipEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoPipEnabled(enabled)
        }
    }

    fun setDefaultSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
        viewModelScope.launch {
            repository.setDefaultSpeed(speed)
        }
    }

    fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (data.scheme == "http" || data.scheme == "https") {
                    playStreamUrl(data.toString())
                } else {
                    importLocalVideos(listOf(data), autoPlayFirst = true)
                }
            }
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                val extractedUrl = extractFirstUrl(sharedText)
                if (extractedUrl != null) {
                    playStreamUrl(extractedUrl)
                }
            }
        }
    }

    private fun normalizeStreamUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (!trimmed.contains("://")) "https://$trimmed" else trimmed
        return if (withScheme.startsWith("http://", ignoreCase = true) ||
            withScheme.startsWith("https://", ignoreCase = true)
        ) withScheme else null
    }

    override fun onCleared() {
        super.onCleared()
        hotspotServer.stopSharing()
        playerManager.release()
    }
}
