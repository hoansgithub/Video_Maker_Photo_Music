package com.aimusic.videoeditor.media.library

import com.aimusic.videoeditor.domain.model.OverlayFrame

/**
 * FrameLibrary - Provides all available bundled overlay frames
 *
 * WebP frames with transparency that render on top of video.
 * Frames are stored in assets/frames/ directory.
 */
object FrameLibrary {

    private val frames = listOf(
        OverlayFrame(
            id = "frame1",
            name = "Classic Border",
            assetPath = "frames/frame1.webp",
            isPremium = false
        ),
        OverlayFrame(
            id = "frame2",
            name = "Vintage",
            assetPath = "frames/frame2.webp",
            isPremium = false
        )
    )

    fun getAll(): List<OverlayFrame> = frames

    fun getById(id: String): OverlayFrame? = frames.find { it.id == id }

    fun getFree(): List<OverlayFrame> = frames.filter { !it.isPremium }

    fun getPremium(): List<OverlayFrame> = frames.filter { it.isPremium }
}
