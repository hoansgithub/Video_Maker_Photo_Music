package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow

/**
 * GetAllProjectsUseCase - Retrieves all projects
 *
 * Provides both one-time fetch and real-time observation of projects.
 */
class GetAllProjectsUseCase(
    private val repository: ProjectRepository
) {
    /**
     * Get all projects once
     * @return Result containing list of all projects
     */
    suspend operator fun invoke(): Result<List<Project>> {
        return try {
            val projects = repository.getAllProjects()
            Result.success(projects)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe all projects for real-time updates
     * @return Flow emitting list of projects whenever they change
     */
    fun observe(): Flow<List<Project>> {
        return repository.observeAllProjects()
    }
}
