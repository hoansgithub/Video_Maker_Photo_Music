package com.videomaker.aimusic.media.renderer

import android.opengl.GLES20
import android.util.Log
import com.videomaker.aimusic.domain.model.Transition
import com.videomaker.aimusic.media.effects.ShaderErrorHandler

/**
 * ShaderProgramCache - Compiles and caches GL shader programs.
 *
 * Compiles GLSL transition shaders into GL programs on first use.
 * Cached by shader ID so programs are reused across frames.
 * When the effect set changes, programs swap instantly (next frame).
 *
 * Uses the existing ShaderErrorHandler for compilation + fallback.
 */
class ShaderProgramCache {

    companion object {
        private const val TAG = "ShaderProgramCache"

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
         * Fragment shader wrapper that provides the GL Transitions API.
         * Wraps user transition shaders with getFromColor/getToColor/progress/ratio uniforms.
         */
        private fun wrapTransitionShader(transitionCode: String): String = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexFrom;
            uniform sampler2D uTexTo;
            uniform float progress;
            uniform float ratio;

            vec4 getFromColor(vec2 uv) {
                return texture2D(uTexFrom, uv);
            }

            vec4 getToColor(vec2 uv) {
                return texture2D(uTexTo, uv);
            }

            $transitionCode

            void main() {
                gl_FragColor = transition(vTexCoord);
            }
        """

        /** Simple passthrough shader for hold frames (no transition). */
        private const val PASSTHROUGH_FRAGMENT = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexFrom;
            void main() {
                gl_FragColor = texture2D(uTexFrom, vTexCoord);
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

    // Special programs
    private var passthroughProgram: Int = 0
    private var fallbackProgram: Int = 0

    /**
     * Initialize special programs (passthrough + fallback crossfade).
     * Must be called on GL thread after surface is created.
     */
    fun init() {
        passthroughProgram = compileProgram(VERTEX_SHADER, PASSTHROUGH_FRAGMENT)
        fallbackProgram = compileProgram(VERTEX_SHADER, wrapTransitionShader(CROSSFADE_TRANSITION))

        if (passthroughProgram == 0) {
            Log.e(TAG, "Failed to compile passthrough program")
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
     * Get the passthrough program (for hold frames, no transition).
     */
    fun getPassthroughProgram(): Int = passthroughProgram

    /**
     * Release all cached programs. Must be called on GL thread.
     */
    fun release() {
        programCache.values.forEach { programId ->
            if (programId != 0) GLES20.glDeleteProgram(programId)
        }
        programCache.clear()

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

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
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

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val infoLog = GLES20.glGetShaderInfoLog(shader)
            val typeStr = if (type == GLES20.GL_VERTEX_SHADER) "vertex" else "fragment"
            Log.e(TAG, "Shader compilation failed ($typeStr): $infoLog")
            GLES20.glDeleteShader(shader)
            return 0
        }

        return shader
    }
}
