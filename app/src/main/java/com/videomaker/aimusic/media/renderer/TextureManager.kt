package com.videomaker.aimusic.media.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.opengl.GLES20

import android.util.Log
import android.media.ExifInterface
import java.io.ByteArrayInputStream

/**
 * TextureManager - Loads and caches images as GL textures using a sliding window.
 *
 * Only keeps up to [MAX_CACHED_TEXTURES] textures loaded around the current
 * playback position. At any frame, only 2 textures are needed (current + next
 * during transition). The window pre-loads 1-2 ahead for smooth transitions.
 *
 * Optimized for preview frame rate over texture resolution:
 * - Textures are 1x viewport size (minimum needed for sharp preview).
 * - Decoded as RGB_565 (2 bytes/pixel vs 4 bytes for ARGB_8888).
 * - GL_NEAREST minification filter (faster sampling).
 *
 * Must be called from the GL thread.
 */
class TextureManager(private val context: Context) {

    companion object {
        private const val TAG = "TextureManager"
        private const val MAX_CACHED_TEXTURES = 3
    }

    // Cached GL texture IDs by image index (sliding window)
    private val textureCache = mutableMapOf<Int, Int>()

    // Original aspect ratio (width/height) by image index
    private val aspectRatioCache = mutableMapOf<Int, Float>()

    // URIs that produced the current textures — used to detect changes
    private var currentUris: List<Uri> = emptyList()

    // Max texture dimensions for downsampling (set from actual viewport)
    private var maxTextureWidth = 0
    private var maxTextureHeight = 0

    // GL hardware max texture size (queried once on GL thread)
    private var glMaxTextureSize = 0

    /**
     * Detect if the image URI list has changed. If so, invalidate cache.
     * Does NOT load any textures — call [ensureTexturesForFrame] after.
     * Must be called on GL thread.
     */
    fun updateImageUris(imageUris: List<Uri>) {
        if (imageUris == currentUris) return

        // URI list changed — release all cached textures
        releaseTextures()
        currentUris = imageUris.toList()
    }

    /**
     * Ensure textures are loaded for the given image indices.
     * Loads missing textures on demand and evicts excess beyond [MAX_CACHED_TEXTURES].
     * Must be called on GL thread.
     */
    fun ensureTexturesForFrame(neededIndices: Set<Int>) {
        if (currentUris.isEmpty()) return

        // Load any missing textures
        for (index in neededIndices) {
            if (index < 0 || index >= currentUris.size) continue
            if (textureCache.containsKey(index)) continue
            loadTexture(currentUris[index], index)
        }

        // Evict if over capacity
        if (textureCache.size > MAX_CACHED_TEXTURES) {
            val toEvict = textureCache.keys
                .filter { it !in neededIndices }
                .sortedBy { key ->
                    // Evict furthest from any needed index first
                    neededIndices.minOf { needed -> kotlin.math.abs(key - needed) }
                }
                .reversed()

            val evictCount = textureCache.size - MAX_CACHED_TEXTURES
            for (i in 0 until evictCount.coerceAtMost(toEvict.size)) {
                val evictIndex = toEvict[i]
                val texId = textureCache.remove(evictIndex) ?: continue
                val ids = intArrayOf(texId)
                GLES20.glDeleteTextures(1, ids, 0)
                aspectRatioCache.remove(evictIndex)
            }
        }
    }

    /**
     * Get the GL texture ID for a given image index.
     * Returns 0 if not currently cached.
     */
    fun getTexture(index: Int): Int {
        return textureCache[index] ?: 0
    }

    /**
     * Get the original aspect ratio (width/height) of the texture at the given index.
     * Returns 1.0 if not currently cached.
     */
    fun getAspectRatio(index: Int): Float {
        return aspectRatioCache[index] ?: 1f
    }

    /**
     * Release all GL textures. Must be called on GL thread.
     */
    fun releaseTextures() {
        if (textureCache.isNotEmpty()) {
            val ids = textureCache.values.toIntArray()
            GLES20.glDeleteTextures(ids.size, ids, 0)
            textureCache.clear()
            aspectRatioCache.clear()
            Log.d(TAG, "Released all textures")
        }
        currentUris = emptyList()
    }

