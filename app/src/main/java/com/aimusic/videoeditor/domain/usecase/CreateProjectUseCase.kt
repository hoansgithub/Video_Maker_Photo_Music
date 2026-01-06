package com.aimusic.videoeditor.domain.usecase

import android.net.Uri
import com.aimusic.videoeditor.domain.model.Project
import com.aimusic.videoeditor.domain.repository.ProjectRepository

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
