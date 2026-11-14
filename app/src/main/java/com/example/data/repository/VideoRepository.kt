package com.example.data.repository

import android.net.Uri
import com.example.data.local.MediaMetadataHelper
import com.example.data.local.PlayerSettings
import com.example.data.local.SettingsDataStore
import com.example.data.local.VideoDao
import com.example.data.local.VideoEntity
import com.example.data.model.VideoSourceType
import kotlinx.coroutines.flow.Flow

class VideoRepository(
    private val videoDao: VideoDao,
    private val metadataHelper: MediaMetadataHelper,
    private val settingsDataStore: SettingsDataStore
) {
    val allVideos: Flow<List<VideoEntity>> = videoDao.getAllVideos()
    val deviceVideos: Flow<List<VideoEntity>> = videoDao.getDeviceVideos()
    val linkVideos: Flow<List<VideoEntity>> = videoDao.getLinkVideos()
    val watchHistory: Flow<List<VideoEntity>> = videoDao.getWatchHistory()
    val bookmarkedVideos: Flow<List<VideoEntity>> = videoDao.getBookmarkedVideos()
    val settings: Flow<PlayerSettings> = settingsDataStore.settingsFlow

    suspend fun importDeviceVideos(uris: List<Uri>): List<VideoEntity> {
        val added = mutableListOf<VideoEntity>()
        for (uri in uris) {
            val existing = videoDao.getByUri(uri.toString())
            if (existing != null) {
                added.add(existing)
                continue
            }
            val entity = metadataHelper.extractVideoMetadata(
                uri = uri,
                sourceType = VideoSourceType.DEVICE
            )
            val id = videoDao.insertVideo(entity)
            added.add(entity.copy(id = id))
        }
        return added
    }

    suspend fun saveOrPrepareStreamVideo(
        url: String,
        title: String? = null,
        sourceType: VideoSourceType = VideoSourceType.WEB_LINK,
        sourcePageUrl: String? = null,
        bookmark: Boolean = true
    ): VideoEntity {
        val cleanUrl = url.trim()
        val existing = videoDao.getByUri(cleanUrl)
        if (existing != null) {
            val updated = existing.copy(
                title = title?.takeIf { it.isNotBlank() } ?: existing.title,
                isBookmarked = existing.isBookmarked || bookmark
            )
            videoDao.insertVideo(updated)
            return updated
        }
        val entity = metadataHelper.extractVideoMetadata(
            uri = Uri.parse(cleanUrl),
            sourceType = sourceType,
            explicitTitle = title,
            sourcePageUrl = sourcePageUrl
        ).copy(isBookmarked = bookmark)
        val id = videoDao.insertVideo(entity)
        return entity.copy(id = id)
    }

    suspend fun getByUri(uri: String): VideoEntity? = videoDao.getByUri(uri)

    suspend fun updatePlaybackProgress(uri: String, positionMs: Long, durationMs: Long) {
        videoDao.updatePlaybackProgress(uri, positionMs, durationMs)
    }

    suspend fun toggleBookmark(video: VideoEntity) {
        videoDao.setBookmarked(video.id, !video.isBookmarked)
    }

    suspend fun deleteVideo(video: VideoEntity) {
        videoDao.deleteById(video.id)
    }

    suspend fun setDarkTheme(enabled: Boolean) = settingsDataStore.setDarkTheme(enabled)
    suspend fun setAutoPlayNext(enabled: Boolean) = settingsDataStore.setAutoPlayNext(enabled)
    suspend fun setAutoPipEnabled(enabled: Boolean) = settingsDataStore.setAutoPipEnabled(enabled)
    suspend fun setDefaultSpeed(speed: Float) = settingsDataStore.setDefaultSpeed(speed)
}
