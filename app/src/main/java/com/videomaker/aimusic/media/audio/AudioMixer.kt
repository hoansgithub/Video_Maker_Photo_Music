package com.videomaker.aimusic.media.audio

import com.videomaker.aimusic.domain.model.AudioNode
import kotlin.math.min

/**
 * AudioMixer - Mixes multiple decoded PCM streams at a given timestamp.
 *
 * Applies per-node volume and fade envelopes.
 * Outputs a mixed PCM buffer for playback or export.
 *
 * This is used during export; for preview playback, multiple ExoPlayer
 * instances handle mixing via Android's audio HAL.
 */
class AudioMixer {

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNELS = 2 // Stereo
        private const val BYTES_PER_SAMPLE = 2 // 16-bit PCM

        /**
         * Number of bytes per millisecond of audio.
         */
        private const val BYTES_PER_MS = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE / 1000
    }

    /**
     * Mix multiple PCM buffers from active audio nodes at a given timeline position.
     *
     * @param nodeBuffers Map of AudioNode to its decoded PCM data for the current chunk
     * @param timelineMs Current position on the timeline (for fade calculation)
     * @param chunkSizeBytes Size of each PCM chunk in bytes
     * @return Mixed PCM output buffer
     */
    fun mix(
        nodeBuffers: Map<AudioNode, ShortArray>,
        timelineMs: Long,
        chunkSizeBytes: Int
    ): ShortArray {
        val samplesPerChunk = chunkSizeBytes / BYTES_PER_SAMPLE
        val output = ShortArray(samplesPerChunk)

        if (nodeBuffers.isEmpty()) return output

        // Mix all node buffers with per-node volume
        for ((node, pcmData) in nodeBuffers) {
            val nodeVolume = node.volumeAt(timelineMs)
            if (nodeVolume <= 0f) continue

            val samplesToMix = min(samplesPerChunk, pcmData.size)
            for (i in 0 until samplesToMix) {
                val mixed = output[i].toInt() + (pcmData[i] * nodeVolume).toInt()
                // Clamp to 16-bit range to prevent clipping distortion
                output[i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        return output
    }

    /**
     * Determine which audio nodes are active at a given timeline position.
     */
    fun getActiveNodes(nodes: List<AudioNode>, timelineMs: Long): List<AudioNode> {
        return nodes.filter { it.isActiveAt(timelineMs) }
    }
}
