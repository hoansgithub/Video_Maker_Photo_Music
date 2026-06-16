package com.videomaker.aimusic.media.renderer

import android.os.SystemClock

/**
 * PlaybackClock - Shared time source between GL renderer and ExoPlayer audio.
 *
 * The GL renderer reads currentTimeMs() each frame to determine which
 * image and transition to display. The ExoPlayer audio syncs to this clock.
 *
 * Thread-safe: all fields are volatile for safe cross-thread reads.
 */
class PlaybackClock {

    // Playback anchor: wall-clock time when play was last started
    @Volatile private var playStartUptimeMs: Long = 0L

    // Position offset at the time play was started (or last seek)
    @Volatile private var positionOffsetMs: Long = 0L

    @Volatile private var _isPlaying: Boolean = false
    val isPlaying: Boolean get() = _isPlaying

    @Volatile private var _totalDurationMs: Long = 0L
    val totalDurationMs: Long get() = _totalDurationMs

    /**
     * Start or resume playback from the current position.
     */
    fun play() {
        if (_isPlaying) return
        playStartUptimeMs = SystemClock.uptimeMillis()
        _isPlaying = true
    }

    /**
     * Pause playback, freezing the current position.
     */
    fun pause() {
        if (!_isPlaying) return
        // Capture current position before pausing
        positionOffsetMs = currentTimeMs()
        _isPlaying = false
    }

    /**
     * Seek to a specific position.
     * Works whether playing or paused.
     */
    fun seekTo(positionMs: Long) {
        positionOffsetMs = positionMs.coerceIn(0L, _totalDurationMs)
        if (_isPlaying) {
            // Reset play anchor so elapsed time starts from new position
            playStartUptimeMs = SystemClock.uptimeMillis()
        }
    }

    /**
     * Get the current playback position in milliseconds.
     * If playing, calculates from wall clock; if paused, returns frozen position.
     */
    fun currentTimeMs(): Long {
        if (!_isPlaying) return positionOffsetMs

        val elapsed = SystemClock.uptimeMillis() - playStartUptimeMs
        val position = positionOffsetMs + elapsed

        // Clamp to duration (no looping at this level)
        return if (_totalDurationMs > 0) {
            position.coerceIn(0L, _totalDurationMs)
        } else {
            position.coerceAtLeast(0L)
        }
    }

    /**
     * Set the total duration. Called when project settings change.
     */
    fun setDuration(durationMs: Long) {
        _totalDurationMs = durationMs
    }

    /**
     * Reset clock to initial state.
     */
    fun reset() {
        _isPlaying = false
        positionOffsetMs = 0L
        playStartUptimeMs = 0L
    }
}
