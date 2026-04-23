package com.videomaker.aimusic.domain.model

import kotlinx.serialization.Serializable

/**
 * Beat-sync data from Supabase beats-cache bucket.
 *
 * Provides beat positions and tempo information for syncing video transitions to music beats.
 *
 * JSON format: { "beats": [[time_s, kick_strength], ...], "bpm": 95.0, "num_beats": 178 }
 *
 * Algorithm:
 * - Pick every 4th beat as transition point (hardcoded, not adaptive)
 * - Transition duration = min(60000/BPM, 1000) ms (same for all transitions)
 * - Last image holds for 6 beats with audio fadeout
 * - Transition STARTS at beat time (not centered on it)
 *
 * @param beats Beat positions in seconds (extracted from [time_s, kick_strength] pairs)
 * @param bpm Detected tempo (beats per minute)
 * @param numBeats Total number of beats in song
 */
@Serializable
data class BeatSyncData(
    val beats: List<Double>,  // Only time positions (beats[i][0]), kick_strength ignored
    val bpm: Double,
    val numBeats: Int
)
