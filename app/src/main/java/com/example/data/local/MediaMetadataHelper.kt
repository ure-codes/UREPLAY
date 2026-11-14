package com.example.data.local

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.VideoSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class MediaMetadataHelper(private val context: Context) {

    suspend fun extractVideoMetadata(
        uri: Uri,
        sourceType: VideoSourceType,
        explicitTitle: String? = null,
        sourcePageUrl: String? = null
    ): VideoEntity = withContext(Dispatchers.IO) {
        if (sourceType == VideoSourceType.DEVICE) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }

        var displayName = explicitTitle?.takeIf { it.isNotBlank() }
        var fileSize = 0L
        var mimeType = context.contentResolver.getType(uri) ?: guessMimeType(uri.toString())

        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (displayName == null && nameIdx >= 0) {
                            displayName = cursor.getString(nameIdx)
                        }
                        if (sizeIdx >= 0) {
                            fileSize = cursor.getLong(sizeIdx)
                        }
                    }
                }
            }
        }

        var durationMs = 0L
        var resolution = ""
        var thumbnailPath: String? = null

        if (sourceType == VideoSourceType.DEVICE) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val titleMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                if (displayName.isNullOrBlank() && !titleMeta.isNullOrBlank()) {
                    displayName = titleMeta
                }
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
                if (width > 0 && height > 0) {
                    resolution = formatResolutionLabel(width, height)
                }
                val frameTimeUs = if (durationMs > 4000L) 2_000_000L else 500_000L
                val bitmap = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bitmap != null) {
                    thumbnailPath = saveThumbnail(uri.toString(), bitmap)
                    bitmap.recycle()
                }
            } catch (_: Throwable) {
                // Metadata extraction is best-effort
            } finally {
                runCatching { retriever.release() }
            }
        }

        val resolvedTitle = (displayName ?: deriveNameFromUri(uri))
            .substringBeforeLast('.')
            .ifBlank { "Untitled Video" }

        VideoEntity(
            uri = uri.toString(),
            title = resolvedTitle,
            sourceType = sourceType,
            durationMs = durationMs,
            fileSizeBytes = fileSize,
            resolution = resolution,
            mimeType = mimeType,
            thumbnailPath = thumbnailPath,
            sourcePageUrl = sourcePageUrl,
            isBookmarked = sourceType != VideoSourceType.DEVICE
        )
    }

    private fun saveThumbnail(uriKey: String, bitmap: Bitmap): String? {
        return runCatching {
            val thumbsDir = File(context.filesDir, "thumbs").apply { mkdirs() }
            val fileName = "thumb_${abs(uriKey.hashCode())}.jpg"
            val outFile = File(thumbsDir, fileName)
            val maxW = 640
            val scaled = if (bitmap.width > maxW) {
                val ratio = maxW.toFloat() / bitmap.width.toFloat()
                Bitmap.createScaledBitmap(bitmap, maxW, (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
            } else {
                bitmap
            }
            FileOutputStream(outFile).use { stream ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, stream)
            }
            if (scaled !== bitmap) scaled.recycle()
            outFile.absolutePath
        }.getOrNull()
    }

    private fun deriveNameFromUri(uri: Uri): String {
        val last = uri.lastPathSegment?.substringAfterLast('/')
        return if (!last.isNullOrBlank()) last else uri.host ?: "Stream Video"
    }

    private fun guessMimeType(url: String): String {
        val lower = url.lowercase()
        return when {
            ".m3u8" in lower -> "application/x-mpegURL"
            ".mpd" in lower -> "application/dash+xml"
            ".webm" in lower -> "video/webm"
            ".mkv" in lower -> "video/x-matroska"
            else -> "video/mp4"
        }
    }

    private fun formatResolutionLabel(width: Int, height: Int): String {
        val minDim = minOf(width, height)
        return when {
            minDim >= 2160 -> "4K"
            minDim >= 1440 -> "1440p"
            minDim >= 1080 -> "1080p"
            minDim >= 720 -> "720p"
            minDim >= 480 -> "480p"
            else -> "${width}x${height}"
        }
    }
}
