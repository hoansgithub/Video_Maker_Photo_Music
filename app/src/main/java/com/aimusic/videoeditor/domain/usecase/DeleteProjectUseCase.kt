package com.aimusic.videoeditor.domain.usecase

import com.aimusic.videoeditor.domain.repository.ProjectRepository

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
