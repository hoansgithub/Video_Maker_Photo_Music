package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.TemplateRepository

class SearchTemplatesUseCase(
    private val repository: TemplateRepository
) {
    suspend operator fun invoke(query: String): Result<List<VideoTemplate>> =
        repository.searchTemplates(query)
}