package com.videomaker.aimusic.domain.model

import kotlinx.serialization.Serializable

/**
 * Video export quality options
 */
@Serializable
enum class VideoQuality(val displayName: String, val height: Int) {
    HD_720("720p", 720),
    FHD_1080("1080p", 1080);

    companion object {
        val DEFAULT = HD_720
    }
}
