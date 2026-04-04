package com.videomaker.aimusic.media.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File

/**
 * AudioSegmentExtractor - Fast extraction of audio segments without transcoding
 *
 * Extracts a segment by copying compressed audio samples directly (no decode/encode).
 * Much faster than AudioTranscoder since we skip the decoding/encoding steps.
 */
object AudioSegmentExtractor {

    private const val TAG = "AudioSegmentExtractor"
    private const val TIMEOUT_US = 10000L

    /**
     * Extracts a trimmed segment from audio source (fast, no transcoding)
     *
     * @param context Android context
     * @param sourceUri Source audio URI
     * @param trimStartMs Start position in source audio
     * @param trimEndMs End position in source audio
     * @param outputFile Output file (same format as source)
     * @return true if successful
     */
    fun extractSegment(
        context: Context,
        sourceUri: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        outputFile: File
    ): Boolean {
        val extractor = MediaExtractor()
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

            // Determine output format based on source MIME type
            val sourceMime = sourceFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mpeg"
            val outputFormat = when {
                sourceMime.contains("mp4") || sourceMime.contains("aac") ->
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                else ->
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 // Default to MP4
            }

            // Setup muxer
            muxer = MediaMuxer(outputFile.absolutePath, outputFormat)
            val muxerTrackIndex = muxer.addTrack(sourceFormat)
            muxer.start()

            // Seek to trim start
            val trimStartUs = trimStartMs * 1000
            val trimEndUs = trimEndMs * 1000
            extractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            // Copy samples in the trimmed range
            val bufferInfo = android.media.MediaCodec.BufferInfo()
            val buffer = java.nio.ByteBuffer.allocate(1024 * 1024) // 1MB buffer
            var sampleCount = 0

            while (true) {
                val sampleTime = extractor.sampleTime

                // Stop if we've passed the trim end
                if (sampleTime > trimEndUs) {
                    break
                }

                // Read sample
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)

                if (sampleSize < 0) {
                    break // End of stream
                }

                // Only write samples within our trim range
                if (sampleTime >= trimStartUs) {
                    bufferInfo.presentationTimeUs = sampleTime - trimStartUs // Reset timestamp to start at 0
                    bufferInfo.size = sampleSize
                    bufferInfo.offset = 0
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                    sampleCount++
                }

                // Advance to next sample
                if (!extractor.advance()) {
                    break
                }
            }

            android.util.Log.d(TAG, "Extracted $sampleCount samples from ${trimStartMs}ms to ${trimEndMs}ms")
            true

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Extraction error", e)
            outputFile.delete()
            false
        } finally {
            try {
                muxer?.stop()
                muxer?.release()
                extractor.release()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Cleanup error", e)
            }
        }
    }

    fun generateOutputFilename(projectId: String, trimStartMs: Long, trimEndMs: Long): String {
        return "segment_${projectId}_${trimStartMs}_${trimEndMs}.m4a"
    }
}
