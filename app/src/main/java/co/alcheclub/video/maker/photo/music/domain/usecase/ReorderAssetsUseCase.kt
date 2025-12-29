package co.alcheclub.video.maker.photo.music.domain.usecase

import co.alcheclub.video.maker.photo.music.domain.model.Asset
import co.alcheclub.video.maker.photo.music.domain.repository.ProjectRepository

/**
 * ReorderAssetsUseCase - Reorders assets in a project timeline
 */
class ReorderAssetsUseCase(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(projectId: String, assets: List<Asset>) {
        repository.reorderAssets(projectId, assets)
    }

    /**
     * Move an asset from one position to another
     */
    suspend fun move(projectId: String, assets: List<Asset>, fromIndex: Int, toIndex: Int): List<Asset> {
        if (fromIndex == toIndex) return assets
        if (fromIndex !in assets.indices || toIndex !in assets.indices) return assets

        val mutableAssets = assets.toMutableList()
        val item = mutableAssets.removeAt(fromIndex)
        mutableAssets.add(toIndex, item)

        // Update order indices
        val reindexedAssets = mutableAssets.mapIndexed { index, asset ->
            asset.copy(orderIndex = index)
        }

        repository.reorderAssets(projectId, reindexedAssets)
        return reindexedAssets
    }
}
