package com.videomaker.aimusic.domain.model

import android.net.Uri

/**
 * Project - Domain model for a video project.
 *
 * BEAT-SYNC ONLY: Duration calculated from beats + image count
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
     * Total project duration in milliseconds (BEAT-SYNC ONLY)
     *
     * Uses pre-calculated value from settings.totalDurationMs
     * Beat-sync duration is calculated in ViewModel and cached in settings
     */
    val totalDurationMs: Long
        get() = settings.totalDurationMs

    companion object {
        /**
         * Calculate beat-sync duration (call in ViewModel, not in getter!)
         * This is expensive - should only be called once and cached in settings.totalDurationMs
         */
        fun calculateBeatSyncDuration(
            beatData: com.videomaker.aimusic.domain.model.BeatSyncData,
            assetCount: Int,
            trimStartMs: Long
        ): Long? {
            if (assetCount == 0) return null

            return try {
                val calculator = com.videomaker.aimusic.media.composition.BeatSyncTimingCalculator()
                val clips = calculator.calculateClips(
                    beatData = beatData,
                    imageSequence = (0 until assetCount).toList(),
                    trimStartMs = trimStartMs,
                    trimEndMs = null,
                    numShaders = 1
                )
                clips.sumOf { it.totalDurationMs }
            } catch (e: Exception) {
                android.util.Log.e("Project", "Beat-sync duration calculation failed", e)
                null
            }
        }
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
