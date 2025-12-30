package co.alcheclub.video.maker.photo.music.domain.usecase

import co.alcheclub.video.maker.photo.music.domain.model.ProjectSettings
import co.alcheclub.video.maker.photo.music.domain.repository.ProjectRepository

/**
 * UpdateProjectSettingsUseCase - Updates project settings
 */
class UpdateProjectSettingsUseCase(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(projectId: String, settings: ProjectSettings) {
        // Validate settings
        val validImageDurations = ProjectSettings.IMAGE_DURATION_OPTIONS.map { it * 1000L }
        val validTransitionDurations = ProjectSettings.TRANSITION_DURATION_OPTIONS.map { it.toLong() }

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
            // Ensure transition duration is one of the valid options
            transitionOverlapMs = if (settings.transitionOverlapMs in validTransitionDurations) {
                settings.transitionOverlapMs
            } else {
                // Find closest valid option
                validTransitionDurations.minByOrNull {
                    kotlin.math.abs(it - settings.transitionOverlapMs)
                } ?: 500L
            },
            audioVolume = settings.audioVolume.coerceIn(0f, 1f)
        )
        repository.updateSettings(projectId, validatedSettings)
    }
}
