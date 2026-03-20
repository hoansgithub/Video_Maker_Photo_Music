package com.videomaker.aimusic.domain.usecase

import android.net.Uri
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.domain.repository.ProjectRepository

/**
 * CreateProjectUseCase - Creates a new project with selected assets
 *
 * Requires at least one asset to create a project.
 */
class CreateProjectUseCase(
    private val repository: ProjectRepository
) {
    /**
     * Create a new project with the given assets (default settings)
     * @param assets List of asset URIs (must not be empty)
     * @return Result containing the created Project
     */
    suspend operator fun invoke(assets: List<Uri>): Result<Project> {
        return try {
            require(assets.isNotEmpty()) { "Cannot create project without assets" }
            val project = repository.createProject(assets)
            Result.success(project)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new project with the given assets and settings
     * @param assets List of asset URIs (must not be empty)
     * @param settings Initial project settings (effect set, duration, aspect ratio, etc.)
     * @return Result containing the created Project
     */
    suspend operator fun invoke(assets: List<Uri>, settings: ProjectSettings): Result<Project> {
        return try {
            require(assets.isNotEmpty()) { "Cannot create project without assets" }
            val project = repository.createProject(assets, settings)
            Result.success(project)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
