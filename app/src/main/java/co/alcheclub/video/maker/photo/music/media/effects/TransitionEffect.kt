package co.alcheclub.video.maker.photo.music.media.effects

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.BaseGlShaderProgram
import co.alcheclub.video.maker.photo.music.domain.model.Transition

/**
 * TransitionEffect - Applies GL Transitions shaders between two images
 *
 * SINGLE SOURCE OF TRUTH ARCHITECTURE:
 * Both FROM and TO textures are pre-processed with identical blur background effect.
 * This shader just blends between them directly - no UV transformation needed.
 *
 * Pipeline:
 * - FROM texture: Media3 loads pre-processed cache PNG
 * - TO texture: Loaded from same pre-processed cache PNG (flipped for OpenGL)
 * - Shader: Direct sampling, simple blend
 *
 * Timing:
 * - clipStartTimeUs: Global composition time when clip starts
 * - clipDurationUs: Total clip duration
 * - transitionDurationUs: Duration of transition at end of clip
 * - presentationTimeUs in drawFrame is GLOBAL composition time
 */
class TransitionEffect(
    private val transition: Transition,
    private val toImageBitmap: Bitmap?,
    private val transitionDurationUs: Long,
    private val clipDurationUs: Long,
    private val clipStartTimeUs: Long = 0L,
    private val targetAspectRatio: Float = 16f / 9f
) : GlEffect {

    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean
    ): GlShaderProgram {
        return TransitionShaderProgram(
            useHdr = useHdr,
            transition = transition,
            toImageBitmap = toImageBitmap,
            transitionDurationUs = transitionDurationUs,
            clipDurationUs = clipDurationUs,
            clipStartTimeUs = clipStartTimeUs
        )
    }
}

/**
 * TransitionShaderProgram - Simple direct sampling shader
 *
 * SIMPLIFIED: Both FROM and TO textures are pre-processed identically.
 * Just sample and blend - no GPU blur processing needed.
 */
