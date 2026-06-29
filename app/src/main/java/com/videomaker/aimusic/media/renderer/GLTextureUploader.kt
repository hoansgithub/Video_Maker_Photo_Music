package com.videomaker.aimusic.media.renderer

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log

/**
 * Safe GL texture upload that prevents native SIGSEGV crashes in GPU drivers.
 *
 * Mali (and some Adreno) drivers crash in memcpy during glTexImage2D when GPU
 * memory allocation fails silently and the driver copies pixels into an
 * invalid/undersized buffer.
 *
 * This utility separates allocation from copy:
 * 1. Pre-allocate GPU texture storage with glTexImage2D(null) — no pixel copy
 * 2. Check GL errors — catches GPU OOM as a GL error, not a native crash
 * 3. Upload pixels with GLUtils.texSubImage2D into the pre-allocated storage
 *
 * Must be called on the GL thread with the target texture already bound.
 */
object GLTextureUploader {

    private const val TAG = "GLTextureUploader"

    /**
     * Conservative max texture dimension.
     *
     * Most mobile GPUs report 4096–16384 for GL_MAX_TEXTURE_SIZE, but
     * allocating at those limits under memory pressure causes native
     * crashes in Mali/Adreno drivers. 4096 covers 4K output and is
     * safe for all mainstream mobile GPUs.
     */
    const val SAFE_MAX_TEXTURE_SIZE = 4096

    /**
     * Upload [bitmap] to the currently bound GL texture, safely.
     *
     * Call after `glBindTexture` and `glTexParameteri` setup.
     *
     * @return true if upload succeeded, false on failure (GPU OOM, recycled bitmap, etc.)
     */
    fun safeTexImage2D(target: Int, level: Int, bitmap: Bitmap, border: Int): Boolean {
        if (bitmap.isRecycled) {
            Log.w(TAG, "Bitmap recycled, skipping texture upload")
            return false
        }

        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "Invalid bitmap dimensions: ${width}x$height")
            return false
        }

        // Determine GL format/type from bitmap config to match what
        // GLUtils.texSubImage2D will use internally.
        val internalFormat: Int
        val format: Int
        val type: Int
        when (bitmap.config) {
            Bitmap.Config.RGB_565 -> {
                internalFormat = GLES20.GL_RGB
                format = GLES20.GL_RGB
                type = GLES20.GL_UNSIGNED_SHORT_5_6_5
            }
            Bitmap.Config.ALPHA_8 -> {
                internalFormat = GLES20.GL_ALPHA
                format = GLES20.GL_ALPHA
                type = GLES20.GL_UNSIGNED_BYTE
            }
            else -> {
                // ARGB_8888 and all other configs
                internalFormat = GLES20.GL_RGBA
                format = GLES20.GL_RGBA
                type = GLES20.GL_UNSIGNED_BYTE
            }
        }

        // Drain any pending GL errors so our checks are accurate.
        @Suppress("ControlFlowWithEmptyBody")
        while (GLES20.glGetError() != GLES20.GL_NO_ERROR) {}

        // Step 1: Pre-allocate GPU texture storage (null = no pixel copy).
        // If the GPU can't allocate, this sets a GL error instead of
        // crashing in the driver's memcpy.
        GLES20.glTexImage2D(
            target, level, internalFormat,
            width, height, border,
            format, type, null
        )

        val allocError = GLES20.glGetError()
        if (allocError != GLES20.GL_NO_ERROR) {
            Log.e(
                TAG,
                "GPU texture alloc failed: ${width}x$height, " +
                    "error=0x${Integer.toHexString(allocError)}"
            )
            return false
        }

        // Step 2: Re-check bitmap right before the copy.
        if (bitmap.isRecycled) {
            Log.w(TAG, "Bitmap recycled after GPU alloc, skipping pixel upload")
            return false
        }

        // Step 3: Copy pixels into the pre-allocated texture.
        // The destination buffer is already allocated, so the driver's
        // memcpy targets valid memory — eliminating the main crash vector.
        GLUtils.texSubImage2D(target, level, 0, 0, bitmap)

        val uploadError = GLES20.glGetError()
        if (uploadError != GLES20.GL_NO_ERROR) {
            Log.e(
                TAG,
                "Texture pixel upload failed: ${width}x$height, " +
                    "error=0x${Integer.toHexString(uploadError)}"
            )
            return false
        }

        return true
    }

    /**
     * Query GL_MAX_TEXTURE_SIZE, capped at [SAFE_MAX_TEXTURE_SIZE].
     * Must be called on the GL thread.
     */
    fun querySafeMaxTextureSize(): Int {
        val buf = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, buf, 0)
        return buf[0].coerceIn(1024, SAFE_MAX_TEXTURE_SIZE)
    }
}
