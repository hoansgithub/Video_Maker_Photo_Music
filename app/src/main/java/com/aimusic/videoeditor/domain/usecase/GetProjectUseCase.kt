package com.aimusic.videoeditor.domain.usecase

import com.aimusic.videoeditor.domain.model.Project
import com.aimusic.videoeditor.domain.repository.ProjectRepository
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
