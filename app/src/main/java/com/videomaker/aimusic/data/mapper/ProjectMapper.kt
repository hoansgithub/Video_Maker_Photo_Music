package com.videomaker.aimusic.data.mapper

import android.net.Uri
import com.videomaker.aimusic.data.local.database.entity.AssetEntity
import com.videomaker.aimusic.data.local.database.entity.ProjectEntity
import com.videomaker.aimusic.data.local.database.entity.ProjectWithAssets
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.Asset
import com.videomaker.aimusic.domain.model.AssetType
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.ProjectSettings

/**
 * ProjectMapper - Maps between database entities and domain models
 */
object ProjectMapper {

    /**
     * Map ProjectWithAssets entity to Project domain model
     */
    fun toDomain(entity: ProjectWithAssets): Project {
        return Project(
            id = entity.project.id,
            name = entity.project.name,
            createdAt = entity.project.createdAt,
            updatedAt = entity.project.updatedAt,
            thumbnailUri = entity.project.thumbnailUri?.let { Uri.parse(it) },
            settings = toSettings(entity.project),
            assets = entity.assets
                .sortedBy { it.orderIndex }
                .map { toDomain(it) }
        )
    }

    /**
     * Map ProjectEntity to ProjectSettings
     * Applies defaults for null values from legacy projects
     */
    private fun toSettings(entity: ProjectEntity): ProjectSettings {
        return ProjectSettings(
            imageDurationMs = entity.imageDurationMs,
            transitionPercentage = entity.transitionPercentage,
            effectSetId = entity.effectSetId,
            overlayFrameId = entity.overlayFrameId,
            musicSongId = entity.musicSongId,
            musicSongName = entity.musicSongName,
            musicSongUrl = entity.musicSongUrl,
            musicSongCoverUrl = entity.musicSongCoverUrl,
            customAudioUri = entity.customAudioUri?.let { Uri.parse(it) },
            processedAudioUri = entity.processedAudioUri?.let { Uri.parse(it) },
            audioVolume = entity.audioVolume,
            musicTrimStartMs = entity.musicTrimStartMs,
            musicTrimEndMs = entity.musicTrimEndMs,
            aspectRatio = AspectRatio.fromString(entity.aspectRatio)
        )
    }

    /**
     * Map AssetEntity to Asset domain model
     */
    fun toDomain(entity: AssetEntity): Asset {
        return Asset(
            id = entity.id,
            uri = Uri.parse(entity.uri),
            orderIndex = entity.orderIndex,
            type = AssetType.fromString(entity.type)
        )
    }

    /**
     * Map Project domain model to ProjectEntity
     */
    fun toEntity(project: Project): ProjectEntity {
        return ProjectEntity(
            id = project.id,
            name = project.name,
            createdAt = project.createdAt,
            updatedAt = project.updatedAt,
            thumbnailUri = project.thumbnailUri?.toString(),
            imageDurationMs = project.settings.imageDurationMs,
            transitionPercentage = project.settings.transitionPercentage,
            effectSetId = project.settings.effectSetId,
            overlayFrameId = project.settings.overlayFrameId,
            musicSongId = project.settings.musicSongId,
            musicSongName = project.settings.musicSongName,
            musicSongUrl = project.settings.musicSongUrl,
            musicSongCoverUrl = project.settings.musicSongCoverUrl,
            customAudioUri = project.settings.customAudioUri?.toString(),
            processedAudioUri = project.settings.processedAudioUri?.toString(),
            audioVolume = project.settings.audioVolume,
            musicTrimStartMs = project.settings.musicTrimStartMs,
            musicTrimEndMs = project.settings.musicTrimEndMs,
            aspectRatio = project.settings.aspectRatio.name
        )
    }

    /**
     * Map Asset domain model to AssetEntity
     */
    fun toEntity(asset: Asset, projectId: String): AssetEntity {
        return AssetEntity(
            id = asset.id,
            projectId = projectId,
            uri = asset.uri.toString(),
            orderIndex = asset.orderIndex,
            type = asset.type.name
        )
    }

    /**
     * Create AssetEntity from URI (for new assets)
     */
    fun createAssetEntity(
        id: String,
        projectId: String,
        uri: Uri,
        orderIndex: Int
    ): AssetEntity {
        return AssetEntity(
            id = id,
            projectId = projectId,
            uri = uri.toString(),
            orderIndex = orderIndex,
            type = AssetType.IMAGE.name
        )
    }
}
