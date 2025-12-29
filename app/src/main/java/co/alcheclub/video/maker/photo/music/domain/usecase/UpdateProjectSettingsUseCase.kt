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
        val validTransitionDurations = ProjectSettings.DURATION_OPTIONS.map { it * 1000L }
        val validatedSettings = settings.copy(
            // Ensure transition duration is one of the valid options
            transitionDurationMs = if (settings.transitionDurationMs in validTransitionDurations) {
                settings.transitionDurationMs
            } else {
                // Find closest valid option
                validTransitionDurations.minByOrNull {
                    kotlin.math.abs(it - settings.transitionDurationMs)
                } ?: 3000L
            },
            audioVolume = settings.audioVolume.coerceIn(0f, 1f)
        )
        repository.updateSettings(projectId, validatedSettings)
    }
}
