package com.videomaker.aimusic.media.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.annotation.DrawableRes
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlin.math.min

/**
 * Overlays app logo as watermark at bottom-right corner.
 */
class WatermarkOverlayEffect(
    private val context: Context,
    @param:DrawableRes private val watermarkResId: Int
) : GlEffect {

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
            }
        }

        return Size(inputWidth, inputHeight)
    }

    private fun buildOverlayBitmap(inputWidth: Int, inputHeight: Int): Bitmap? {
        val logo = BitmapFactory.decodeResource(context.resources, watermarkResId) ?: return null
        val overlay = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlay)

        val minDim = min(inputWidth, inputHeight).toFloat()
        val margin = (minDim * 0.04f).toInt().coerceAtLeast(16)
        val logoSize = (minDim * 0.12f).toInt().coerceAtLeast(48)

        val right = inputWidth - margin
        val bottom = inputHeight - margin
        val left = right - logoSize
        val top = bottom - logoSize

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = 190
            isFilterBitmap = true
        }
        canvas.drawBitmap(logo, null, Rect(left, top, right, bottom), paint)
        logo.recycle()
        return overlay
    }

    private fun createTexture(bitmap: Bitmap): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap, 0)

        return textureId
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return

        program.use()
        program.setSamplerTexIdUniform("uVideoSampler", inputTexId, 0)

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
varying vec2 vTexCoords;

void main() {
    vec4 videoColor = texture2D(uVideoSampler, vTexCoords);
    if (uHasOverlay < 0.5) {
        gl_FragColor = videoColor;
        return;
    }

    vec4 overlayColor = texture2D(uOverlaySampler, vTexCoords);
    gl_FragColor = mix(videoColor, overlayColor, overlayColor.a);
}
"""
    }
}
