package com.aimusic.videoeditor.domain.usecase

import com.aimusic.videoeditor.domain.model.Project
import com.aimusic.videoeditor.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow

/**
 * GetAllProjectsUseCase - Retrieves all projects
 */
class GetAllProjectsUseCase(
    private val repository: ProjectRepository
) {
    /**
     * Get all projects once
     */
    suspend operator fun invoke(): List<Project> {
        return repository.getAllProjects()
    }

    /**
     * Observe all projects for real-time updates
     */
    fun observe(): Flow<List<Project>> {
        return repository.observeAllProjects()
    }
}
