package com.videomaker.aimusic.media.renderer

import android.graphics.Bitmap
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Safe GL texture upload that prevents native SIGSEGV crashes in GPU drivers.
 *
 * Mali drivers on MediaTek SoCs (mt6855/mt6835/mt6878) crash in memcpy during
 * GLUtils.texImage2D / texSubImage2D when reading directly from a Bitmap's
 * native pixel buffer. The crash occurs at page boundaries, likely due to MTE
 * (Memory Tagging Extension) invalidating the bitmap's backing memory.
 *
 * This utility copies bitmap pixels into a stable direct ByteBuffer first,
 * then uploads via GLES20.glTexImage2D with that buffer — bypassing GLUtils
 * entirely and eliminating the native crash vector.
 *
 * Must be called on the GL thread with the target texture already bound.
 */
object GLTextureUploader {

    private const val TAG = "GLTextureUploader"

    /**
     * GL_BGRA_EXT (0x80E1) from GL_EXT_texture_format_BGRA8888.
     * Available on all Mali and Adreno GPUs.
     */
    private const val GL_BGRA_EXT = 0x80E1

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
     * Copies bitmap pixels to a ByteBuffer first, then uploads via
     * GLES20.glTexImage2D. This avoids GLUtils reading directly from
     * the bitmap's native memory, which crashes on Mali.
     *
     * Handles ARGB_8888 byte order differences across API levels:
     * - API 26+ (Android 8.0+): native format is RGBA → GL_RGBA
     * - API 24-25 (Android 7.x): native format is BGRA → GL_BGRA_EXT
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

        // Determine GL format/type from bitmap config.
        // For ARGB_8888: native pixel byte order changed in Android 8.0.
        //   API 26+: RGBA (matches GL_RGBA)
        //   API 24-25: BGRA (needs GL_BGRA_EXT)
        // RGB_565 and ALPHA_8 have no byte order ambiguity.
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
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    // Android 7.x: native pixel order is BGRA
                    internalFormat = GL_BGRA_EXT
                    format = GL_BGRA_EXT
                } else {
                    // Android 8.0+: native pixel order is RGBA
                    internalFormat = GLES20.GL_RGBA
                    format = GLES20.GL_RGBA
                }
                type = GLES20.GL_UNSIGNED_BYTE
            }
        }

        // Copy bitmap pixels to a direct ByteBuffer we own.
        // This snapshot is stable — even if the bitmap's native memory is
        // later invalidated by GC/MTE, our buffer remains valid.
        val buffer: ByteBuffer
        try {
            buffer = ByteBuffer.allocateDirect(bitmap.byteCount)
                .order(ByteOrder.nativeOrder())
            bitmap.copyPixelsToBuffer(buffer)
            buffer.position(0)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Native OOM allocating ByteBuffer: ${width}x$height, ${bitmap.byteCount} bytes")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy bitmap pixels: ${width}x$height, ${e.message}")
            return false
        }

        // Drain any pending GL errors so our checks are accurate.
        @Suppress("ControlFlowWithEmptyBody")
        while (GLES20.glGetError() != GLES20.GL_NO_ERROR) {}

        // Set GL_UNPACK_ALIGNMENT to match bitmap row alignment.
        // Default is 4, but RGB_565 rows (2 bytes/pixel) and ALPHA_8 rows
        // (1 byte/pixel) may not be 4-byte aligned. Mismatched alignment
        // causes GL to read extra padding bytes per row → stretched image.
        val unpackAlignment = when (bitmap.config) {
            Bitmap.Config.RGB_565 -> 2
            Bitmap.Config.ALPHA_8 -> 1
            else -> 4
        }
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, unpackAlignment)
        try {
            // Single-step upload: allocate GPU texture and copy pixels in one call.
            // Providing the buffer directly lets the driver handle allocation and
            // copy atomically, avoiding the lazy-allocation pitfall of the
            // glTexImage2D(null) + texSubImage2D pattern.
            GLES20.glTexImage2D(
                target, level, internalFormat,
                width, height, border,
                format, type, buffer
            )
        } finally {
            // Always restore default alignment
            if (unpackAlignment != 4) {
                GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
            }
        }

        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(
                TAG,
                "Texture upload failed: ${width}x$height config=${bitmap.config}, " +
                    "error=0x${Integer.toHexString(error)}"
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
