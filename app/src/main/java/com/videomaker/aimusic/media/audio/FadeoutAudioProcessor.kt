package com.videomaker.aimusic.media.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FadeoutAudioProcessor - Fades audio out over a specified time window.
 *
 * Implements linear fadeout: volume gradually decreases from 1.0 to 0.0
 * over the fadeout duration at the end of the audio.
 *
 * Matches Python ffmpeg filter: afade=t=out:st={start_time}:d={duration}
 *
 * @param totalDurationUs Total audio duration in microseconds
 * @param fadeoutDurationUs Fadeout duration in microseconds (e.g., 6 beats)
 * @param baseVolume Base volume multiplier applied before fadeout
 *
 * Thread Safety: This processor is not thread-safe. It should only be used
 * from a single thread (the audio processing thread).
 */
class FadeoutAudioProcessor(
    private val totalDurationUs: Long,
    private val fadeoutDurationUs: Long,
    private val baseVolume: Float = 1.0f
) : AudioProcessor {

    private var inputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded: Boolean = false
    private var isActive: Boolean = false

    // Playback position tracking
    private var bytesProcessed: Long = 0L
    private var sampleRate: Int = 0
    private var channelCount: Int = 0

    // Fadeout timing in microseconds
    private val fadeoutStartUs: Long = totalDurationUs - fadeoutDurationUs

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        // Only process 16-bit PCM audio
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            this.inputAudioFormat = AudioFormat.NOT_SET
            this.outputAudioFormat = AudioFormat.NOT_SET
            isActive = false
            return AudioFormat.NOT_SET
        }

        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat
        this.sampleRate = inputAudioFormat.sampleRate
        this.channelCount = inputAudioFormat.channelCount
        this.bytesProcessed = 0L

        // Always active if fadeout duration > 0
        isActive = fadeoutDurationUs > 0L

        return if (isActive) outputAudioFormat else AudioFormat.NOT_SET
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive || inputAudioFormat == AudioFormat.NOT_SET) {
            return
        }

        val remaining = inputBuffer.remaining()
        if (remaining == 0) {
            return
        }

        // Ensure output buffer has enough capacity
        if (outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        // Convert bytes to time position
        // 16-bit PCM: 2 bytes per sample per channel
        val bytesPerSample = 2
        val bytesPerFrame = bytesPerSample * channelCount
        val samplesProcessed = bytesProcessed / bytesPerFrame
        val startPositionUs = (samplesProcessed * 1_000_000L) / sampleRate

        // Process 16-bit PCM samples (2 bytes per sample)
        val sampleCount = remaining / 2
        for (i in 0 until sampleCount) {
            // Check for buffer underflow before reading
            if (inputBuffer.remaining() < 2) {
                android.util.Log.e("FadeoutAudioProcessor", "Buffer underflow: remaining=${inputBuffer.remaining()}")
                break
            }

            // Calculate time position for this sample
            val currentSampleIndex = samplesProcessed + (i / channelCount)
            val currentPositionUs = (currentSampleIndex * 1_000_000L) / sampleRate

            // Calculate fadeout volume
            val fadeVolume = when {
                currentPositionUs < fadeoutStartUs -> 1.0f  // Before fadeout
                currentPositionUs >= totalDurationUs -> 0.0f  // After fadeout
                else -> {
                    // Linear fadeout: 1.0 → 0.0 over fadeout duration
                    val fadeProgress = (currentPositionUs - fadeoutStartUs).toFloat() / fadeoutDurationUs.toFloat()
                    1.0f - fadeProgress.coerceIn(0f, 1f)
                }
            }

            // Apply base volume and fadeout
            val totalVolume = baseVolume * fadeVolume

            val sample = inputBuffer.short
            val adjustedSample = (sample * totalVolume).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outputBuffer.putShort(adjustedSample.toShort())
        }

        bytesProcessed += remaining
        outputBuffer.flip()
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        bytesProcessed = 0L
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
        isActive = false
        sampleRate = 0
        channelCount = 0
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
