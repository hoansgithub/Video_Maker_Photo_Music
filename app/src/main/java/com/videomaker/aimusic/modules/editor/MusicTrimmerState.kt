package com.videomaker.aimusic.modules.editor

import androidx.compose.runtime.Immutable

/**
 * MusicTrimmerState - State for music trimmer bottom sheet
 *
 * Thread-safe sealed class to prevent race conditions
 * Immutable data classes to prevent concurrent modification
 */
@Immutable
sealed class MusicTrimmerState {
    /**
     * Trimmer is closed - normal editor mode
     */
    @Immutable
    data object Closed : MusicTrimmerState()

    /**
     * Trimmer is open - music trim mode
     * Main video player is paused, independent music preview active
     *
     * @param songName Name of the song being trimmed
     * @param songDurationMs Total duration of the song in milliseconds
     * @param trimStartMs Current trim start position (can be adjusted by user)
     * @param trimEndMs Current trim end position (can be adjusted by user)
     * @param currentMusicPositionMs Current playback position in the song (for playhead indicator)
     * @param isMusicPlaying Is the music preview currently playing
     * @param wasMainPlayerPlaying Was the main video player playing before trimmer opened (for restore)
     */
    @Immutable
    data class Open(
        val songName: String,
        val songDurationMs: Long,
        val trimStartMs: Long,
        val trimEndMs: Long,
        val currentMusicPositionMs: Long = 0L,
        val isMusicPlaying: Boolean = false,
        val wasMainPlayerPlaying: Boolean = false
    ) : MusicTrimmerState() {
        /**
         * Validate trim positions
         * Ensures end > start and minimum 5s duration
         */
        val isValid: Boolean
            get() = trimEndMs > trimStartMs &&
                    (trimEndMs - trimStartMs) >= MIN_TRIM_DURATION_MS

        /**
         * Get trim duration in milliseconds
         */
        val trimDurationMs: Long
            get() = trimEndMs - trimStartMs

        companion object {
            const val MIN_TRIM_DURATION_MS = 5000L  // 5 seconds
        }
    }
}
