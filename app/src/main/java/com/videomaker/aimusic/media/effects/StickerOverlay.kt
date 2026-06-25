package com.videomaker.aimusic.media.effects

import android.graphics.Bitmap
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.Size
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import com.videomaker.aimusic.domain.model.StickerPlacement

/**
 * StickerOverlay - A Media3 [BitmapOverlay] that renders one sticker placement onto the
 * exported video, matching the live preview.
 *
 * - [getBitmap] returns the current animation frame for the given presentation time
 *   (sticker loops for the whole video; static stickers always return their one frame).
 * - [getOverlaySettings] maps the normalized placement (position / size / rotation /
 *   opacity) into Media3 overlay space using the actual output frame size from
 *   [configure], so the result is resolution-independent.
 *
 * @param placement Normalized placement (persisted with the project)
 * @param decoded Pre-decoded frames for this sticker
 * @param clipStartUs This clip's start time on the global timeline (keeps animation
 *   continuous across the per-clip Media3 items)
 */
class StickerOverlay(
    private val placement: StickerPlacement,
    private val decoded: DecodedSticker,
    private val clipStartUs: Long
) : BitmapOverlay() {

    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var cachedSettings: StaticOverlaySettings? = null

    override fun configure(videoSize: Size) {
        if (videoSize.width != videoWidth || videoSize.height != videoHeight) {
            videoWidth = videoSize.width
            videoHeight = videoSize.height
            cachedSettings = null
        }
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val globalMs = (clipStartUs + presentationTimeUs) / 1000L
        return decoded.frameAt(globalMs)
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        cachedSettings?.let { return it }

        val frame = decoded.frames.first().bitmap
        val bw = frame.width.coerceAtLeast(1)
        // Size is anchored to the frame's SHORT side (the 1080 video dimension in every aspect
        // ratio), so a sticker keeps the same absolute size across ratios — matching the editor
        // preview ([StickerImagesLayer]) and behaving like text's ratio-invariant font size.
        val sizeRefPx = minOf(videoWidth, videoHeight)

        // Width-driven uniform scale (box width = widthFractionOfVideo * shortSide, height
        // follows the sticker's aspect ratio). overlayWidthPx = bw * scale = box width.
        val scale = if (sizeRefPx > 0) {
            val boxWidthPx = placement.widthFractionOfVideo * sizeRefPx
            (boxWidthPx / bw).coerceAtLeast(0.001f)
        } else {
            1f
        }

        // Background anchor in NDC [-1, 1], y up. centerY is top-down, so invert it.
        val bgX = placement.centerXNorm * 2f - 1f
        val bgY = 1f - placement.centerYNorm * 2f

        val settings = StaticOverlaySettings.Builder()
            .setScale(scale, scale)
            // Compose rotates clockwise (y-down); Media3 rotates counter-clockwise (y-up).
            .setRotationDegrees(-placement.rotationDeg)
            .setAlphaScale(placement.opacity.coerceIn(0f, 1f))
            .setOverlayFrameAnchor(0f, 0f)        // center of the sticker
            .setBackgroundFrameAnchor(bgX, bgY)   // target point on the video
            .build()
        cachedSettings = settings
        return settings
    }
}
