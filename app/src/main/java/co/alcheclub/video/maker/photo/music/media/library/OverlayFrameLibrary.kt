package co.alcheclub.video.maker.photo.music.media.library

import co.alcheclub.video.maker.photo.music.R
import co.alcheclub.video.maker.photo.music.domain.model.OverlayFrame

/**
 * OverlayFrameLibrary - Provides all available overlay frames
 *
 * Decorative frames rendered on top of the video content.
 */
object OverlayFrameLibrary {

    private val frames = listOf(
        OverlayFrame(
            id = "frame1",
            name = "Frame 1",
            drawableRes = R.drawable.frame1,
            isPremium = false
        ),
        OverlayFrame(
            id = "frame2",
            name = "Frame 2",
            drawableRes = R.drawable.frame2,
            isPremium = false
        )
    )

    fun getAll(): List<OverlayFrame> = frames

    fun getById(id: String): OverlayFrame? = frames.find { it.id == id }

    fun getFree(): List<OverlayFrame> = frames.filter { !it.isPremium }

    fun getPremium(): List<OverlayFrame> = frames.filter { it.isPremium }
}
