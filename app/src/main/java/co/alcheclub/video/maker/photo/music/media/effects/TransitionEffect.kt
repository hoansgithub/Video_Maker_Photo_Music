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
    private val clipStartTimeUs: Long = 0L,  // Global time when this clip starts
    private val targetAspectRatio: Float = 16f / 9f  // Target aspect ratio for blur background
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
            clipStartTimeUs = clipStartTimeUs,
            targetAspectRatio = targetAspectRatio
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
    private val clipStartTimeUs: Long,
    private val targetAspectRatio: Float
) : BaseGlShaderProgram(useHdr, /* expectedInputCount= */ 1) {

    private var glProgram: GlProgram? = null
    private var toTextureId: Int = -1
    private var inputWidth: Int = 0
    private var inputHeight: Int = 0
    private var isConfigured: Boolean = false
    private var frameCount: Int = 0
    private var toImageAspectRatio: Float = 1f

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
            toImageAspectRatio = toImageBitmap.width.toFloat() / toImageBitmap.height.toFloat()
            android.util.Log.d("TransitionEffect", "Created texture: $toTextureId from bitmap ${toImageBitmap.width}x${toImageBitmap.height}, aspect=$toImageAspectRatio")
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

        // Pass aspect ratios for blur background effect on TO image
        try {
            program.setFloatUniform("uToInputAspect", toImageAspectRatio)
        } catch (_: Exception) {}

        try {
            program.setFloatUniform("uTargetAspect", targetAspectRatio)
        } catch (_: Exception) {}

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
uniform float uToInputAspect;
uniform float uTargetAspect;
varying vec2 vTexCoords;

// Gaussian blur for TO image background (matches BlurBackgroundEffect exactly)
vec4 gaussianBlurTo(vec2 uv, vec2 direction) {
    float weight0 = 0.2272542543;
    float weight1 = 0.3165327489;
    float weight2 = 0.0703065234;
    float offset1 = 1.3846153846;
    float offset2 = 3.2307692308;
    float blurAmount = 0.025;
    vec2 step = direction * blurAmount;

    vec4 color = texture2D(uToSampler, uv) * weight0;
    color += texture2D(uToSampler, clamp(uv + step * offset1, 0.0, 1.0)) * weight1;
    color += texture2D(uToSampler, clamp(uv - step * offset1, 0.0, 1.0)) * weight1;
    color += texture2D(uToSampler, clamp(uv + step * offset2, 0.0, 1.0)) * weight2;
    color += texture2D(uToSampler, clamp(uv - step * offset2, 0.0, 1.0)) * weight2;
    return color;
}

// GL Transitions API
vec4 getFromColor(vec2 uv) {
    return texture2D(uFromSampler, uv);
}

// Apply blur background effect to TO image (same as BlurBackgroundEffect)
vec4 getToColor(vec2 uv) {
    // Background: scale to fill (zoom in, crop edges)
    vec2 bgUV;
    if (uToInputAspect > uTargetAspect) {
        float scale = uTargetAspect / uToInputAspect;
        bgUV.x = (uv.x - 0.5) * scale + 0.5;
        bgUV.y = uv.y;
    } else {
        float scale = uToInputAspect / uTargetAspect;
        bgUV.x = uv.x;
        bgUV.y = (uv.y - 0.5) * scale + 0.5;
    }

    // 4-direction Gaussian blur for background
    vec4 blurH = gaussianBlurTo(bgUV, vec2(1.0, 0.0));
    vec4 blurV = gaussianBlurTo(bgUV, vec2(0.0, 1.0));
    vec4 blurD1 = gaussianBlurTo(bgUV, vec2(0.707, 0.707));
    vec4 blurD2 = gaussianBlurTo(bgUV, vec2(0.707, -0.707));
    vec4 bgColor = (blurH + blurV + blurD1 + blurD2) * 0.25;

    // Foreground: scale to fit (centered, show entire image)
    vec2 fgUV;
    if (uToInputAspect > uTargetAspect) {
        float scale = uTargetAspect / uToInputAspect;
        fgUV.x = uv.x;
        fgUV.y = (uv.y - 0.5) / scale + 0.5;
    } else {
        float scale = uToInputAspect / uTargetAspect;
        fgUV.x = (uv.x - 0.5) / scale + 0.5;
        fgUV.y = uv.y;
    }

    // Return foreground if in bounds, otherwise blurred background
    if (fgUV.x >= 0.0 && fgUV.x <= 1.0 && fgUV.y >= 0.0 && fgUV.y <= 1.0) {
        return texture2D(uToSampler, fgUV);
    } else {
        return bgColor;
    }
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
