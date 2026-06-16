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
import com.videomaker.aimusic.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * ProjectRepositoryImpl - Implementation of ProjectRepository
 *
 * Handles all database operations for projects and assets.
 */
class ProjectRepositoryImpl(
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
            audioNodesJson = audioNodesJson
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

        // Update entity with new settings
        val updatedEntity = existingEntity.copy(
            totalDurationMs = settings.totalDurationMs,
            effectSetId = settings.effectSetId,
            templateId = settings.templateId?.takeIf { it.isNotBlank() },
            overlayFrameId = settings.overlayFrameId,
            aspectRatio = settings.aspectRatio.name,
            audioNodesJson = audioNodesJson,
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
}
