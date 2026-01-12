package com.videomaker.aimusic.domain.model

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
     * Calculate total video duration based on assets and transitions
     *
     * Architecture:
     * - Each image (except last) plays for: imageDurationMs + transitionOverlapMs
     * - Last image plays for: imageDurationMs only
     *
     * Total = N × imageDurationMs + (N-1) × transitionOverlapMs
     *
     * Example with 5 photos at 3s each with 500ms transitions:
     * = 5 × 3000 + 4 × 500 = 15000 + 2000 = 17000ms (17 seconds)
     */
    val totalDurationMs: Long
        get() {
            if (assets.isEmpty()) return 0L
            val n = assets.size
            return n * settings.imageDurationMs + (n - 1) * settings.transitionOverlapMs
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