private class TransitionShaderProgram(
    useHdr: Boolean,
    private val transition: Transition,
    private val toImageBitmap: Bitmap?,
    private val transitionDurationUs: Long,
    private val clipDurationUs: Long,
    private val clipStartTimeUs: Long
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram: GlProgram? = null
    private var toTextureId: Int = -1
    private var inputWidth: Int = 0
    private var inputHeight: Int = 0
    private var isConfigured: Boolean = false
    private var frameCount: Int = 0

    private val transitionStartTimeUs: Long = clipStartTimeUs + clipDurationUs - transitionDurationUs
    private val transitionEndTimeUs: Long = clipStartTimeUs + clipDurationUs

    init {
        android.util.Log.d("TransitionEffect", "TransitionShaderProgram created:")
        android.util.Log.d("TransitionEffect", "  transition=${transition.name}")
        android.util.Log.d("TransitionEffect", "  clipStartTimeUs=${clipStartTimeUs}us (${clipStartTimeUs/1000}ms)")
        android.util.Log.d("TransitionEffect", "  clipDurationUs=${clipDurationUs}us (${clipDurationUs/1000}ms)")
        android.util.Log.d("TransitionEffect", "  transitionDurationUs=${transitionDurationUs}us (${transitionDurationUs/1000}ms)")
        android.util.Log.d("TransitionEffect", "  transitionStartTimeUs=${transitionStartTimeUs}us (${transitionStartTimeUs/1000}ms)")
        android.util.Log.d("TransitionEffect", "  transitionEndTimeUs=${transitionEndTimeUs}us (${transitionEndTimeUs/1000}ms)")
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        this.inputWidth = inputWidth
        this.inputHeight = inputHeight

        // Initialize transition shader
        if (glProgram == null) {
            try {
                val fragmentShader = buildFragmentShader(transition.shaderCode)
                glProgram = GlProgram(VERTEX_SHADER, fragmentShader)
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
                android.util.Log.d("TransitionEffect", "Transition shader compiled successfully")
            } catch (e: Exception) {
                android.util.Log.e("TransitionEffect", "Failed to compile transition shader: ${e.message}", e)
                throw e
            }
        }

        // Create TO texture from pre-processed bitmap
        if (toTextureId == -1 && toImageBitmap != null && !toImageBitmap.isRecycled) {
            toTextureId = createTextureFromBitmap(toImageBitmap)
            android.util.Log.d("TransitionEffect", "Created TO texture: $toTextureId from bitmap ${toImageBitmap.width}x${toImageBitmap.height}")
        }

        isConfigured = true
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return

        if (!isConfigured || inputWidth <= 0 || inputHeight <= 0) {
            android.util.Log.w("TransitionEffect", "drawFrame called before configure, skipping")
            return
        }

        // Calculate progress
        val linearProgress = when {
            presentationTimeUs < transitionStartTimeUs -> 0f
            presentationTimeUs >= transitionEndTimeUs -> 1f
            else -> {
                val elapsed = presentationTimeUs - transitionStartTimeUs
                (elapsed.toFloat() / transitionDurationUs.toFloat()).coerceIn(0f, 1f)
            }
        }

        // Apply sine ease-out curve for smooth transition
        val progress = kotlin.math.sin(linearProgress * Math.PI.toFloat() / 2f)

        // Logging
        frameCount++
        if (frameCount % 30 == 1 || progress > 0.9f) {
            android.util.Log.d("TransitionEffect", "FRAME #$frameCount: time=${presentationTimeUs/1000}ms, progress=${"%.4f".format(progress)}")
        }

        program.use()

        // Bind FROM texture (input from Media3 - pre-processed cache PNG)
        program.setSamplerTexIdUniform("uFromSampler", inputTexId, 0)

        // Bind TO texture (pre-processed cache PNG, flipped for OpenGL)
        val toTexId = if (toTextureId != -1) toTextureId else inputTexId
        program.setSamplerTexIdUniform("uToSampler", toTexId, 1)

        // Set uniforms
        try {
            program.setFloatUniform("progress", progress)
        } catch (e: Exception) {
            android.util.Log.w("TransitionEffect", "Failed to set progress uniform: ${e.message}")
        }

        try {
            program.setFloatUniform("ratio", inputWidth.toFloat() / inputHeight.toFloat())
        } catch (_: Exception) {}

        try {
            program.setFloatUniform("smoothness", 0.05f)
        } catch (_: Exception) {}

        try {
            program.setFloatsUniform("fadeColor", floatArrayOf(0f, 0f, 0f))
        } catch (_: Exception) {}

        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        glProgram?.delete()
        glProgram = null

        if (toTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(toTextureId), 0)
            toTextureId = -1
        }
    }

    private fun createTextureFromBitmap(bitmap: Bitmap): Int {
        return try {
            val textureIds = IntArray(1)
            GLES20.glGenTextures(1, textureIds, 0)
            val textureId = textureIds[0]

            if (textureId == 0) {
                android.util.Log.e("TransitionEffect", "Failed to generate texture ID")
                return -1
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                android.util.Log.e("TransitionEffect", "GL error creating texture: $error")
                GLES20.glDeleteTextures(1, textureIds, 0)
                return -1
            }

            android.util.Log.d("TransitionEffect", "Created texture $textureId from bitmap ${bitmap.width}x${bitmap.height}")
            textureId
        } catch (e: Exception) {
            android.util.Log.e("TransitionEffect", "Failed to create texture from bitmap", e)
            -1
        }
    }

    /**
     * Build fragment shader for transition
     *
     * SIMPLIFIED: Both FROM and TO are pre-processed identically.
     * Just sample directly - no UV transformation needed.
     */
    private fun buildFragmentShader(transitionCode: String): String {
        return """
precision highp float;
uniform sampler2D uFromSampler;
uniform sampler2D uToSampler;
uniform float progress;
uniform float ratio;
uniform float smoothness;
uniform vec3 fadeColor;
varying vec2 vTexCoords;

// SINGLE SOURCE OF TRUTH: Both textures are pre-processed identically
vec4 getFromColor(vec2 uv) {
    return texture2D(uFromSampler, uv);
}

vec4 getToColor(vec2 uv) {
    // Flip Y coordinate for OpenGL texture orientation
    // This preserves exact color values (no bitmap manipulation)
    vec2 flippedUV = vec2(uv.x, 1.0 - uv.y);
    return texture2D(uToSampler, flippedUV);
}

$transitionCode

void main() {
    vec4 color = transition(vTexCoords);
    // Force alpha=1.0 for consistent output
    gl_FragColor = vec4(color.rgb, 1.0);
}
"""
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
    }
}
