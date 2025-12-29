package co.alcheclub.video.maker.photo.music.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import co.alcheclub.video.maker.photo.music.data.local.database.entity.AssetEntity
import kotlinx.coroutines.flow.Flow

/**
 * AssetDao - Data Access Object for asset operations
 */
@Dao
interface AssetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: AssetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assets: List<AssetEntity>)

    @Update
    suspend fun update(asset: AssetEntity)

    @Update
    suspend fun updateAll(assets: List<AssetEntity>)

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getById(id: String): AssetEntity?

    @Query("SELECT * FROM assets WHERE projectId = :projectId ORDER BY orderIndex ASC")
    suspend fun getByProjectId(projectId: String): List<AssetEntity>

    @Query("SELECT * FROM assets WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun observeByProjectId(projectId: String): Flow<List<AssetEntity>>

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM assets WHERE projectId = :projectId")
    suspend fun deleteByProjectId(projectId: String)

    @Query("SELECT COUNT(*) FROM assets WHERE projectId = :projectId")
    suspend fun countByProjectId(projectId: String): Int
}
