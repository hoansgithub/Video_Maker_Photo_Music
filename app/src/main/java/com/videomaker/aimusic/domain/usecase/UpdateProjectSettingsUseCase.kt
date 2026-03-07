package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.domain.repository.ProjectRepository

/**
 * UpdateProjectSettingsUseCase - Updates project settings
 *
 * Validates settings using ProjectSettings.validate() to ensure
 * all values are within acceptable ranges and options.
 */
class UpdateProjectSettingsUseCase(
    private val repository: ProjectRepository
) {
    /**
     * Update project settings with validation
     * @param projectId Project ID to update
     * @param settings New settings (will be validated)
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(
        projectId: String,
        settings: ProjectSettings
    ): Result<Unit> {
        return try {
            // Validate settings (done in domain model)
            val validatedSettings = settings.validate()

            repository.updateSettings(projectId, validatedSettings)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
