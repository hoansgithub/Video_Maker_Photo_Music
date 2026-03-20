package com.videomaker.aimusic.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.videomaker.aimusic.data.local.database.entity.ProjectEntity
import com.videomaker.aimusic.data.local.database.entity.ProjectWithAssets
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

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC LIMIT :limit")
    fun observeAll(limit: Int = 100): Flow<List<ProjectEntity>>

    @Transaction
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC LIMIT :limit")
    fun observeAllWithAssets(limit: Int = 100): Flow<List<ProjectWithAssets>>

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE projects SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTimestamp(id: String, updatedAt: Long)

    @Query("""
        UPDATE projects SET
            imageDurationMs = :imageDurationMs,
            transitionPercentage = :transitionPercentage,
            effectSetId = :effectSetId,
            overlayFrameId = :overlayFrameId,
            musicSongId = :musicSongId,
            musicSongName = :musicSongName,
            musicSongUrl = :musicSongUrl,
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
        effectSetId: String?,
        overlayFrameId: String?,
        musicSongId: Long?,
        musicSongName: String?,
        musicSongUrl: String?,
        customAudioUri: String?,
        audioVolume: Float,
        aspectRatio: String,
        updatedAt: Long
    )
}
