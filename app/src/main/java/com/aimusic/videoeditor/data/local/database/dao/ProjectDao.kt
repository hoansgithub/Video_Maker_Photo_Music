package com.aimusic.videoeditor.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aimusic.videoeditor.data.local.database.entity.ProjectEntity
import com.aimusic.videoeditor.data.local.database.entity.ProjectWithAssets
import kotlinx.coroutines.flow.Flow

/**
 * ProjectDao - Data Access Object for project operations
 */
@Dao
interface ProjectDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: String): ProjectEntity?

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getWithAssets(id: String): ProjectWithAssets?

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeWithAssets(id: String): Flow<ProjectWithAssets?>

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Transaction
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAllWithAssets(): Flow<List<ProjectWithAssets>>

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE projects SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTimestamp(id: String, updatedAt: Long)

    @Query("""
        UPDATE projects SET
            imageDurationMs = :imageDurationMs,
            transitionPercentage = :transitionPercentage,
            transitionId = :transitionId,
            overlayFrameId = :overlayFrameId,
            audioTrackId = :audioTrackId,
            customAudioUri = :customAudioUri,
            audioVolume = :audioVolume,
            aspectRatio = :aspectRatio,
            updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateSettings(
        id: String,
        imageDurationMs: Long,
        transitionPercentage: Int,
        transitionId: String?,
        overlayFrameId: String?,
        audioTrackId: String?,
        customAudioUri: String?,
        audioVolume: Float,
        aspectRatio: String,
        updatedAt: Long
    )
}
