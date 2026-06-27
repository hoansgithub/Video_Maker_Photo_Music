package com.videomaker.aimusic.media.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES20
import com.videomaker.aimusic.media.renderer.GLTextureUploader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.videomaker.aimusic.domain.model.TextOverlay
import com.videomaker.aimusic.domain.model.TextFontPreset
import com.videomaker.aimusic.domain.model.mockFontPresets

/**
 * TextOverlayEffect - Blends custom text overlays on top of the video frames.
 */
class TextOverlayEffect(
    private val context: Context,
    private val textOverlays: List<TextOverlay>,
    private val fontPresets: List<TextFontPreset>
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return TextOverlayShaderProgram(this.context, textOverlays, fontPresets, useHdr)
    }
}

private class TextOverlayShaderProgram(
    private val context: Context,
    private val textOverlays: List<TextOverlay>,
    private val fontPresets: List<TextFontPreset>,
    useHdr: Boolean
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram: GlProgram? = null
    private var overlayTextureId: Int = -1
    private var overlayBitmap: Bitmap? = null

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

        if (overlayBitmap == null && textOverlays.isNotEmpty()) {
            overlayBitmap = buildOverlayBitmap(inputWidth, inputHeight)
        }

        if (overlayTextureId == -1 && overlayBitmap != null) {
            overlayBitmap?.let { bitmap ->
                overlayTextureId = createTexture(bitmap)
            }
        }

        return Size(inputWidth, inputHeight)
    }

    private fun resolveTypeface(fontId: String): Typeface? {
        // 1. Try to find local resource font in presets
        val preset = fontPresets.find { it.id == fontId }
        if (preset != null) {
            if (preset.fontResId != null) {
                try {
                    return ResourcesCompat.getFont(context, preset.fontResId)
                } catch (e: Exception) {
                    android.util.Log.e(
                        "TextOverlayEffect",
                        "Failed to load local font ${preset.name}",
                        e
                    )
                }
            }

            // 2. Try to load from custom font file
            val file = preset.getFontFile(context)
            if (file != null && file.exists()) {
                try {
                    return Typeface.createFromFile(file)
                } catch (e: Exception) {
                    android.util.Log.e(
                        "TextOverlayEffect",
                        "Failed to load font file ${file.absolutePath}",
                        e
                    )
                }
            }
        }

        // 3. Fallback to default mock presets if not found
        val mockPreset = mockFontPresets.find { it.id == fontId }
        if (mockPreset != null && mockPreset.fontResId != null) {
            try {
                return ResourcesCompat.getFont(context, mockPreset.fontResId)
            } catch (_: Exception) {
            }
        }

        return null
    }

    private fun buildOverlayBitmap(inputWidth: Int, inputHeight: Int): Bitmap? {
        val tempBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tempBitmap)

        textOverlays.forEach { overlay ->
            drawTextOverlay(canvas, overlay, inputWidth, inputHeight)
        }

        // Flip vertically for OpenGL texture coordinates (GL Y starts at bottom)
        val flippedBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        val flippedCanvas = Canvas(flippedBitmap)
        val matrix = Matrix().apply {
            postScale(1f, -1f, inputWidth / 2f, inputHeight / 2f)
        }
        flippedCanvas.drawBitmap(tempBitmap, matrix, null)
        tempBitmap.recycle()

        return flippedBitmap
    }

    private fun drawTextOverlay(canvas: Canvas, overlay: TextOverlay, width: Int, height: Int) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = overlay.color.toInt()
            style = Paint.Style.FILL
            // Reference width is 360dp in design, scale font size proportionally
            textSize = 18f * (width / 360f)

            val tf = resolveTypeface(overlay.fontId)
            if (tf != null) {
                typeface = tf
            } else {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        }

        // Wrap to 90% of screen width to avoid touching edges
        val wrapWidth = (width * 0.9f).toInt()
        val staticLayout =
            StaticLayout.Builder.obtain(overlay.text, 0, overlay.text.length, paint, wrapWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()

        val centerX = overlay.xPercentage * width
        val centerY = overlay.yPercentage * height

        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(overlay.rotation)
        canvas.scale(overlay.scale, overlay.scale)

        val layoutWidth = staticLayout.width
        val layoutHeight = staticLayout.height
        canvas.translate(-layoutWidth / 2f, -layoutHeight / 2f)
        staticLayout.draw(canvas)

        canvas.restore()
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
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
            )
            if (!GLTextureUploader.safeTexImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)) {
                GLES20.glDeleteTextures(1, textureIds, 0)
                return -1
            }
        } catch (e: Exception) {
            android.util.Log.e("TextOverlayEffect", "Failed to upload texture: ${e.message}")
            GLES20.glDeleteTextures(1, textureIds, 0)
            return -1
        }

        return textureId
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return

        program.use()
        program.setSamplerTexIdUniform("uVideoSampler", inputTexId, 0)

        if (overlayTextureId != -1) {
            program.setSamplerTexIdUniform("uOverlaySampler", overlayTextureId, 1)
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

        if (overlayTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(overlayTextureId), 0)
            overlayTextureId = -1
        }

        overlayBitmap?.recycle()
        overlayBitmap = null
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
