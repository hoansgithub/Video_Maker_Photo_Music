package com.videomaker.aimusic.domain.model

import android.net.Uri

/**
 * Project - Domain model for a video project.
 *
 * During the duration migration, `totalDurationMs` prefers the migrated value in
 * settings when present and falls back to the legacy asset-based calculation
 * until all callers are updated.
 */
data class Project(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailUri: Uri?,
    val settings: ProjectSettings,
    val assets: List<Asset>,
    val isWatermarkFree: Boolean = false  // True if user watched ad to remove watermark
) {
    /**
     * Total project duration in milliseconds.
     *
     * Uses migrated settings when available, otherwise falls back to the legacy
     * per-asset duration formula during the transition.
     */
    val totalDurationMs: Long
        get() {
            val configuredTotalDurationMs = settings.totalDurationMs
            if (configuredTotalDurationMs > 0L) return configuredTotalDurationMs

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
