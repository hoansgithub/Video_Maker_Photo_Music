package com.videomaker.aimusic.media.export

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.videomaker.aimusic.domain.model.VideoQuality
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.videomaker.aimusic.domain.repository.ProjectRepository
import com.videomaker.aimusic.media.composition.CompositionFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * VideoExportWorker - WorkManager worker for background video export
 *
 * Uses Media3 Transformer to export video.
 * Reports progress via setProgress().
 * Handles cancellation properly.
 */
class VideoExportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val projectRepository: ProjectRepository by inject()
    private val compositionFactory: CompositionFactory by inject()
    private val audioCache: com.videomaker.aimusic.media.audio.AudioPreviewCache by inject()
    private val beatSyncRepository: com.videomaker.aimusic.domain.repository.BeatSyncRepository by inject()
    private val audioPreprocessingService: com.videomaker.aimusic.media.audio.AudioPreprocessingService by inject()

    companion object {
        private const val TAG = "VideoExportWorker"
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_ERROR = "error"
        const val KEY_FORCE_WATERMARK_FREE = "force_watermark_free"
        const val KEY_QUALITY = "quality"
        const val KEY_ERROR_CODE = "error_code"

        private const val AUDIO_BITRATE = 128_000 // 128 kbps AAC

        private fun getBitrateForQuality(quality: VideoQuality): Int = when (quality) {
            VideoQuality.HD_720 -> 2_500_000   // 2.5 Mbps
            VideoQuality.FHD_1080 -> 5_000_000 // 5 Mbps
        }

        private val exportCleanupMutex = Mutex()
    }

    private var transformer: Transformer? = null
    private var progressHandler: Handler? = null
    private var progressRunnable: Runnable? = null

    /**
     * Check if there is enough disk space for the export.
     * Estimates output size based on duration and bitrate with 50% overhead margin.
     */
    private fun checkDiskSpace(durationMs: Long, quality: VideoQuality): String? {
        val videoBitrate = getBitrateForQuality(quality)
        val totalBitrate = videoBitrate + AUDIO_BITRATE
        val durationSeconds = durationMs / 1000.0
        val estimatedBytes = (durationSeconds * totalBitrate / 8 * 1.5).toLong()
        val safetyMargin = 50L * 1024 * 1024 // 50MB
        val requiredBytes = estimatedBytes + safetyMargin

        val moviesDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: applicationContext.filesDir
        val availableBytes = moviesDir.usableSpace

        return if (availableBytes < requiredBytes) {
            val requiredMB = requiredBytes / (1024 * 1024)
            val availableMB = availableBytes / (1024 * 1024)
            "Insufficient storage space. Need ~${requiredMB}MB, available: ${availableMB}MB. Free up space and try again."
        } else {
            null // Enough space
        }
    }

    /**
     * Delete old export files for this project before creating a new one.
     * Uses Mutex to prevent race conditions when watermarked and clean exports run in parallel.
     */
    private suspend fun cleanupOldExports(projectId: String, moviesDir: File) {
        exportCleanupMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val oldFiles = moviesDir.listFiles { file ->
                        file.name.startsWith("video_${projectId}") && file.name.endsWith(".mp4")
                    }
                    oldFiles?.forEach { file ->
                        if (file.delete()) {
                            android.util.Log.d(TAG, "Deleted old export: ${file.name}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to clean old exports: ${e.message}")
                }
            }
        }
    }

    override suspend fun doWork(): Result {
        val projectId = inputData.getString(KEY_PROJECT_ID)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Missing project ID"))

        return try {
            val forceWatermarkFree = inputData.getBoolean(KEY_FORCE_WATERMARK_FREE, false)
            val quality = inputData.getString(KEY_QUALITY)?.let { qualityStr ->
                runCatching { VideoQuality.valueOf(qualityStr) }.getOrElse { e ->
                    android.util.Log.e(TAG, "Invalid quality value: $qualityStr, using DEFAULT", e)
                    VideoQuality.DEFAULT
                }
            } ?: VideoQuality.DEFAULT

            var project = projectRepository.getProject(projectId)
                ?: return Result.failure(workDataOf(KEY_ERROR to "Project not found"))

            // Watermark removal is per-session: main export always includes watermark,
            // clean export (forceWatermarkFree=true) always excludes it — regardless of
            // the persisted isWatermarkFree flag so old projects are treated the same.
            project = project.copy(isWatermarkFree = forceWatermarkFree)

            android.util.Log.d("VideoExportWorker", "Loaded project: id=$projectId, forceWatermarkFree=$forceWatermarkFree, isWatermarkFree=${project.isWatermarkFree}")

            if (project.assets.isEmpty()) {
                return Result.failure(workDataOf(KEY_ERROR to "Project has no assets"))
            }

            // Load beat-sync data if music is selected but beatSyncData is null
            // (beatSyncData is not persisted in database, must be loaded from Supabase)
            val exportSongId = project.settings.primaryAudioNode?.songId
            if (exportSongId != null && project.settings.beatSyncData == null) {
                android.util.Log.d("VideoExportWorker", "Loading beat-sync data for song $exportSongId")

                val beatSyncData = try {
                    beatSyncRepository.getBeatData(exportSongId).getOrNull()
                        ?: return Result.failure(workDataOf(KEY_ERROR to "Beat-sync data not available for this song"))
                } catch (e: Exception) {
                    android.util.Log.e("VideoExportWorker", "Failed to load beat-sync data", e)
                    return Result.failure(workDataOf(KEY_ERROR to "Failed to load beat-sync data: ${e.message}"))
                }

                // Calculate total duration with beat-sync data
                val exportTrimStart = project.settings.primaryAudioNode?.trimStartMs ?: project.settings.hookStartTimeMs
                val totalDurationMs = com.videomaker.aimusic.domain.model.Project.calculateBeatSyncDuration(
                    beatData = beatSyncData,
                    assetCount = project.assets.size,
                    trimStartMs = exportTrimStart
                ) ?: 0L

                // Update project settings with beat-sync data
                project = project.copy(
                    settings = project.settings.copy(
                        beatSyncData = beatSyncData,
                        totalDurationMs = totalDurationMs
                    )
                )

                android.util.Log.d("VideoExportWorker", "Beat-sync data loaded: BPM=${beatSyncData.bpm}, duration=${totalDurationMs}ms")
            }

            // Re-preprocess to ensure processedAudioUri points to a valid cached file.
            // Volume is applied by VolumeAudioProcessor in CompositionFactory, NOT baked in here.
            val exportNode = project.settings.primaryAudioNode
            if (exportNode?.songId != null &&
                exportNode.songUrl != null &&
                project.settings.beatSyncData != null) {

                android.util.Log.d("VideoExportWorker", "Preprocessing audio with fadeout for export")

                val beatData = project.settings.beatSyncData
                val beatMs = 60000.0 / beatData.bpm
                val fadeoutDurationMs = (beatMs * 6).toLong() // 6 beats fadeout
                val preprocessedUri = audioPreprocessingService.preprocessAudioWithFadeout(
                    sourceUri = android.net.Uri.parse(exportNode.songUrl),
                    songId = exportNode.songId,
                    trimStartMs = exportNode.trimStartMs,
                    totalDurationMs = project.settings.totalDurationMs,
                    fadeoutDurationMs = fadeoutDurationMs
                )

                if (preprocessedUri != null) {
                    val updatedNode = exportNode.copy(processedAudioUri = preprocessedUri.toString())
                    project = project.copy(
                        settings = project.settings.copy(
                            audioNodes = listOf(updatedNode) + project.settings.audioNodes.drop(1)
                        )
                    )
                    android.util.Log.d("VideoExportWorker", "Audio preprocessed with fadeout: $preprocessedUri")
                } else {
                    android.util.Log.e("VideoExportWorker", "Audio preprocessing failed — aborting export")
                    return Result.failure(workDataOf(
                        KEY_ERROR to "Audio processing failed. Please check your network and try again.",
                        KEY_ERROR_CODE to -1
                    ))
                }
            }

            // Validate duration before export
            val durationMs = project.settings.totalDurationMs
            if (durationMs <= 0) {
                return Result.failure(workDataOf(
                    KEY_ERROR to "Invalid project duration. Please re-create the video.",
                    KEY_ERROR_CODE to -1
                ))
            }

            // Check disk space before starting export
            val spaceError = checkDiskSpace(durationMs, quality)
            if (spaceError != null) {
                return Result.failure(workDataOf(KEY_ERROR to spaceError))
            }

            // Clean up old export files for this project
            val moviesDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: applicationContext.filesDir
            cleanupOldExports(projectId, moviesDir)

            val outputFile = createOutputFile(projectId, isClean = forceWatermarkFree)
            val composition = compositionFactory.createComposition(project, includeAudio = true, forExport = true, exportQuality = quality)

            try {
                exportVideo(composition, outputFile.absolutePath, quality)
                Result.success(workDataOf(KEY_OUTPUT_PATH to outputFile.absolutePath))
            } catch (e: ExportException) {
                val errorMessage = mapExportError(e)
                Result.failure(workDataOf(
                    KEY_ERROR to errorMessage,
                    KEY_ERROR_CODE to e.errorCode
                ))
            } catch (e: Exception) {
                Result.failure(workDataOf(
                    KEY_ERROR to (e.message ?: "Export failed"),
                    KEY_ERROR_CODE to -1
                ))
            } finally {
                // Clean up preprocessed image PNGs created by this local CompositionFactory instance
                compositionFactory.recycleBitmaps()
            }

        } catch (e: Exception) {
            Result.failure(workDataOf(
                KEY_ERROR to (e.message ?: "Export failed"),
                KEY_ERROR_CODE to -1
            ))
        }
    }

    /**
     * Map ExportException error codes to user-friendly messages.
     */
    private fun mapExportError(e: ExportException): String {
        return when (e.errorCode) {
            ExportException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "A media file could not be found. Please check your images and try again."
            ExportException.ERROR_CODE_IO_UNSPECIFIED ->
                "A file error occurred during export. Please free up storage and try again."
            ExportException.ERROR_CODE_ENCODER_INIT_FAILED ->
                "Video encoder not supported on this device. Try a lower quality setting."
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED ->
                "Video format not supported on this device. Try a lower quality setting."
            ExportException.ERROR_CODE_DECODER_INIT_FAILED ->
                "Media decoder failed to initialize. Please restart the app and try again."
            else ->
                "Export failed (error ${e.errorCode}): ${e.message ?: "Unknown error"}"
        }
    }

    private fun createOutputFile(projectId: String, isClean: Boolean = false): File {
        val moviesDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: applicationContext.filesDir

        val suffix = if (isClean) "_clean" else ""
        val fileName = "video_${projectId}${suffix}_${System.currentTimeMillis()}.mp4"
        return File(moviesDir, fileName)
    }

    private suspend fun exportVideo(composition: Composition, outputPath: String, quality: VideoQuality = VideoQuality.DEFAULT) {
        suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            progressHandler = mainHandler
            val isCancelled = AtomicBoolean(false)

            // Transformer must be created and started on the main thread
            mainHandler.post {
                try {
                    // SimpleCache is global - if music was cached during preview,
                    // Transformer will use the cached version automatically
                    val encoderFactory = DefaultEncoderFactory.Builder(applicationContext)
                        .setEnableFallback(true)
                        .setRequestedVideoEncoderSettings(
                            androidx.media3.transformer.VideoEncoderSettings.Builder()
                                .setBitrate(getBitrateForQuality(quality))
                                .build()
                        )
                        .build()

                    transformer = Transformer.Builder(applicationContext)
                        .setEncoderFactory(encoderFactory)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, result: ExportResult) {
                                stopProgressTracking()
                                cleanupTransformer()
                                if (continuation.isActive) {
                                    continuation.resume(Unit)
                                }
                            }

                            override fun onError(
                                composition: Composition,
                                result: ExportResult,
                                exception: ExportException
                            ) {
                                android.util.Log.e(TAG, "Transformer error: ${exception.errorCode}, ${exception.message}", exception)
                                stopProgressTracking()
                                cleanupTransformer()
                                if (continuation.isActive) {
                                    continuation.resumeWithException(exception)
                                }
                            }
                        })
                        .build()

                    // Start progress tracking

                    val runnable = object : Runnable {
                        override fun run() {
                            if (isCancelled.get()) return
                            val progressHolder = ProgressHolder()
                            transformer?.getProgress(progressHolder)

                            val progress = progressHolder.progress
                            if (progress >= 0) {
                                setProgressAsync(workDataOf(KEY_PROGRESS to progress))
                            }

                            if (progress < 100 && !isStopped && !isCancelled.get()) {
                                mainHandler.postDelayed(this, 500)
                            }
                        }
                    }
                    progressRunnable = runnable
                    mainHandler.post(runnable)

                    // Start export on main thread
                    transformer?.start(composition, outputPath)

                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to start transformer", e)
                    cleanupTransformer()
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            }

            // Handle cancellation
            continuation.invokeOnCancellation {
                isCancelled.set(true)   // Immediate flag — runnable checks this before re-posting
                mainHandler.post {
                    transformer?.cancel()
                    stopProgressTracking()
                    cleanupTransformer()
                }
                File(outputPath).delete()
            }
        }
    }

    private fun stopProgressTracking() {
        progressRunnable?.let { runnable ->
            progressHandler?.removeCallbacks(runnable)
        }
        progressHandler = null
        progressRunnable = null
    }

    /**
     * Clean up transformer resources
     *
     * Note: Transformer doesn't have a release() method in Media3.
     * Cleanup is done by calling cancel() and nullifying the reference.
     * This follows the pattern from Media3's official demo:
     * https://github.com/androidx/media/blob/release/demos/transformer/src/main/java/androidx/media3/demo/transformer/TransformerActivity.java
     */
    private fun cleanupTransformer() {
        transformer?.cancel()
        transformer = null
    }

}
