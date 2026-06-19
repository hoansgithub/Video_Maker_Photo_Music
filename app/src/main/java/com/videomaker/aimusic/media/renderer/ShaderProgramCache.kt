package com.videomaker.aimusic.media.renderer

import android.opengl.GLES20
import android.util.Log
import com.videomaker.aimusic.domain.model.Transition
import com.videomaker.aimusic.media.effects.ShaderErrorHandler

/**
 * Cached uniform and attribute locations for a compiled GL program.
 * Looked up once at compile time, reused every frame — avoids
 * 8-11 glGetUniformLocation/glGetAttribLocation calls per draw.
 */
data class ProgramLocations(
    val aPosition: Int = -1,
    val aTexCoord: Int = -1,
    val uTexFrom: Int = -1,
    val uTexTo: Int = -1,
    val progress: Int = -1,
    val ratio: Int = -1,
    val uInputAspect: Int = -1,
    val uInputAspectFrom: Int = -1,
    val uInputAspectTo: Int = -1,
    val uTargetAspect: Int = -1
)

/**
 * ShaderProgramCache - Compiles and caches GL shader programs.
 *
 * Compiles GLSL transition shaders into GL programs on first use.
 * Cached by shader ID so programs are reused across frames.
 * When the effect set changes, programs swap instantly (next frame).
 *
 * All shaders include blur-background rendering so images maintain
 * their original aspect ratio with a blurred fill for letterbox bars.
 * This matches the export path (GPUImagePreprocessor).
 *
 * Uses the existing ShaderErrorHandler for compilation + fallback.
 */
class ShaderProgramCache {

    companion object {
        private const val TAG = "ShaderProgramCache"

        // Reusable buffer for GL status queries — avoids allocation per compile
        private val GL_STATUS = IntArray(1)

        /**
         * Standard vertex shader for fullscreen quad rendering.
         * Passes through position and texture coordinates.
         */
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        /**
         * Shared GLSL functions for blur-background rendering.
         * Gaussian blur with 4-direction sampling for the background area,
         * and aspect-ratio-aware fit/fill UV mapping.
         * Matches GPUImagePreprocessor's fragment shader so preview
         * and export render identically.
         */
        /**
         * Preview blur: 2 directions (H + V) x 5 samples = 10 texture lookups per pixel.
         * Halved from 4 directions (20 lookups) — visually identical for a blurred background.
         * Export path uses GPUImagePreprocessor so export quality is unaffected.
         */
        private const val BLUR_BG_HELPERS = """
vec4 gaussianBlur(sampler2D tex, vec2 uv, vec2 dir) {
    float w0 = 0.2272542543;
    float w1 = 0.3165327489;
    float w2 = 0.0703065234;
    float o1 = 1.3846153846;
    float o2 = 3.2307692308;
    float amt = 0.025;
    vec2 s = dir * amt;
    vec4 c = texture2D(tex, uv) * w0;
    c += texture2D(tex, clamp(uv + s * o1, 0.0, 1.0)) * w1;
    c += texture2D(tex, clamp(uv - s * o1, 0.0, 1.0)) * w1;
    c += texture2D(tex, clamp(uv + s * o2, 0.0, 1.0)) * w2;
    c += texture2D(tex, clamp(uv - s * o2, 0.0, 1.0)) * w2;
    return c;
}
vec4 sampleWithBlurBg(sampler2D tex, vec2 uv, float inAspect, float tgtAspect) {
    vec2 bgUV;
    if (inAspect > tgtAspect) {
        float sc = tgtAspect / inAspect;
        bgUV = vec2((uv.x - 0.5) * sc + 0.5, uv.y);
    } else {
        float sc = inAspect / tgtAspect;
        bgUV = vec2(uv.x, (uv.y - 0.5) * sc + 0.5);
    }
    vec4 bH = gaussianBlur(tex, bgUV, vec2(1.0, 0.0));
    vec4 bV = gaussianBlur(tex, bgUV, vec2(0.0, 1.0));
    vec4 bg = (bH + bV) * 0.5;
    vec2 fgUV;
    if (inAspect > tgtAspect) {
        float sc = tgtAspect / inAspect;
        fgUV = vec2(uv.x, (uv.y - 0.5) / sc + 0.5);
    } else {
        float sc = inAspect / tgtAspect;
        fgUV = vec2((uv.x - 0.5) / sc + 0.5, uv.y);
    }
    if (fgUV.x >= 0.0 && fgUV.x <= 1.0 && fgUV.y >= 0.0 && fgUV.y <= 1.0) {
        return texture2D(tex, fgUV);
    } else {
        return bg;
    }
}
"""

        /**
         * Fragment shader for hold frames with blur background.
         * Renders the image at its original aspect ratio centered in the viewport,
         * with a blurred version filling the background bars.
         * Uses mediump for ~2x shader throughput on mobile GPUs (preview only;
         * export path uses GPUImagePreprocessor with its own shaders).
         */
        private val BLUR_BG_FRAGMENT = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D uTexFrom;
uniform float uInputAspect;
uniform float uTargetAspect;
$BLUR_BG_HELPERS
void main() {
    gl_FragColor = sampleWithBlurBg(uTexFrom, vTexCoord, uInputAspect, uTargetAspect);
}
"""

        /**
         * Fragment shader wrapper with blur-background in getFromColor/getToColor.
         * Each image maintains its original aspect ratio with blurred fill for bars.
         * Per-image aspect ratios via uInputAspectFrom / uInputAspectTo uniforms.
         * Uses mediump for ~2x shader throughput on mobile GPUs (preview only).
         */
        private fun wrapTransitionShader(transitionCode: String): String = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D uTexFrom;
uniform sampler2D uTexTo;
uniform float progress;
uniform float ratio;
uniform float uInputAspectFrom;
uniform float uInputAspectTo;
uniform float uTargetAspect;
$BLUR_BG_HELPERS
vec4 getFromColor(vec2 uv) {
    return sampleWithBlurBg(uTexFrom, vec2(uv.x, 1.0 - uv.y), uInputAspectFrom, uTargetAspect);
}
vec4 getToColor(vec2 uv) {
    return sampleWithBlurBg(uTexTo, vec2(uv.x, 1.0 - uv.y), uInputAspectTo, uTargetAspect);
}
$transitionCode
void main() {
    gl_FragColor = transition(vec2(vTexCoord.x, 1.0 - vTexCoord.y));
}
"""

        /** Simple crossfade fallback for failed shader compilations. */
        private const val CROSSFADE_TRANSITION = """
            vec4 transition(vec2 uv) {
                return mix(getFromColor(uv), getToColor(uv), progress);
            }
        """
    }

