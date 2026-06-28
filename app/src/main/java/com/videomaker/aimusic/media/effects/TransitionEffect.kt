package com.videomaker.aimusic.media.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import com.videomaker.aimusic.media.renderer.GLTextureUploader
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.BaseGlShaderProgram
import com.videomaker.aimusic.domain.model.Transition

/**
 * TransitionEffect - Applies GL Transitions shaders between two images
 *
 * SINGLE SOURCE OF TRUTH ARCHITECTURE:
 * Both FROM and TO textures are loaded from GPU-preprocessed cache files.
 * We IGNORE Media3's input texture to ensure consistent color handling.
 *
 * LAZY LOADING: Textures are loaded on-demand in configure() from file paths,
 * not upfront. Only 2 bitmaps are ever in memory at a time (loaded, uploaded
 * to GPU, then immediately recycled). This reduces peak memory from ~161 MB
 * (all transition bitmaps) to ~7.4 MB (2 bitmaps).
 *
 * Timing:
 * - clipStartTimeUs: Global composition time when clip starts
 * - clipDurationUs: Total clip duration
 * - transitionDurationUs: Duration of transition at end of clip
 * - presentationTimeUs in drawFrame is GLOBAL composition time
 */
class TransitionEffect(
    private val transition: Transition,
    private val fromImagePath: String?,
    private val toImagePath: String?,
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
            fromImagePath = fromImagePath,
            toImagePath = toImagePath,
            transitionDurationUs = transitionDurationUs,
            clipDurationUs = clipDurationUs,
            clipStartTimeUs = clipStartTimeUs
        )
    }
}

/**
 * TransitionShaderProgram - Single source of truth for texture loading
 *
 * CRITICAL: We load BOTH FROM and TO textures ourselves using identical methods.
 * This guarantees no color difference between them.
 * Media3's input texture is IGNORED.
 *
 * LAZY LOADING: Textures are loaded from cache files in configure() (called once
 * per clip by Media3). Bitmaps are recycled immediately after GPU upload.
 */
// Pre-allocated constant — avoids heap allocation per drawFrame call
private val FADE_COLOR_BLACK = floatArrayOf(0f, 0f, 0f)

