package com.aimusic.videoeditor.domain.model

import android.net.Uri

/**
 * ProjectSettings - Domain model for project editing settings
 *
 * Users can individually select each setting - mix and match as they like.
 *
 * TRANSITION TIMING:
 * - transitionPercentage: Percentage of image duration used for transition (10-50%)
 * - Example: 30% with 5s image = 1.5s transition
 * - The transition spans the end of current image and start of next
 * - This creates smooth, visible 3D effects
 *
 * @param imageDurationMs Duration each image is displayed (2-12 seconds)
 * @param transitionPercentage Percentage of image duration for transition (10-50%)
 * @param transitionId ID of selected transition effect (null = no transition)
 * @param overlayFrameId ID of selected overlay frame (null = none)
 * @param audioTrackId ID of bundled audio track (null = none)
 * @param customAudioUri User's custom audio URI (overrides audioTrackId)
 * @param audioVolume Music volume (0.0 to 1.0)
 * @param aspectRatio Output video aspect ratio
 */
data class ProjectSettings(
    val imageDurationMs: Long = 3000L,
    val transitionPercentage: Int = 30, // 30% of image duration for transition
    val transitionId: String? = "rgb_split", // Default to RGB Split transition
    val overlayFrameId: String? = null,
    val audioTrackId: String? = DEFAULT_AUDIO_TRACK_ID,
    val customAudioUri: Uri? = null,
    val audioVolume: Float = 1.0f,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16
) {
    /**
     * Calculate actual transition duration in milliseconds
     *
     * Based on percentage of TOTAL time for an image pair:
     * - 2 images at 5s each = 10s total
     * - 30% transition = 3s (starts at ~3.5s, ends at ~6.5s)
     *
     * Formula: transitionDuration = percentage × (imageDuration × 2) / 100
     */
    val transitionOverlapMs: Long
        get() = (imageDurationMs * 2 * transitionPercentage / 100)

    /**
     * Legacy compatibility - maps to imageDurationMs
     * @deprecated Use imageDurationMs instead
     */
    @Deprecated("Use imageDurationMs instead", ReplaceWith("imageDurationMs"))
    val transitionDurationMs: Long get() = imageDurationMs

    companion object {
        const val DEFAULT_AUDIO_TRACK_ID = "track1"

        val DEFAULT = ProjectSettings()

        // Available image durations (in seconds): 2, 3, 4, 5, 6, 8, 10, 12
        val IMAGE_DURATION_OPTIONS = listOf(2, 3, 4, 5, 6, 8, 10, 12)

        // Available transition percentages: 10%, 20%, 30%, 40%, 50%
        val TRANSITION_PERCENTAGE_OPTIONS = listOf(10, 20, 30, 40, 50)
    }
}
