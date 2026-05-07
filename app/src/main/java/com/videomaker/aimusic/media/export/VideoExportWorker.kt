package com.videomaker.aimusic.media.export

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.videomaker.aimusic.domain.repository.ProjectRepository
import com.videomaker.aimusic.media.composition.CompositionFactory
import kotlinx.coroutines.suspendCancellableCoroutine
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
    }

    private var transformer: Transformer? = null
    private var progressHandler: Handler? = null
    private var progressRunnable: Runnable? = null

    override suspend fun doWork(): Result {
        val projectId = inputData.getString(KEY_PROJECT_ID)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Missing project ID"))

        return try {
            var project = projectRepository.getProject(projectId)
                ?: return Result.failure(workDataOf(KEY_ERROR to "Project not found"))

            android.util.Log.d("VideoExportWorker", "Loaded project: id=$projectId")

            if (project.assets.isEmpty()) {
                return Result.failure(workDataOf(KEY_ERROR to "Project has no assets"))
            }

            // Load beat-sync data if music is selected but beatSyncData is null
            // (beatSyncData is not persisted in database, must be loaded from Supabase)
            if (project.settings.musicSongId != null && project.settings.beatSyncData == null) {
                android.util.Log.d("VideoExportWorker", "Loading beat-sync data for song ${project.settings.musicSongId}")

                val beatSyncData = try {
                    beatSyncRepository.getBeatData(project.settings.musicSongId).getOrNull()
                        ?: return Result.failure(workDataOf(KEY_ERROR to "Beat-sync data not available for this song"))
                } catch (e: Exception) {
                    android.util.Log.e("VideoExportWorker", "Failed to load beat-sync data", e)
                    return Result.failure(workDataOf(KEY_ERROR to "Failed to load beat-sync data: ${e.message}"))
                }

                // Calculate total duration with beat-sync data
                val totalDurationMs = com.videomaker.aimusic.domain.model.Project.calculateBeatSyncDuration(
                    beatData = beatSyncData,
                    assetCount = project.assets.size,
                    trimStartMs = 0L
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

            // Preprocess audio with fadeout if needed
            if (project.settings.musicSongId != null &&
                project.settings.musicSongUrl != null &&
                project.settings.beatSyncData != null &&
                project.settings.processedAudioUri == null) {

                android.util.Log.d("VideoExportWorker", "Preprocessing audio with fadeout for export")

                val beatData = project.settings.beatSyncData
                val beatMs = 60000.0 / beatData.bpm
                val fadeoutDurationMs = (beatMs * 6).toLong() // 6 beats fadeout
                val hookStartTimeMs = project.settings.hookStartTimeMs

                val preprocessedUri = audioPreprocessingService.preprocessAudioWithFadeout(
                    sourceUri = android.net.Uri.parse(project.settings.musicSongUrl),
                    songId = project.settings.musicSongId,
                    trimStartMs = hookStartTimeMs,
                    totalDurationMs = project.settings.totalDurationMs,
                    fadeoutDurationMs = fadeoutDurationMs,
                    baseVolume = project.settings.audioVolume
                )

                if (preprocessedUri != null) {
                    project = project.copy(
                        settings = project.settings.copy(
                            processedAudioUri = preprocessedUri
                        )
                    )
                    android.util.Log.d("VideoExportWorker", "✅ Audio preprocessed with fadeout: $preprocessedUri")
                } else {
                    android.util.Log.w("VideoExportWorker", "⚠️ Audio preprocessing failed, export will have no audio")
                }
            }

            val outputFile = createOutputFile(projectId)
            val composition = compositionFactory.createComposition(project, includeAudio = true, forExport = true)

            try {
                exportVideo(composition, outputFile.absolutePath)
                Result.success(workDataOf(KEY_OUTPUT_PATH to outputFile.absolutePath))
            } catch (e: Exception) {
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Export failed")))
            } finally {
                // Clean up preprocessed image PNGs created by this local CompositionFactory instance
                compositionFactory.recycleBitmaps()
            }

        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Export failed")))
        }
    }

    private fun createOutputFile(projectId: String): File {
        val moviesDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: applicationContext.filesDir

        val fileName = "video_${projectId}_${System.currentTimeMillis()}.mp4"
        return File(moviesDir, fileName)
    }

    private suspend fun exportVideo(composition: Composition, outputPath: String) {
        suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            progressHandler = mainHandler
            val isCancelled = AtomicBoolean(false)

            // Transformer must be created and started on the main thread
            mainHandler.post {
                try {
                    // SimpleCache is global - if music was cached during preview,
                    // Transformer will use the cached version automatically
                    transformer = Transformer.Builder(applicationContext)
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
