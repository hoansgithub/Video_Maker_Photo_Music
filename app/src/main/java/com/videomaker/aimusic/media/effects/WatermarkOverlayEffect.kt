package com.videomaker.aimusic.media.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.opengl.GLES20
import com.videomaker.aimusic.media.renderer.GLTextureUploader
import androidx.annotation.DrawableRes
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Overlays app logo as watermark at bottom-right corner.
 *
 * Memory optimization: creates a logo-sized bitmap (~67 KB) instead of a
 * full-resolution bitmap (~8.2 MB at 1080p). The shader positions the
 * watermark using a normalized rect uniform.
 */
class WatermarkOverlayEffect(
    private val context: Context,
    @param:DrawableRes private val watermarkResId: Int
) : GlEffect {

    internal data class OverlayPlacement(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    companion object {
        /**
         * Calculate watermark draw rect in bitmap space.
         *
         * Bitmap pixels are authored in Canvas top-left coordinates, but this overlay texture
         * is sampled with GL coordinates where Y is inverted. Drawing near top maps visually
         * to bottom in the exported frame.
         */
        internal fun calculateOverlayPlacement(inputWidth: Int, inputHeight: Int): OverlayPlacement {
            val minDim = kotlin.math.min(inputWidth, inputHeight).toFloat()
            val margin = (minDim * 0.04f).toInt().coerceAtLeast(16)
            val logoSize = (minDim * 0.12f).toInt().coerceAtLeast(48)

            val right = inputWidth - margin
            val left = right - logoSize
            val top = margin
            val bottom = top + logoSize

            return OverlayPlacement(
                left = left,
                top = top,
                right = right,
                bottom = bottom
            )
        }

        internal fun shouldPreFlipLogoVertically(): Boolean = true
    }

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return WatermarkOverlayShaderProgram(this.context, watermarkResId, useHdr)
    }
}

private class WatermarkOverlayShaderProgram(
    private val context: Context,
    @param:DrawableRes private val watermarkResId: Int,
    useHdr: Boolean
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram: GlProgram? = null
    private var watermarkTextureId: Int = -1
    private var watermarkBitmap: Bitmap? = null

    // Watermark placement in normalized GL texture coordinates: [left, bottom, right, top]
    private var watermarkRect = floatArrayOf(0f, 0f, 0f, 0f)

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        if (glProgram == null) {
            val program = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            program.setBufferAttribute(
                "aPosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            program.setBufferAttribute(
                "aTexCoords",
                GlUtil.getTextureCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            glProgram = program
        }

        if (watermarkBitmap == null) {
            watermarkBitmap = buildOverlayBitmap(inputWidth, inputHeight)
        }
        if (watermarkTextureId == -1) {
            watermarkBitmap?.let { bitmap ->
                watermarkTextureId = createTexture(bitmap)
                bitmap.recycle()
                watermarkBitmap = null
            }
        }

        return Size(inputWidth, inputHeight)
    }

    /**
     * Build a logo-sized bitmap instead of full-resolution.
     * Stores normalized placement for the shader to position the watermark.
     */
    private fun buildOverlayBitmap(inputWidth: Int, inputHeight: Int): Bitmap? {
        val logo = BitmapFactory.decodeResource(context.resources, watermarkResId) ?: return null
        val placement = WatermarkOverlayEffect.calculateOverlayPlacement(inputWidth, inputHeight)
        val logoWidth = (placement.right - placement.left).coerceAtLeast(1)
        val logoHeight = (placement.bottom - placement.top).coerceAtLeast(1)

        // Store placement as normalized GL texture coordinates for the shader.
        // Canvas top-left origin -> GL bottom-left origin: flip Y.
        watermarkRect = floatArrayOf(
            placement.left.toFloat() / inputWidth,
            1f - placement.bottom.toFloat() / inputHeight,
            placement.right.toFloat() / inputWidth,
            1f - placement.top.toFloat() / inputHeight
        )

        // Create logo-sized bitmap (~67 KB) instead of full-resolution (~8 MB at 1080p)
        val overlay = Bitmap.createBitmap(logoWidth, logoHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlay)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = 190
            isFilterBitmap = true
        }
        val destinationRect = Rect(0, 0, logoWidth, logoHeight)

        if (WatermarkOverlayEffect.shouldPreFlipLogoVertically()) {
            val srcRect = RectF(0f, 0f, logo.width.toFloat(), logo.height.toFloat())
            val dstRect = RectF(destinationRect)
            val matrix = Matrix().apply {
                setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.FILL)
                postScale(1f, -1f, dstRect.centerX(), dstRect.centerY())
            }
            canvas.drawBitmap(logo, matrix, paint)
        } else {
            canvas.drawBitmap(logo, null, destinationRect, paint)
        }
        logo.recycle()
        return overlay
    }

    private fun createTexture(bitmap: Bitmap): Int {
        if (bitmap.isRecycled) return -1

        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]

        try {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            if (!GLTextureUploader.safeTexImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)) {
                GLES20.glDeleteTextures(1, textureIds, 0)
                return -1
            }
        } catch (e: Exception) {
            android.util.Log.e("WatermarkOverlay", "Failed to upload texture: ${e.message}")
            GLES20.glDeleteTextures(1, textureIds, 0)
            return -1
        }

        return textureId
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return

        program.use()
        program.setSamplerTexIdUniform("uVideoSampler", inputTexId, 0)
        program.setFloatsUniform("uWatermarkRect", watermarkRect)

        if (watermarkTextureId != -1) {
            program.setSamplerTexIdUniform("uOverlaySampler", watermarkTextureId, 1)
            program.setFloatUniform("uHasOverlay", 1f)
        } else {
            program.setFloatUniform("uHasOverlay", 0f)
        }

        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        glProgram?.delete()
        glProgram = null

        if (watermarkTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(watermarkTextureId), 0)
            watermarkTextureId = -1
        }

        watermarkBitmap?.recycle()
        watermarkBitmap = null
    }

    companion object {
        private const val VERTEX_SHADER = """
attribute vec4 aPosition;
attribute vec4 aTexCoords;
varying vec2 vTexCoords;
void main() {
    gl_Position = aPosition;
    vTexCoords = aTexCoords.xy;
}
"""

        private const val FRAGMENT_SHADER = """
precision highp float;
uniform sampler2D uVideoSampler;
uniform sampler2D uOverlaySampler;
uniform float uHasOverlay;
uniform vec4 uWatermarkRect;
varying vec2 vTexCoords;

void main() {
    vec4 videoColor = texture2D(uVideoSampler, vTexCoords);
    if (uHasOverlay < 0.5) {
        gl_FragColor = videoColor;
        return;
    }

    if (vTexCoords.x >= uWatermarkRect.x && vTexCoords.x <= uWatermarkRect.z &&
        vTexCoords.y >= uWatermarkRect.y && vTexCoords.y <= uWatermarkRect.w) {
        vec2 wmUV = (vTexCoords - uWatermarkRect.xy) / (uWatermarkRect.zw - uWatermarkRect.xy);
        vec4 overlayColor = texture2D(uOverlaySampler, wmUV);
        gl_FragColor = mix(videoColor, overlayColor, overlayColor.a);
    } else {
        gl_FragColor = videoColor;
    }
}
"""
    }
}
