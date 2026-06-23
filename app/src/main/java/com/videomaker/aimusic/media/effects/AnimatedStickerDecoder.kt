@file:Suppress("DEPRECATION") // android.graphics.Movie is the only lib-free GIF frame sampler

package com.videomaker.aimusic.media.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import com.videomaker.aimusic.domain.model.StickerPlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * A single decoded animation frame.
 *
 * @param bitmap ARGB_8888 bitmap (with transparency)
 * @param durationMs how long this frame is shown
 */
class StickerFrame(val bitmap: Bitmap, val durationMs: Int)

/**
 * A fully decoded sticker ready for export compositing.
 *
 * Static stickers have a single frame. Animated GIFs are rasterized into frames with
 * timing. Animated WebP currently falls back to its first frame (platform has no
 * lib-free frame extractor); it still animates in the live preview via Coil.
 */
class DecodedSticker(
    val frames: List<StickerFrame>,
    val totalDurationMs: Int
) {
    val isAnimated: Boolean get() = frames.size > 1

    /** Pick the frame for an absolute timeline time (sticker loops for the whole video). */
    fun frameAt(timeMs: Long): Bitmap {
        if (frames.size <= 1 || totalDurationMs <= 0) return frames.first().bitmap
        var t = (timeMs % totalDurationMs).toInt()
        for (frame in frames) {
            if (t < frame.durationMs) return frame.bitmap
            t -= frame.durationMs
        }
        return frames.last().bitmap
    }

    fun recycle() {
        frames.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
    }
}

/**
 * Downloads and decodes sticker assets for the export pipeline.
 *
 * - GIF → rasterized frames via [Movie] (deterministic, sampled at a fixed step).
 * - PNG / static WebP / static images → single bitmap.
 * - Animated WebP → first frame only (logged limitation).
 */
class AnimatedStickerDecoder(private val context: Context) {

    companion object {
        private const val TAG = "StickerDecoder"
        private const val MAX_DIMENSION = 384       // downscale cap (stickers are small)
        private const val GIF_SAMPLE_STEP_MS = 50   // ~20 fps rasterization
        private const val MAX_GIF_FRAMES = 120
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000
    }

    /** Decode every placement; entries that fail to download/decode are dropped. */
    suspend fun decodeAll(
        placements: List<StickerPlacement>
    ): List<Pair<StickerPlacement, DecodedSticker>> = withContext(Dispatchers.IO) {
        // De-dupe network/decoding work by URL — many placements can share one asset.
        val byUrl = HashMap<String, DecodedSticker?>()
        placements.mapNotNull { placement ->
            val decoded = byUrl.getOrPut(placement.assetUrl) {
                runCatching { decode(placement.assetUrl) }.getOrNull()
            }
            if (decoded == null) null else placement to decoded
        }
    }

    private fun decode(url: String): DecodedSticker? {
        val bytes = download(url) ?: return null
        return if (isGif(bytes)) {
            decodeGif(bytes) ?: decodeStatic(bytes)
        } else {
            decodeStatic(bytes)
        }
    }

    private fun download(url: String): ByteArray? {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
            }
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Sticker download failed: $url (${e.message})")
            null
        }
    }

    private fun isGif(bytes: ByteArray): Boolean =
        bytes.size >= 3 && bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()

    private fun decodeStatic(bytes: ByteArray): DecodedSticker? {
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scaled = downscale(raw)
        if (scaled !== raw) raw.recycle()
        val argb = ensureArgb(scaled)
        return DecodedSticker(listOf(StickerFrame(argb, 0)), totalDurationMs = 0)
    }

    private fun decodeGif(bytes: ByteArray): DecodedSticker? {
        val movie = try {
            Movie.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        } ?: return null

        val width = movie.width()
        val height = movie.height()
        if (width <= 0 || height <= 0) return null

        val duration = movie.duration()
        // Non-animated GIF (single frame or unknown duration) → static.
        if (duration <= 0) return null

        val scale = scaleFactor(width, height)
        val outW = (width * scale).toInt().coerceAtLeast(1)
        val outH = (height * scale).toInt().coerceAtLeast(1)

        val frames = ArrayList<StickerFrame>()
        var t = 0
        while (t < duration && frames.size < MAX_GIF_FRAMES) {
            val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.TRANSPARENT)
            val canvas = Canvas(bmp)
            canvas.scale(scale, scale)
            movie.setTime(t)
            movie.draw(canvas, 0f, 0f)
            val step = minOf(GIF_SAMPLE_STEP_MS, duration - t)
            frames.add(StickerFrame(bmp, step))
            t += GIF_SAMPLE_STEP_MS
        }
        if (frames.isEmpty()) return null
        val total = frames.sumOf { it.durationMs }
        return DecodedSticker(frames, totalDurationMs = total)
    }

    private fun scaleFactor(w: Int, h: Int): Float {
        val maxDim = maxOf(w, h)
        return if (maxDim > MAX_DIMENSION) MAX_DIMENSION.toFloat() / maxDim else 1f
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val scale = scaleFactor(bitmap.width, bitmap.height)
        if (scale >= 1f) return bitmap
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun ensureArgb(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        val converted = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        bitmap.recycle()
        return converted
    }
}
