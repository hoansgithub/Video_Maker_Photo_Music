package com.videomaker.aimusic.media.effects

import android.opengl.GLES20
import android.util.Log
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil

/**
 * ShaderErrorHandler - Comprehensive GL shader error prevention and recovery
 *
 * Prevents crashes from:
 * 1. Shader compilation errors (syntax errors in GLSL)
 * 2. Shader linking errors (vertex/fragment mismatch)
 * 3. Missing uniforms/attributes
 * 4. Invalid GL state
 * 5. Texture creation failures
 *
 * Usage:
 * ```kotlin
 * val result = ShaderErrorHandler.createProgram(vertexShader, fragmentShader)
 * when (result) {
 *     is ShaderResult.Success -> {
 *         glProgram = result.program
 *     }
 *     is ShaderResult.Failure -> {
 *         Log.e(TAG, "Shader failed: ${result.error}")
 *         // Fallback to passthrough shader
 *     }
 * }
 * ```
 */
object ShaderErrorHandler {

    private const val TAG = "ShaderErrorHandler"

    sealed class ShaderResult {
        data class Success(val program: GlProgram) : ShaderResult()
        data class Failure(val error: ShaderError) : ShaderResult()
    }

    data class ShaderError(
        val type: ErrorType,
        val message: String,
        val glError: Int = GLES20.GL_NO_ERROR
    ) {
        enum class ErrorType {
            COMPILATION,
            LINKING,
            UNIFORM_NOT_FOUND,
            ATTRIBUTE_NOT_FOUND,
            TEXTURE_CREATION,
            GL_STATE_ERROR,
            UNKNOWN
        }

        override fun toString(): String {
            val glErrorStr = if (glError != GLES20.GL_NO_ERROR) {
                " (GL Error: 0x${glError.toString(16)})"
            } else ""
            return "$type: $message$glErrorStr"
        }
    }

    /**
     * Safely create and compile GL program with comprehensive error checking
     */
    fun createProgram(
        vertexShader: String,
        fragmentShader: String
    ): ShaderResult {
        return try {
            // Check GL state before attempting compilation
            val preError = GLES20.glGetError()
            if (preError != GLES20.GL_NO_ERROR) {
                return ShaderResult.Failure(
                    ShaderError(
                        ShaderError.ErrorType.GL_STATE_ERROR,
                        "GL error before shader creation",
                        preError
                    )
                )
            }

            // Attempt to create program
            val program = GlProgram(vertexShader, fragmentShader)

            // Verify program was linked successfully
            val postError = GLES20.glGetError()
            if (postError != GLES20.GL_NO_ERROR) {
                program.delete()
                return ShaderResult.Failure(
                    ShaderError(
                        ShaderError.ErrorType.LINKING,
                        "Shader linking failed",
                        postError
                    )
                )
            }

            ShaderResult.Success(program)
        } catch (e: GlUtil.GlException) {
            // GlProgram throws GlException on compilation/linking errors
            val errorType = when {
                e.message?.contains("compile", ignoreCase = true) == true ->
                    ShaderError.ErrorType.COMPILATION
                e.message?.contains("link", ignoreCase = true) == true ->
                    ShaderError.ErrorType.LINKING
                else -> ShaderError.ErrorType.UNKNOWN
            }

            ShaderResult.Failure(
                ShaderError(
                    errorType,
                    e.message ?: "Unknown shader error"
                )
            )
        } catch (e: Exception) {
            ShaderResult.Failure(
                ShaderError(
                    ShaderError.ErrorType.UNKNOWN,
                    e.message ?: "Unexpected error during shader creation"
                )
            )
        }
    }

    /**
     * Safely set float uniform - returns true if successful
     */
    fun safeSetFloatUniform(
        program: GlProgram,
        name: String,
        value: Float
    ): Boolean {
        return try {
            program.setFloatUniform(name, value)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set uniform '$name': ${e.message}")
            false
        }
    }

    /**
     * Safely set floats uniform (vec2, vec3, vec4) - returns true if successful
     */
    fun safeSetFloatsUniform(
        program: GlProgram,
        name: String,
        values: FloatArray
    ): Boolean {
        return try {
            program.setFloatsUniform(name, values)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set uniform '$name': ${e.message}")
            false
        }
    }

    /**
     * Safely set integer uniform - returns true if successful
     */
    fun safeSetIntUniform(
        program: GlProgram,
        name: String,
        value: Int
    ): Boolean {
        return try {
            program.setIntUniform(name, value)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set uniform '$name': ${e.message}")
            false
        }
    }

    /**
     * Safely set sampler texture uniform - returns true if successful
     */
    fun safeSetSamplerTexIdUniform(
        program: GlProgram,
        name: String,
        texId: Int,
        texUnitIndex: Int
    ): Boolean {
        return try {
            program.setSamplerTexIdUniform(name, texId, texUnitIndex)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set sampler '$name': ${e.message}")
            false
        }
    }

    /**
     * Check if GL is in a valid state for rendering
     */
    fun isGlStateValid(): Boolean {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "Invalid GL state: 0x${error.toString(16)}")
            return false
        }
        return true
    }

    /**
     * Create a simple passthrough shader for fallback
     * This shader just passes through the input texture without any effects
     */
    fun createPassthroughShader(): String {
        return """
vec4 transition(vec2 uv) {
    // Simple crossfade fallback
    return mix(getFromColor(uv), getToColor(uv), progress);
}
        """.trimIndent()
    }

    /**
     * Validate shader code before compilation
     * Returns null if valid, error message if invalid
     */
    fun validateShaderCode(shaderCode: String): String? {
        // Check for required functions
        if (!shaderCode.contains("transition")) {
            return "Shader must contain 'transition' function"
        }

        // Check for balanced braces
        val openBraces = shaderCode.count { it == '{' }
        val closeBraces = shaderCode.count { it == '}' }
        if (openBraces != closeBraces) {
            return "Unbalanced braces: $openBraces open, $closeBraces close"
        }

        // Check for balanced parentheses
        val openParens = shaderCode.count { it == '(' }
        val closeParens = shaderCode.count { it == ')' }
        if (openParens != closeParens) {
            return "Unbalanced parentheses: $openParens open, $closeParens close"
        }

        // Basic syntax check - no null characters
        if (shaderCode.contains('\u0000')) {
            return "Shader contains null characters"
        }

        return null
    }

    /**
     * Log detailed shader compilation error
     */
    fun logShaderError(error: ShaderError, shaderName: String) {
        Log.e(TAG, """
            |Shader Error in '$shaderName':
            |Type: ${error.type}
            |Message: ${error.message}
            |GL Error: ${if (error.glError != GLES20.GL_NO_ERROR) "0x${error.glError.toString(16)}" else "None"}
        """.trimMargin())
    }
}
