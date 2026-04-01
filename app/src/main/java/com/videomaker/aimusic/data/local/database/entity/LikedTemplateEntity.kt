package com.videomaker.aimusic.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "liked_templates")
data class LikedTemplateEntity(
    @PrimaryKey val templateId: String,
    val name: String,
    val thumbnailPath: String,
    val songId: Long,
    val effectSetId: String,
    val aspectRatio: String,
    val isPremium: Boolean,
    val useCount: Long,
    val likedAt: Long = System.currentTimeMillis()
)
