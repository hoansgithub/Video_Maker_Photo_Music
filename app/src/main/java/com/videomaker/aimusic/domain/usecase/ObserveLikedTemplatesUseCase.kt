package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.LikedTemplateRepository
import kotlinx.coroutines.flow.Flow

class ObserveLikedTemplatesUseCase(private val repository: LikedTemplateRepository) {
    operator fun invoke(): Flow<List<VideoTemplate>> = repository.observeLikedTemplates()
    fun ids(): Flow<Set<String>> = repository.observeLikedTemplateIds()
}
