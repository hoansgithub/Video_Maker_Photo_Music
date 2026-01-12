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
    /**
     * Get project once
     */
    suspend operator fun invoke(projectId: String): Project? {
        return repository.getProject(projectId)
    }

    /**
     * Observe project for real-time updates
     */
    fun observe(projectId: String): Flow<Project?> {
        return repository.observeProject(projectId)
    }
}
