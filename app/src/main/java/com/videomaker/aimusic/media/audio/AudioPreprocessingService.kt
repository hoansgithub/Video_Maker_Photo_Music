package com.videomaker.aimusic.media.audio

import android.content.Context
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
import com.videomaker.aimusic.domain.model.BeatSyncData
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
    }

    /**
     * Preprocess audio with fadeout applied.
     *
     * @param sourceUri Original audio URI (song or custom audio)
     * @param songId Song ID for cache key (0 for custom audio)
     * @param trimStartMs Start position in source audio (hook_start_time)
     * @param totalDurationMs Total video duration
     * @param fadeoutDurationMs Fadeout duration (6 beats in milliseconds)
     * @param baseVolume Base volume multiplier
     * @return URI of preprocessed audio file, or null if preprocessing failed
     */
    suspend fun preprocessAudioWithFadeout(
        sourceUri: Uri,
        songId: Long,
        trimStartMs: Long,
        totalDurationMs: Long,
        fadeoutDurationMs: Long,
        baseVolume: Float
    ): Uri? {
        try {
            // Check cache first
            val cacheFile = getCacheFile(songId, trimStartMs, totalDurationMs, fadeoutDurationMs, baseVolume)
            if (cacheFile.exists() && cacheFile.length() > 0) {
                Log.d(TAG, "Using cached preprocessed audio: ${cacheFile.name}")
                return Uri.fromFile(cacheFile)
            }

            Log.d(TAG, "Preprocessing audio with fadeout: songId=$songId, trimStart=${trimStartMs}ms, duration=${totalDurationMs}ms, fadeout=${fadeoutDurationMs}ms")

            // Create temporary output file
            val tempFile = File.createTempFile("audio_preprocessing_", ".m4a", context.cacheDir)

            val totalDurationUs = totalDurationMs * 1000L
            val fadeoutDurationUs = fadeoutDurationMs * 1000L

            // Beat-sync mode: TRIM audio from source (NO LOOPING)
            // Audio is trimmed to exact video duration with fadeout applied
            Log.d(TAG, "Trimming audio to video duration (no looping)")

            val audioEffects = Effects(
                listOf(FadeoutAudioProcessor(totalDurationUs, fadeoutDurationUs, baseVolume)),
                emptyList()
            )

            // Trim audio from hook_start_time to (hook_start_time + video_duration)
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
                .setEffects(audioEffects)
                .build()

            val composition = Composition.Builder(
                listOf(EditedMediaItemSequence.Builder(listOf(editedMediaItem)).build())
            ).build()

            // Run Transformer
            val result = transformAudio(composition, tempFile.absolutePath)

            if (result != null) {
                // Move to cache - with fallback to copy if rename fails
                val renameSuccess = tempFile.renameTo(cacheFile)
                if (!renameSuccess) {
                    // Fallback: copy + delete (handles cross-filesystem moves)
                    Log.d(TAG, "Rename failed, using copy fallback")
                    tempFile.copyTo(cacheFile, overwrite = true)
                    tempFile.delete()
                }

                Log.d(TAG, "✅ Audio preprocessed successfully: ${cacheFile.name} (${cacheFile.length() / 1024}KB)")

                // Cleanup old cache files
                cleanupCache()

                return Uri.fromFile(cacheFile)
            } else {
                tempFile.delete()
                Log.e(TAG, "Audio preprocessing failed")
                return null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Audio preprocessing error: ${e.message}", e)
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
    private fun getCacheFile(songId: Long, trimStartMs: Long, totalDurationMs: Long, fadeoutDurationMs: Long, baseVolume: Float = 1.0f): File {
        val volumeInt = (baseVolume * 100).toInt()
        val filename = "${songId}_trim${trimStartMs}_dur${totalDurationMs}_fade${fadeoutDurationMs}_vol${volumeInt}.m4a"
        return File(cacheDir, filename)
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
    fun hasCachedPreprocessedAudio(songId: Long, trimStartMs: Long, totalDurationMs: Long, fadeoutDurationMs: Long, baseVolume: Float = 1.0f): Boolean {
        val cacheFile = getCacheFile(songId, trimStartMs, totalDurationMs, fadeoutDurationMs, baseVolume)
        return cacheFile.exists() && cacheFile.length() > 0
    }
}
