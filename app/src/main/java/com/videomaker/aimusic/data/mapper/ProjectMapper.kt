package com.videomaker.aimusic.data.mapper

import android.net.Uri
import com.videomaker.aimusic.data.local.database.entity.AssetEntity
import com.videomaker.aimusic.data.local.database.entity.ProjectEntity
import com.videomaker.aimusic.data.local.database.entity.ProjectWithAssets
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.Asset
import com.videomaker.aimusic.domain.model.AssetType
import com.videomaker.aimusic.domain.model.AudioNode
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.ProjectSettings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * ProjectMapper - Maps between database entities and domain models
 */
object ProjectMapper {

    private val json = Json { ignoreUnknownKeys = true }

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
                .map { toDomain(it) },
            isWatermarkFree = entity.project.isWatermarkFree
        )
    }

    /**
     * Map ProjectEntity to ProjectSettings
     */
    private fun toSettings(entity: ProjectEntity): ProjectSettings {
        val audioNodes = entity.audioNodesJson?.let { jsonStr ->
            try {
                json.decodeFromString(ListSerializer(AudioNode.serializer()), jsonStr)
            } catch (e: Exception) {
                android.util.Log.w("ProjectMapper", "Failed to deserialize audioNodesJson: ${e.message}")
                emptyList()
            }
        } ?: emptyList()

        return ProjectSettings(
            totalDurationMs = entity.totalDurationMs,
            effectSetId = entity.effectSetId,
            templateId = entity.templateId?.takeIf { it.isNotBlank() },
            overlayFrameId = entity.overlayFrameId,
            aspectRatio = AspectRatio.fromString(entity.aspectRatio),
            audioNodes = audioNodes
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
        val audioNodesJson = if (project.settings.audioNodes.isNotEmpty()) {
            json.encodeToString(ListSerializer(AudioNode.serializer()), project.settings.audioNodes)
        } else {
            null
        }

        return ProjectEntity(
            id = project.id,
            name = project.name,
            createdAt = project.createdAt,
            updatedAt = project.updatedAt,
            thumbnailUri = project.thumbnailUri?.toString(),
            totalDurationMs = project.settings.totalDurationMs,
            effectSetId = project.settings.effectSetId,
            templateId = project.settings.templateId?.takeIf { it.isNotBlank() },
            overlayFrameId = project.settings.overlayFrameId,
            aspectRatio = project.settings.aspectRatio.name,
            audioNodesJson = audioNodesJson,
            isWatermarkFree = project.isWatermarkFree
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
