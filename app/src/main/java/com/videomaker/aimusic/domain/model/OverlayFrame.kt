package com.videomaker.aimusic.domain.model

/**
 * OverlayFrame - Decorative frame overlay for videos
 *
 * Frames are PNG/WebP images with transparency that are rendered
 * on top of the video content as a decorative border.
 */
data class OverlayFrame(
    val id: String,
    val name: String,
    val description: String = "",
    val thumbnailUrl: String = "",
    val frameUrl: String = "",
    val isPremium: Boolean = false,
    val isActive: Boolean = true
)
