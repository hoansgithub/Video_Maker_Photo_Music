package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.domain.repository.ProjectRepository

/**
 * UpdateProjectSettingsUseCase - Updates project settings (BEAT-SYNC ONLY)
 */
class UpdateProjectSettingsUseCase(
    private val repository: ProjectRepository
) {
    /**
     * Update project settings
     * @param projectId Project ID to update
     * @param settings New settings
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(
        projectId: String,
        settings: ProjectSettings
    ): Result<Unit> {
        return try {
            repository.updateSettings(projectId, settings)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
