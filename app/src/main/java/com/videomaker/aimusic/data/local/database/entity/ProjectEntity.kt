package com.videomaker.aimusic.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ProjectEntity - Room entity for storing project data (BEAT-SYNC ONLY)
 *
 * Stores project metadata and settings in a single table.
 * Assets are stored in a separate table with foreign key relationship.
 * All legacy fixed-duration fields removed.
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
    val totalDurationMs: Long = 0L,
    val effectSetId: String? = "dreamy_vibes",
    val overlayFrameId: String? = null,
    val musicSongId: Long? = null,
    val musicSongName: String? = null, // Cached song name for display
    val musicSongUrl: String? = null, // Cached song URL for playback
    val musicSongCoverUrl: String? = null, // Cached cover URL for display
    val customAudioUri: String? = null,
    val processedAudioUri: String? = null, // Preprocessed audio with fadeout
    val audioVolume: Float = 1.0f,
    val aspectRatio: String = "RATIO_9_16",

    // Watermark removal (rewarded ad unlock)
    val isWatermarkFree: Boolean = false // True if user watched ad to remove watermark for this project
)
