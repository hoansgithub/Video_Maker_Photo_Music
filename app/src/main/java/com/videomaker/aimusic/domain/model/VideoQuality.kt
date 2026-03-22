package com.videomaker.aimusic.domain.model

/**
 * Video export quality options
 */
enum class VideoQuality(val displayName: String, val height: Int) {
    HD_720("720p", 720),
    FHD_1080("1080p", 1080);

    companion object {
        val DEFAULT = HD_720
    }
}
