package com.videomaker.aimusic.media.renderer

import android.os.SystemClock

/**
 * PlaybackClock - Shared time source between GL renderer and ExoPlayer audio.
 *
 * The GL renderer reads currentTimeMs() each frame to determine which
 * image and transition to display. The ExoPlayer audio syncs to this clock.
 *
 * Thread-safe: compound reads/writes are synchronized to prevent torn reads
 * when seekTo() updates both positionOffsetMs and playStartUptimeMs.
 */
class PlaybackClock {

    // Lock for compound field access (seekTo writes 2 fields, currentTimeMs reads 3)
    private val lock = Any()

    // Playback anchor: wall-clock time when play was last started
    private var playStartUptimeMs: Long = 0L

    // Position offset at the time play was started (or last seek)
    private var positionOffsetMs: Long = 0L

    @Volatile private var _isPlaying: Boolean = false
    val isPlaying: Boolean get() = _isPlaying

    @Volatile private var _totalDurationMs: Long = 0L
    val totalDurationMs: Long get() = _totalDurationMs

    @Volatile private var _looping: Boolean = true

    /**
     * Start or resume playback from the current position.
     */
    fun play() {
        synchronized(lock) {
            if (_isPlaying) return
            playStartUptimeMs = SystemClock.uptimeMillis()
            _isPlaying = true
        }
    }

    /**
     * Pause playback, freezing the current position.
     */
    fun pause() {
        synchronized(lock) {
            if (!_isPlaying) return
            // Capture current position before pausing
            positionOffsetMs = currentTimeMsInternal()
            _isPlaying = false
        }
    }

    /**
     * Seek to a specific position.
     * Works whether playing or paused.
     */
    fun seekTo(positionMs: Long) {
        synchronized(lock) {
            positionOffsetMs = positionMs.coerceIn(0L, _totalDurationMs)
            if (_isPlaying) {
                // Reset play anchor so elapsed time starts from new position
                playStartUptimeMs = SystemClock.uptimeMillis()
            }
        }
    }

    /**
     * Get the current playback position in milliseconds.
     * If playing, calculates from wall clock; if paused, returns frozen position.
     * When looping is enabled, wraps around at totalDurationMs.
     */
    fun currentTimeMs(): Long {
        synchronized(lock) {
            return currentTimeMsInternal()
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
        synchronized(lock) {
            _isPlaying = false
            positionOffsetMs = 0L
            playStartUptimeMs = 0L
        }
    }

    // Internal: must be called under lock
    private fun currentTimeMsInternal(): Long {
        if (!_isPlaying) return positionOffsetMs

        val elapsed = SystemClock.uptimeMillis() - playStartUptimeMs
        val position = positionOffsetMs + elapsed

        return if (_totalDurationMs > 0 && _looping) {
            position % _totalDurationMs
        } else if (_totalDurationMs > 0) {
            position.coerceIn(0L, _totalDurationMs)
        } else {
            position.coerceAtLeast(0L)
        }
    }
}
