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
 * - The "to" texture (next image) is PRE-LOADED as a RAW Bitmap by CompositionFactory
 * - GPU blur is applied to the TO texture to match BlurBackgroundEffect exactly
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
 * - Applying GPU blur to TO texture (matches BlurBackgroundEffect exactly)
 * - Calculating progress based on presentation time
 * - Executing the transition shader
 */
private class TransitionShaderProgram(
    useHdr: Boolean,
    private val transition: Transition,
    private val toImageBitmap: Bitmap?,
    private val transitionDurationUs: Long,
    private val clipDurationUs: Long,
    private val clipStartTimeUs: Long,
    private val targetAspectRatio: Float
) : BaseGlShaderProgram(useHdr, 1) {

    // Main transition shader
    private var glProgram: GlProgram? = null

    // Blur background shader (same as BlurBackgroundEffect)
    private var blurProgram: GlProgram? = null

    // Raw TO texture (before blur)
    private var rawToTextureId: Int = -1

    // Processed TO texture (after blur - this is what we use in transitions)
    private var processedToTextureId: Int = -1

    // Framebuffer for blur rendering
    private var blurFramebuffer: Int = -1

    private var inputWidth: Int = 0
    private var inputHeight: Int = 0
    private var isConfigured: Boolean = false
    private var isBlurApplied: Boolean = false
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

        // Initialize transition shader program
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

        // Initialize blur shader program (same as BlurBackgroundEffect)
        if (blurProgram == null) {
            try {
                blurProgram = GlProgram(VERTEX_SHADER, BLUR_BACKGROUND_FRAGMENT_SHADER)
                blurProgram!!.setBufferAttribute(
                    "aPosition",
                    GlUtil.getNormalizedCoordinateBounds(),
                    GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
                )
                blurProgram!!.setBufferAttribute(
                    "aTexCoords",
                    GlUtil.getTextureCoordinateBounds(),
                    GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
                )
                android.util.Log.d("TransitionEffect", "Blur shader compiled successfully")
            } catch (e: Exception) {
                android.util.Log.e("TransitionEffect", "Failed to compile blur shader: ${e.message}", e)
                throw e
            }
        }

        // Create raw TO texture from bitmap
        if (rawToTextureId == -1 && toImageBitmap != null && !toImageBitmap.isRecycled) {
            rawToTextureId = createTextureFromBitmap(toImageBitmap)
            android.util.Log.d("TransitionEffect", "Created raw texture: $rawToTextureId from bitmap ${toImageBitmap.width}x${toImageBitmap.height}")
        }

        // Create framebuffer and output texture for blur
        if (blurFramebuffer == -1 && rawToTextureId != -1) {
            setupBlurFramebuffer(inputWidth, inputHeight)
        }

        isConfigured = true
        return Size(inputWidth, inputHeight)
    }

    /**
     * Setup framebuffer for rendering blur to texture
     */
    private fun setupBlurFramebuffer(width: Int, height: Int) {
        // Create output texture
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        processedToTextureId = texIds[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, processedToTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )

        // Create framebuffer
        val fbIds = IntArray(1)
        GLES20.glGenFramebuffers(1, fbIds, 0)
        blurFramebuffer = fbIds[0]

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFramebuffer)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, processedToTextureId, 0
        )

        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            android.util.Log.e("TransitionEffect", "Framebuffer not complete: $status")
        }

        // Unbind
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        android.util.Log.d("TransitionEffect", "Blur framebuffer setup: fb=$blurFramebuffer, tex=$processedToTextureId, size=${width}x${height}")
    }

    /**
     * Apply blur background effect to TO texture
     * This is done once on the first frame to match BlurBackgroundEffect exactly
     *
     * IMPORTANT: We use the same aspect ratio calculation as BlurBackgroundEffect
     * to ensure perfect color match at transition boundaries
     */
    private fun applyBlurToTexture() {
        if (isBlurApplied || blurProgram == null || rawToTextureId == -1 || blurFramebuffer == -1) {
            return
        }

        // Calculate input aspect from bitmap (original image aspect ratio)
        val bitmapAspect = if (toImageBitmap != null && !toImageBitmap.isRecycled) {
            toImageBitmap.width.toFloat() / toImageBitmap.height.toFloat()
        } else {
            1f
        }

        android.util.Log.d("TransitionEffect", "Applying GPU blur to TO texture:")
        android.util.Log.d("TransitionEffect", "  bitmap size: ${toImageBitmap?.width}x${toImageBitmap?.height}")
        android.util.Log.d("TransitionEffect", "  bitmap aspect: $bitmapAspect")
        android.util.Log.d("TransitionEffect", "  target aspect: $targetAspectRatio")
        android.util.Log.d("TransitionEffect", "  output size: ${inputWidth}x${inputHeight}")

        // Save current framebuffer
        val prevFramebuffer = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prevFramebuffer, 0)

        // Save current viewport
        val prevViewport = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, prevViewport, 0)

        // Bind our framebuffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFramebuffer)
        GLES20.glViewport(0, 0, inputWidth, inputHeight)

        // Clear
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Render blur using SAME shader as BlurBackgroundEffect
        blurProgram!!.use()
        blurProgram!!.setSamplerTexIdUniform("uTexSampler", rawToTextureId, 0)

        // Pass aspect ratios - same as BlurBackgroundEffect
        blurProgram!!.setFloatUniform("uInputAspect", bitmapAspect)
        blurProgram!!.setFloatUniform("uTargetAspect", targetAspectRatio)

        blurProgram!!.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Restore previous framebuffer and viewport
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFramebuffer[0])
        GLES20.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3])

        isBlurApplied = true
        android.util.Log.d("TransitionEffect", "GPU blur applied successfully")
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return

        if (!isConfigured || inputWidth <= 0 || inputHeight <= 0) {
            android.util.Log.w("TransitionEffect", "drawFrame called before configure, skipping")
            return
        }

        // Apply blur to TO texture on first frame (deferred from configure)
        if (!isBlurApplied) {
            applyBlurToTexture()
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

        // Apply sine ease-out curve
        val progress = kotlin.math.sin(linearProgress * Math.PI.toFloat() / 2f)

        // Detailed logging for boundary analysis
        frameCount++
        val isTransitioning = progress > 0f && progress < 1f
        val isNearEnd = progress > 0.9f

        // Log frequently near transition end to diagnose the jump
        if (isNearEnd || frameCount % 30 == 1) {
            val toTexId = when {
                processedToTextureId != -1 && isBlurApplied -> processedToTextureId
                rawToTextureId != -1 -> rawToTextureId
                else -> inputTexId
            }
            android.util.Log.d("TransitionEffect", "FRAME #$frameCount: time=${presentationTimeUs/1000}ms, linearProgress=${"%.4f".format(linearProgress)}, progress=${"%.4f".format(progress)}, toTex=$toTexId, inputTex=$inputTexId")

            if (progress >= 0.98f) {
                android.util.Log.w("TransitionEffect", ">>> NEAR BOUNDARY: Most shaders return getToColor(uv) at this point <<<")
            }
        }

        program.use()

        // Bind "from" texture (current input - already processed by BlurBackgroundEffect)
        program.setSamplerTexIdUniform("uFromSampler", inputTexId, 0)

        // Bind "to" texture (GPU-blurred TO texture, or raw if blur failed)
        val toTexId = when {
            processedToTextureId != -1 && isBlurApplied -> processedToTextureId
            rawToTextureId != -1 -> rawToTextureId
            else -> inputTexId
        }
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

        blurProgram?.delete()
        blurProgram = null

        if (rawToTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(rawToTextureId), 0)
            rawToTextureId = -1
        }

        if (processedToTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(processedToTextureId), 0)
            processedToTextureId = -1
        }

        if (blurFramebuffer != -1) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(blurFramebuffer), 0)
            blurFramebuffer = -1
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

            textureId
        } catch (e: Exception) {
            android.util.Log.e("TransitionEffect", "Failed to create texture from bitmap", e)
            -1
        }
    }

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

