package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.repository.ProjectRepository

/**
 * RemoveAssetUseCase - Removes an asset from a project
 *
 * Enforces minimum 2 images constraint - won't remove if it would
 * leave less than 2 images in the project.
 */
class RemoveAssetUseCase(
    private val projectRepository: ProjectRepository
) {
    companion object {
        const val MIN_ASSETS = 2
    }

    sealed class RemoveResult {
        data object Success : RemoveResult()
        data object BlockedByMinimum : RemoveResult()
        data object ProjectNotFound : RemoveResult()
    }

    /**
     * Remove an asset from a project
     * @param projectId Project ID
     * @param assetId Asset ID to remove
     * @return Result containing RemoveResult indicating success or reason for failure
     */
    suspend operator fun invoke(
        projectId: String,
        assetId: String
    ): Result<RemoveResult> {
        return try {
            // Fetch project to check asset count
            val project = projectRepository.getProject(projectId)
                ?: return Result.success(RemoveResult.ProjectNotFound)

            // Enforce minimum 2 images constraint
            if (project.assets.size <= MIN_ASSETS) {
                return Result.success(RemoveResult.BlockedByMinimum)
            }

            projectRepository.removeAsset(projectId, assetId)
            Result.success(RemoveResult.Success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
