package co.alcheclub.video.maker.photo.music.media.composition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import co.alcheclub.video.maker.photo.music.domain.model.Asset
import co.alcheclub.video.maker.photo.music.domain.model.Project
import co.alcheclub.video.maker.photo.music.domain.model.ProjectSettings
import co.alcheclub.video.maker.photo.music.domain.model.Transition
import co.alcheclub.video.maker.photo.music.media.effects.BlurBackgroundEffect
import co.alcheclub.video.maker.photo.music.media.effects.FrameOverlayEffect
import co.alcheclub.video.maker.photo.music.media.effects.TransitionEffect
import co.alcheclub.video.maker.photo.music.media.library.AudioTrackLibrary
import co.alcheclub.video.maker.photo.music.media.library.FrameLibrary
import co.alcheclub.video.maker.photo.music.media.library.TransitionShaderLibrary
import androidx.media3.common.Effect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.min
import kotlin.math.max

/**
 * CompositionFactory - Creates Media3 Composition from Project domain model
 *
 * Builds the composition with:
 * - Image sequence with configured duration
 * - Aspect ratio presentation effect
 * - Transitions between images (pre-loaded bitmaps)
 * - Background music (looped if needed)
 *
 * Threading: This class uses suspend functions for bitmap loading.
 * Call createComposition from a coroutine scope.
 *
 * Memory Management: Track returned bitmaps via lastTransitionBitmaps
 * and call recycleBitmaps() when composition is no longer needed.
 */
class CompositionFactory(private val context: Context) {

    /**
     * Tracks the last set of transition bitmaps for memory management.
     * Call recycleBitmaps() when the composition is disposed.
     */
    private var lastTransitionBitmaps: Map<Int, Bitmap>? = null

