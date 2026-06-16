package com.videomaker.aimusic.media.renderer

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log

/**
 * TextureManager - Loads and caches images as GL textures.
 *
 * Textures are loaded on first use and cached by image index.
 * Only reloads when the image list changes (not on effect/ratio change).
 * Must be called from the GL thread.
 */
class TextureManager(private val context: Context) {

    companion object {
        private const val TAG = "TextureManager"
    }

    // Cached GL texture IDs indexed by image position
    private var textureIds: IntArray = IntArray(0)

    // URIs that produced the current textures — used to detect changes
    private var loadedUris: List<Uri> = emptyList()

    /**
     * Ensure textures are loaded for the given image URIs.
     * If URIs haven't changed, reuses cached textures.
     * Must be called on GL thread.
     */
    fun ensureTextures(imageUris: List<Uri>) {
        if (imageUris == loadedUris && textureIds.size == imageUris.size) {
            return // Already loaded
        }

        // Release old textures
        releaseTextures()

        if (imageUris.isEmpty()) return

        // Allocate new texture IDs
        textureIds = IntArray(imageUris.size)
        GLES20.glGenTextures(imageUris.size, textureIds, 0)

        for (i in imageUris.indices) {
            loadTexture(imageUris[i], textureIds[i])
        }

        loadedUris = imageUris.toList()
        Log.d(TAG, "Loaded ${imageUris.size} textures")
    }

    /**
     * Get the GL texture ID for a given image index.
     * Returns 0 if index is out of bounds.
     */
    fun getTexture(index: Int): Int {
        if (index < 0 || index >= textureIds.size) return 0
        return textureIds[index]
    }

    /**
     * Release all GL textures. Must be called on GL thread.
     */
    fun releaseTextures() {
        if (textureIds.isNotEmpty()) {
            GLES20.glDeleteTextures(textureIds.size, textureIds, 0)
            textureIds = IntArray(0)
            loadedUris = emptyList()
            Log.d(TAG, "Released all textures")
        }
    }

    /**
     * Handle GL context loss (e.g., app backgrounded).
     * All textures become invalid — clear state so they reload on next frame.
     */
    fun onContextLost() {
        // Don't delete textures (already invalid), just clear tracking state
        textureIds = IntArray(0)
        loadedUris = emptyList()
    }

    private fun loadTexture(uri: Uri, textureId: Int) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: run {
                Log.w(TAG, "Cannot open URI: $uri")
                return
            }

            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                Log.w(TAG, "Failed to decode bitmap: $uri")
                return
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

            // Set texture parameters
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            // Upload bitmap to GPU
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load texture $uri: ${e.message}")
        }
    }
}