    // Cache: shader ID -> compiled GL program ID
    private val programCache = mutableMapOf<String, Int>()

    // Cache: GL program ID -> uniform/attribute locations
    private val locationCache = mutableMapOf<Int, ProgramLocations>()

    // Special programs
    private var passthroughProgram: Int = 0
    private var fallbackProgram: Int = 0

    /**
     * Initialize special programs (blur-bg hold frame + fallback crossfade).
     * Must be called on GL thread after surface is created.
     */
    fun init() {
        passthroughProgram = compileProgram(VERTEX_SHADER, BLUR_BG_FRAGMENT)
        fallbackProgram = compileProgram(VERTEX_SHADER, wrapTransitionShader(CROSSFADE_TRANSITION))

        if (passthroughProgram == 0) {
            Log.e(TAG, "Failed to compile blur-bg program")
        }
        if (fallbackProgram == 0) {
            Log.e(TAG, "Failed to compile fallback program")
        }
    }

    /**
     * Get the compiled GL program for a transition shader.
     * Compiles on first use and caches for subsequent frames.
     * Returns fallback crossfade program if compilation fails.
     */
    fun getProgram(transition: Transition): Int {
        programCache[transition.id]?.let { return it }

        // Validate shader before compilation
        val validationError = ShaderErrorHandler.validateShaderCode(transition.shaderCode)
        if (validationError != null) {
            Log.w(TAG, "Shader validation failed for ${transition.id}: $validationError")
            return fallbackProgram
        }

        // Compile the transition shader
        val fragmentShader = wrapTransitionShader(transition.shaderCode)
        val program = compileProgram(VERTEX_SHADER, fragmentShader)

        if (program != 0) {
            programCache[transition.id] = program
            return program
        }

        Log.w(TAG, "Failed to compile shader ${transition.id}, using fallback")
        return fallbackProgram
    }

    /**
     * Get the blur-background program (for hold frames, no transition).
     */
    fun getPassthroughProgram(): Int = passthroughProgram

    /**
     * Get cached uniform/attribute locations for a compiled program.
     * Locations are looked up once and reused every frame.
     */
    fun getLocations(programId: Int): ProgramLocations {
        return locationCache.getOrPut(programId) { lookupLocations(programId) }
    }

    private fun lookupLocations(programId: Int): ProgramLocations {
        return ProgramLocations(
            aPosition = GLES20.glGetAttribLocation(programId, "aPosition"),
            aTexCoord = GLES20.glGetAttribLocation(programId, "aTexCoord"),
            uTexFrom = GLES20.glGetUniformLocation(programId, "uTexFrom"),
            uTexTo = GLES20.glGetUniformLocation(programId, "uTexTo"),
            progress = GLES20.glGetUniformLocation(programId, "progress"),
            ratio = GLES20.glGetUniformLocation(programId, "ratio"),
            uInputAspect = GLES20.glGetUniformLocation(programId, "uInputAspect"),
            uInputAspectFrom = GLES20.glGetUniformLocation(programId, "uInputAspectFrom"),
            uInputAspectTo = GLES20.glGetUniformLocation(programId, "uInputAspectTo"),
            uTargetAspect = GLES20.glGetUniformLocation(programId, "uTargetAspect")
        )
    }

    /**
     * Release all cached programs. Must be called on GL thread.
     */
    fun release() {
        programCache.values.forEach { programId ->
            if (programId != 0) GLES20.glDeleteProgram(programId)
        }
        programCache.clear()
        locationCache.clear()

        if (passthroughProgram != 0) {
            GLES20.glDeleteProgram(passthroughProgram)
            passthroughProgram = 0
        }
        if (fallbackProgram != 0) {
            GLES20.glDeleteProgram(fallbackProgram)
            fallbackProgram = 0
        }
    }

    /**
     * Handle GL context loss — all programs become invalid.
     */
    fun onContextLost() {
        programCache.clear()
        locationCache.clear()
        passthroughProgram = 0
        fallbackProgram = 0
    }

    private fun compileProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0

        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }

        val program = GLES20.glCreateProgram()
        if (program == 0) {
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, GL_STATUS, 0)
        if (GL_STATUS[0] == 0) {
            val infoLog = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "Program link failed: $infoLog")
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }

        // Shaders can be detached after linking
        GLES20.glDetachShader(program, vertexShader)
        GLES20.glDetachShader(program, fragmentShader)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0

        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, GL_STATUS, 0)
        if (GL_STATUS[0] == 0) {
            val infoLog = GLES20.glGetShaderInfoLog(shader)
            val typeStr = if (type == GLES20.GL_VERTEX_SHADER) "vertex" else "fragment"
            Log.e(TAG, "Shader compilation failed ($typeStr): $infoLog")
            GLES20.glDeleteShader(shader)
            return 0
        }

        return shader
    }
}
