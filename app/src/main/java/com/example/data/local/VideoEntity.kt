package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.VideoSourceType

@Entity(
    tableName = "videos",
    indices = [Index(value = ["uri"], unique = true)]
)
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val title: String,
    val sourceType: VideoSourceType,
    val durationMs: Long = 0L,
    val lastPositionMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val resolution: String = "",
    val mimeType: String = "video/*",
    val thumbnailPath: String? = null,
    val sourcePageUrl: String? = null,
    val isBookmarked: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null
)
