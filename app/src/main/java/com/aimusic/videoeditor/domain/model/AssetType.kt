package com.aimusic.videoeditor.domain.model

/**
 * AssetType - Types of media assets
 *
 * MVP: IMAGE only
 * Future: VIDEO support
 */
enum class AssetType {
    IMAGE;

    companion object {
        fun fromString(value: String): AssetType {
            return entries.find { it.name == value } ?: IMAGE
        }
    }
}
