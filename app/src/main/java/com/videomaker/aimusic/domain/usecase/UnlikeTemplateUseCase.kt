package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.repository.LikedTemplateRepository

class UnlikeTemplateUseCase(private val repository: LikedTemplateRepository) {
    suspend operator fun invoke(templateId: String) = repository.unlikeTemplate(templateId)
}
