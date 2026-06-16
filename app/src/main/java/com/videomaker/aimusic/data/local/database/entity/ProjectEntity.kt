package com.videomaker.aimusic.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ProjectEntity - Room entity for storing project data (BEAT-SYNC ONLY)
 *
 * Stores project metadata and settings in a single table.
 * Assets are stored in a separate table with foreign key relationship.
 * Audio is stored as serialized JSON list of AudioNode in audioNodesJson.
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
    val templateId: String? = null,
    val overlayFrameId: String? = null,
    val aspectRatio: String = "RATIO_9_16",

    // Multi-track audio (serialized List<AudioNode> as JSON)
    val audioNodesJson: String? = null,

    // Watermark removal (rewarded ad unlock)
    val isWatermarkFree: Boolean = false // True if user watched ad to remove watermark for this project
)
