package com.videomaker.aimusic.media.renderer

import android.app.ActivityManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import android.media.ExifInterface
import java.io.ByteArrayInputStream

/**
 * TextureManager - Loads and caches images as GL textures.
 *
 * Textures are loaded on first use and cached by image index.
 * Only reloads when the image list changes (not on effect/ratio change).
 * Tracks each texture's original aspect ratio for blur-background rendering.
 * Must be called from the GL thread.
 */
class TextureManager(private val context: Context) {

    companion object {
        private const val TAG = "TextureManager"
    }

    // Cached GL texture IDs indexed by image position
    private var textureIds: IntArray = IntArray(0)

    // Original aspect ratio (width/height) of each loaded texture
    private var textureAspectRatios: FloatArray = FloatArray(0)

    // URIs that produced the current textures — used to detect changes
    private var loadedUris: List<Uri> = emptyList()

    // Max texture dimensions for downsampling (set from actual viewport + hardware)
    private var maxTextureWidth = 0
    private var maxTextureHeight = 0

    // GL hardware max texture size (queried once on GL thread)
    private var glMaxTextureSize = 0

    // Device memory tier: low-memory devices use 1x viewport, others 2x
    private val qualityMultiplier: Int = run {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (am.isLowRamDevice || am.memoryClass <= 128) 1 else 2
    }

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
        textureAspectRatios = FloatArray(imageUris.size) { 1f }
        GLES20.glGenTextures(imageUris.size, textureIds, 0)

        for (i in imageUris.indices) {
            loadTexture(imageUris[i], textureIds[i], i)
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
     * Get the original aspect ratio (width/height) of the texture at the given index.
     * Returns 1.0 if index is out of bounds.
     */
    fun getAspectRatio(index: Int): Float {
        if (index < 0 || index >= textureAspectRatios.size) return 1f
        return textureAspectRatios[index]
    }

    /**
     * Release all GL textures. Must be called on GL thread.
     */
    fun releaseTextures() {
        if (textureIds.isNotEmpty()) {
            GLES20.glDeleteTextures(textureIds.size, textureIds, 0)
            textureIds = IntArray(0)
            textureAspectRatios = FloatArray(0)
            loadedUris = emptyList()
            Log.d(TAG, "Released all textures")
        }
    }

    /**
     * Set viewport dimensions for texture downsampling.
     * Must be called on GL thread (queries GL_MAX_TEXTURE_SIZE on first call).
     *
     * Texture limit = viewport * qualityMultiplier, capped by GL hardware max.
     * Low-memory devices: 1x viewport. Normal devices: 2x viewport.
     */
    fun setViewportSize(width: Int, height: Int) {
        if (glMaxTextureSize == 0) {
            val buf = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, buf, 0)
            glMaxTextureSize = buf[0].coerceAtLeast(1024)
        }
        maxTextureWidth = (width * qualityMultiplier).coerceAtMost(glMaxTextureSize)
        maxTextureHeight = (height * qualityMultiplier).coerceAtMost(glMaxTextureSize)
        Log.d(TAG, "Texture limit: ${maxTextureWidth}x$maxTextureHeight " +
            "(viewport=${width}x$height, multiplier=${qualityMultiplier}x, glMax=$glMaxTextureSize)")
    }

    /**
     * Handle GL context loss (e.g., app backgrounded).
     * All textures become invalid — clear state so they reload on next frame.
     */
    fun onContextLost() {
        // Don't delete textures (already invalid), just clear tracking state
        textureIds = IntArray(0)
        textureAspectRatios = FloatArray(0)
        loadedUris = emptyList()
    }

    private fun loadTexture(uri: Uri, textureId: Int, index: Int) {
        try {
            // Read entire image into byte array once (single I/O for both EXIF and decode)
            val inputStream = context.contentResolver.openInputStream(uri) ?: run {
                Log.w(TAG, "Cannot open URI: $uri")
                return
            }
            val bytes = inputStream.readBytes()
            inputStream.close()

            // Read EXIF rotation from byte array (no second I/O)
            val exifRotation = readExifRotation(bytes)

            // Decode bounds to calculate downsampling
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

            // Decode with downsampling for preview viewport
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight)
            }
            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)

            if (bitmap == null) {
                Log.w(TAG, "Failed to decode bitmap: $uri")
                return
            }

            // Apply EXIF rotation if needed
            if (exifRotation != 0) {
                val matrix = Matrix()
                matrix.postRotate(exifRotation.toFloat())
                val rotated = android.graphics.Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotated != bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
            }

            // Store original aspect ratio (width / height)
            textureAspectRatios[index] = bitmap.width.toFloat() / bitmap.height.toFloat()

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

    private fun readExifRotation(bytes: ByteArray): Int {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Calculate power-of-2 downsample factor so decoded bitmap fits within
     * the viewport-derived max dimensions. Prevents loading 4K+ images
     * for a small preview viewport.
     */
    private fun calculateInSampleSize(imageWidth: Int, imageHeight: Int): Int {
        // If viewport not set yet, don't downsample
        if (maxTextureWidth == 0 || maxTextureHeight == 0) return 1

        var inSampleSize = 1
        if (imageWidth > maxTextureWidth || imageHeight > maxTextureHeight) {
            val halfWidth = imageWidth / 2
            val halfHeight = imageHeight / 2
            while ((halfWidth / inSampleSize) >= maxTextureWidth &&
                (halfHeight / inSampleSize) >= maxTextureHeight
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
