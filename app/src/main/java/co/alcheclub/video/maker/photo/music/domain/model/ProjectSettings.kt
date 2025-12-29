package co.alcheclub.video.maker.photo.music.domain.model

import android.net.Uri

/**
 * ProjectSettings - Domain model for project editing settings
 *
 * Users can individually select each setting - mix and match as they like.
 *
 * @param transitionDurationMs Duration each image is shown (2-12 seconds)
 * @param transitionSetId ID of selected transition set (contains 20+ transitions)
 * @param overlayFrameId ID of selected overlay frame (null = none)
 * @param audioTrackId ID of bundled audio track (null = none)
 * @param customAudioUri User's custom audio URI (overrides audioTrackId)
 * @param audioVolume Music volume (0.0 to 1.0)
 * @param aspectRatio Output video aspect ratio
 */
data class ProjectSettings(
    val transitionDurationMs: Long = 3000L,
    val transitionSetId: String = "classic",
    val overlayFrameId: String? = null,
    val audioTrackId: String? = DEFAULT_AUDIO_TRACK_ID,
    val customAudioUri: Uri? = null,
    val audioVolume: Float = 1.0f,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16
) {
    companion object {
        const val DEFAULT_AUDIO_TRACK_ID = "track1"

        val DEFAULT = ProjectSettings()

        // Available durations (in seconds): 2, 3, 4, 5, 6, 8, 10, 12
        val DURATION_OPTIONS = listOf(2, 3, 4, 5, 6, 8, 10, 12)
    }
}
