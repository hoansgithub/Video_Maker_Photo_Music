package com.aimusic.videoeditor.media.library

import com.aimusic.videoeditor.domain.model.OverlayFrame

/**
 * FrameLibrary - Provides all available bundled overlay frames
 *
 * WebP frames with transparency that render on top of video.
 * Frames are stored in assets/frames/ directory.
 *
 * NOTE: Local frames use frameUrl as the asset path (e.g., "frames/frame1.webp")
 * Remote frames would use full URLs in frameUrl.
 */
object FrameLibrary {

    private val frames = listOf(
        OverlayFrame(
            id = "frame1",
            name = "Classic Border",
            frameUrl = "frames/frame1.webp",
            isPremium = false
        ),
        OverlayFrame(
            id = "frame2",
            name = "Vintage",
            frameUrl = "frames/frame2.webp",
            isPremium = false
        )
    )

    fun getAll(): List<OverlayFrame> = frames.filter { it.isActive }

    fun getById(id: String): OverlayFrame? = frames.find { it.id == id && it.isActive }

    fun getFree(): List<OverlayFrame> = frames.filter { !it.isPremium && it.isActive }

    fun getPremium(): List<OverlayFrame> = frames.filter { it.isPremium && it.isActive }
}
