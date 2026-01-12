package com.videomaker.aimusic.media.composition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * GPUImagePreprocessor - Preprocesses images using GPU for consistent color handling
 *
 * SINGLE SOURCE OF TRUTH ARCHITECTURE:
 * 1. Load original image
 * 2. Render through GPU blur shader (same as BlurBackgroundEffect)
 * 3. Read pixels and save to cache
 * 4. Both Media3 and TransitionEffect use this cached GPU output
 *
 * This ensures identical color handling because:
 * - GPU renders the blur effect
 * - The output is saved as-is (no additional color transformations)
 * - Both consumers load the same pre-rendered file
 */
class GPUImagePreprocessor(private val context: Context) {

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var isInitialized = false

    // Framebuffer objects
    private var framebuffer: Int = 0
    private var outputTexture: Int = 0

    // Shader program
    private var shaderProgram: Int = 0
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var textureHandle: Int = 0
    private var inputAspectHandle: Int = 0
    private var targetAspectHandle: Int = 0

    /**
     * Initialize EGL context for offscreen rendering
     */
    fun initialize(): Boolean {
        if (isInitialized) return true

        try {
            // Get EGL display
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                android.util.Log.e(TAG, "Failed to get EGL display")
                return false
            }

            // Initialize EGL
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                android.util.Log.e(TAG, "Failed to initialize EGL")
                return false
            }

