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
 * This effect creates smooth transitions between the current frame and a "to" texture.
 * The transition progress is based on presentation time within the clip.
 *
 * Architecture:
 * - Each clip with a transition gets this effect applied
 * - The "to" texture (next image) is PRE-LOADED as a Bitmap by CompositionFactory
 * - Progress animates from 0.0 to 1.0 over the transition duration
 *
 * IMPORTANT: Bitmap must be pre-loaded before creating this effect.
 * Loading from URI inside GL thread causes crashes and blocking I/O.
 *
 * Timing:
 * - clipStartTimeUs: The global composition time when this clip starts
 * - clipDurationUs: How long this clip lasts
 * - transitionDurationUs: Duration of transition at end of clip
 * - presentationTimeUs in drawFrame is GLOBAL composition time
 *
 * Usage in CompositionFactory:
 * - Pre-load next image bitmap before creating effect
 * - For last N milliseconds of each clip, apply TransitionEffect
 * - The effect blends current frame with next image using GLSL shader
 */
class TransitionEffect(
    private val transition: Transition,
    private val toImageBitmap: Bitmap?,
    private val transitionDurationUs: Long,
    private val clipDurationUs: Long,
    private val clipStartTimeUs: Long = 0L  // Global time when this clip starts
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
 * TransitionShaderProgram - Executes the GLSL transition shader
 *
 * Handles:
 * - Creating GL texture from pre-loaded bitmap
 * - Calculating progress based on presentation time
 * - Executing the transition shader
 *
 * Timing (all in microseconds):
 * - clipStartTimeUs: Global composition time when this clip starts
 * - clipDurationUs: Duration of this clip
 * - transitionDurationUs: Duration of transition at end of clip
 * - transitionStartTimeUs: Global time when transition starts (clipStart + clipDuration - transitionDuration)
 * - transitionEndTimeUs: Global time when transition ends (clipStart + clipDuration)
 */
private class TransitionShaderProgram(
    useHdr: Boolean,
    private val transition: Transition,
    private val toImageBitmap: Bitmap?,
    private val transitionDurationUs: Long,
    private val clipDurationUs: Long,
    private val clipStartTimeUs: Long
) : BaseGlShaderProgram(useHdr, /* expectedInputCount= */ 1) {

    private var glProgram: GlProgram? = null
    private var toTextureId: Int = -1
    private var inputWidth: Int = 0
    private var inputHeight: Int = 0
    private var isConfigured: Boolean = false
    private var frameCount: Int = 0

    // Calculate GLOBAL times for when transition starts and ends
    // Transition starts at: (clip start) + (clip duration) - (transition duration)
    // Transition ends at: (clip start) + (clip duration)
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

        // Initialize shader program (only once)
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
                android.util.Log.d("TransitionEffect", "Shader program compiled successfully")
            } catch (e: Exception) {
                android.util.Log.e("TransitionEffect", "Failed to compile shader: ${e.message}", e)
                throw e
            }
        }

        // Create GL texture from pre-loaded bitmap (only once)
        if (toTextureId == -1 && toImageBitmap != null && !toImageBitmap.isRecycled) {
            toTextureId = createTextureFromBitmap(toImageBitmap)
            android.util.Log.d("TransitionEffect", "Created texture: $toTextureId from bitmap ${toImageBitmap.width}x${toImageBitmap.height}")
        }

        isConfigured = true
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return

        // Safety check: ensure configure was called
        if (!isConfigured || inputWidth <= 0 || inputHeight <= 0) {
            android.util.Log.w("TransitionEffect", "drawFrame called before configure, skipping")
            return
        }

        // Calculate progress (0.0 to 1.0) using GLOBAL presentation time
        // Before transitionStartTimeUs: progress = 0 (show from image only)
        // During transition: progress interpolates 0 to 1 with easing
        // After transitionEndTimeUs: progress = 1 (show to image only)
        val linearProgress = when {
            presentationTimeUs < transitionStartTimeUs -> 0f
            presentationTimeUs >= transitionEndTimeUs -> 1f
            else -> {
                val elapsed = presentationTimeUs - transitionStartTimeUs
                (elapsed.toFloat() / transitionDurationUs.toFloat()).coerceIn(0f, 1f)
            }
        }

        // Apply sine ease-out curve - gentle deceleration at the end
        // Formula: sin(t * π/2)
        // At t=0.5: progress=0.71 (vs 0.875 for cubic - more balanced)
        // At t=0.9: progress=0.99 (smooth finish)
        val progress = kotlin.math.sin(linearProgress * Math.PI.toFloat() / 2f)

        // Log during transition or every 30th frame
        frameCount++
        val isTransitioning = progress > 0f && progress < 1f
        if (isTransitioning || frameCount % 30 == 1) {
            android.util.Log.d("TransitionEffect", "drawFrame: time=${presentationTimeUs/1000}ms, progress=${"%.2f".format(progress)}, transitioning=$isTransitioning")
        }

        program.use()

        // Bind "from" texture (current input)
        program.setSamplerTexIdUniform("uFromSampler", inputTexId, 0)

        // Bind "to" texture (next image) - fall back to input if not available
        val toTexId = if (toTextureId != -1) toTextureId else inputTexId
        program.setSamplerTexIdUniform("uToSampler", toTexId, 1)

        // Set uniforms - wrap in try-catch as some may be optimized out by GLSL compiler
        // if not used by the specific transition shader
        try {
            program.setFloatUniform("progress", progress)
        } catch (e: Exception) {
            android.util.Log.w("TransitionEffect", "Failed to set progress uniform: ${e.message}")
        }

        try {
            program.setFloatUniform("ratio", inputWidth.toFloat() / inputHeight.toFloat())
        } catch (_: Exception) {
            // Uniform not used by this shader (optimized out)
        }

        try {
            program.setFloatUniform("smoothness", 0.05f)
        } catch (_: Exception) {
            // Uniform not used by this shader
        }

        try {
            program.setFloatsUniform("fadeColor", floatArrayOf(0f, 0f, 0f))
        } catch (_: Exception) {
            // Uniform not used by this shader
        }

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

    /**
     * Create OpenGL texture from pre-loaded bitmap
     * Called on GL thread during configure()
     */
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

            // Check for GL errors
            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                android.util.Log.e("TransitionEffect", "GL error creating texture: $error")
                GLES20.glDeleteTextures(1, textureIds, 0)
                return -1
            }

            textureId
        } catch (e: Exception) {
            android.util.Log.e("TransitionEffect", "Failed to create texture from bitmap", e)
            -1
        }
    }

    /**
     * Build fragment shader with GL Transitions boilerplate
     *
     * Note: The "to" texture bitmap is pre-flipped vertically in CompositionFactory
     * so both "from" and "to" textures have the same orientation. This ensures
     * geometric transitions (circle, diamond, etc.) align correctly at boundaries.
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

// GL Transitions API
vec4 getFromColor(vec2 uv) {
    return texture2D(uFromSampler, uv);
}

vec4 getToColor(vec2 uv) {
    // No Y-flip needed - bitmap is pre-flipped in CompositionFactory
    return texture2D(uToSampler, uv);
}

// Transition implementation
$transitionCode

void main() {
    gl_FragColor = transition(vTexCoords);
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
