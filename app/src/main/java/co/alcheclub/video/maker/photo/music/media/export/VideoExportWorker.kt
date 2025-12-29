package co.alcheclub.video.maker.photo.music.media.export

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
import co.alcheclub.lib.acccore.di.ACCDI
import co.alcheclub.video.maker.photo.music.domain.repository.ProjectRepository
import co.alcheclub.video.maker.photo.music.media.composition.CompositionFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
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
) : CoroutineWorker(context, params) {

    companion object {
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
            // Get dependencies via ACCDI
            val projectRepository: ProjectRepository = ACCDI.get()
            val compositionFactory = CompositionFactory(applicationContext)

            // Load project
            val project = projectRepository.getProject(projectId)
                ?: return Result.failure(workDataOf(KEY_ERROR to "Project not found"))

            if (project.assets.isEmpty()) {
                return Result.failure(workDataOf(KEY_ERROR to "Project has no assets"))
            }

            // Create output file
            val outputFile = createOutputFile(projectId)

            // Build composition
            val composition = compositionFactory.createComposition(project)

            // Export video
            exportVideo(composition, outputFile.absolutePath)

            // Return success with output path
            Result.success(workDataOf(KEY_OUTPUT_PATH to outputFile.absolutePath))

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
            val handler = Handler(Looper.getMainLooper())
            progressHandler = handler

            transformer = Transformer.Builder(applicationContext)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        stopProgressTracking()
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException
                    ) {
                        stopProgressTracking()
                        if (continuation.isActive) {
                            continuation.resumeWithException(exception)
                        }
                    }
                })
                .build()

            // Start progress tracking
            val runnable = object : Runnable {
                override fun run() {
                    val progressHolder = ProgressHolder()
                    transformer?.getProgress(progressHolder)

                    val progress = progressHolder.progress
                    if (progress >= 0) {
                        setProgressAsync(workDataOf(KEY_PROGRESS to progress))
                    }

                    if (progress < 100 && !isStopped) {
                        handler.postDelayed(this, 500)
                    }
                }
            }
            progressRunnable = runnable
            handler.post(runnable)

            // Start export
            transformer?.start(composition, outputPath)

            // Handle cancellation
            continuation.invokeOnCancellation {
                transformer?.cancel()
                stopProgressTracking()
                // Delete partial file
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

}
