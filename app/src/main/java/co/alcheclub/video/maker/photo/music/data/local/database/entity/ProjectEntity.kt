package co.alcheclub.video.maker.photo.music.data.local.database.entity

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
    val transitionDurationMs: Long = 3000L,
    val transitionSetId: String = "classic",
    val overlayFrameId: String? = null,
    val audioTrackId: String? = null,
    val customAudioUri: String? = null,
    val audioVolume: Float = 1.0f,
    val aspectRatio: String = "RATIO_16_9"
)
