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
            val project = projectRepository.getProject(projectId)
                ?: return Result.failure(workDataOf(KEY_ERROR to "Project not found"))

            android.util.Log.d("VideoExportWorker", "Loaded project: id=$projectId, trimStart=${project.settings.musicTrimStartMs}, trimEnd=${project.settings.musicTrimEndMs}")

            if (project.assets.isEmpty()) {
                return Result.failure(workDataOf(KEY_ERROR to "Project has no assets"))
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
