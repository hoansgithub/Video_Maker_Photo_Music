package com.videomaker.aimusic.media.effects

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.Size
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import com.videomaker.aimusic.domain.model.StickerPlacement

/**
 * CompositeStickerOverlay - A single [BitmapOverlay] that composites ALL stickers in a
 * [StickerRun][com.videomaker.aimusic.modules.editor.overlay.OverlayRun.StickerRun] into
 * one video-resolution bitmap per frame.
 *
 * **Why**: Media3's [OverlayEffect] allocates a separate GL texture per overlay. With many
 * stickers (e.g. 30), the GPU runs out of texture memory and export fails with error 5001
 * (`ExportException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED`). By drawing all stickers
 * onto one bitmap, only **1** GL texture is needed per StickerRun regardless of sticker count.
 *
 * **Coordinate mapping**: Sticker placement values are normalized (0..1); this overlay maps
 * them to pixel coordinates on the composite canvas:
 * - `centerXNorm * videoWidth` -> pixel X
 * - `centerYNorm * videoHeight` -> pixel Y
 * - `widthFractionOfVideo * min(videoWidth, videoHeight) / stickerBitmapWidth` -> Canvas scale
 * - `rotationDeg` applied directly (Canvas y-down matches Compose conventions)
 * - `opacity` applied via [Paint.alpha]
 *
 * @param stickerEntries Pre-decoded stickers paired with their placements
 * @param clipStartUs Clip start time on the global timeline (keeps animation continuous)
 */
class CompositeStickerOverlay(
    private val stickerEntries: List<Pair<StickerPlacement, DecodedSticker>>,
    private val clipStartUs: Long
) : BitmapOverlay() {

    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var compositeBitmap: Bitmap? = null
    private var compositeCanvas: Canvas? = null
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val drawMatrix = Matrix()
    private var cachedSettings: StaticOverlaySettings? = null

    override fun configure(videoSize: Size) {
        val w = videoSize.width.coerceAtLeast(1)
        val h = videoSize.height.coerceAtLeast(1)
        if (w != videoWidth || h != videoHeight) {
            videoWidth = w
            videoHeight = h
            // Do NOT recycle the old bitmap — Media3's GL thread may still be uploading it
            // from the previous getBitmap() call. Let GC collect it once unreferenced.
            val bmp = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
            compositeBitmap = bmp
            compositeCanvas = Canvas(bmp)
            cachedSettings = null
        }
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val bitmap = compositeBitmap ?: run {
            val w = videoWidth.coerceAtLeast(1)
            val h = videoHeight.coerceAtLeast(1)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                compositeBitmap = it
                compositeCanvas = Canvas(it)
            }
        }
        val canvas = compositeCanvas ?: Canvas(bitmap).also { compositeCanvas = it }

        // Clear to fully transparent
        bitmap.eraseColor(Color.TRANSPARENT)

        val globalMs = (clipStartUs + presentationTimeUs) / 1000L
        val sizeRefPx = minOf(videoWidth, videoHeight)

        for ((placement, decoded) in stickerEntries) {
            val frame = decoded.frameAt(globalMs)
            val bw = frame.width.coerceAtLeast(1)
            val bh = frame.height.coerceAtLeast(1)

            // Uniform scale: sticker box width = widthFractionOfVideo * shortSide
            val scale = if (sizeRefPx > 0) {
                val boxWidthPx = placement.widthFractionOfVideo * sizeRefPx
                (boxWidthPx / bw).coerceAtLeast(0.001f)
            } else {
                1f
            }

            // Pixel position of the sticker center
            val cx = placement.centerXNorm * videoWidth
            val cy = placement.centerYNorm * videoHeight

            // Build transform: translate center to origin, scale, rotate, translate to position
            drawMatrix.reset()
            drawMatrix.postTranslate(-bw / 2f, -bh / 2f)
            drawMatrix.postScale(scale, scale)
            drawMatrix.postRotate(placement.rotationDeg)
            drawMatrix.postTranslate(cx, cy)

            // Opacity
            drawPaint.alpha = (placement.opacity.coerceIn(0f, 1f) * 255f).toInt()

            canvas.drawBitmap(frame, drawMatrix, drawPaint)
        }

        return bitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        cachedSettings?.let { return it }

        // Full-frame coverage: the composite bitmap IS the video resolution, so scale 1:1,
        // centered anchors. Sticker positions are already baked into pixel coordinates.
        val settings = StaticOverlaySettings.Builder()
            .setScale(1f, 1f)
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(0f, 0f)
            .build()
        cachedSettings = settings
        return settings
    }
}
