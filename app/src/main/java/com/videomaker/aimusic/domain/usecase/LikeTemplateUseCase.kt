package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.LikedTemplateRepository

class LikeTemplateUseCase(private val repository: LikedTemplateRepository) {
    suspend operator fun invoke(template: VideoTemplate) = repository.likeTemplate(template)
}
