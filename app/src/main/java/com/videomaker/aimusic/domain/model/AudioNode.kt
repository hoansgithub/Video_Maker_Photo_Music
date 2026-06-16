package com.videomaker.aimusic.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * AudioNode - Represents a single audio clip on the multi-track timeline.
 *
 * Multiple AudioNodes can overlap on the timeline. Each has its own
 * source (song or custom audio), trim range, volume, and fade settings.
 *
 * Timeline placement:
 * ```
 * Timeline: |----[Node A: 0-10s]------[Node B: 8-18s]---------|
 *                                  ↑ overlap (8-10s): both play simultaneously
 * ```
 *
 * @param id Unique identifier for this node
 * @param songId Supabase MusicSong ID (null if using custom audio)
 * @param customAudioUri User's custom audio URI from device (overrides songId)
 * @param songName Cached song name for display
 * @param songArtist Cached artist name for display
 * @param songUrl Supabase song mp3 URL for playback
 * @param coverUrl Song cover image URL for display
 * @param startTimeMs Position on the timeline where this node starts
 * @param trimStartMs Start of the playback range within the source audio
 * @param trimEndMs End of the playback range within the source audio (null = end of source)
 * @param volume Per-node volume (0.0 to 1.0)
 * @param fadeInMs Fade-in duration at the start of the clip
 * @param fadeOutMs Fade-out duration at the end of the clip
 * @param processedAudioUri URI of pre-processed audio (trimmed + faded)
 */
@Serializable
data class AudioNode(
    val id: String = UUID.randomUUID().toString(),
    val songId: Long? = null,
    val customAudioUri: String? = null,
    val songName: String? = null,
    val songArtist: String? = null,
    val songUrl: String? = null,
    val coverUrl: String? = null,
    val startTimeMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long? = null,
    val volume: Float = 1.0f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val processedAudioUri: String? = null
) {
    /**
     * Duration of the trimmed audio clip.
     * Returns null if trimEndMs is not set (full source duration unknown).
     */
    val trimmedDurationMs: Long?
        get() = trimEndMs?.let { it - trimStartMs }

    /**
     * End position on the timeline.
     * Returns null if trimmed duration is unknown.
     */
    val endTimeMs: Long?
        get() = trimmedDurationMs?.let { startTimeMs + it }

    /**
     * Check if this node is active (should be playing) at a given timeline position.
     */
    fun isActiveAt(timelineMs: Long): Boolean {
        if (timelineMs < startTimeMs) return false
        val end = endTimeMs ?: return timelineMs >= startTimeMs
        return timelineMs < end
    }

    /**
     * Calculate the volume at a given timeline position, accounting for fades.
     */
    fun volumeAt(timelineMs: Long): Float {
        if (!isActiveAt(timelineMs)) return 0f

        val posInClip = timelineMs - startTimeMs
        val duration = trimmedDurationMs ?: return volume

        // Fade in
        if (fadeInMs > 0 && posInClip < fadeInMs) {
            return volume * (posInClip.toFloat() / fadeInMs.toFloat())
        }

        // Fade out
        val fadeOutStart = duration - fadeOutMs
        if (fadeOutMs > 0 && posInClip > fadeOutStart) {
            val remaining = duration - posInClip
            return volume * (remaining.toFloat() / fadeOutMs.toFloat())
        }

        return volume
    }
}
