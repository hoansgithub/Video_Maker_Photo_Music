package com.videomaker.aimusic.media.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

/**
 * AudioTranscoder - Transcodes and loops audio segments
 *
 * Handles MP3 → AAC transcoding with looping to fill target duration.
 * Pipeline: Extract MP3 → Decode to PCM → Loop PCM → Encode to AAC → Mux to M4A
 */
object AudioTranscoder {

    private const val TAG = "AudioTranscoder"
    private const val TIMEOUT_US = 10000L

    /**
     * Transcodes and loops audio to match target duration
     *
     * @param context Android context
     * @param sourceUri Source audio URI (MP3 or other format)
     * @param trimStartMs Start position in source audio
     * @param trimEndMs End position in source audio
     * @param targetDurationMs Target output duration (video length)
     * @param outputFile Output M4A file
     * @return true if successful
     */
    fun transcodeAndLoop(
        context: Context,
        sourceUri: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        targetDurationMs: Long,
        outputFile: File
    ): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        return try {
            // Setup extractor
            when (sourceUri.scheme) {
                "http", "https" -> extractor.setDataSource(sourceUri.toString())
                "content", "file" -> {
                    context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
                        extractor.setDataSource(pfd.fileDescriptor)
                    }
                }
            }

            // Find audio track
            val audioTrackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                val format = extractor.getTrackFormat(index)
                format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return false

            val sourceFormat = extractor.getTrackFormat(audioTrackIndex)
            extractor.selectTrack(audioTrackIndex)

            // Seek to trim start
            extractor.seekTo(trimStartMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            // Create decoder
            val decoderName = MediaCodecList(MediaCodecList.ALL_CODECS)
                .findDecoderForFormat(sourceFormat)
            decoder = MediaCodec.createByCodecName(decoderName)
            decoder.configure(sourceFormat, null, null, 0)
            decoder.start()

            // Get audio parameters
            val sampleRate = sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = sourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // Create AAC encoder with high quality settings
            val encoderFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 320000) // High quality: 320kbps (was 128kbps)
                setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // Setup muxer
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Decode, loop, and encode
            val success = processAudio(
                extractor = extractor,
                decoder = decoder,
                encoder = encoder,
                muxer = muxer,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                targetDurationMs = targetDurationMs,
                sampleRate = sampleRate,
                channelCount = channelCount
            )

