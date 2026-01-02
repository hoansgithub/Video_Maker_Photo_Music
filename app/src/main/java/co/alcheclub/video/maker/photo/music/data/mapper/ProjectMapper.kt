package co.alcheclub.video.maker.photo.music.data.mapper

import android.net.Uri
import co.alcheclub.video.maker.photo.music.data.local.database.entity.AssetEntity
import co.alcheclub.video.maker.photo.music.data.local.database.entity.ProjectEntity
import co.alcheclub.video.maker.photo.music.data.local.database.entity.ProjectWithAssets
import co.alcheclub.video.maker.photo.music.domain.model.AspectRatio
import co.alcheclub.video.maker.photo.music.domain.model.Asset
import co.alcheclub.video.maker.photo.music.domain.model.AssetType
import co.alcheclub.video.maker.photo.music.domain.model.Project
import co.alcheclub.video.maker.photo.music.domain.model.ProjectSettings

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
            transitionId = entity.transitionId,
            overlayFrameId = entity.overlayFrameId,
            // Apply default audio track if not set (legacy projects)
            audioTrackId = entity.audioTrackId ?: ProjectSettings.DEFAULT_AUDIO_TRACK_ID,
            customAudioUri = entity.customAudioUri?.let { Uri.parse(it) },
            audioVolume = entity.audioVolume,
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
            transitionId = project.settings.transitionId,
            overlayFrameId = project.settings.overlayFrameId,
            audioTrackId = project.settings.audioTrackId,
            customAudioUri = project.settings.customAudioUri?.toString(),
            audioVolume = project.settings.audioVolume,
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
