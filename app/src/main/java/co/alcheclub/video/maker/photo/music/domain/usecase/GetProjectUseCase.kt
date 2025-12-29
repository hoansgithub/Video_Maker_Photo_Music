package co.alcheclub.video.maker.photo.music.domain.usecase

import co.alcheclub.video.maker.photo.music.domain.model.Project
import co.alcheclub.video.maker.photo.music.domain.repository.ProjectRepository
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
