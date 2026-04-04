package com.videomaker.aimusic.media.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

/**
 * AudioLooper - Extracts and loops audio segments
 *
 * Creates a new audio file that loops a trimmed segment to fill a target duration.
 * Used to avoid Media3 ClippingConfiguration + looping issues.
 */
object AudioLooper {

    /**
     * Creates a looped audio file from a trimmed segment
     *
     * @param context Android context
     * @param sourceUri Source audio file URI
     * @param trimStartMs Trim start position in milliseconds
     * @param trimEndMs Trim end position in milliseconds
     * @param targetDurationMs Target duration for the output (video duration)
     * @param outputFile Output file to write the looped audio
     * @return true if successful, false otherwise
     */
    fun createLoopedAudio(
        context: Context,
        sourceUri: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        targetDurationMs: Long,
        outputFile: File
    ): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        return try {
            // Set up extractor - handle both local and remote URIs
            when (sourceUri.scheme) {
                "http", "https" -> {
                    // Remote URL - use string path directly
                    extractor.setDataSource(sourceUri.toString())
                }
                "content", "file" -> {
                    // Local URI - use file descriptor
                    context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
                        extractor.setDataSource(pfd.fileDescriptor)
                    } ?: run {
                        android.util.Log.e("AudioLooper", "Failed to open source file")
                        return false
                    }
                }
                else -> {
                    android.util.Log.e("AudioLooper", "Unsupported URI scheme: ${sourceUri.scheme}")
                    return false
                }
            }

            // Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                android.util.Log.e("AudioLooper", "No audio track found")
                return false
            }

            android.util.Log.d("AudioLooper", "Audio format: $audioFormat")

            extractor.selectTrack(audioTrackIndex)

            // Set up muxer with M4A output
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val muxerTrackIndex = try {
                muxer.addTrack(audioFormat)
            } catch (e: Exception) {
                android.util.Log.e("AudioLooper", "Failed to add track with format: $audioFormat", e)
                return false
            }

            muxer.start()

            // Calculate loop parameters
            val segmentDurationMs = trimEndMs - trimStartMs
            val loopCount = (targetDurationMs + segmentDurationMs - 1) / segmentDurationMs
            val trimStartUs = trimStartMs * 1000
            val trimEndUs = trimEndMs * 1000

            android.util.Log.d("AudioLooper", "Looping: ${segmentDurationMs}ms segment x $loopCount times for ${targetDurationMs}ms")

            // Buffer for audio data
            val bufferInfo = MediaCodec.BufferInfo()
            val buffer = ByteBuffer.allocate(256 * 1024) // 256KB buffer
            var outputPresentationTimeUs = 0L

            // Loop the segment
            for (loop in 0 until loopCount.toInt()) {
                // Seek to trim start for each loop
                extractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                // Copy samples from trim start to trim end
                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        // End of file
                        break
                    }

                    val sampleTime = extractor.sampleTime

                    // Check if we've passed the trim end
                    if (sampleTime > trimEndUs) {
                        break
                    }

                    // Check if we've reached target duration
                    if (outputPresentationTimeUs >= targetDurationMs * 1000) {
                        break
                    }

                    // Write sample to muxer
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = outputPresentationTimeUs
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)

                    // Advance to next sample
                    outputPresentationTimeUs += (sampleTime - trimStartUs).coerceAtLeast(0)
                    extractor.advance()
                }

                // If we've reached target duration, stop
                if (outputPresentationTimeUs >= targetDurationMs * 1000) {
                    break
                }
            }

            android.util.Log.d("AudioLooper", "Successfully created looped audio: ${outputFile.absolutePath}")
            true

        } catch (e: Exception) {
            android.util.Log.e("AudioLooper", "Failed to create looped audio", e)
            outputFile.delete() // Clean up partial file
            false
        } finally {
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {
                android.util.Log.e("AudioLooper", "Error releasing muxer", e)
            }
            extractor.release()
        }
    }

    /**
     * Generates output filename for looped audio
     */
    fun generateLoopedAudioFilename(projectId: String, trimStartMs: Long, trimEndMs: Long): String {
        return "looped_audio_${projectId}_${trimStartMs}_${trimEndMs}.m4a"
    }
}
