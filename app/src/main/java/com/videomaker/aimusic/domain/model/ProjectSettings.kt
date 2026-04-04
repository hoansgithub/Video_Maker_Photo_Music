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
 * MUSIC TRIM:
 * - musicTrimStartMs: Start position in song to begin playback (0 = song beginning)
 * - musicTrimEndMs: End position in song (null = use full song from start position)
 * - Music will loop if video duration exceeds trimmed music duration
 * - Minimum trim duration: 5 seconds (enforced in validation)
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
 * @param musicTrimStartMs Start position in song for trimming (0 = beginning)
 * @param musicTrimEndMs End position in song for trimming (null = use full song)
 * @param processedAudioUri URI of pre-processed looped audio (auto-generated from trim)
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
    val musicTrimStartMs: Long = 0L, // Start trim position (0 = no trim at start)
    val musicTrimEndMs: Long? = null, // End trim position (null = use full song)
    val processedAudioUri: Uri? = null, // Pre-processed looped audio
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
     * Check if music is trimmed
     */
    val isMusicTrimmed: Boolean
        get() = musicTrimStartMs > 0L || musicTrimEndMs != null

    /**
     * Calculate trimmed music duration
     * Returns null if no end trim applied (use full song from start)
     */
    val musicTrimDurationMs: Long?
        get() = musicTrimEndMs?.let { it - musicTrimStartMs }

    /**
     * Format music trim info for display
     * Example: "0:30 - 1:00 (30s)"
     * Returns null if not trimmed
     */
    fun formatMusicTrimInfo(): String? {
        if (!isMusicTrimmed) return null

        val startFormatted = formatTimeMs(musicTrimStartMs)
        val endFormatted = musicTrimEndMs?.let { formatTimeMs(it) } ?: "End"
        val durationFormatted = musicTrimDurationMs?.let { formatTimeMs(it) } ?: ""

        return if (durationFormatted.isNotEmpty()) {
            "$startFormatted - $endFormatted ($durationFormatted)"
        } else {
            "$startFormatted - $endFormatted"
        }
    }

    /**
     * Validate music trim positions
     * Ensures minimum 5-second duration if both start and end are set
     * Returns validated copy or original if no trim
     */
    fun validateMusicTrim(): ProjectSettings {
        // If no trim applied, return as-is
        if (!isMusicTrimmed) return this

        // If only start is set (no end), valid
        if (musicTrimEndMs == null) return this

        // Both start and end are set - validate minimum duration
        val duration = musicTrimEndMs - musicTrimStartMs
        if (duration < MIN_MUSIC_TRIM_DURATION_MS) {
            // Invalid trim - reset to no trim
            return copy(musicTrimStartMs = 0L, musicTrimEndMs = null)
        }

        return this
    }

    /**
     * Validate and correct settings to ensure they match available options
     * @return Validated ProjectSettings with corrected values
     */
    fun validate(): ProjectSettings {
        val minDurationMs = (MIN_IMAGE_DURATION_SECONDS * 1000).toLong()
        val maxDurationMs = (MAX_IMAGE_DURATION_SECONDS * 1000).toLong()
        val stepMs = (IMAGE_DURATION_STEP * 1000).toLong()

        return copy(
            // Ensure image duration is within valid range and rounded to nearest 0.1s step
            imageDurationMs = imageDurationMs.coerceIn(minDurationMs, maxDurationMs).let { duration ->
                // Round to nearest step (100ms for 0.1s)
                ((duration + stepMs / 2) / stepMs) * stepMs
            },
            // Ensure transition percentage is one of the valid options
            transitionPercentage = if (transitionPercentage in TRANSITION_PERCENTAGE_OPTIONS) {
                transitionPercentage
            } else {
                // Find closest valid option
                TRANSITION_PERCENTAGE_OPTIONS.minByOrNull {
                    kotlin.math.abs(it - transitionPercentage)
                } ?: 30
            },
            // Ensure audio volume is in valid range
            audioVolume = audioVolume.coerceIn(0f, 1f)
        )
    }

    companion object {
        val DEFAULT = ProjectSettings()

        // Image duration range in seconds with 0.1s increments
        // Range covers all Supabase templates (min: 0.628s, max: 1.667s as of 2026-04-01)
        const val MIN_IMAGE_DURATION_SECONDS = 0.5f
        const val MAX_IMAGE_DURATION_SECONDS = 5.0f
        const val IMAGE_DURATION_STEP = 0.1f

        // Available transition percentages: 10%, 20%, 30%, 40%, 50%
        val TRANSITION_PERCENTAGE_OPTIONS = listOf(10, 20, 30, 40, 50)

        // Music trim constraints
        const val MIN_MUSIC_TRIM_DURATION_MS = 5000L  // 5 seconds minimum

        /**
         * Format milliseconds to MM:SS format
         */
        private fun formatTimeMs(ms: Long): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
    }
}

// Extension function for formatting (used by companion object)
private fun formatTimeMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