vec4 getFromColor(vec2 uv) {
    return texture2D(uFromSampler, uv);
}

vec4 getToColor(vec2 uv) {
    return texture2D(uToSampler, uv);
}

$transitionCode

void main() {
    vec4 result = transition(vTexCoords);

    // Smooth boundary blending to prevent color jump at clip transitions
    // At progress > 0.96, apply subtle brightness/saturation normalization
    // This masks minor differences between pre-processed TO texture and next clip's BlurBackgroundEffect
    if (progress > 0.96) {
        float normalizeAmount = smoothstep(0.96, 1.0, progress) * 0.08;

        // Calculate luminance
        float luma = dot(result.rgb, vec3(0.299, 0.587, 0.114));

        // Slightly reduce saturation and normalize brightness towards middle gray
        result.rgb = mix(result.rgb, vec3(luma), normalizeAmount * 0.3);

        // Subtle brightness normalization (prevents darkening jump)
        result.rgb += normalizeAmount * (0.5 - luma) * 0.15;
    }

    gl_FragColor = result;
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

        /**
         * Blur background shader - EXACT copy from BlurBackgroundEffect
         * This ensures perfect color match between FROM and TO textures
         */
        private const val BLUR_BACKGROUND_FRAGMENT_SHADER = """
precision highp float;
uniform sampler2D uTexSampler;
uniform float uInputAspect;
uniform float uTargetAspect;
varying vec2 vTexCoords;

vec4 gaussianBlur(sampler2D tex, vec2 uv, vec2 direction) {
    float weight0 = 0.2272542543;
    float weight1 = 0.3165327489;
    float weight2 = 0.0703065234;

    float offset1 = 1.3846153846;
    float offset2 = 3.2307692308;

    float blurAmount = 0.025;
    vec2 step = direction * blurAmount;

    vec4 color = texture2D(tex, uv) * weight0;

    color += texture2D(tex, clamp(uv + step * offset1, 0.0, 1.0)) * weight1;
    color += texture2D(tex, clamp(uv - step * offset1, 0.0, 1.0)) * weight1;
    color += texture2D(tex, clamp(uv + step * offset2, 0.0, 1.0)) * weight2;
    color += texture2D(tex, clamp(uv - step * offset2, 0.0, 1.0)) * weight2;

    return color;
}

void main() {
    vec2 uv = vTexCoords;

    // Background: scale to fill (zoom in, crop edges)
    vec2 bgUV;
    if (uInputAspect > uTargetAspect) {
        float scale = uTargetAspect / uInputAspect;
        bgUV.x = (uv.x - 0.5) * scale + 0.5;
        bgUV.y = uv.y;
    } else {
        float scale = uInputAspect / uTargetAspect;
        bgUV.x = uv.x;
        bgUV.y = (uv.y - 0.5) * scale + 0.5;
    }

    // 4-direction Gaussian blur
    vec4 blurH = gaussianBlur(uTexSampler, bgUV, vec2(1.0, 0.0));
    vec4 blurV = gaussianBlur(uTexSampler, bgUV, vec2(0.0, 1.0));
    vec4 blurD1 = gaussianBlur(uTexSampler, bgUV, vec2(0.707, 0.707));
    vec4 blurD2 = gaussianBlur(uTexSampler, bgUV, vec2(0.707, -0.707));

    vec4 bgColor = (blurH + blurV + blurD1 + blurD2) * 0.25;

    // Foreground: scale to fit (centered, show entire image)
    vec2 fgUV;
    if (uInputAspect > uTargetAspect) {
        float scale = uTargetAspect / uInputAspect;
        fgUV.x = uv.x;
        fgUV.y = (uv.y - 0.5) / scale + 0.5;
    } else {
        float scale = uInputAspect / uTargetAspect;
        fgUV.x = (uv.x - 0.5) / scale + 0.5;
        fgUV.y = uv.y;
    }

    // Check if we're in the foreground region
    if (fgUV.x >= 0.0 && fgUV.x <= 1.0 && fgUV.y >= 0.0 && fgUV.y <= 1.0) {
        gl_FragColor = texture2D(uTexSampler, fgUV);
    } else {
        gl_FragColor = bgColor;
    }
}
"""
    }
}