            android.util.Log.d(TAG, if (success) "Transcode successful" else "Transcode failed")
            success

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Transcode error", e)
            outputFile.delete()
            false
        } finally {
            // Release each resource independently to prevent cascade failures
            runCatching { decoder?.stop() }.onFailure {
                android.util.Log.w(TAG, "Decoder stop failed", it)
            }
            runCatching { decoder?.release() }.onFailure {
                android.util.Log.w(TAG, "Decoder release failed", it)
            }
            runCatching { encoder?.stop() }.onFailure {
                android.util.Log.w(TAG, "Encoder stop failed", it)
            }
            runCatching { encoder?.release() }.onFailure {
                android.util.Log.w(TAG, "Encoder release failed", it)
            }
            runCatching { muxer?.stop() }.onFailure {
                android.util.Log.w(TAG, "Muxer stop failed", it)
            }
            runCatching { muxer?.release() }.onFailure {
                android.util.Log.w(TAG, "Muxer release failed", it)
            }
            runCatching { extractor.release() }.onFailure {
                android.util.Log.w(TAG, "Extractor release failed", it)
            }
        }
    }

    private fun processAudio(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        encoder: MediaCodec,
        muxer: MediaMuxer,
        trimStartMs: Long,
        trimEndMs: Long,
        targetDurationMs: Long,
        sampleRate: Int,
        channelCount: Int
    ): Boolean {
        val decoderBuffers = mutableListOf<ByteBuffer>()
        val trimEndUs = trimEndMs * 1000
        var muxerTrackIndex = -1
        var muxerStarted = false
        var totalEncodedUs = 0L
        val targetDurationUs = targetDurationMs * 1000

        // Phase 1: Decode trimmed segment to memory
        var decoderDone = false
        var decoderInputDone = false

        while (!decoderDone) {
            // Feed decoder
            if (!decoderInputDone) {
                val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex) ?: run {
                        android.util.Log.e("AudioTranscoder", "Failed to get decoder input buffer at index $inputIndex")
                        continue
                    }
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)

                    if (sampleSize < 0 || extractor.sampleTime > trimEndUs) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        decoderInputDone = true
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // Get decoded output
            val bufferInfo = MediaCodec.BufferInfo()
            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

            when {
                outputIndex >= 0 -> {
                    val outputBuffer = decoder.getOutputBuffer(outputIndex) ?: run {
                        android.util.Log.e("AudioTranscoder", "Failed to get decoder output buffer at index $outputIndex")
                        decoder.releaseOutputBuffer(outputIndex, false)
                        continue
                    }
                    if (bufferInfo.size > 0) {
                        // Store decoded PCM
                        val pcmData = ByteBuffer.allocate(bufferInfo.size)
                        pcmData.put(outputBuffer)
                        pcmData.rewind()
                        decoderBuffers.add(pcmData)
                    }

                    decoder.releaseOutputBuffer(outputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        decoderDone = true
                    }
                }
            }
        }

        android.util.Log.d(TAG, "Decoded ${decoderBuffers.size} PCM buffers")

        if (decoderBuffers.isEmpty()) {
            return false
        }

        // Phase 2: Loop PCM and encode to AAC
        var currentBufferIndex = 0
        var encoderInputDone = false

        while (totalEncodedUs < targetDurationUs) {
            // Feed encoder with looped PCM
            if (!encoderInputDone) {
                val inputIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputIndex) ?: run {
                        android.util.Log.e("AudioTranscoder", "Failed to get encoder input buffer at index $inputIndex")
                        continue
                    }

                    if (currentBufferIndex < decoderBuffers.size) {
                        val pcmData = decoderBuffers[currentBufferIndex]
                        pcmData.rewind()

                        // Check buffer size and only put what fits
                        val bytesToWrite = minOf(pcmData.remaining(), inputBuffer.remaining())
                        val originalLimit = pcmData.limit()
                        pcmData.limit(pcmData.position() + bytesToWrite)
                        inputBuffer.put(pcmData)
                        pcmData.limit(originalLimit)

                        encoder.queueInputBuffer(inputIndex, 0, bytesToWrite, totalEncodedUs, 0)
                        currentBufferIndex++

                        // Loop: restart from beginning when we reach the end
                        if (currentBufferIndex >= decoderBuffers.size) {
                            currentBufferIndex = 0
                        }

                        // Advance time
                        // Calculate duration based on actual audio format
                        // Formula: (bytes * 1000000) / (sampleRate * channelCount * bytesPerSample)
                        // bytesPerSample = 2 for 16-bit PCM
                        val bufferDurationUs = (bytesToWrite * 1000000L) / (sampleRate * channelCount * 2)
                        totalEncodedUs += bufferDurationUs

                    } else {
                        encoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        encoderInputDone = true
                    }
                }
            }

            // Get encoded output and write to muxer
            val bufferInfo = MediaCodec.BufferInfo()
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                outputIndex >= 0 -> {
                    val outputBuffer = encoder.getOutputBuffer(outputIndex) ?: run {
                        android.util.Log.e("AudioTranscoder", "Failed to get encoder output buffer at index $outputIndex")
                        encoder.releaseOutputBuffer(outputIndex, false)
                        continue
                    }

                    if (bufferInfo.size > 0 && muxerStarted) {
                        muxer.writeSampleData(muxerTrackIndex, outputBuffer, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(outputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
            }

            // Stop if we've encoded enough
            if (totalEncodedUs >= targetDurationUs && !encoderInputDone) {
                val inputIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    encoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    encoderInputDone = true
                }
            }
        }

        return true
    }

    fun generateOutputFilename(projectId: String, trimStartMs: Long, trimEndMs: Long): String {
        return "looped_aac_${projectId}_${trimStartMs}_${trimEndMs}.m4a"
    }
}
