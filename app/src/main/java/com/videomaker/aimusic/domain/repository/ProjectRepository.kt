package com.videomaker.aimusic.domain.repository

import android.net.Uri
import com.videomaker.aimusic.domain.model.Asset
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.ProjectSettings
import kotlinx.coroutines.flow.Flow

/**
 * ProjectRepository - Domain layer interface for project operations
 */
interface ProjectRepository {

    /**
     * Create a new project with the given assets
     * @param assets URIs of selected images
     * @return Created project
     */
    suspend fun createProject(assets: List<Uri>): Project

    /**
     * Get a project by ID
     * @param id Project ID
     * @return Project or null if not found
     */
    suspend fun getProject(id: String): Project?

    /**
     * Observe a project by ID for real-time updates
     * @param id Project ID
     * @return Flow of project updates
     */
    fun observeProject(id: String): Flow<Project?>

    /**
     * Get all projects
     * @return List of all projects
     */
    suspend fun getAllProjects(): List<Project>

    /**
     * Observe all projects for real-time updates
     * @return Flow of all projects
     */
    fun observeAllProjects(): Flow<List<Project>>

    /**
     * Update project settings
     * @param projectId Project ID
     * @param settings New settings
     */
    suspend fun updateSettings(projectId: String, settings: ProjectSettings)

    /**
     * Reorder assets in a project
     * @param projectId Project ID
     * @param assets Reordered assets list
     */
    suspend fun reorderAssets(projectId: String, assets: List<Asset>)

    /**
     * Add assets to an existing project
     * @param projectId Project ID
     * @param assetUris URIs to add
     */
    suspend fun addAssets(projectId: String, assetUris: List<Uri>)

    /**
     * Remove an asset from a project
     * @param projectId Project ID
     * @param assetId Asset ID to remove
     */
    suspend fun removeAsset(projectId: String, assetId: String)

    /**
     * Delete a project and all its assets
     * @param projectId Project ID
     */
    suspend fun deleteProject(projectId: String)

    /**
     * Update project name
     * @param projectId Project ID
     * @param name New name
     */
    suspend fun updateProjectName(projectId: String, name: String)
}
