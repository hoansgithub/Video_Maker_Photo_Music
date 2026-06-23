package com.videomaker.aimusic.media.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.videomaker.aimusic.media.composition.ExportAudioLoopPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * AudioPreprocessingService - Pre-renders audio with beat-sync fadeout applied.
 *
 * Benefits:
 * - Smooth preview playback (no real-time audio processing)
 * - Faster exports (audio already processed)
 * - Consistent behavior between preview and export
 *
 * Cache strategy:
 * - Files stored in: <cache_dir>/audio_preprocessed/{songId}_fadeout_{durationMs}.m4a
 * - Reuses existing file if parameters match
 * - Automatic cleanup of old files
 */
class AudioPreprocessingService(
    private val context: Context
) {

    private val cacheDir = File(context.cacheDir, "audio_preprocessed").apply { mkdirs() }

    companion object {
        private const val TAG = "AudioPreprocessing"
        private const val MAX_CACHE_SIZE_MB = 100L
        // Bump when preprocessing logic changes to invalidate stale cache entries
        private const val CACHE_VERSION = 2
    }

    /**
     * Preprocess audio with fadeout applied.
     *
     * Volume is NOT baked in — apply via VolumeAudioProcessor at composition time
     * (or audioPlayer.volume at preview time) so a single cache file serves all volumes.
     *
     * If the source audio (from trimStartMs to end) is shorter than totalDurationMs,
     * the audio is looped to fill the video duration before applying fadeout.
     *
     * Optimized: no HTTP download step — Transformer handles remote URIs natively,
     * and MediaExtractor reads duration from HTTP headers without downloading the full file.
     *
     * @param sourceUri Original audio URI (song or custom audio)
     * @param songId Song ID for cache key (0 for custom audio)
     * @param trimStartMs Start position in source audio (hook_start_time)
     * @param totalDurationMs Total video duration
     * @param fadeoutDurationMs Fadeout duration (6 beats in milliseconds)
     * @param songDurationMs Unused — kept for API compatibility. Duration is measured locally.
     * @return URI of preprocessed audio file, or null if preprocessing failed
     */
    suspend fun preprocessAudioWithFadeout(
        sourceUri: Uri,
        songId: Long,
        trimStartMs: Long,
        totalDurationMs: Long,
        fadeoutDurationMs: Long,
        songDurationMs: Long? = null,
        measuredDurationMs: Long? = null
    ): Uri? {
        try {
            // Check cache first
            val cacheFile = getCacheFile(songId, trimStartMs, totalDurationMs, fadeoutDurationMs)
            if (cacheFile.exists() && cacheFile.length() > 0) {
                Log.d(TAG, "Using cached preprocessed audio: ${cacheFile.name}")
                return Uri.fromFile(cacheFile)
            }

            Log.d(TAG, "Preprocessing audio: songId=$songId, trimStart=${trimStartMs}ms, duration=${totalDurationMs}ms, fadeout=${fadeoutDurationMs}ms")

            // Use pre-measured duration if available, otherwise measure (MediaExtractor reads from HTTP headers without full download)
            val actualDurationMs = measuredDurationMs ?: withContext(Dispatchers.IO) {
                getAudioDurationMs(sourceUri)
            }
            Log.d(TAG, "Audio duration: ${actualDurationMs}ms (pre-measured=${measuredDurationMs != null})")

            return if (actualDurationMs != null && actualDurationMs > 0) {
                val availableMusicMs = (actualDurationMs - trimStartMs).coerceAtLeast(0)
                val plan = ExportAudioLoopPlanner.plan(availableMusicMs, totalDurationMs)
                Log.d(TAG, "Loop check: available=${availableMusicMs}ms, video=${totalDurationMs}ms, shouldLoop=${plan.shouldLoop}")
                if (plan.shouldLoop) {
                    processWithLoop(sourceUri, trimStartMs, totalDurationMs, fadeoutDurationMs, actualDurationMs, cacheFile)
                } else {
                    processWithoutLoop(sourceUri, trimStartMs, totalDurationMs, fadeoutDurationMs, cacheFile)
                }
            } else {
                Log.w(TAG, "Could not measure audio duration, skipping loop check")
                processWithoutLoop(sourceUri, trimStartMs, totalDurationMs, fadeoutDurationMs, cacheFile)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Audio preprocessing error: ${e.message}", e)
            return null
        }
    }

    /**
     * No looping needed — single Transformer pass: trim + fadeout.
     */
    private suspend fun processWithoutLoop(
        sourceUri: Uri,
        trimStartMs: Long,
        totalDurationMs: Long,
        fadeoutDurationMs: Long,
        cacheFile: File
    ): Uri? {
        val tempFile = File.createTempFile("audio_preprocessing_", ".m4a", context.cacheDir)
        val totalDurationUs = totalDurationMs * 1000L
        val fadeoutDurationUs = fadeoutDurationMs * 1000L

        val clippingConfig = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(trimStartMs)
            .setEndPositionMs(trimStartMs + totalDurationMs)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(clippingConfig)
            .build()

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .setDurationUs(totalDurationUs)
            .setEffects(Effects(listOf(FadeoutAudioProcessor(totalDurationUs, fadeoutDurationUs)), emptyList()))
            .build()

        val composition = Composition.Builder(
            listOf(EditedMediaItemSequence.Builder(listOf(editedMediaItem)).build())
        ).build()

        val result = transformAudio(composition, tempFile.absolutePath)
        return moveToCacheOrNull(tempFile, cacheFile, result)
    }

    /**
     * Looping needed — single Transformer pass:
     * Concatenates clipped segments into a sequence and applies fadeout at composition level.
     * Transformer handles MP3/AAC/etc. decoding and HTTP sources natively.
     */
    private suspend fun processWithLoop(
        sourceUri: Uri,
        trimStartMs: Long,
        totalDurationMs: Long,
        fadeoutDurationMs: Long,
        songDurationMs: Long,
        cacheFile: File
    ): Uri? {
        val segmentDurationMs = (songDurationMs - trimStartMs).coerceAtLeast(1)
        val fullLoops = (totalDurationMs / segmentDurationMs).toInt()
        val remainingMs = totalDurationMs % segmentDurationMs

        Log.d(TAG, "Looping via Transformer (single pass): segment=${segmentDurationMs}ms x $fullLoops full loops + ${remainingMs}ms remainder")

        val tempFile = File.createTempFile("audio_preprocessing_", ".m4a", context.cacheDir)
        val totalDurationUs = totalDurationMs * 1000L
        val fadeoutDurationUs = fadeoutDurationMs * 1000L

        val fullClip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(trimStartMs)
            .setEndPositionMs(songDurationMs)
            .build()

        val fullLoopItem = EditedMediaItem.Builder(
            MediaItem.Builder().setUri(sourceUri).setClippingConfiguration(fullClip).build()
        ).setRemoveVideo(true).build()

        val items = mutableListOf<EditedMediaItem>()
        repeat(fullLoops) { items.add(fullLoopItem) }

        if (remainingMs > 0) {
            val partialClip = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(trimStartMs)
                .setEndPositionMs(trimStartMs + remainingMs)
                .build()
            val partialItem = EditedMediaItem.Builder(
                MediaItem.Builder().setUri(sourceUri).setClippingConfiguration(partialClip).build()
            ).setRemoveVideo(true).build()
            items.add(partialItem)
        }

        // Single pass: concatenate segments + apply fadeout at composition level
        val composition = Composition.Builder(
            listOf(EditedMediaItemSequence.Builder(items).build())
        )
            .setEffects(Effects(listOf(FadeoutAudioProcessor(totalDurationUs, fadeoutDurationUs)), emptyList()))
            .build()

        val result = transformAudio(composition, tempFile.absolutePath)
        if (result == null) {
            tempFile.delete()
            // Fall back to no-loop
            return processWithoutLoop(sourceUri, trimStartMs, totalDurationMs, fadeoutDurationMs, cacheFile)
        }

        return moveToCacheOrNull(tempFile, cacheFile, result)
    }

    /**
     * Move temp file to cache on success, delete on failure.
     */
    private suspend fun moveToCacheOrNull(tempFile: File, cacheFile: File, result: ExportResult?): Uri? {
        if (result != null) {
            val renameSuccess = tempFile.renameTo(cacheFile)
            if (!renameSuccess) {
                Log.d(TAG, "Rename failed, using copy fallback")
                tempFile.copyTo(cacheFile, overwrite = true)
                tempFile.delete()
            }
            Log.d(TAG, "Audio preprocessed successfully: ${cacheFile.name} (${cacheFile.length() / 1024}KB)")
            cleanupCache()
            return Uri.fromFile(cacheFile)
        } else {
            tempFile.delete()
            Log.e(TAG, "Audio preprocessing failed")
            return null
        }
    }

    /**
     * Transform audio using Media3 Transformer with suspend/resume.
     * CRITICAL: Transformer must be created and started on the main thread.
     */
    private suspend fun transformAudio(
        composition: Composition,
        outputPath: String
    ): ExportResult? = suspendCancellableCoroutine { continuation ->
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        // Transformer must be created and started on the main thread
        mainHandler.post {
            try {
                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) {
                                continuation.resume(exportResult)
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            Log.e(TAG, "Transformer error: ${exportException.message}", exportException)
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    })
                    .build()

                transformer.start(composition, outputPath)

                continuation.invokeOnCancellation {
                    mainHandler.post {
                        try {
                            transformer.cancel()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error cancelling transformer: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start transformer: ${e.message}", e)
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }

    /**
     * Get cache file for preprocessed audio.
     * Filename includes parameters to ensure cache invalidation when settings change.
     */
    private fun getCacheFile(songId: Long, trimStartMs: Long, totalDurationMs: Long, fadeoutDurationMs: Long): File {
        val filename = "${songId}_trim${trimStartMs}_dur${totalDurationMs}_fade${fadeoutDurationMs}_v${CACHE_VERSION}.m4a"
        return File(cacheDir, filename)
    }

    /**
     * Measure the actual audio duration of a source URI using MediaExtractor.
     * Returns duration in milliseconds, or null if measurement fails.
     * Must be called on IO thread for file:// URIs with ContentResolver.
     */
    internal fun getAudioDurationMs(sourceUri: Uri): Long? {
        val extractor = MediaExtractor()
        return try {
            when (sourceUri.scheme) {
                "http", "https" -> extractor.setDataSource(sourceUri.toString())
                "content", "file" -> {
                    context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
                        extractor.setDataSource(pfd.fileDescriptor)
                    } ?: return null
                }
                else -> {
                    // Try as file path (Uri.fromFile produces "file" scheme)
                    val path = sourceUri.path ?: return null
                    extractor.setDataSource(path)
                }
            }

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    return durationUs / 1000
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to measure audio duration: ${e.message}")
            null
        } finally {
            extractor.release()
        }
    }

    /**
     * Clean up cache directory if it exceeds size limit.
     * Deletes oldest files first.
     * Runs on IO dispatcher to prevent ANR from file operations.
     */
    private suspend fun cleanupCache() = withContext(Dispatchers.IO) {
        try {
            val files = cacheDir.listFiles() ?: return@withContext
            val totalSize = files.sumOf { it.length() }
            val maxBytes = MAX_CACHE_SIZE_MB * 1024 * 1024

            if (totalSize > maxBytes) {
                Log.d(TAG, "Cache size ${totalSize / 1024 / 1024}MB exceeds limit ${MAX_CACHE_SIZE_MB}MB, cleaning up...")

                // Sort by last modified, oldest first
                val sortedFiles = files.sortedBy { it.lastModified() }

                var currentSize = totalSize
                for (file in sortedFiles) {
                    if (currentSize <= maxBytes) break

                    val size = file.length()
                    if (file.delete()) {
                        currentSize -= size
                        Log.d(TAG, "Deleted old cache file: ${file.name}")
                    }
                }

                Log.d(TAG, "Cache cleanup complete: ${currentSize / 1024 / 1024}MB")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cache cleanup error: ${e.message}")
        }
    } // End withContext(Dispatchers.IO)

    /**
     * Clear all preprocessed audio cache.
     */
    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "Cache cleared")
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing cache: ${e.message}")
        }
    }

    /**
     * Check if preprocessed audio exists in cache.
     */
    fun hasCachedPreprocessedAudio(songId: Long, trimStartMs: Long, totalDurationMs: Long, fadeoutDurationMs: Long): Boolean {
        val cacheFile = getCacheFile(songId, trimStartMs, totalDurationMs, fadeoutDurationMs)
        return cacheFile.exists() && cacheFile.length() > 0
    }
}
