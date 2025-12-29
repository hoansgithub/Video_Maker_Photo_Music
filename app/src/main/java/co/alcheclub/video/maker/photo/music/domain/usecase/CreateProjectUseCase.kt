package co.alcheclub.video.maker.photo.music.domain.usecase

import android.net.Uri
import co.alcheclub.video.maker.photo.music.domain.model.Project
import co.alcheclub.video.maker.photo.music.domain.repository.ProjectRepository

/**
 * CreateProjectUseCase - Creates a new project with selected assets
 */
class CreateProjectUseCase(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(assets: List<Uri>): Project {
        require(assets.isNotEmpty()) { "Cannot create project without assets" }
        return repository.createProject(assets)
    }
}
