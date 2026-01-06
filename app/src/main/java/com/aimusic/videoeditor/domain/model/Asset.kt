package com.aimusic.videoeditor.domain.model

import android.net.Uri

/**
 * Asset - Domain model for a media asset in a project
 */
data class Asset(
    val id: String,
    val uri: Uri,
    val orderIndex: Int,
    val type: AssetType = AssetType.IMAGE
)
