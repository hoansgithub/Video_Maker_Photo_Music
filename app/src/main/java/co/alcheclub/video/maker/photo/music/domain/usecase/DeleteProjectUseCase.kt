package co.alcheclub.video.maker.photo.music.domain.usecase

import co.alcheclub.video.maker.photo.music.domain.repository.ProjectRepository

/**
 * DeleteProjectUseCase - Deletes a project and all its assets
 */
class DeleteProjectUseCase(
    private val repository: ProjectRepository
) {
    /**
     * Delete a project by ID
     */
    suspend operator fun invoke(projectId: String) {
        repository.deleteProject(projectId)
    }
}
