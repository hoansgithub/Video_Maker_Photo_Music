package co.alcheclub.video.maker.photo.music.domain.usecase

import co.alcheclub.video.maker.photo.music.domain.repository.ProjectRepository

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

    /**
     * Remove an asset from a project
     * @param projectId Project ID
     * @param assetId Asset ID to remove
     * @param currentAssetCount Current number of assets in the project
     * @return true if asset was removed, false if blocked by minimum constraint
     */
    suspend operator fun invoke(
        projectId: String,
        assetId: String,
        currentAssetCount: Int
    ): Boolean {
        // Enforce minimum 2 images constraint
        if (currentAssetCount <= MIN_ASSETS) {
            return false
        }

        projectRepository.removeAsset(projectId, assetId)
        return true
    }

    /**
     * Check if an asset can be removed (won't violate minimum constraint)
     */
    fun canRemove(currentAssetCount: Int): Boolean {
        return currentAssetCount > MIN_ASSETS
    }
}
