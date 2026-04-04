package com.videomaker.aimusic.domain.repository

import com.videomaker.aimusic.domain.model.VideoTemplate
import kotlinx.coroutines.flow.Flow

interface LikedTemplateRepository {
    suspend fun likeTemplate(template: VideoTemplate)
    suspend fun unlikeTemplate(templateId: String)
    fun observeLikedTemplates(): Flow<List<VideoTemplate>>
    fun observeLikedTemplateIds(): Flow<Set<String>>
    fun observeIsLiked(templateId: String): Flow<Boolean>
}
