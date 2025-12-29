package co.alcheclub.video.maker.photo.music.media.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.BaseGlShaderProgram
import java.io.IOException

/**
 * FrameOverlayEffect - Renders a decorative frame on top of video
 *
 * The frame is a WebP image with transparency that scales-to-fill
 * the video area and renders on top of the content.
 */
class FrameOverlayEffect(
    private val context: Context,
    private val frameAssetPath: String
) : GlEffect {

    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean
    ): GlShaderProgram {
        return FrameOverlayShaderProgram(this.context, frameAssetPath, useHdr)
    }
}

/**
 * Shader program that overlays a frame texture on top of video
 */
private class FrameOverlayShaderProgram(
    private val context: Context,
    private val frameAssetPath: String,
    useHdr: Boolean
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram: GlProgram? = null
    private var frameTextureId: Int = -1
    private var frameBitmap: Bitmap? = null

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        // Load frame bitmap if not already loaded
        if (frameBitmap == null) {
            frameBitmap = loadFrameBitmap()
        }

        // Initialize shader program
        if (glProgram == null) {
            glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            glProgram!!.setBufferAttribute(
                "aPosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            glProgram!!.setBufferAttribute(
                "aTexCoords",
                GlUtil.getTextureCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )

            // Create frame texture
            frameBitmap?.let { bitmap ->
                frameTextureId = createTexture(bitmap)
            }
        }

        return Size(inputWidth, inputHeight)
    }

    private fun loadFrameBitmap(): Bitmap? {
        return try {
            context.assets.open(frameAssetPath).use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (e: IOException) {
            android.util.Log.e("FrameOverlay", "Failed to load frame: $frameAssetPath", e)
            null
        }
    }

    private fun createTexture(bitmap: Bitmap): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]

        android.util.Log.d("FrameOverlay", "Creating texture id=$textureId for bitmap ${bitmap.width}x${bitmap.height} hasAlpha=${bitmap.hasAlpha()}")

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // Use RGBA format explicitly for transparency support
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap, 0)

        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            android.util.Log.e("FrameOverlay", "GL error after texImage2D: $error")
        }

        return textureId
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return

        program.use()

        // Bind video texture to unit 0
        program.setSamplerTexIdUniform("uVideoSampler", inputTexId, 0)

        // Bind frame texture to unit 1 if available
        if (frameTextureId != -1) {
            program.setSamplerTexIdUniform("uFrameSampler", frameTextureId, 1)
            program.setFloatUniform("uHasFrame", 1f)
        } else {
            program.setFloatUniform("uHasFrame", 0f)
        }

        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        glProgram?.delete()
        glProgram = null

        if (frameTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(frameTextureId), 0)
            frameTextureId = -1
        }

        frameBitmap?.recycle()
        frameBitmap = null
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
uniform sampler2D uFrameSampler;
uniform float uHasFrame;
varying vec2 vTexCoords;

void main() {
    // Sample video
    vec4 videoColor = texture2D(uVideoSampler, vTexCoords);

    if (uHasFrame < 0.5) {
        gl_FragColor = videoColor;
        return;
    }

    // Sample frame - stretch to fill video area exactly
    vec4 frameColor = texture2D(uFrameSampler, vTexCoords);

    // Alpha blend frame on top of video
    gl_FragColor = mix(videoColor, frameColor, frameColor.a);
}
"""
    }
}
