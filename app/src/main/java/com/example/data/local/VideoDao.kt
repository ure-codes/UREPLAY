package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY COALESCE(lastPlayedAt, addedAt) DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE sourceType = 'DEVICE' ORDER BY addedAt DESC")
    fun getDeviceVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE sourceType IN ('WEB_LINK', 'HOTSPOT') ORDER BY addedAt DESC")
    fun getLinkVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC")
    fun getWatchHistory(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE isBookmarked = 1 ORDER BY addedAt DESC")
    fun getBookmarkedVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity): Long

    @Query("UPDATE videos SET lastPositionMs = :positionMs, durationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE durationMs END, lastPlayedAt = :playedAt WHERE uri = :uri")
    suspend fun updatePlaybackProgress(
        uri: String,
        positionMs: Long,
        durationMs: Long,
        playedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE videos SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun setBookmarked(id: Long, bookmarked: Boolean)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteById(id: Long)
}
