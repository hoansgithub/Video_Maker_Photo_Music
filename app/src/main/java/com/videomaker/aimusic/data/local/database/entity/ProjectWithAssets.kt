package com.videomaker.aimusic.data.local.database.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * ProjectWithAssets - Room relation for loading project with its assets
 *
 * Used for efficient one-query loading of project and all associated assets.
 */
data class ProjectWithAssets(
    @Embedded
    val project: ProjectEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val assets: List<AssetEntity>
)
