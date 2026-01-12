package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.domain.repository.ProjectRepository

/**
 * UpdateProjectSettingsUseCase - Updates project settings
 */
class UpdateProjectSettingsUseCase(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(projectId: String, settings: ProjectSettings) {
        // Validate settings
        val validImageDurations = ProjectSettings.IMAGE_DURATION_OPTIONS.map { it * 1000L }
        val validTransitionPercentages = ProjectSettings.TRANSITION_PERCENTAGE_OPTIONS

        val validatedSettings = settings.copy(
            // Ensure image duration is one of the valid options
            imageDurationMs = if (settings.imageDurationMs in validImageDurations) {
                settings.imageDurationMs
            } else {
                // Find closest valid option
                validImageDurations.minByOrNull {
                    kotlin.math.abs(it - settings.imageDurationMs)
                } ?: 3000L
            },
            // Ensure transition percentage is one of the valid options
            transitionPercentage = if (settings.transitionPercentage in validTransitionPercentages) {
                settings.transitionPercentage
            } else {
                // Find closest valid option
                validTransitionPercentages.minByOrNull {
                    kotlin.math.abs(it - settings.transitionPercentage)
                } ?: 30
            },
            audioVolume = settings.audioVolume.coerceIn(0f, 1f)
        )
        repository.updateSettings(projectId, validatedSettings)
    }
}
