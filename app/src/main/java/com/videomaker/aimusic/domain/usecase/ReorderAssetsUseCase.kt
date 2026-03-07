package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.Asset
import com.videomaker.aimusic.domain.repository.ProjectRepository

/**
 * ReorderAssetsUseCase - Reorders assets in a project timeline
 *
 * Updates the order of assets in the project. The caller (ViewModel) should
 * handle the list manipulation (drag-drop, move, etc.) and pass the final
 * reordered list to this UseCase.
 */
class ReorderAssetsUseCase(
    private val repository: ProjectRepository
) {
    /**
     * Reorder assets in a project
     * @param projectId Project ID
     * @param assets Reordered list of assets (order indices will be updated automatically)
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(
        projectId: String,
        assets: List<Asset>
    ): Result<Unit> {
        return try {
            // Update order indices based on list position
            val reindexedAssets = assets.mapIndexed { index, asset ->
                asset.copy(orderIndex = index)
            }

            repository.reorderAssets(projectId, reindexedAssets)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
