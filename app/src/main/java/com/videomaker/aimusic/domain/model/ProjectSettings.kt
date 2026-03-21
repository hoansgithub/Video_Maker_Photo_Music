package com.videomaker.aimusic.domain.model

import android.net.Uri

/**
 * ProjectSettings - Domain model for project editing settings
 *
 * Users can individually select each setting - mix and match as they like.
 *
 * EFFECT SET:
 * - effectSetId: ID of selected effect set (collection of transitions)
 * - Each effect set contains multiple transitions that cycle through images
 * - This provides variety while maintaining a cohesive visual theme
 *
 * TRANSITION TIMING:
 * - transitionPercentage: Percentage of image duration used for transition (10-50%)
 * - Example: 30% with 5s image = 1.5s transition
 * - The transition spans the end of current image and start of next
 * - This creates smooth, visible 3D effects
 *
 * @param imageDurationMs Duration each image is displayed (2-12 seconds)
 * @param transitionPercentage Percentage of image duration for transition (10-50%)
 * @param effectSetId ID of selected effect set (null = no transitions)
 * @param overlayFrameId ID of selected overlay frame (null = none)
 * @param musicSongId ID of Supabase MusicSong selected for this project (null = none)
 * @param musicSongName Cached song name for display purposes (null = none)
 * @param musicSongUrl Supabase song mp3 URL stored for offline composition (null = none)
 * @param musicSongCoverUrl Supabase song cover image URL for display (null = none)
 * @param customAudioUri User's custom audio URI from device (overrides musicSongId)
 * @param audioVolume Music volume (0.0 to 1.0)
 * @param aspectRatio Output video aspect ratio
 */
data class ProjectSettings(
    val imageDurationMs: Long = 3000L,
    val transitionPercentage: Int = 30, // 30% of image duration for transition
    val effectSetId: String? = "dreamy_vibes", // Default effect set
    val overlayFrameId: String? = null,
    val musicSongId: Long? = null,
    val musicSongName: String? = null, // Cached for display only
    val musicSongUrl: String? = null, // Cached for offline playback
    val musicSongCoverUrl: String? = null, // Cached for display only
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
     * Validate and correct settings to ensure they match available options
     * @return Validated ProjectSettings with corrected values
     */
    fun validate(): ProjectSettings {
        val validImageDurations = IMAGE_DURATION_OPTIONS.map { it * 1000L }
        val validTransitionPercentages = TRANSITION_PERCENTAGE_OPTIONS

        return copy(
            // Ensure image duration is one of the valid options
            imageDurationMs = if (imageDurationMs in validImageDurations) {
                imageDurationMs
            } else {
                // Find closest valid option
                validImageDurations.minByOrNull {
                    kotlin.math.abs(it - imageDurationMs)
                } ?: 3000L
            },
            // Ensure transition percentage is one of the valid options
            transitionPercentage = if (transitionPercentage in validTransitionPercentages) {
                transitionPercentage
            } else {
                // Find closest valid option
                validTransitionPercentages.minByOrNull {
                    kotlin.math.abs(it - transitionPercentage)
                } ?: 30
            },
            // Ensure audio volume is in valid range
            audioVolume = audioVolume.coerceIn(0f, 1f)
        )
    }

    companion object {
        val DEFAULT = ProjectSettings()

        // Available image durations (in seconds): 2, 3, 4, 5, 6, 8, 10, 12
        val IMAGE_DURATION_OPTIONS = listOf(2, 3, 4, 5, 6, 8, 10, 12)

        // Available transition percentages: 10%, 20%, 30%, 40%, 50%
        val TRANSITION_PERCENTAGE_OPTIONS = listOf(10, 20, 30, 40, 50)
    }
}
