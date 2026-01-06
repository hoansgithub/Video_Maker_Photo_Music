package com.aimusic.videoeditor.data.repository

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.aimusic.videoeditor.domain.repository.ExportProgress
import com.aimusic.videoeditor.domain.repository.ExportRepository
import com.aimusic.videoeditor.media.export.VideoExportWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * ExportRepositoryImpl - Implementation of ExportRepository
 *
 * Uses WorkManager to handle background video export.
 */
class ExportRepositoryImpl(
    private val workManager: WorkManager
) : ExportRepository {

    override fun startExport(projectId: String): UUID {
        val request = OneTimeWorkRequestBuilder<VideoExportWorker>()
            .setInputData(
                workDataOf(VideoExportWorker.KEY_PROJECT_ID to projectId)
            )
            .build()

        workManager.enqueueUniqueWork(
            "export_$projectId",
            ExistingWorkPolicy.REPLACE,
            request
        )

        return request.id
    }

    override fun observeExportProgress(workId: UUID): Flow<ExportProgress> {
        return workManager.getWorkInfoByIdFlow(workId).map { workInfo ->
            mapWorkInfoToProgress(workInfo)
        }
    }

    override fun cancelExport(workId: UUID) {
        workManager.cancelWorkById(workId)
    }

    private fun mapWorkInfoToProgress(workInfo: WorkInfo?): ExportProgress {
        if (workInfo == null) {
            return ExportProgress.Preparing
        }

        return when (workInfo.state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED -> ExportProgress.Preparing

            WorkInfo.State.RUNNING -> {
                val progress = workInfo.progress.getInt(VideoExportWorker.KEY_PROGRESS, 0)
                ExportProgress.Processing(progress)
            }

            WorkInfo.State.SUCCEEDED -> {
                val outputPath = workInfo.outputData.getString(VideoExportWorker.KEY_OUTPUT_PATH)
                    ?: ""
                ExportProgress.Success(outputPath)
            }

            WorkInfo.State.FAILED -> {
                val error = workInfo.outputData.getString(VideoExportWorker.KEY_ERROR)
                    ?: "Export failed"
                ExportProgress.Error(error)
            }

            WorkInfo.State.CANCELLED -> ExportProgress.Cancelled
        }
    }
}
