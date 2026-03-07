package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow

/**
 * GetProjectUseCase - Retrieves a project by ID
 */
class GetProjectUseCase(
    private val repository: ProjectRepository
) {
    sealed class GetProjectResult {
        data class Success(val project: Project) : GetProjectResult()
        data object NotFound : GetProjectResult()
    }

    /**
     * Get project by ID
     * @param projectId Project ID to retrieve
     * @return Result containing Success with project or NotFound
     */
    suspend operator fun invoke(projectId: String): Result<GetProjectResult> {
        return try {
            val project = repository.getProject(projectId)
            if (project != null) {
                Result.success(GetProjectResult.Success(project))
            } else {
                Result.success(GetProjectResult.NotFound)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe project for real-time updates
     * @param projectId Project ID to observe
     * @return Flow emitting project updates (nullable if not found)
     */
    fun observe(projectId: String): Flow<Project?> {
        return repository.observeProject(projectId)
    }
}