            // Choose config
            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)) {
                android.util.Log.e(TAG, "Failed to choose EGL config")
                return false
            }
            val eglConfig = configs[0] ?: return false

            // Create context
            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                android.util.Log.e(TAG, "Failed to create EGL context")
                return false
            }

            // Create 1x1 pbuffer surface (we'll use FBO for actual rendering)
            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
            )
            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, surfaceAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                android.util.Log.e(TAG, "Failed to create EGL surface")
                return false
            }

            // Make context current
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                android.util.Log.e(TAG, "Failed to make EGL context current")
                return false
            }

            // Compile shader
            if (!compileShader()) {
                android.util.Log.e(TAG, "Failed to compile shader")
                return false
            }

            isInitialized = true
            android.util.Log.d(TAG, "GPU preprocessor initialized successfully")
            return true

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to initialize GPU preprocessor", e)
            return false
        }
    }

    /**
     * Preprocess an image through GPU blur shader
     *
     * @param inputUri Original image URI
     * @param outputFile Cache file to save the processed image
     * @param targetAspectRatio Target aspect ratio (e.g., 16/9)
     * @param textureSize Maximum texture dimension
     * @return True if successful
     */
    fun preprocessImage(
        inputUri: Uri,
        outputFile: File,
        targetAspectRatio: Float,
        textureSize: Int
    ): Boolean {
        if (!isInitialized && !initialize()) {
            android.util.Log.e(TAG, "GPU preprocessor not initialized")
            return false
        }

        try {
            // Make context current
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                android.util.Log.e(TAG, "Failed to make EGL context current")
                return false
            }

            // Load input bitmap
            val inputBitmap = loadBitmap(inputUri, textureSize) ?: return false
            val inputWidth = inputBitmap.width
            val inputHeight = inputBitmap.height
            val inputAspect = inputWidth.toFloat() / inputHeight.toFloat()

            // Calculate output dimensions
            val outputWidth = textureSize
            val outputHeight = (textureSize / targetAspectRatio).toInt()

            // Create input texture
            val inputTexture = createTexture(inputBitmap)
            inputBitmap.recycle()

            if (inputTexture == 0) {
                android.util.Log.e(TAG, "Failed to create input texture")
                return false
            }

            // Create framebuffer and output texture
            setupFramebuffer(outputWidth, outputHeight)

            // Render
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
            GLES20.glViewport(0, 0, outputWidth, outputHeight)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            // Use shader
            GLES20.glUseProgram(shaderProgram)

            // Bind input texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexture)
            GLES20.glUniform1i(textureHandle, 0)

            // Set uniforms
            GLES20.glUniform1f(inputAspectHandle, inputAspect)
            GLES20.glUniform1f(targetAspectHandle, targetAspectRatio)

            // Draw quad
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, QUAD_VERTICES)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, TEX_COORDS)
            GLES20.glEnableVertexAttribArray(texCoordHandle)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            // Read pixels
            val buffer = ByteBuffer.allocateDirect(outputWidth * outputHeight * 4)
            buffer.order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, outputWidth, outputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            buffer.rewind()

            // Create bitmap from pixels (flip Y because OpenGL origin is bottom-left)
            val outputBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            outputBitmap.copyPixelsFromBuffer(buffer)

            // Flip vertically
            val flippedBitmap = flipBitmapVertically(outputBitmap)
            outputBitmap.recycle()

            // Save to file
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { out ->
                flippedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            flippedBitmap.recycle()

            // Cleanup
            GLES20.glDeleteTextures(1, intArrayOf(inputTexture), 0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

            android.util.Log.d(TAG, "Preprocessed image: ${outputFile.name} (${outputWidth}x${outputHeight})")
            return true

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to preprocess image", e)
            return false
        }
    }

    private fun loadBitmap(uri: Uri, maxSize: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, maxSize, maxSize)

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load bitmap from $uri", e)
            null
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        var sampleSize = 1
        if (width > maxWidth || height > maxHeight) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while ((halfWidth / sampleSize) >= maxWidth && (halfHeight / sampleSize) >= maxHeight) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    private fun createTexture(bitmap: Bitmap): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]

        if (textureId == 0) return 0

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        return textureId
    }

    private fun setupFramebuffer(width: Int, height: Int) {
        // Delete old resources
        if (framebuffer != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        }
        if (outputTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(outputTexture), 0)
        }

        // Create output texture
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        outputTexture = texIds[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, outputTexture)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        // Create framebuffer
        val fbIds = IntArray(1)
        GLES20.glGenFramebuffers(1, fbIds, 0)
        framebuffer = fbIds[0]

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, outputTexture, 0)

        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            android.util.Log.e(TAG, "Framebuffer not complete: $status")
        }
    }

    private fun flipBitmapVertically(source: Bitmap): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.setScale(1f, -1f, source.width / 2f, source.height / 2f)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun compileShader(): Boolean {
        val vertexShader = compileShaderCode(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShaderCode(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        if (vertexShader == 0 || fragmentShader == 0) return false

        shaderProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(shaderProgram, vertexShader)
        GLES20.glAttachShader(shaderProgram, fragmentShader)
        GLES20.glLinkProgram(shaderProgram)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            android.util.Log.e(TAG, "Shader link error: ${GLES20.glGetProgramInfoLog(shaderProgram)}")
            GLES20.glDeleteProgram(shaderProgram)
            return false
        }

        positionHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "aTexCoords")
        textureHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexSampler")
        inputAspectHandle = GLES20.glGetUniformLocation(shaderProgram, "uInputAspect")
        targetAspectHandle = GLES20.glGetUniformLocation(shaderProgram, "uTargetAspect")

        return true
    }

    private fun compileShaderCode(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            android.util.Log.e(TAG, "Shader compile error: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    /**
     * Release all GPU resources
     */
    fun release() {
        if (!isInitialized) return

        try {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            if (framebuffer != 0) {
                GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
                framebuffer = 0
            }
            if (outputTexture != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(outputTexture), 0)
                outputTexture = 0
            }
            if (shaderProgram != 0) {
                GLES20.glDeleteProgram(shaderProgram)
                shaderProgram = 0
            }

            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)

            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
            isInitialized = false

            android.util.Log.d(TAG, "GPU preprocessor released")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error releasing GPU preprocessor", e)
        }
    }

    companion object {
        private const val TAG = "GPUImagePreprocessor"

        // Quad vertices (full screen)
        private val QUAD_VERTICES = ByteBuffer.allocateDirect(8 * 4).apply {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(floatArrayOf(
                    -1f, -1f,  // bottom-left
                    1f, -1f,   // bottom-right
                    -1f, 1f,   // top-left
                    1f, 1f     // top-right
                ))
                position(0)
            }
        }

        // Texture coordinates
        private val TEX_COORDS = ByteBuffer.allocateDirect(8 * 4).apply {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(floatArrayOf(
                    0f, 0f,  // bottom-left
                    1f, 0f,  // bottom-right
                    0f, 1f,  // top-left
                    1f, 1f   // top-right
                ))
                position(0)
            }
        }

        // Same blur shader as BlurBackgroundEffect for consistency
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

    // Background: scale to fill
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

    // 4-direction blur
    vec4 blurH = gaussianBlur(uTexSampler, bgUV, vec2(1.0, 0.0));
    vec4 blurV = gaussianBlur(uTexSampler, bgUV, vec2(0.0, 1.0));
    vec4 blurD1 = gaussianBlur(uTexSampler, bgUV, vec2(0.707, 0.707));
    vec4 blurD2 = gaussianBlur(uTexSampler, bgUV, vec2(0.707, -0.707));
    vec4 bgColor = (blurH + blurV + blurD1 + blurD2) * 0.25;

    // Foreground: scale to fit
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
