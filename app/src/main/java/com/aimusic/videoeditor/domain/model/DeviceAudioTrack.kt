package com.aimusic.videoeditor.domain.model

import android.net.Uri

/**
 * DeviceAudioTrack - Audio file from device storage
 *
 * Represents an audio file queried from MediaStore for use as custom background music.
 *
 * @param id Unique identifier (MediaStore ID)
 * @param uri Content URI to the audio file
 * @param title Display name / title
 * @param artist Artist name (if available)
 * @param album Album name (if available)
 * @param durationMs Track duration in milliseconds
 * @param size File size in bytes
 */
data class DeviceAudioTrack(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val size: Long
) {
    /**
     * Formatted duration string (mm:ss)
     */
    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    /**
     * Display subtitle (artist - album or just artist/album)
     */
    val subtitle: String?
        get() = when {
            artist != null && album != null -> "$artist • $album"
            artist != null -> artist
            album != null -> album
            else -> null
        }
}
