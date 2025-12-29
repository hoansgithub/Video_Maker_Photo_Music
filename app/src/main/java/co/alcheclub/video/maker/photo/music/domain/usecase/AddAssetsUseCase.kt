package co.alcheclub.video.maker.photo.music.domain.usecase

import android.net.Uri
import co.alcheclub.video.maker.photo.music.domain.repository.ProjectRepository

/**
 * AddAssetsUseCase - Adds new assets to an existing project
 */
class AddAssetsUseCase(
    private val projectRepository: ProjectRepository
) {
    /**
     * Add assets to a project
     * @param projectId Project ID
     * @param assetUris URIs of images to add
     */
    suspend operator fun invoke(projectId: String, assetUris: List<Uri>) {
        if (assetUris.isEmpty()) return
        projectRepository.addAssets(projectId, assetUris)
    }
}
