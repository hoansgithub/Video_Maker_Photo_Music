package co.alcheclub.video.maker.photo.music.domain.model

import android.net.Uri

/**
 * ProjectSettings - Domain model for project editing settings
 *
 * Users can individually select each setting - mix and match as they like.
 *
 * @param imageDurationMs Duration each image is displayed (2-12 seconds)
 * @param transitionOverlapMs Duration of transition between images (200-1000ms)
 * @param transitionSetId ID of selected transition set (or single transition ID)
 * @param overlayFrameId ID of selected overlay frame (null = none)
 * @param audioTrackId ID of bundled audio track (null = none)
 * @param customAudioUri User's custom audio URI (overrides audioTrackId)
 * @param audioVolume Music volume (0.0 to 1.0)
 * @param aspectRatio Output video aspect ratio
 */
data class ProjectSettings(
    val imageDurationMs: Long = 3000L,
    val transitionOverlapMs: Long = 500L,
    val transitionSetId: String? = "classic", // Default to classic set (fade transitions)
    val overlayFrameId: String? = null,
    val audioTrackId: String? = DEFAULT_AUDIO_TRACK_ID,
    val customAudioUri: Uri? = null,
    val audioVolume: Float = 1.0f,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16
) {
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

        // Available transition durations (in milliseconds): 200, 300, 500, 700, 1000
        val TRANSITION_DURATION_OPTIONS = listOf(200, 300, 500, 700, 1000)
    }
}
