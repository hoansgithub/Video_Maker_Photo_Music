@file:Suppress("DEPRECATION") // android.graphics.Movie is the only lib-free GIF frame sampler

package com.videomaker.aimusic.media.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.videomaker.aimusic.domain.model.StickerPlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
 * timing via [Movie]. Animated WebP frames are decoded via [ImageDecoder] +
 * [AnimatedImageDrawable] with frame durations from RIFF ANMF headers.
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
 * - GIF -> rasterized frames via [Movie] (deterministic, sampled at a fixed step).
 * - Animated WebP -> [ImageDecoder] + [AnimatedImageDrawable] with callback-based
 *   frame capture on a [HandlerThread].
 * - PNG / static WebP / static images -> single bitmap.
 */
class AnimatedStickerDecoder(private val context: Context) {

    companion object {
        private const val TAG = "StickerDecoder"
        private const val MAX_DIMENSION = 384       // downscale cap (stickers are small)
        private const val GIF_SAMPLE_STEP_MS = 50   // ~20 fps rasterization
        private const val MAX_ANIM_FRAMES = 120
        private const val MAX_DOWNLOAD_BYTES = 10 * 1024 * 1024 // 10 MB safety cap
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
        return when {
            isGif(bytes) -> decodeGif(bytes) ?: decodeStatic(bytes)
            isAnimatedWebP(bytes) -> {
                Log.d(TAG, "Detected animated WebP (${bytes.size} bytes)")
                decodeAnimatedWebP(bytes) ?: decodeStatic(bytes)
            }
            else -> decodeStatic(bytes)
        }
    }

