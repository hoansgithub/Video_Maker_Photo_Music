package com.videomaker.aimusic.media.composition

import com.videomaker.aimusic.domain.model.BeatSyncData
import kotlin.math.min

/**
 * BeatSyncClip - Represents a single video clip with beat-sync timing.
 */
data class BeatSyncClip(
    val imageIndex: Int,
    val holdDurationMs: Long,
    val transitionDurationMs: Long,
    val totalDurationMs: Long,
    val transitionShaderIndex: Int,
    val hasTransition: Boolean
)

/**
 * BeatSyncTimingCalculator - Matches Python reference exactly (demo_beat_sync.py).
 *
 * Key algorithm (matching Python detect_beats.py + demo_beat_sync.py):
 * 1. BPM-based beats_per_transition: >=140→1, >=100→2, <100→4
 * 2. Transition duration = 1 BEAT (capped at 1000ms)
 * 3. Transitions START at beats (not centered!)
 * 4. All transitions have SAME duration
 * 5. Last image holds for 6 beats AFTER last transition ends
 *
 * Python reference: detect_beats.py:43-49, demo_beat_sync.py
 */
class BeatSyncTimingCalculator {

    companion object {
        const val MIN_BEATS_PER_TRANSITION = 1
        const val FADEOUT_BEATS = 6
        const val MAX_TRANSITION_MS = 1000L  // Cap transition at 1 second

        /** BPM-based beats per transition (inverted from pipeline) */
        fun beatsPerTransitionForBpm(bpm: Double): Int = when {
            bpm >= 140 -> 4
            bpm >= 100 -> 2
            else -> 1
        }
    }

    fun calculateClips(
        beatData: BeatSyncData,
        imageSequence: List<Int>,
        trimStartMs: Long,
        trimEndMs: Long?,
        numShaders: Int = 1
    ): List<BeatSyncClip> {
        if (imageSequence.isEmpty()) return emptyList()

        val numTransitions = imageSequence.size - 1

        // Special case: single image (no transitions, just fadeout hold)
        if (numTransitions == 0) {
            val safeBpm = beatData.bpm.coerceAtLeast(1.0)
            val beatMs = 60000.0 / safeBpm
            val fadeoutHoldMs = (beatMs * FADEOUT_BEATS).toLong()

            return listOf(
                BeatSyncClip(
                    imageIndex = imageSequence[0],
                    holdDurationMs = fadeoutHoldMs,
                    transitionDurationMs = 0,
                    totalDurationMs = fadeoutHoldMs,
                    transitionShaderIndex = 0,
                    hasTransition = false
                )
            )
        }

        // BPM-based beats per transition (detect_beats.py:43-49)
        val safeBpm = beatData.bpm.coerceAtLeast(1.0)
        val preferredBpt = beatsPerTransitionForBpm(safeBpm)

        // Step 1: Get all beats and filter to trim window
        val allBeatsMs = beatData.beats.map { it * 1000.0 }

        // Calculate how many beats we need
        val beatsNeeded = (numTransitions * preferredBpt) + FADEOUT_BEATS + 4

        // Determine trim end to include enough beats
        val calculatedEndMs = trimEndMs?.toDouble() ?: run {
            val startIndex = allBeatsMs.indexOfFirst { it >= trimStartMs }
            if (startIndex < 0) return emptyList()

            val endBeatIndex = (startIndex + beatsNeeded).coerceAtMost(allBeatsMs.size - 1)
            allBeatsMs.getOrNull(endBeatIndex) ?: allBeatsMs.lastOrNull() ?: return emptyList()
        }

        // Filter beats within trim window and make relative to trim start
        val trimmedBeatsMs = allBeatsMs
            .filter { it in trimStartMs.toDouble()..calculatedEndMs }
            .map { it - trimStartMs }

        if (trimmedBeatsMs.isEmpty()) return emptyList()

        // Step 2: Use BPM-based spacing, compress if not enough beats available
        val availableBeats = trimmedBeatsMs.size
        val beatsPerTransition = if (availableBeats >= numTransitions * preferredBpt) {
            preferredBpt
        } else {
            val calculated = availableBeats / (numTransitions + 1)
            calculated.coerceAtLeast(MIN_BEATS_PER_TRANSITION)
        }

        // Pick transitions with calculated spacing
        val transitionTimes = mutableListOf<Double>()
        var i = beatsPerTransition - 1  // Start at Nth beat (0-indexed)
        while (i < trimmedBeatsMs.size && transitionTimes.size < numTransitions) {
            transitionTimes.add(trimmedBeatsMs[i])
            i += beatsPerTransition
        }

        // Graceful degradation: if still not enough transitions, use what we have
        if (transitionTimes.isEmpty()) {
            return emptyList()
        }

        // Step 3: Calculate transition duration = 1 BEAT
        val beatMs = 60000.0 / safeBpm
        val transitionDurationMs = min(beatMs, MAX_TRANSITION_MS.toDouble()).toLong()

        // Step 4: Build clips
        val clips = mutableListOf<BeatSyncClip>()

        // Build clips for available transitions (may be fewer than requested)
        val actualTransitions = transitionTimes.size
        for (idx in 0 until actualTransitions) {
            val transTime = transitionTimes[idx]

            // Transition STARTS at beat time (Python line 490-491)
            val transStartMs = transTime
            val transEndMs = transTime + transitionDurationMs

            // Hold = time from previous transition end to this transition start
            val clipStartMs = if (idx == 0) {
                0.0
            } else {
                transitionTimes[idx - 1] + transitionDurationMs
            }
            val holdDur = (transStartMs - clipStartMs).toLong().coerceAtLeast(0)

            clips.add(
                BeatSyncClip(
                    imageIndex = imageSequence[idx],
                    holdDurationMs = holdDur,
                    transitionDurationMs = transitionDurationMs,
                    totalDurationMs = holdDur + transitionDurationMs,
                    transitionShaderIndex = idx % numShaders,
                    hasTransition = true
                )
            )
        }

        // If we have more images than transitions, add clips without transitions
        for (idx in actualTransitions until (imageSequence.size - 1)) {
            // Hold for the full beat interval
            val holdDur = (beatMs * beatsPerTransition).toLong()
            clips.add(
                BeatSyncClip(
                    imageIndex = imageSequence[idx],
                    holdDurationMs = holdDur,
                    transitionDurationMs = 0,
                    totalDurationMs = holdDur,
                    transitionShaderIndex = idx % numShaders,
                    hasTransition = false
                )
            )
        }

        // Last clip: holds for 6 beats AFTER last transition ends (Python line 469-472)
        val fadeoutHoldMs = (beatMs * FADEOUT_BEATS).toLong()

        clips.add(
            BeatSyncClip(
                imageIndex = imageSequence.last(),
                holdDurationMs = fadeoutHoldMs,
                transitionDurationMs = 0,
                totalDurationMs = fadeoutHoldMs,
                transitionShaderIndex = 0,
                hasTransition = false
            )
        )

        return clips
    }
}
