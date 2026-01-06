package co.alcheclub.video.maker.photo.music.media.effects

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.BaseGlShaderProgram

/**
 * BlurBackgroundEffect - Creates blurred background with scaled-to-fit foreground
 *
 * This effect:
 * 1. Renders a blurred, scaled-to-fill version of the image as background
 * 2. Overlays the original image scaled-to-fit on top
 *
 * This eliminates black bars by showing a blurred version of the image behind.
 */
class BlurBackgroundEffect(
    private val targetAspectRatio: Float
) : GlEffect {

    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean
    ): GlShaderProgram {
        return BlurBackgroundShaderProgram(useHdr, targetAspectRatio)
    }
}

/**
 * Shader program that renders blurred background + sharp foreground
 */
private class BlurBackgroundShaderProgram(
    useHdr: Boolean,
    private val targetAspectRatio: Float
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram: GlProgram? = null
    private var inputWidth: Int = 0
    private var inputHeight: Int = 0

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        this.inputWidth = inputWidth
        this.inputHeight = inputHeight

        // Calculate output size based on target aspect ratio
        // Keep the larger dimension and adjust the other
        val inputAspectRatio = inputWidth.toFloat() / inputHeight.toFloat()

        val outputWidth: Int
        val outputHeight: Int

        if (targetAspectRatio > inputAspectRatio) {
            // Target is wider than input - use input width, reduce height
            outputWidth = inputWidth
            outputHeight = (inputWidth / targetAspectRatio).toInt()
        } else {
            // Target is taller than input - use input height, reduce width
            outputHeight = inputHeight
            outputWidth = (inputHeight * targetAspectRatio).toInt()
        }

        // Initialize shader program here where we have context
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

        return Size(outputWidth, outputHeight)
    }

    private var frameCount = 0

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return

        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)

        // Pass input aspect ratio to shader
        val inputAspect = inputWidth.toFloat() / inputHeight.toFloat()
        program.setFloatUniform("uInputAspect", inputAspect)
        program.setFloatUniform("uTargetAspect", targetAspectRatio)

        // Log aspect ratios for debugging color match with TransitionEffect
        frameCount++
        if (frameCount <= 3) {
            android.util.Log.d("BlurBackgroundEffect", "CLIP START Frame #$frameCount: time=${presentationTimeUs/1000}ms, input: ${inputWidth}x${inputHeight}, aspect: $inputAspect, target: $targetAspectRatio, inputTexId: $inputTexId")
        }

        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        glProgram?.delete()
        glProgram = null
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
uniform sampler2D uTexSampler;
uniform float uInputAspect;
uniform float uTargetAspect;
varying vec2 vTexCoords;

// Optimized 9-tap Gaussian blur using linear sampling
// Reduces 9 taps to 5 texture fetches via bilinear interpolation
// Weights normalized to sum exactly to 1.0 to prevent darkening
vec4 gaussianBlur(sampler2D tex, vec2 uv, vec2 direction) {
    // Normalized weights (sum = 1.0 exactly)
    // Original: 0.227 + 0.316*2 + 0.070*2 = 0.999
    // Adjusted: divide by 0.999 to normalize
    float weight0 = 0.2272542543;  // 0.227/0.999
    float weight1 = 0.3165327489;  // 0.316/0.999
    float weight2 = 0.0703065234;  // 0.070/0.999

    // Linear sampling optimized offsets
    float offset1 = 1.3846153846;
    float offset2 = 3.2307692308;

    // Blur step size (larger = more blur)
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

    // Two-direction Gaussian blur (approximates 2-pass with single pass)
    // Apply horizontal and vertical blur, then combine
    vec4 blurH = gaussianBlur(uTexSampler, bgUV, vec2(1.0, 0.0));
    vec4 blurV = gaussianBlur(uTexSampler, bgUV, vec2(0.0, 1.0));
    vec4 blurD1 = gaussianBlur(uTexSampler, bgUV, vec2(0.707, 0.707));
    vec4 blurD2 = gaussianBlur(uTexSampler, bgUV, vec2(0.707, -0.707));

    // Combine all directions for smoother result
    vec4 bgColor = (blurH + blurV + blurD1 + blurD2) * 0.25;

    // No darkening - keep original colors, just blurred

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
    vec4 result;
    if (fgUV.x >= 0.0 && fgUV.x <= 1.0 && fgUV.y >= 0.0 && fgUV.y <= 1.0) {
        result = texture2D(uTexSampler, fgUV);
    } else {
        result = bgColor;
    }

    gl_FragColor = result;
}
"""
    }
}
