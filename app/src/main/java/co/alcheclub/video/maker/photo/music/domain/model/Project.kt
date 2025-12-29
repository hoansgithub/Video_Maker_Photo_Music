package co.alcheclub.video.maker.photo.music.domain.model

import android.net.Uri

/**
 * Project - Domain model for a video project
 */
data class Project(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailUri: Uri?,
    val settings: ProjectSettings,
    val assets: List<Asset>
) {
    /**
     * Calculate total video duration based on assets and transition duration
     *
     * Total = numberOfImages × transitionDuration
     *
     * Example with 5 photos at 3s each = 15 seconds
     */
    val totalDurationMs: Long
        get() {
            if (assets.isEmpty()) return 0L
            return assets.size * settings.transitionDurationMs
        }

    /**
     * Format duration as MM:SS
     */
    val formattedDuration: String
        get() {
            val totalSeconds = totalDurationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}
