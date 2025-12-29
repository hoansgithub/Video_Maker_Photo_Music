package co.alcheclub.video.maker.photo.music.data.repository

import android.net.Uri
import co.alcheclub.video.maker.photo.music.data.local.database.dao.AssetDao
import co.alcheclub.video.maker.photo.music.data.local.database.dao.ProjectDao
import co.alcheclub.video.maker.photo.music.data.local.database.entity.ProjectEntity
import co.alcheclub.video.maker.photo.music.data.mapper.ProjectMapper
import co.alcheclub.video.maker.photo.music.domain.model.Asset
import co.alcheclub.video.maker.photo.music.domain.model.Project
import co.alcheclub.video.maker.photo.music.domain.model.ProjectSettings
import co.alcheclub.video.maker.photo.music.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    override suspend fun createProject(assets: List<Uri>): Project {
        val projectId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Create project entity
        val projectEntity = ProjectEntity(
            id = projectId,
            name = "Project ${now.toString().takeLast(4)}",
            createdAt = now,
            updatedAt = now,
            thumbnailUri = assets.firstOrNull()?.toString()
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
            .let { flow ->
                var result: List<Project> = emptyList()
                flow.collect { result = it }
                result
            }
    }

    override fun observeAllProjects(): Flow<List<Project>> {
        return projectDao.observeAllWithAssets().map { list ->
            list.map { ProjectMapper.toDomain(it) }
        }
    }

    override suspend fun updateSettings(projectId: String, settings: ProjectSettings) {
        projectDao.updateSettings(
            id = projectId,
            transitionDurationMs = settings.transitionDurationMs,
            transitionSetId = settings.transitionSetId,
            overlayFrameId = settings.overlayFrameId,
            audioTrackId = settings.audioTrackId,
            customAudioUri = settings.customAudioUri?.toString(),
            audioVolume = settings.audioVolume,
            aspectRatio = settings.aspectRatio.name,
            updatedAt = System.currentTimeMillis()
        )
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
}