    /**
     * Recycle all tracked transition bitmaps.
     * Call this when the composition is no longer needed to free memory.
     */
    fun recycleBitmaps() {
        val bitmaps = lastTransitionBitmaps
        lastTransitionBitmaps = null

        if (bitmaps != null) {
            var recycledCount = 0
            bitmaps.values.forEach { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                    recycledCount++
                }
            }
            android.util.Log.d("CompositionFactory", "Recycled $recycledCount transition bitmaps")

            // Force garbage collection to reclaim memory
            System.gc()
        }
    }

    /**
     * Create a Media3 Composition from a Project
     *
     * @param project The project to create composition from
     * @param includeAudio Whether to include audio track (false for preview, true for export)
     * @param forExport If true, uses higher resolution textures for export quality
     */
    suspend fun createComposition(project: Project, includeAudio: Boolean = true, forExport: Boolean = false): Composition {
        val settings = project.settings
        val textureSize = if (forExport) EXPORT_TEXTURE_SIZE else PREVIEW_TEXTURE_SIZE
        android.util.Log.d("CompositionFactory", "createComposition: ${project.assets.size} assets, includeAudio=$includeAudio, forExport=$forExport, textureSize=$textureSize")

        // Recycle previous bitmaps before creating new ones
        recycleBitmaps()

        // Pre-load transition bitmaps in PARALLEL using coroutines
        val startTime = System.currentTimeMillis()
        android.util.Log.d("CompositionFactory", "Pre-loading transition bitmaps in parallel...")
        val transitionBitmaps = preloadTransitionBitmapsParallel(project.assets, settings, textureSize)
        lastTransitionBitmaps = transitionBitmaps  // Track for later recycling
        val loadTime = System.currentTimeMillis() - startTime
        android.util.Log.d("CompositionFactory", "Pre-loaded ${transitionBitmaps.size} transition bitmaps in ${loadTime}ms")

        // Create video/image sequence
        android.util.Log.d("CompositionFactory", "Creating video sequence...")
        val videoSequence = createVideoSequence(project.assets, settings, transitionBitmaps)
        android.util.Log.d("CompositionFactory", "Video sequence created")

        val sequences = mutableListOf(videoSequence)

        // Add audio sequence if music is selected and audio is enabled
        if (includeAudio) {
            android.util.Log.d("CompositionFactory", "Creating audio sequence...")
            val audioSequence = createAudioSequence(settings, project.totalDurationMs)
            if (audioSequence != null) {
                sequences.add(audioSequence)
                android.util.Log.d("CompositionFactory", "Audio sequence added")
            } else {
                android.util.Log.d("CompositionFactory", "No audio sequence created")
            }
        }

        android.util.Log.d("CompositionFactory", "Building composition with ${sequences.size} sequences")
        return Composition.Builder(sequences).build()
    }

    /**
     * Pre-load bitmaps for all transition images with memory-safe chunked loading
     *
     * MEMORY OPTIMIZATION:
     * - Loads bitmaps in chunks of MAX_CONCURRENT_LOADS to prevent OOM
     * - Each bitmap is processed on IO dispatcher
     * - Forces GC between chunks for memory pressure relief
     *
     * IMPORTANT: This is a suspend function - call from coroutine scope.
     * The bitmaps are kept in memory and passed to TransitionEffect.
     *
     * @param textureSize Resolution for transition textures (360 for preview, 720 for export)
     * @return Map of asset index to pre-loaded bitmap for the NEXT image
     */
    private suspend fun preloadTransitionBitmapsParallel(
        assets: List<Asset>,
        settings: ProjectSettings,
        textureSize: Int
    ): Map<Int, Bitmap> {
        // Skip if transitions are disabled
        if (settings.transitionId == null) {
            android.util.Log.d("CompositionFactory", "Transitions disabled, skipping bitmap preload")
            return emptyMap()
        }

        val aspectRatio = settings.aspectRatio.ratio
        val indices = (0 until assets.lastIndex).toList()
        val results = mutableMapOf<Int, Bitmap>()

        // Load bitmaps in chunks to prevent OOM
        indices.chunked(MAX_CONCURRENT_LOADS).forEach { chunk ->
            val chunkResults = coroutineScope {
                chunk.map { index ->
                    async(Dispatchers.IO) {
                        try {
                            val nextAsset = assets[index + 1]
                            val rawBitmap = loadBitmapFromUri(nextAsset.uri, textureSize, textureSize)
                            if (rawBitmap != null) {
                                // Just flip the bitmap vertically for GL texture orientation
                                // Blur background is now applied in TransitionEffect shader
                                // using the same GPU code as BlurBackgroundEffect
                                val flippedBitmap = flipBitmapVertically(rawBitmap)

                                // Recycle raw bitmap if different from flipped
                                if (rawBitmap != flippedBitmap) {
                                    rawBitmap.recycle()
                                }

                                index to flippedBitmap
                            } else {
                                android.util.Log.w("CompositionFactory", "Failed to preload bitmap for asset ${index + 1}")
                                null
                            }
                        } catch (e: OutOfMemoryError) {
                            android.util.Log.e("CompositionFactory", "OOM loading bitmap for asset ${index + 1}", e)
                            System.gc()
                            null
                        } catch (e: Exception) {
                            android.util.Log.e("CompositionFactory", "Error loading bitmap for asset ${index + 1}", e)
                            null
                        }
                    }
                }.awaitAll()
            }

            // Add successful results
            chunkResults.filterNotNull().forEach { (index, bitmap) ->
                results[index] = bitmap
            }

            // Hint GC between chunks to free temporary memory
            if (chunk.size == MAX_CONCURRENT_LOADS) {
                System.gc()
            }
        }

        return results
    }

    /**
     * Flip bitmap vertically for OpenGL texture orientation
     * OpenGL has (0,0) at bottom-left, while Android Bitmap has (0,0) at top-left
     */
    private fun flipBitmapVertically(source: Bitmap): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.setScale(1f, -1f, source.width / 2f, source.height / 2f)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Create a bitmap with blur background effect
     *
     * PERFORMANCE OPTIMIZED:
     * - Uses ColorMatrix for GPU-accelerated desaturation/darkening
     * - Combines flip with draw operation (single pass)
     * - Reduced bitmap allocations
     *
     * Mimics the BlurBackgroundEffect shader:
     * 1. Create output bitmap at target aspect ratio
     * 2. Draw blurred, scaled-to-fill version as background with effects
     * 3. Overlay original scaled-to-fit on top
     * 4. Flip vertically during draw (not separate pass)
     *
     * @param textureSize Resolution for output texture
     * Note: Does NOT recycle the source bitmap - caller is responsible
     */
    private fun createBlurBackgroundBitmap(source: Bitmap, targetAspectRatio: Float, textureSize: Int): Bitmap {
        // Calculate output dimensions maintaining aspect ratio
        val outputWidth = textureSize
        val outputHeight = (textureSize / targetAspectRatio).toInt()

        // Create output bitmap
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Apply vertical flip transform to entire canvas (single operation instead of separate flip)
        canvas.scale(1f, -1f, outputWidth / 2f, outputHeight / 2f)

        // 1. Create blurred background (scaled to fill)
        val blurredBg = createBlurredBackground(source, outputWidth, outputHeight)

        // 2. Draw blurred background - NO darkening to match GPU shader
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(blurredBg, 0f, 0f, bgPaint)
        blurredBg.recycle()

        // 3. Calculate scale-to-fit dimensions for foreground
        val sourceAspect = source.width.toFloat() / source.height.toFloat()
        val outputAspect = outputWidth.toFloat() / outputHeight.toFloat()

        val fitWidth: Float
        val fitHeight: Float
        if (sourceAspect > outputAspect) {
            fitWidth = outputWidth.toFloat()
            fitHeight = outputWidth / sourceAspect
        } else {
            fitHeight = outputHeight.toFloat()
            fitWidth = outputHeight * sourceAspect
        }

        // Center the foreground
        val left = (outputWidth - fitWidth) / 2f
        val top = (outputHeight - fitHeight) / 2f

        // 5. Draw foreground (scaled to fit)
        val destRect = RectF(left, top, left + fitWidth, top + fitHeight)
        val srcRect = Rect(0, 0, source.width, source.height)
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, srcRect, destRect, fgPaint)

        return output
    }

    /**
     * Create a blurred background bitmap scaled to fill target dimensions
     * Always creates a NEW bitmap, never returns source
     * Uses StackBlur algorithm (no RenderScript dependency)
     *
     * Blur radius is matched to GPU shader's blurAmount = 0.025 (2.5% of image)
     */
    private fun createBlurredBackground(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        // First, create a scaled-to-fill copy
        val scaledToFill = createScaledToFillCopy(source, targetWidth, targetHeight)

        // Calculate blur radius to match GPU shader
        // GPU uses blurAmount = 0.025 (2.5% of image size)
        val blurRadius = (targetWidth * 0.025f).toInt().coerceIn(1, 25)

        // Then apply blur using StackBlur
        return try {
            stackBlur(scaledToFill, blurRadius)
        } catch (e: Exception) {
            android.util.Log.w("CompositionFactory", "Blur failed: ${e.message}")
            scaledToFill
        }
    }

    /**
     * Create a new bitmap scaled to fill (and cropped to) target dimensions
     * Always returns a NEW bitmap
     */
    private fun createScaledToFillCopy(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val sourceAspect = source.width.toFloat() / source.height.toFloat()
        val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()

        // Calculate scale to fill
        val scale: Float
        val srcRect: Rect
        if (sourceAspect > targetAspect) {
            // Source is wider - scale by height, crop width
            scale = targetHeight.toFloat() / source.height
            val scaledWidth = (source.width * scale).toInt()
            val cropX = ((scaledWidth - targetWidth) / 2 / scale).toInt()
            srcRect = Rect(cropX, 0, source.width - cropX, source.height)
        } else {
            // Source is taller - scale by width, crop height
            scale = targetWidth.toFloat() / source.width
            val scaledHeight = (source.height * scale).toInt()
            val cropY = ((scaledHeight - targetHeight) / 2 / scale).toInt()
            srcRect = Rect(0, cropY, source.width, source.height - cropY)
        }

        // Create new bitmap and draw scaled/cropped source
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val destRect = Rect(0, 0, targetWidth, targetHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, srcRect, destRect, paint)

        return result
    }

    /**
     * StackBlur algorithm - Fast blur without RenderScript
     *
     * Based on the StackBlur algorithm by Mario Klingemann.
     * This is a pure Kotlin implementation that works on all Android versions.
     *
     * @param bitmap The bitmap to blur (modified in place)
     * @param radius Blur radius (1-25)
     * @return The blurred bitmap
     */
    private fun stackBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceIn(1, 25)
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = r + r + 1

        val rArr = IntArray(wh)
        val gArr = IntArray(wh)
        val bArr = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        i = 0
        while (i < 256 * divsum) {
            dv[i] = i / divsum
            i++
        }

        yi = 0
        yw = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = r + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        y = 0
        while (y < h) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            i = -r
            while (i <= r) {
                p = pix[yi + min(wm, max(i, 0))]
                sir = stack[i + r]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = r

            x = 0
            while (x < w) {
                rArr[yi] = dv[rsum]
                gArr[yi] = dv[gsum]
                bArr[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - r + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmin[x] = min(x + r + 1, wm)
                }
                p = pix[yw + vmin[x]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
                x++
            }
            yw += w
            y++
        }

        x = 0
        while (x < w) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            yp = -r * w
            i = -r
            while (i <= r) {
                yi = max(0, yp) + x

                sir = stack[i + r]

                sir[0] = rArr[yi]
                sir[1] = gArr[yi]
                sir[2] = bArr[yi]

                rbs = r1 - kotlin.math.abs(i)

                rsum += rArr[yi] * rbs
                gsum += gArr[yi] * rbs
                bsum += bArr[yi] * rbs

                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }

                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackpointer = r
            y = 0
            while (y < h) {
                pix[yi] = (0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - r + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vmin[y] = min(y + r1, hm) * w
                }
                p = x + vmin[y]

                sir[0] = rArr[p]
                sir[1] = gArr[p]
                sir[2] = bArr[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
                y++
            }
            x++
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }

    /**
     * Load bitmap from URI with size constraints
     *
     * @param uri Source URI
     * @param maxWidth Maximum width
     * @param maxHeight Maximum height
     * @return Decoded bitmap or null if failed
     */
    private fun loadBitmapFromUri(uri: Uri, maxWidth: Int, maxHeight: Int): Bitmap? {
        return try {
            // First pass: get dimensions only
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            // Calculate sample size to reduce memory usage
            val sampleSize = calculateSampleSize(
                options.outWidth,
                options.outHeight,
                maxWidth,
                maxHeight
            )

            // Second pass: decode actual bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } catch (e: Exception) {
            android.util.Log.e("CompositionFactory", "Failed to load bitmap from $uri", e)
            null
        }
    }

    /**
     * Calculate sample size for bitmap decoding
     */
    private fun calculateSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        var sampleSize = 1
        if (sourceWidth > targetWidth || sourceHeight > targetHeight) {
            val halfWidth = sourceWidth / 2
            val halfHeight = sourceHeight / 2
            while ((halfWidth / sampleSize) >= targetWidth &&
                (halfHeight / sampleSize) >= targetHeight) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    /**
     * Create the video/image sequence from assets with transitions
     *
     * Each image (except the last) gets a transition effect that blends
     * it with the next image during the transition duration.
     *
     * Uses a single selected transition for all image pairs.
     *
     * IMPORTANT: We track cumulative start time (clipStartTimeUs) for each clip
     * because presentationTimeUs in GlShaderProgram.drawFrame() is GLOBAL
     * (relative to composition start), not local to each clip.
     */
    private fun createVideoSequence(
        assets: List<Asset>,
        settings: ProjectSettings,
        transitionBitmaps: Map<Int, Bitmap>
    ): EditedMediaItemSequence {
        // Get the selected transition
        val selectedTransition = getTransition(settings)

        var cumulativeStartTimeUs = 0L  // Track global start time for each clip

        val editedItems = assets.mapIndexed { index, asset ->
            // Get pre-loaded bitmap for next image (null for last image)
            val nextBitmap = transitionBitmaps[index]
            val hasTransition = nextBitmap != null && selectedTransition != null

            // Use the same transition for all clips
            val transition = if (hasTransition) selectedTransition else null

            // Calculate clip duration
            val imageDurationMs = settings.imageDurationMs
            val transitionDurationMs = if (hasTransition) settings.transitionOverlapMs else 0L
            val totalDurationMs = imageDurationMs + transitionDurationMs

            val clipStartTimeUs = cumulativeStartTimeUs

            android.util.Log.d("CompositionFactory", "Clip $index: startTime=${clipStartTimeUs/1000}ms, duration=${totalDurationMs}ms, transition=${transition?.name ?: "none"}")

            val editedItem = createEditedMediaItem(
                asset = asset,
                settings = settings,
                transition = transition,
                nextImageBitmap = nextBitmap,
                hasTransition = hasTransition,
                clipStartTimeUs = clipStartTimeUs
            )

            // Update cumulative start time for next clip
            cumulativeStartTimeUs += totalDurationMs * 1000L

            editedItem
        }
        return EditedMediaItemSequence.Builder(editedItems).build()
    }

    /**
     * Get the selected transition effect
     *
     * Returns a single transition that will be used for all image pairs.
     * Returns null if no transition is selected (transitions disabled).
     */
    private fun getTransition(settings: ProjectSettings): Transition? {
        val transitionId = settings.transitionId ?: return null

        val transition = TransitionShaderLibrary.getById(transitionId)
        if (transition == null) {
            android.util.Log.w("CompositionFactory", "Transition not found: $transitionId, using default")
            return TransitionShaderLibrary.getDefault()
        }

        android.util.Log.d("CompositionFactory", "getTransition: id=$transitionId, name=${transition.name}")
        return transition
    }

    companion object {
        // Default frame rate for image sequences (30 fps is standard)
        private const val DEFAULT_FRAME_RATE = 30

        // Texture sizes for transition images
        // Preview uses lower resolution for faster processing and less memory
        // Export uses full resolution for quality
        // Memory per bitmap: width * height * 4 bytes (ARGB_8888)
        // 360px: ~0.5MB per bitmap, 540px: ~1.2MB, 720px: ~2MB, 1080px: ~4.5MB
        private const val PREVIEW_TEXTURE_SIZE = 360  // Reduced from 540 to save memory
        private const val EXPORT_TEXTURE_SIZE = 720   // Reduced from 1080 for stability

        // Maximum concurrent bitmap loads to prevent OOM
        private const val MAX_CONCURRENT_LOADS = 4
    }

    /**
     * Create an EditedMediaItem from an asset (image) with optional transition
     *
     * For images in Media3 Transformer, we MUST set:
     * - setImageDurationMs() on MediaItem.Builder - how long to display
     * - setFrameRate() on EditedMediaItem.Builder - required for Transformer export
     * - setDurationUs() on EditedMediaItem.Builder - explicit duration for ImageAssetLoader
     *
     * Transition architecture:
     * - Each clip displays for imageDurationMs + transitionDurationMs
     * - The transition effect activates during the last transitionDurationMs
     * - It blends the current image with the next image (nextImageBitmap)
     * - The last clip has no transition (nextImageBitmap is null)
     *
     * @param clipStartTimeUs The GLOBAL composition time when this clip starts
     *                        Used for correct transition timing since drawFrame
     *                        receives global presentationTimeUs
     *
     * See: ImageAssetLoader line 115 checks both durationUs and frameRate
     */
    private fun createEditedMediaItem(
        asset: Asset,
        settings: ProjectSettings,
        transition: Transition?,
        nextImageBitmap: Bitmap?,
        hasTransition: Boolean,
        clipStartTimeUs: Long
    ): EditedMediaItem {
        // Image display duration (how long each image is shown)
        val imageDurationMs = settings.imageDurationMs

        // Transition duration (overlap at the end)
        val transitionDurationMs = settings.transitionOverlapMs

        // Total clip duration = image time + transition time (if not last image)
        val totalDurationMs = if (hasTransition) {
            imageDurationMs + transitionDurationMs
        } else {
            imageDurationMs
        }
        val totalDurationUs = totalDurationMs * 1000L
        val transitionDurationUs = transitionDurationMs * 1000L

        // For images, use setImageDurationMs on MediaItem.Builder
        val mediaItem = MediaItem.Builder()
            .setUri(asset.uri)
            .setImageDurationMs(totalDurationMs)
            .build()

        val effects = createEffects(
            settings = settings,
            transition = transition,
            nextImageBitmap = nextImageBitmap,
            transitionDurationUs = transitionDurationUs,
            clipDurationUs = totalDurationUs,
            clipStartTimeUs = clipStartTimeUs
        )

        return EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .setDurationUs(totalDurationUs)     // Required for Transformer ImageAssetLoader
            .setFrameRate(DEFAULT_FRAME_RATE) // Required for Transformer ImageAssetLoader
            .build()
    }

    /**
     * Create effects for aspect ratio presentation, transitions, and optional frame overlay
     *
     * Effect chain (order matters):
     * 1. BlurBackgroundEffect - Blur background with fit-inside content
     * 2. TransitionEffect - Blend with next image (if applicable)
     * 3. FrameOverlayEffect - Overlay frame on top (if selected)
     *
     * Uses BlurBackgroundEffect to:
     * 1. Show a blurred, scaled-to-fill version of the image as background
     * 2. Overlay the original image scaled-to-fit on top (no cropping)
     *
     * @param clipStartTimeUs Global composition time when this clip starts
     */
    private fun createEffects(
        settings: ProjectSettings,
        transition: Transition?,
        nextImageBitmap: Bitmap?,
        transitionDurationUs: Long,
        clipDurationUs: Long,
        clipStartTimeUs: Long
    ): Effects {
        val aspectRatio = settings.aspectRatio.ratio
        val videoEffects = mutableListOf<Effect>()

        // 1. Base effect: blur background with fit-inside content
        videoEffects.add(BlurBackgroundEffect(aspectRatio))

        // 2. Transition effect (if there's a next image and transition is enabled)
        // Note: TransitionEffect now applies blur background to TO image using same GPU code
        // This ensures perfect color consistency when transitioning to next clip
        if (transition != null && nextImageBitmap != null) {
            android.util.Log.d("CompositionFactory", "Adding TransitionEffect: ${transition.name}, clipStart=${clipStartTimeUs}us")
            videoEffects.add(
                TransitionEffect(
                    transition = transition,
                    toImageBitmap = nextImageBitmap,
                    transitionDurationUs = transitionDurationUs,
                    clipDurationUs = clipDurationUs,
                    clipStartTimeUs = clipStartTimeUs,
                    targetAspectRatio = aspectRatio
                )
            )
        }

        // 3. Optional: overlay frame on top (scale-to-fill)
        settings.overlayFrameId?.let { frameId ->
            FrameLibrary.getById(frameId)?.let { frame ->
                videoEffects.add(FrameOverlayEffect(context, frame.assetPath))
            }
        }

        return Effects(
            /* audioProcessors= */ emptyList(),
            /* videoEffects= */ videoEffects
        )
    }

    /**
     * Create audio sequence from bundled track or custom URI
     *
     * IMPORTANT for CompositionPlayer:
     * - All EditedMediaItems MUST have setDurationUs() - explicit duration required
     * - Both video and audio sequences MUST be equal length
     * - isLooping is NOT supported for preview
     *
     * See: https://github.com/androidx/media/issues/1560
     */
    private fun createAudioSequence(
        settings: ProjectSettings,
        totalVideoDurationMs: Long
    ): EditedMediaItemSequence? {
        // Get audio URI from custom audio or bundled track
        val audioUri = getAudioUri(settings)
        if (audioUri == null) {
            android.util.Log.d("CompositionFactory", "No audio URI available")
            return null
        }

        val totalVideoDurationUs = totalVideoDurationMs * 1000L
        android.util.Log.d("CompositionFactory", "Creating audio sequence with URI: $audioUri, videoDuration: ${totalVideoDurationMs}ms (${totalVideoDurationUs}us)")

        // Create audio MediaItem with explicit clipping to video duration
        // This is required for CompositionPlayer to determine sequence duration
        val audioItem = MediaItem.Builder()
            .setUri(audioUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setEndPositionMs(totalVideoDurationMs)
                    .build()
            )
            .build()

        // CRITICAL: Set explicit duration in microseconds for CompositionPlayer
        // Without this, CompositionPlayer throws IllegalStateException: -9223372036854775807
        val editedAudioItem = EditedMediaItem.Builder(audioItem)
            .setRemoveVideo(true) // Audio only
            .setDurationUs(totalVideoDurationUs)
            .build()

        return EditedMediaItemSequence.Builder(listOf(editedAudioItem)).build()
    }

    /**
     * Get audio URI from settings
     */
    private fun getAudioUri(settings: ProjectSettings): Uri? {
        android.util.Log.d("CompositionFactory", "getAudioUri: customAudioUri=${settings.customAudioUri}, audioTrackId=${settings.audioTrackId}")

        // Custom audio takes precedence
        settings.customAudioUri?.let { return it }

        // Otherwise, get bundled track
        settings.audioTrackId?.let { trackId ->
            val track = AudioTrackLibrary.getById(trackId)
            android.util.Log.d("CompositionFactory", "Track lookup: trackId=$trackId, found=${track != null}")
            if (track != null) {
                // Copy asset to cache and return file URI
                // Media3 doesn't support file:///android_asset/ URIs directly
                val uri = copyAssetToCache(track.assetPath)
                android.util.Log.d("CompositionFactory", "Audio URI from cache: $uri")
                return uri
            }
        }

        return null
    }

    /**
     * Copy bundled asset to cache directory for Media3 access
     */
    private fun copyAssetToCache(assetPath: String): Uri? {
        return try {
            val fileName = assetPath.substringAfterLast("/")
            val cacheFile = java.io.File(context.cacheDir, "audio/$fileName")
            android.util.Log.d("CompositionFactory", "Cache file path: ${cacheFile.absolutePath}, exists: ${cacheFile.exists()}")

            // Only copy if not already cached
            if (!cacheFile.exists()) {
                cacheFile.parentFile?.mkdirs()
                android.util.Log.d("CompositionFactory", "Copying asset: $assetPath")
                context.assets.open(assetPath).use { input ->
                    cacheFile.outputStream().use { output ->
                        val bytes = input.copyTo(output)
                        android.util.Log.d("CompositionFactory", "Copied $bytes bytes to cache")
                    }
                }
            } else {
                android.util.Log.d("CompositionFactory", "Using cached file: ${cacheFile.length()} bytes")
            }

            val uri = Uri.fromFile(cacheFile)
            android.util.Log.d("CompositionFactory", "Audio file URI: $uri")
            uri
        } catch (e: Exception) {
            android.util.Log.e("CompositionFactory", "Failed to copy audio asset: $assetPath", e)
            null
        }
    }
}
