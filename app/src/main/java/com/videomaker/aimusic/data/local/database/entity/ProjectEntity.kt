package com.videomaker.aimusic.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ProjectEntity - Room entity for storing project data
 *
 * Stores project metadata and settings in a single table.
 * Assets are stored in a separate table with foreign key relationship.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailUri: String?,

    // Settings (embedded for simplicity)
    val imageDurationMs: Long = 3000L,
    val transitionPercentage: Int = 30, // Percentage of image duration for transition (10-50%)
    val effectSetId: String? = "dreamy_vibes",
    val overlayFrameId: String? = null,
    val musicSongId: Long? = null,
    val musicSongName: String? = null, // Cached song name for display
    val musicSongUrl: String? = null, // Cached song URL for playback
    val musicSongCoverUrl: String? = null, // Cached cover URL for display
    val customAudioUri: String? = null,
    val processedAudioUri: String? = null, // Downloaded local music file for reliable playback
    val audioVolume: Float = 1.0f,
    val musicTrimStartMs: Long = 0L, // Music trim start position (0 = no trim at start)
    val musicTrimEndMs: Long? = null, // Music trim end position (null = use full song)
    val aspectRatio: String = "RATIO_9_16"
)
