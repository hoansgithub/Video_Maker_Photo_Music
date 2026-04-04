package com.videomaker.aimusic.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.videomaker.aimusic.data.local.database.entity.LikedSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedSongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE songId = :songId")
    suspend fun deleteById(songId: Long)

    @Query("SELECT * FROM liked_songs ORDER BY likedAt DESC LIMIT :limit")
    fun observeAll(limit: Int = 100): Flow<List<LikedSongEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE songId = :songId LIMIT 1)")
    fun observeIsLiked(songId: Long): Flow<Boolean>

    @Query("SELECT songId FROM liked_songs ORDER BY likedAt DESC LIMIT :limit")
    fun observeLikedIds(limit: Int = 500): Flow<List<Long>>
}
