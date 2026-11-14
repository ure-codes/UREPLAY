package com.example.ui.util

import java.util.Locale

fun formatDurationMs(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

fun extractFirstUrl(text: String): String? {
    val regex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    return regex.find(text)?.value
}

fun isLikelyVideoUrl(url: String): Boolean {
    val clean = url.lowercase().substringBefore('?').substringBefore('#')
    return clean.endsWith(".mp4") ||
        clean.endsWith(".m3u8") ||
        clean.endsWith(".mpd") ||
        clean.endsWith(".webm") ||
        clean.endsWith(".mkv") ||
        clean.endsWith(".mov") ||
        clean.endsWith(".avi") ||
        clean.endsWith(".ts") ||
        "/stream" in clean
}
