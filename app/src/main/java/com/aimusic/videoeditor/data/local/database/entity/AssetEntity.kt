package com.aimusic.videoeditor.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AssetEntity - Room entity for storing project assets (images)
 *
 * Linked to ProjectEntity via foreign key.
 * orderIndex determines the order of assets in the timeline.
 */
@Entity(
    tableName = "assets",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class AssetEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val uri: String,
    val orderIndex: Int,
    val type: String = "IMAGE"
)
