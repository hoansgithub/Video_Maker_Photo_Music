package com.videomaker.aimusic.domain.usecase

import android.net.Uri
import com.videomaker.aimusic.domain.repository.ProjectRepository

/**
 * AddAssetsUseCase - Adds new assets to an existing project
 *
 * Skips operation if asset list is empty.
 */
class AddAssetsUseCase(
    private val projectRepository: ProjectRepository
) {
    /**
     * Add assets to a project
     * @param projectId Project ID
     * @param assetUris URIs of images to add
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(
        projectId: String,
        assetUris: List<Uri>
    ): Result<Unit> {
        return try {
            if (assetUris.isEmpty()) {
                return Result.success(Unit)
            }
            projectRepository.addAssets(projectId, assetUris)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
