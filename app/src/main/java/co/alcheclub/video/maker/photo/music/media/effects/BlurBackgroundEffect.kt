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
        }

        return Size(outputWidth, outputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return

        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)

        // Pass input aspect ratio to shader
        val inputAspect = inputWidth.toFloat() / inputHeight.toFloat()
        program.setFloatUniform("uInputAspect", inputAspect)
        program.setFloatUniform("uTargetAspect", targetAspectRatio)

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
// Weights and offsets from RasterGrid's efficient gaussian blur
vec4 gaussianBlur(sampler2D tex, vec2 uv, vec2 direction) {
    // Linear sampling optimized weights (combines adjacent samples)
    float weight0 = 0.2270270270;
    float weight1 = 0.3162162162;
    float weight2 = 0.0702702703;

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

    // Darken and desaturate background slightly
    float luminance = dot(bgColor.rgb, vec3(0.299, 0.587, 0.114));
    bgColor.rgb = mix(bgColor.rgb, vec3(luminance), 0.3); // 30% desaturation
    bgColor.rgb *= 0.55; // Darken

    // Subtle vignette on background
    float vignette = 1.0 - length(uv - 0.5) * 0.5;
    bgColor.rgb *= vignette;

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
