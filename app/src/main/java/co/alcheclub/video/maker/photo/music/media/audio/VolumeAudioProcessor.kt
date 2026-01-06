package co.alcheclub.video.maker.photo.music.media.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * VolumeAudioProcessor - Adjusts audio volume by scaling PCM samples
 *
 * Supports 16-bit PCM audio (most common format).
 * Volume is applied as a linear multiplier (0.0 = silent, 1.0 = original, >1.0 = amplify).
 *
 * Thread Safety: This processor is not thread-safe. It should only be used
 * from a single thread (the audio processing thread).
 */
class VolumeAudioProcessor(
    private val volume: Float
) : AudioProcessor {

    private var inputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded: Boolean = false
    private var isActive: Boolean = false

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
        isActive = volume != 1.0f

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

        // Process 16-bit PCM samples (2 bytes per sample)
        val sampleCount = remaining / 2
        for (i in 0 until sampleCount) {
            val sample = inputBuffer.short
            // Apply volume and clamp to 16-bit range
            val adjustedSample = (sample * volume).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outputBuffer.putShort(adjustedSample.toShort())
        }

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
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
        isActive = false
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
