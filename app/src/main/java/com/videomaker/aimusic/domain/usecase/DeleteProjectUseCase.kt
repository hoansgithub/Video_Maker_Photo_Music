package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.repository.ProjectRepository

/**
 * DeleteProjectUseCase - Deletes a project and all its assets
 *
 * This is a destructive operation that removes the project and all
 * associated data from storage.
 */
class DeleteProjectUseCase(
    private val repository: ProjectRepository
) {
    /**
     * Delete a project by ID
     * @param projectId ID of the project to delete
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(projectId: String): Result<Unit> {
        return try {
            repository.deleteProject(projectId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