private class TransitionShaderProgram(
    useHdr: Boolean,
    private val transition: Transition,
    private val fromImagePath: String?,
    private val toImagePath: String?,
    private val transitionDurationUs: Long,
    private val clipDurationUs: Long,
    private val clipStartTimeUs: Long
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram: GlProgram? = null
    private var fromTextureId: Int = -1
    private var toTextureId: Int = -1
    private var inputWidth: Int = 0
    private var inputHeight: Int = 0
    private var isConfigured: Boolean = false

    private val transitionStartTimeUs: Long = clipStartTimeUs + clipDurationUs - transitionDurationUs
    private val transitionEndTimeUs: Long = clipStartTimeUs + clipDurationUs

    companion object {
        private const val TAG = "TransitionShaderProgram"
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

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        this.inputWidth = inputWidth
        this.inputHeight = inputHeight

        // Initialize transition shader with comprehensive error handling
        if (glProgram == null) {
            // Validate shader code before compilation
            val validationError = ShaderErrorHandler.validateShaderCode(transition.shaderCode)
            if (validationError != null) {
                android.util.Log.e(TAG, "Invalid shader '${transition.id}': $validationError")
                // Use passthrough shader as fallback
                val fallbackCode = ShaderErrorHandler.createPassthroughShader()
                val fragmentShader = buildFragmentShader(fallbackCode)
                initializeProgram(fragmentShader, "${transition.id} (fallback)")
            } else {
                // Attempt to compile user's shader
                val fragmentShader = buildFragmentShader(transition.shaderCode)
                val success = initializeProgram(fragmentShader, transition.id)

                // If compilation failed, fall back to passthrough shader
                if (!success) {
                    android.util.Log.w(TAG, "Falling back to passthrough shader for '${transition.id}'")
                    val fallbackCode = ShaderErrorHandler.createPassthroughShader()
                    val fallbackShader = buildFragmentShader(fallbackCode)
                    initializeProgram(fallbackShader, "${transition.id} (fallback)")
                }
            }
        }

        // Lazy load FROM texture from cache file
        if (fromTextureId == -1 && fromImagePath != null) {
            fromTextureId = loadTextureFromFile(fromImagePath)
        }

        // Lazy load TO texture from cache file (SAME METHOD = SAME COLORS)
        if (toTextureId == -1 && toImagePath != null) {
            toTextureId = loadTextureFromFile(toImagePath)
        }

        isConfigured = true
        return Size(inputWidth, inputHeight)
    }

    /**
     * Initialize GL program with comprehensive error handling
     * Returns true if successful, false if failed
     */
    private fun initializeProgram(fragmentShader: String, shaderName: String): Boolean {
        val result = ShaderErrorHandler.createProgram(VERTEX_SHADER, fragmentShader)

        return when (result) {
            is ShaderErrorHandler.ShaderResult.Success -> {
                val program = result.program
                try {
                    // Set up attributes
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
                    android.util.Log.d(TAG, "Successfully initialized shader '$shaderName'")
                    true
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to set buffer attributes for '$shaderName': ${e.message}")
                    program.delete()
                    false
                }
            }
            is ShaderErrorHandler.ShaderResult.Failure -> {
                ShaderErrorHandler.logShaderError(result.error, shaderName)
                false
            }
        }
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return

        if (!isConfigured || inputWidth <= 0 || inputHeight <= 0) {
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

        // Apply cosine ease-in-out curve for smooth beat-sync transitions
        // Formula matches Python preview generation: 0.5 - 0.5 * cos(π * progress)
        // With transition starting at beat: peak visual change occurs at beat + duration/2 (progress=0.5)
        val progress = 0.5f - 0.5f * kotlin.math.cos(linearProgress * Math.PI.toFloat())

        program.use()

        // SINGLE SOURCE OF TRUTH: Use OUR textures, NOT Media3's inputTexId
        // Both textures are created using identical methods → identical colors
        val fromTexId = if (fromTextureId != -1) fromTextureId else inputTexId
        val toTexId = if (toTextureId != -1) toTextureId else inputTexId

        // Set samplers with error handling
        ShaderErrorHandler.safeSetSamplerTexIdUniform(program, "uFromSampler", fromTexId, 0)
        ShaderErrorHandler.safeSetSamplerTexIdUniform(program, "uToSampler", toTexId, 1)

        // Set uniforms with error handling - shader may not use all of these
        ShaderErrorHandler.safeSetFloatUniform(program, "progress", progress)
        ShaderErrorHandler.safeSetFloatUniform(program, "ratio", inputWidth.toFloat() / inputHeight.toFloat())
        ShaderErrorHandler.safeSetFloatUniform(program, "smoothness", 0.05f)
        ShaderErrorHandler.safeSetFloatsUniform(program, "fadeColor", FADE_COLOR_BLACK)

        // Verify GL state before drawing
        if (!ShaderErrorHandler.isGlStateValid()) {
            android.util.Log.w(TAG, "Invalid GL state before drawing, skipping frame")
            return
        }

        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        glProgram?.delete()
        glProgram = null

        if (fromTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(fromTextureId), 0)
            fromTextureId = -1
        }

        if (toTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(toTextureId), 0)
            toTextureId = -1
        }
    }

    /**
     * Load a texture from a cache file on disk.
     * Bitmap is recycled immediately after GPU upload — only the GL texture persists.
     */
    private fun loadTextureFromFile(path: String): Int {
        val file = java.io.File(path)
        if (!file.exists() || file.length() == 0L) {
            android.util.Log.w(TAG, "Texture file missing or empty: $path")
            return -1
        }
        val bitmap = BitmapFactory.decodeFile(path) ?: run {
            android.util.Log.w(TAG, "Failed to decode texture file: $path")
            return -1
        }
        try {
            return createTextureFromBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Create texture from bitmap - SINGLE METHOD FOR BOTH FROM AND TO
     * This guarantees identical color handling for both textures.
     */
    private fun createTextureFromBitmap(bitmap: Bitmap): Int {
        if (bitmap.isRecycled) return -1

        return try {
            val textureIds = IntArray(1)
            GLES20.glGenTextures(1, textureIds, 0)
            val textureId = textureIds[0]

            if (textureId == 0) {
                return -1
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            if (!GLTextureUploader.safeTexImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)) {
                GLES20.glDeleteTextures(1, textureIds, 0)
                return -1
            }

            textureId
        } catch (_: Exception) {
            -1
        }
    }

    /**
     * Build fragment shader for transition
     *
     * SINGLE SOURCE OF TRUTH: Both FROM and TO textures are loaded identically.
     * Both are sampled the same way - no special handling needed.
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

// Flip Y when sampling FROM/TO textures to compensate for GLUtils.texImage2D
// placing bitmap row 0 (top of image) at the bottom of the GL texture.
// The pre-processed PNGs are saved in standard top-to-bottom orientation.
vec4 getFromColor(vec2 uv) {
    return texture2D(uFromSampler, vec2(uv.x, 1.0 - uv.y));
}

vec4 getToColor(vec2 uv) {
    return texture2D(uToSampler, vec2(uv.x, 1.0 - uv.y));
}

$transitionCode

void main() {
    vec4 color = transition(vTexCoords);
    gl_FragColor = vec4(color.rgb, 1.0);
}
"""
    }

}
