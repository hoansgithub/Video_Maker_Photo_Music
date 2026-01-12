package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.repository.ProjectRepository

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
