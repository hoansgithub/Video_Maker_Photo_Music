package com.videomaker.aimusic.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey val songId: Long,
    val name: String,
    val artist: String,
    val coverUrl: String,
    val mp3Url: String,
    val previewUrl: String,
    val durationMs: Int,
    val hookStartTimeMs: Long = 0L,
    val hookStartTimesJson: String = "[]",
    val likedAt: Long = System.currentTimeMillis()
)