    /**
     * Set viewport dimensions for texture downsampling.
     * Must be called on GL thread (queries GL_MAX_TEXTURE_SIZE on first call).
     *
     * Texture limit = 1x viewport size, capped by GL hardware max.
     * Preview prioritizes frame rate over resolution — 1x viewport is sharp enough.
     */
    fun setViewportSize(width: Int, height: Int) {
        if (glMaxTextureSize == 0) {
            glMaxTextureSize = GLTextureUploader.querySafeMaxTextureSize()
        }
        // 1x viewport — minimum for sharp preview, maximum for frame rate
        maxTextureWidth = width.coerceAtMost(glMaxTextureSize)
        maxTextureHeight = height.coerceAtMost(glMaxTextureSize)
        Log.d(TAG, "Texture limit: ${maxTextureWidth}x$maxTextureHeight (1x viewport, safeMax=$glMaxTextureSize)")
    }

    /**
     * Handle GL context loss (e.g., app backgrounded).
     * All textures become invalid — clear state so they reload on next frame.
     */
    fun onContextLost() {
        // Don't delete textures (already invalid), just clear tracking state
        textureCache.clear()
        aspectRatioCache.clear()
        currentUris = emptyList()
    }

    private fun loadTexture(uri: Uri, index: Int) {
        var bitmap: Bitmap? = null
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

            // Decode with downsampling + RGB_565 for preview (half memory vs ARGB_8888)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)

            if (bitmap == null) {
                Log.w(TAG, "Failed to decode bitmap: $uri")
                return
            }

            // Apply EXIF rotation if needed
            if (exifRotation != 0) {
                val matrix = Matrix()
                matrix.postRotate(exifRotation.toFloat())
                val rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotated != bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
                // Rotation may promote to ARGB_8888 — convert back to RGB_565
                if (bitmap.config != Bitmap.Config.RGB_565) {
                    val rgb565 = bitmap.copy(Bitmap.Config.RGB_565, false)
                    if (rgb565 != null) {
                        bitmap.recycle()
                        bitmap = rgb565
                    }
                }
            }

            // Hard clamp: scale down to viewport dimensions.
            // inSampleSize is power-of-2 so decoded bitmaps can be ~2x viewport.
            // On Mali GPUs, oversized textures exhaust GPU memory and crash in
            // texSubImage2D. Clamp to 1x viewport (sharp enough for preview).
            val clampW = if (maxTextureWidth > 0) maxTextureWidth else glMaxTextureSize
            val clampH = if (maxTextureHeight > 0) maxTextureHeight else glMaxTextureSize
            if (clampW > 0 && clampH > 0 &&
                (bitmap.width > clampW || bitmap.height > clampH)
            ) {
                val scale = minOf(
                    clampW.toFloat() / bitmap.width,
                    clampH.toFloat() / bitmap.height
                )
                val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
                // createScaledBitmap may promote to ARGB_8888 — convert back to RGB_565
                if (bitmap.config != Bitmap.Config.RGB_565) {
                    val rgb565 = bitmap.copy(Bitmap.Config.RGB_565, false)
                    if (rgb565 != null) {
                        bitmap.recycle()
                        bitmap = rgb565
                    }
                }
            }

            Log.d(TAG, "Uploading texture[$index]: ${bitmap.width}x${bitmap.height} ${bitmap.config} (${bitmap.byteCount / 1024}KB)")

            val bitmapAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

            // Generate a single texture ID for this entry
            val texIds = IntArray(1)
            GLES20.glGenTextures(1, texIds, 0)
            val textureId = texIds[0]

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

            // GL_NEAREST for min filter — faster sampling at 1x viewport resolution.
            // GL_LINEAR for mag filter — smooth upscaling when image is smaller than viewport.
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            // Upload bitmap to GPU via safe ByteBuffer path.
            // Copies pixels to a stable buffer first, then uploads — prevents
            // native SIGSEGV in Mali drivers reading from bitmap native memory.
            if (!GLTextureUploader.safeTexImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)) {
                Log.w(TAG, "Safe texture upload failed for index $index, skipping")
                GLES20.glDeleteTextures(1, texIds, 0)
                return
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

            // Store in cache only after successful upload
            textureCache[index] = textureId
            aspectRatioCache[index] = bitmapAspectRatio
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load texture $uri: ${e.message}")
        } finally {
            bitmap?.let { if (!it.isRecycled) it.recycle() }
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
            while ((halfWidth / inSampleSize) >= maxTextureWidth ||
                (halfHeight / inSampleSize) >= maxTextureHeight
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
