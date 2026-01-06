package com.aimusic.videoeditor.domain.model

/**
 * OverlayFrame - Represents a decorative frame overlay
 *
 * Frames are WebP images with transparency that render on top of the video.
 * They scale-to-fill the video area.
 */
data class OverlayFrame(
    val id: String,
    val name: String,
    val assetPath: String,
    val thumbnailPath: String = assetPath,
    val isPremium: Boolean = false
)
