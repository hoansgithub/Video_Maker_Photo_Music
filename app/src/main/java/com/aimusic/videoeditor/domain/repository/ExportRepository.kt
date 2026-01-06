package com.aimusic.videoeditor.domain.repository

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * ExportRepository - Repository interface for video export operations
 */
interface ExportRepository {
    /**
     * Start exporting a project
     *
     * @param projectId The project to export
     * @return Work ID for tracking progress
     */
    fun startExport(projectId: String): UUID

    /**
     * Observe export progress
     *
     * @param workId The work ID returned from startExport
     * @return Flow of ExportProgress updates
     */
    fun observeExportProgress(workId: UUID): Flow<ExportProgress>

    /**
     * Cancel an ongoing export
     *
     * @param workId The work ID to cancel
     */
    fun cancelExport(workId: UUID)
}

/**
 * ExportProgress - Sealed class representing export progress states
 */
sealed class ExportProgress {
    /**
     * Export is being prepared (enqueued, waiting to start)
     */
    data object Preparing : ExportProgress()

    /**
     * Export is in progress
     *
     * @param percent Progress percentage (0-100)
     */
    data class Processing(val percent: Int) : ExportProgress()

    /**
     * Export completed successfully
     *
     * @param outputPath Path to the exported video file
     */
    data class Success(val outputPath: String) : ExportProgress()

    /**
     * Export failed
     *
     * @param message Error message
     */
    data class Error(val message: String) : ExportProgress()

    /**
     * Export was cancelled by user
     */
    data object Cancelled : ExportProgress()
}
