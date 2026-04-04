package com.videomaker.aimusic.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.videomaker.aimusic.data.local.database.entity.LikedTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedTemplateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LikedTemplateEntity)

    @Query("DELETE FROM liked_templates WHERE templateId = :templateId")
    suspend fun deleteById(templateId: String)

    @Query("SELECT * FROM liked_templates ORDER BY likedAt DESC LIMIT :limit")
    fun observeAll(limit: Int = 100): Flow<List<LikedTemplateEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_templates WHERE templateId = :templateId LIMIT 1)")
    fun observeIsLiked(templateId: String): Flow<Boolean>

    @Query("SELECT templateId FROM liked_templates ORDER BY likedAt DESC LIMIT :limit")
    fun observeLikedIds(limit: Int = 500): Flow<List<String>>
}
