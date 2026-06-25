package com.videomaker.aimusic.data.repository

import android.net.Uri
import com.videomaker.aimusic.data.local.database.dao.AssetDao
import com.videomaker.aimusic.data.local.database.dao.ProjectDao
import com.videomaker.aimusic.data.local.database.entity.ProjectEntity
import com.videomaker.aimusic.data.mapper.ProjectMapper
import com.videomaker.aimusic.domain.model.Asset
import com.videomaker.aimusic.domain.model.AudioNode
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.domain.model.TextOverlay
import com.videomaker.aimusic.domain.model.StickerPlacement
import com.videomaker.aimusic.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.videomaker.aimusic.data.local.database.ProjectDatabase
import androidx.room.withTransaction
import java.util.UUID

/**
 * ProjectRepositoryImpl - Implementation of ProjectRepository
 *
 * Handles all database operations for projects and assets.
 */
class ProjectRepositoryImpl(
    private val database: ProjectDatabase,
    private val projectDao: ProjectDao,
    private val assetDao: AssetDao
) : ProjectRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createProject(assets: List<Uri>): Project {
        val projectId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Create project entity
        val projectEntity = ProjectEntity(
            id = projectId,
            name = "Project ${now.toString().takeLast(4)}",
            createdAt = now,
            updatedAt = now,
            thumbnailUri = assets.firstOrNull()?.toString(),
            totalDurationMs = ProjectSettings.DEFAULT.totalDurationMs
        )

        // Create asset entities
        val assetEntities = assets.mapIndexed { index, uri ->
            ProjectMapper.createAssetEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                uri = uri,
                orderIndex = index
            )
        }

        // Insert into database
        projectDao.insert(projectEntity)
        assetDao.insertAll(assetEntities)

        // Return the created project
        return getProject(projectId) ?: throw IllegalStateException("Failed to create project")
    }

    override suspend fun createProject(assets: List<Uri>, settings: ProjectSettings): Project {
        val projectId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Serialize audio nodes
        val audioNodesJson = if (settings.audioNodes.isNotEmpty()) {
            json.encodeToString(ListSerializer(AudioNode.serializer()), settings.audioNodes)
        } else {
            null
        }

        // Serialize text overlays
        val textOverlaysJson = if (settings.textOverlays.isNotEmpty()) {
            json.encodeToString(ListSerializer(TextOverlay.serializer()), settings.textOverlays)
        } else {
            null
        }

        // Serialize stickers (persist + read by export pipeline)
        val stickersJson = if (settings.stickers.isNotEmpty()) {
            json.encodeToString(ListSerializer(StickerPlacement.serializer()), settings.stickers)
        } else {
            null
        }

        // Create project entity with settings
        val projectEntity = ProjectEntity(
            id = projectId,
            name = "Project ${now.toString().takeLast(4)}",
            createdAt = now,
            updatedAt = now,
            thumbnailUri = assets.firstOrNull()?.toString(),
            totalDurationMs = settings.totalDurationMs,
            effectSetId = settings.effectSetId,
            templateId = settings.templateId?.takeIf { it.isNotBlank() },
            overlayFrameId = settings.overlayFrameId,
            aspectRatio = settings.aspectRatio.name,
            audioNodesJson = audioNodesJson,
            textOverlaysJson = textOverlaysJson,
            stickersJson = stickersJson
        )

        // Create asset entities
        val assetEntities = assets.mapIndexed { index, uri ->
            ProjectMapper.createAssetEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                uri = uri,
                orderIndex = index
            )
        }

        // Insert into database
        projectDao.insert(projectEntity)
        assetDao.insertAll(assetEntities)

        // Return the created project
        return getProject(projectId) ?: throw IllegalStateException("Failed to create project")
    }

    override suspend fun getProject(id: String): Project? {
        return projectDao.getWithAssets(id)?.let { ProjectMapper.toDomain(it) }
    }

    override fun observeProject(id: String): Flow<Project?> {
        return projectDao.observeWithAssets(id).map { entity ->
            entity?.let { ProjectMapper.toDomain(it) }
        }
    }

    override suspend fun getAllProjects(): List<Project> {
        return projectDao.observeAllWithAssets()
            .map { list -> list.map { ProjectMapper.toDomain(it) } }
            .first()  // Take first emission and complete (fixes infinite Flow issue)
    }

    override fun observeAllProjects(): Flow<List<Project>> {
        return projectDao.observeAllWithAssets().map { list ->
            list.map { ProjectMapper.toDomain(it) }
        }
    }

    override suspend fun updateSettings(projectId: String, settings: ProjectSettings) {
        // Load existing entity
        val existingEntity = projectDao.getById(projectId) ?: return

        // Serialize audio nodes
        val audioNodesJson = if (settings.audioNodes.isNotEmpty()) {
            json.encodeToString(ListSerializer(AudioNode.serializer()), settings.audioNodes)
        } else {
            null
        }

        // Serialize text overlays
        val textOverlaysJson = if (settings.textOverlays.isNotEmpty()) {
            json.encodeToString(ListSerializer(TextOverlay.serializer()), settings.textOverlays)
        } else {
            null
        }

        // Serialize stickers (so they persist and are read by the export pipeline)
        val stickersJson = if (settings.stickers.isNotEmpty()) {
            json.encodeToString(ListSerializer(StickerPlacement.serializer()), settings.stickers)
        } else {
            null
        }

        // Update entity with new settings
        val updatedEntity = existingEntity.copy(
            totalDurationMs = settings.totalDurationMs,
            effectSetId = settings.effectSetId,
            templateId = settings.templateId?.takeIf { it.isNotBlank() },
            overlayFrameId = settings.overlayFrameId,
            aspectRatio = settings.aspectRatio.name,
            audioNodesJson = audioNodesJson,
            textOverlaysJson = textOverlaysJson,
            stickersJson = stickersJson,
            updatedAt = System.currentTimeMillis()
        )

        // Save updated entity
        projectDao.update(updatedEntity)
    }

    override suspend fun reorderAssets(projectId: String, assets: List<Asset>) {
        val assetEntities = assets.mapIndexed { index, asset ->
            ProjectMapper.toEntity(asset.copy(orderIndex = index), projectId)
        }
        assetDao.updateAll(assetEntities)
        projectDao.updateTimestamp(projectId, System.currentTimeMillis())
    }

    override suspend fun addAssets(projectId: String, assetUris: List<Uri>) {
        val existingCount = assetDao.countByProjectId(projectId)
        val assetEntities = assetUris.mapIndexed { index, uri ->
            ProjectMapper.createAssetEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                uri = uri,
                orderIndex = existingCount + index
            )
        }
        assetDao.insertAll(assetEntities)

        // Update thumbnail if this is first asset
        if (existingCount == 0 && assetUris.isNotEmpty()) {
            projectDao.getById(projectId)?.let { project ->
                projectDao.update(project.copy(
                    thumbnailUri = assetUris.first().toString(),
                    updatedAt = System.currentTimeMillis()
                ))
            }
        } else {
            projectDao.updateTimestamp(projectId, System.currentTimeMillis())
        }
    }

    override suspend fun addProjectAssets(projectId: String, assets: List<Asset>) {
        val existingCount = assetDao.countByProjectId(projectId)
        val assetEntities = assets.mapIndexed { index, asset ->
            ProjectMapper.toEntity(asset.copy(orderIndex = existingCount + index), projectId)
        }
        assetDao.insertAll(assetEntities)

        // Update thumbnail if this is first asset
        if (existingCount == 0 && assets.isNotEmpty()) {
            projectDao.getById(projectId)?.let { project ->
                projectDao.update(project.copy(
                    thumbnailUri = assets.first().uri.toString(),
                    updatedAt = System.currentTimeMillis()
                ))
            }
        } else {
            projectDao.updateTimestamp(projectId, System.currentTimeMillis())
        }
    }

    override suspend fun removeAsset(projectId: String, assetId: String) {
        assetDao.deleteById(assetId)

        // Reindex remaining assets
        val remainingAssets = assetDao.getByProjectId(projectId)
        val reindexedAssets = remainingAssets.mapIndexed { index, asset ->
            asset.copy(orderIndex = index)
        }
        assetDao.updateAll(reindexedAssets)

        // Update thumbnail if needed
        projectDao.getById(projectId)?.let { project ->
            val newThumbnail = reindexedAssets.firstOrNull()?.uri
            projectDao.update(project.copy(
                thumbnailUri = newThumbnail,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    override suspend fun deleteProject(projectId: String) {
        // Assets deleted via CASCADE
        projectDao.deleteById(projectId)
    }

    override suspend fun updateProjectName(projectId: String, name: String) {
        projectDao.getById(projectId)?.let { project ->
            projectDao.update(project.copy(
                name = name,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    override suspend fun updateWatermarkFreeStatus(projectId: String, isWatermarkFree: Boolean) {
        projectDao.getById(projectId)?.let { project ->
            projectDao.update(project.copy(
                isWatermarkFree = isWatermarkFree,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    override suspend fun updateProjectAssetsAndSettings(
        projectId: String,
        assets: List<Asset>,
        settings: ProjectSettings?
    ) {
        database.withTransaction {
            val existing = assetDao.getByProjectId(projectId)
            val existingIds = existing.map { it.id }.toSet()
            val newIds = assets.map { it.id }.toSet()

            // 1. Delete removed assets
            existing.forEach { asset ->
                if (asset.id !in newIds) {
                    assetDao.deleteById(asset.id)
                }
            }

            // 2. Insert/replace remaining and new assets with correct orderIndex
            val assetEntities = assets.mapIndexed { index, asset ->
                ProjectMapper.toEntity(asset.copy(orderIndex = index), projectId)
            }
            assetDao.insertAll(assetEntities)

            // 3. Update project settings and/or thumbnail and timestamp
            projectDao.getById(projectId)?.let { project ->
                val newThumbnail = assetEntities.firstOrNull()?.uri
                val baseProject = if (settings != null) {
                    val audioNodesJson = if (settings.audioNodes.isNotEmpty()) {
                        json.encodeToString(ListSerializer(AudioNode.serializer()), settings.audioNodes)
                    } else {
                        null
                    }
                    val textOverlaysJson = if (settings.textOverlays.isNotEmpty()) {
                        json.encodeToString(ListSerializer(TextOverlay.serializer()), settings.textOverlays)
                    } else {
                        null
                    }
                    val stickersJson = if (settings.stickers.isNotEmpty()) {
                        json.encodeToString(ListSerializer(StickerPlacement.serializer()), settings.stickers)
                    } else {
                        null
                    }
                    project.copy(
                        totalDurationMs = settings.totalDurationMs,
                        effectSetId = settings.effectSetId,
                        templateId = settings.templateId?.takeIf { it.isNotBlank() },
                        overlayFrameId = settings.overlayFrameId,
                        aspectRatio = settings.aspectRatio.name,
                        audioNodesJson = audioNodesJson,
                        textOverlaysJson = textOverlaysJson,
                        stickersJson = stickersJson
                    )
                } else {
                    project
                }

                projectDao.update(baseProject.copy(
                    thumbnailUri = newThumbnail,
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
    }
}