    private fun download(url: String): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
            }
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var totalRead = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    totalRead += read
                    if (totalRead > MAX_DOWNLOAD_BYTES) {
                        Log.w(TAG, "Sticker too large (>${MAX_DOWNLOAD_BYTES}): $url")
                        return null
                    }
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sticker download failed: $url (${e.message})")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun isGif(bytes: ByteArray): Boolean =
        bytes.size >= 3 && bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()

    /**
     * Detect animated WebP: RIFF + WEBP + VP8X extended header with animation flag set.
     */
    private fun isAnimatedWebP(bytes: ByteArray): Boolean {
        if (bytes.size < 21) return false
        val isRiffWebP = bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        if (!isRiffWebP) return false
        val isVP8X = bytes[12] == 'V'.code.toByte() && bytes[13] == 'P'.code.toByte() &&
                bytes[14] == '8'.code.toByte() && bytes[15] == 'X'.code.toByte()
        if (!isVP8X) return false
        return (bytes[20].toInt() and 0x02) != 0
    }

    // ============================================================
    // ANIMATED WEBP — ImageDecoder + AnimatedImageDrawable
    // ============================================================

    /**
     * Decode animated WebP using [ImageDecoder] + [AnimatedImageDrawable].
     *
     * Runs the animation on a [HandlerThread] and captures each frame via
     * [Drawable.Callback.invalidateDrawable]. Frame durations come from the
     * ANMF headers parsed separately for accuracy.
     *
     * Android's native WebP decoder handles all frame types (VP8, VP8L, ALPH+VP8)
     * and compositing (blend/dispose) internally.
     */
    private fun decodeAnimatedWebP(bytes: ByteArray): DecodedSticker? {
        val anmfDurations = parseAnmfDurations(bytes)

        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        val drawable = try {
            ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: Exception) {
            Log.w(TAG, "ImageDecoder.decodeDrawable failed: ${e.message}")
            return null
        }

        if (drawable !is AnimatedImageDrawable) {
            Log.d(TAG, "Not AnimatedImageDrawable: ${drawable.javaClass.simpleName}")
            return null
        }

        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        if (width <= 0 || height <= 0) return null

        val scale = scaleFactor(width, height)
        val outW = (width * scale).toInt().coerceAtLeast(1)
        val outH = (height * scale).toInt().coerceAtLeast(1)

        drawable.setBounds(0, 0, width, height)
        drawable.repeatCount = 0 // Play exactly once

        // Thread-safe: HandlerThread writes, calling thread reads after join()
        val frames = Collections.synchronizedList(ArrayList<StickerFrame>())
        val done = CountDownLatch(1)

        val thread = HandlerThread("webp-decoder-${System.nanoTime()}")
        thread.start()
        val handler = Handler(thread.looper)

        try {
            handler.post {
                var frameIndex = 0
                var prevCaptureUptime = SystemClock.uptimeMillis()

                // Capture initial frame (frame 0) before starting animation
                val initialFrame = captureFrame(drawable, outW, outH, scale)
                if (initialFrame == null) {
                    Log.w(TAG, "Failed to capture initial frame")
                    done.countDown()
                    return@post
                }
                frames.add(StickerFrame(initialFrame, 0)) // duration set when next frame arrives

                drawable.callback = object : Drawable.Callback {
                    override fun invalidateDrawable(who: Drawable) {
                        val now = SystemClock.uptimeMillis()
                        val measuredDurationMs = (now - prevCaptureUptime).toInt().coerceAtLeast(10)
                        prevCaptureUptime = now
                        frameIndex++

                        // Set previous frame's duration (prefer ANMF timing, fallback to measured)
                        if (frames.isNotEmpty()) {
                            val prevIdx = frameIndex - 1
                            val duration = anmfDurations?.getOrNull(prevIdx)
                                ?.coerceAtLeast(10) ?: measuredDurationMs
                            val prev = frames.removeAt(frames.lastIndex)
                            frames.add(StickerFrame(prev.bitmap, duration))
                        }

                        if (frames.size >= MAX_ANIM_FRAMES) {
                            drawable.stop()
                            done.countDown()
                            return
                        }

                        // Capture the new frame
                        captureFrame(who, outW, outH, scale)?.let { bmp ->
                            frames.add(StickerFrame(bmp, 0))
                        }
                    }

                    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                        handler.postAtTime(what, `when`)
                    }

                    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                        handler.removeCallbacks(what)
                    }
                }

                drawable.registerAnimationCallback(object : android.graphics.drawable.Animatable2.AnimationCallback() {
                    override fun onAnimationEnd(d: Drawable?) {
                        // Fix the last frame's duration
                        if (frames.isNotEmpty() && frames.last().durationMs == 0) {
                            val lastIdx = frames.size - 1
                            val duration = anmfDurations?.getOrNull(lastIdx)?.coerceAtLeast(10)
                                ?: if (frames.size > 1) {
                                    frames.dropLast(1).map { it.durationMs }.average().toInt().coerceAtLeast(10)
                                } else 100
                            val last = frames.removeAt(frames.lastIndex)
                            frames.add(StickerFrame(last.bitmap, duration))
                        }
                        done.countDown()
                    }
                })

                drawable.start()
            }

            // Wait for one animation loop to complete (most stickers are 1-5 seconds)
            val completed = done.await(15, TimeUnit.SECONDS)
            if (!completed) {
                Log.w(TAG, "AnimatedWebP decode timed out")
            }

            // Stop animation and clear callbacks to prevent use-after-free
            handler.post {
                drawable.stop()
                drawable.callback = null
            }
            handler.removeCallbacksAndMessages(null)
            thread.quitSafely()
            thread.join(2000) // Wait for thread to actually stop

        } catch (e: Exception) {
            Log.e(TAG, "AnimatedWebP decode failed: ${e.message}")
            handler.removeCallbacksAndMessages(null)
            thread.quitSafely()
            recycleFrames(frames)
            return null
        }

        // Filter out frames that never got their duration set (timeout/interrupted)
        val validFrames = frames.filter { it.durationMs > 0 }
        frames.filter { it.durationMs <= 0 }.forEach {
            if (!it.bitmap.isRecycled) it.bitmap.recycle()
        }

        if (validFrames.size <= 1) {
            recycleFrames(validFrames)
            Log.w(TAG, "AnimatedWebP: only ${validFrames.size} valid frames, falling back to static")
            return null
        }

        Log.d(TAG, "AnimatedWebP: ${validFrames.size} frames, ${validFrames.sumOf { it.durationMs }}ms")
        return DecodedSticker(validFrames, totalDurationMs = validFrames.sumOf { it.durationMs })
    }

    /**
     * Parse ANMF chunk headers to extract frame durations without decoding pixels.
     * Returns null if no ANMF chunks found.
     */
    private fun parseAnmfDurations(bytes: ByteArray): List<Int>? {
        if (bytes.size < 30 || !isAnimatedWebP(bytes)) return null

        val durations = mutableListOf<Int>()
        var pos = 12 // After "RIFF" + size + "WEBP"
        while (pos + 8 <= bytes.size) {
            val fourcc = String(bytes, pos, 4, Charsets.US_ASCII)
            val chunkSize = readUint32LE(bytes, pos + 4)
            if (chunkSize < 0) break
            val dataStart = pos + 8

            if (fourcc == "ANMF" && chunkSize >= 16 && dataStart + 15 <= bytes.size) {
                val dur = readUint24LE(bytes, dataStart + 12).coerceAtLeast(10)
                durations.add(dur)
            }

            val nextPos = dataStart + chunkSize + (chunkSize % 2)
            if (nextPos <= pos) break // prevent infinite loop on malformed data
            pos = nextPos
        }

        return durations.ifEmpty { null }
    }

    /** Capture the current visual state of a Drawable into a scaled ARGB_8888 bitmap. */
    private fun captureFrame(drawable: Drawable, outW: Int, outH: Int, scale: Float): Bitmap? {
        return try {
            val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.TRANSPARENT)
            val canvas = Canvas(bmp)
            if (scale != 1f) canvas.scale(scale, scale)
            drawable.draw(canvas)
            bmp
        } catch (e: Exception) {
            Log.w(TAG, "Frame capture failed: ${e.message}")
            null
        }
    }

    private fun recycleFrames(frames: List<StickerFrame>) {
        frames.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
    }

    private fun readUint24LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16)

    private fun readUint32LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    // ============================================================
    // STATIC + GIF decoders
    // ============================================================

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
        if (duration <= 0) return null

        val scale = scaleFactor(width, height)
        val outW = (width * scale).toInt().coerceAtLeast(1)
        val outH = (height * scale).toInt().coerceAtLeast(1)

        val frames = ArrayList<StickerFrame>()
        var t = 0
        while (t < duration && frames.size < MAX_ANIM_FRAMES) {
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

    // ============================================================
    // Image utilities
    // ============================================================

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
        val converted = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return bitmap
        bitmap.recycle()
        return converted
    }
}
