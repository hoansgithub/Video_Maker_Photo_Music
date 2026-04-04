package com.videomaker.aimusic.data.repository

import com.videomaker.aimusic.data.local.database.dao.LikedTemplateDao
import com.videomaker.aimusic.data.local.database.entity.LikedTemplateEntity
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.LikedTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LikedTemplateRepositoryImpl(
    private val dao: LikedTemplateDao
) : LikedTemplateRepository {

    override suspend fun likeTemplate(template: VideoTemplate) {
        dao.insert(template.toEntity())
    }

    override suspend fun unlikeTemplate(templateId: String) {
        dao.deleteById(templateId)
    }

    override fun observeLikedTemplates(): Flow<List<VideoTemplate>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeLikedTemplateIds(): Flow<Set<String>> =
        dao.observeLikedIds().map { it.toSet() }

    override fun observeIsLiked(templateId: String): Flow<Boolean> =
        dao.observeIsLiked(templateId)

    private fun VideoTemplate.toEntity() = LikedTemplateEntity(
        templateId = id,
        name = name,
        thumbnailPath = thumbnailPath,
        songId = songId,
        effectSetId = effectSetId,
        aspectRatio = aspectRatio,
        isPremium = isPremium,
        useCount = useCount
    )

    private fun LikedTemplateEntity.toModel() = VideoTemplate(
        id = templateId,
        name = name,
        thumbnailPath = thumbnailPath,
        songId = songId,
        effectSetId = effectSetId,
        aspectRatio = aspectRatio,
        isPremium = isPremium,
        useCount = useCount
    )
}
