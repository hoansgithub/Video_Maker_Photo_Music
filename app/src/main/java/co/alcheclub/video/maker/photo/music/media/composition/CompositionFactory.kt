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
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.max

/**
 * CompositionFactory - Creates Media3 Composition from Project domain model
 *
 * SINGLE SOURCE OF TRUTH ARCHITECTURE:
 * All images are pre-processed with blur background effect BEFORE creating composition.
 * This ensures perfect color consistency between still images and transitions.
 *
 * Pipeline:
 * 1. Pre-process ALL images (blur background + scale-to-fit foreground)
 * 2. Save to cache as PNG files
 * 3. Use cache URIs for Media3 (instead of original asset URIs)
 * 4. TransitionEffect loads from same cache -> identical colors
 *
 * Threading: This class uses suspend functions for bitmap loading.
 * Call createComposition from a coroutine scope.
 */
class CompositionFactory(private val context: Context) {

    /**
     * Data class for pre-processed image info
     */
    private data class ProcessedImage(
        val cacheUri: Uri,
        val cacheFile: File
    )

    /**
     * Tracks the last set of transition bitmaps for memory management.
     */
    private var lastTransitionBitmaps: Map<Int, Bitmap>? = null

    /**
     * Tracks cache files for cleanup
     */
    private var lastCacheFiles: List<File>? = null

    /**
     * Recycle all tracked transition bitmaps.
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
            System.gc()
        }
    }

    /**
     * Clean up pre-processed image cache files
     */
    fun cleanupCacheFiles() {
        val files = lastCacheFiles
        lastCacheFiles = null

        if (files != null) {
            var deletedCount = 0
            files.forEach { file ->
                if (file.exists() && file.delete()) {
                    deletedCount++
                }
            }
            android.util.Log.d("CompositionFactory", "Deleted $deletedCount cache files")
        }
    }

    /**
     * Create a Media3 Composition from a Project
     *
     * SINGLE SOURCE OF TRUTH:
     * 1. Pre-process ALL images with blur background
     * 2. Save to cache as PNG
     * 3. Use cache URIs for Media3 composition
     * 4. Load TO textures from same cache files
     */
    suspend fun createComposition(project: Project, includeAudio: Boolean = true, forExport: Boolean = false): Composition {
        val settings = project.settings
        val textureSize = if (forExport) EXPORT_TEXTURE_SIZE else PREVIEW_TEXTURE_SIZE
        android.util.Log.d("CompositionFactory", "createComposition: ${project.assets.size} assets, includeAudio=$includeAudio, forExport=$forExport, textureSize=$textureSize")

        // Clean up previous resources
        recycleBitmaps()
        cleanupCacheFiles()

        // STEP 1: Pre-process ALL images with blur background
        val startTime = System.currentTimeMillis()
        android.util.Log.d("CompositionFactory", "Pre-processing ALL images...")
        val processedImages = preProcessAllImages(project.assets, settings, textureSize)
        val preProcessTime = System.currentTimeMillis() - startTime
        android.util.Log.d("CompositionFactory", "Pre-processed ${processedImages.size} images in ${preProcessTime}ms")

        // STEP 2: Load transition TO bitmaps from cache files
        android.util.Log.d("CompositionFactory", "Loading transition bitmaps from cache...")
        val transitionBitmaps = loadTransitionBitmapsFromCache(processedImages, settings)
        lastTransitionBitmaps = transitionBitmaps
        val loadTime = System.currentTimeMillis() - startTime
        android.util.Log.d("CompositionFactory", "Loaded ${transitionBitmaps.size} transition bitmaps in ${loadTime - preProcessTime}ms")

        // STEP 3: Create video sequence using cache URIs
        android.util.Log.d("CompositionFactory", "Creating video sequence...")
        val videoSequence = createVideoSequence(project.assets, settings, processedImages, transitionBitmaps)
        android.util.Log.d("CompositionFactory", "Video sequence created")

        val sequences = mutableListOf(videoSequence)

        // Add audio sequence if enabled
        if (includeAudio) {
            android.util.Log.d("CompositionFactory", "Creating audio sequence...")
            val audioSequence = createAudioSequence(settings, project.totalDurationMs)
            if (audioSequence != null) {
                sequences.add(audioSequence)
                android.util.Log.d("CompositionFactory", "Audio sequence added")
            }
        }

        android.util.Log.d("CompositionFactory", "Building composition with ${sequences.size} sequences")
        return Composition.Builder(sequences).build()
    }

    /**
     * Pre-process ALL images with blur background effect
     *
     * SINGLE SOURCE OF TRUTH: Every image goes through identical processing:
     * 1. Load original image
     * 2. Create blur background (scale-to-fill + Gaussian blur)
     * 3. Overlay sharp foreground (scale-to-fit)
     * 4. Save to cache as PNG
     *
     * @return Map of asset index to ProcessedImage info
     */
    private suspend fun preProcessAllImages(
        assets: List<Asset>,
        settings: ProjectSettings,
        textureSize: Int
    ): Map<Int, ProcessedImage> {
        val targetAspectRatio = settings.aspectRatio.ratio
        val results = mutableMapOf<Int, ProcessedImage>()
        val cacheFiles = mutableListOf<File>()

        // Create cache directory
        val cacheDir = File(context.cacheDir, "preprocessed_images")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        // Process images in chunks to prevent OOM
        assets.indices.toList().chunked(MAX_CONCURRENT_LOADS).forEach { chunk ->
            val chunkResults = coroutineScope {
                chunk.map { index ->
                    async(Dispatchers.IO) {
                        try {
                            val asset = assets[index]
                            val sourceBitmap = loadBitmapFromUri(asset.uri, textureSize, textureSize)

                            if (sourceBitmap != null) {
                                // Apply blur background effect
                                val processedBitmap = createBlurBackgroundBitmap(
                                    source = sourceBitmap,
                                    targetAspectRatio = targetAspectRatio,
                                    textureSize = textureSize
                                )
                                sourceBitmap.recycle()

                                // Save to cache file as PNG
                                val cacheFile = File(cacheDir, "img_${index}_${System.currentTimeMillis()}.png")
                                FileOutputStream(cacheFile).use { out ->
                                    processedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                processedBitmap.recycle()

                                android.util.Log.d("CompositionFactory", "Pre-processed image $index: ${cacheFile.name}")

                                Triple(index, ProcessedImage(Uri.fromFile(cacheFile), cacheFile), cacheFile)
                            } else {
                                android.util.Log.w("CompositionFactory", "Failed to load image $index")
                                null
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("CompositionFactory", "Failed to pre-process image $index", e)
                            null
                        }
                    }
                }.awaitAll()
            }

            // Collect results
            chunkResults.filterNotNull().forEach { (index, processedImage, file) ->
                results[index] = processedImage
                cacheFiles.add(file)
            }

            // GC between chunks
            if (chunk.size == MAX_CONCURRENT_LOADS) {
                System.gc()
            }
        }

        // Track cache files for cleanup
        lastCacheFiles = cacheFiles

        return results
    }

    /**
     * Load transition TO bitmaps from cache files
     *
     * SINGLE SOURCE OF TRUTH: TO textures come from the SAME cache files
     * that Media3 uses for FROM textures. This ensures identical colors.
     *
     * @return Map of clip index to pre-loaded bitmap (flipped for OpenGL)
     */
    private suspend fun loadTransitionBitmapsFromCache(
        processedImages: Map<Int, ProcessedImage>,
        settings: ProjectSettings
    ): Map<Int, Bitmap> {
        // Skip if transitions are disabled
        if (settings.transitionId == null) {
            android.util.Log.d("CompositionFactory", "Transitions disabled, skipping bitmap load")
            return emptyMap()
        }

        val results = mutableMapOf<Int, Bitmap>()

        // For each clip (except last), load the NEXT image's cache file
        processedImages.keys.sorted().dropLast(1).forEach { index ->
            val nextIndex = index + 1
            val nextProcessedImage = processedImages[nextIndex]

            if (nextProcessedImage != null) {
                try {
                    // Load from cache file - SAME file that Media3 will use
                    // DO NOT flip - let shader handle Y-flip to preserve exact color values
                    val bitmap = BitmapFactory.decodeFile(nextProcessedImage.cacheFile.absolutePath)
                    if (bitmap != null) {
                        results[index] = bitmap
                        android.util.Log.d("CompositionFactory", "Loaded TO bitmap for clip $index from cache (no flip)")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CompositionFactory", "Failed to load TO bitmap for clip $index", e)
                }
            }
        }

        return results
    }

    /**
     * Create blur background bitmap
     *
     * Creates output with:
     * 1. Blurred, scale-to-fill background
     * 2. Sharp, scale-to-fit foreground centered on top
     */
    private fun createBlurBackgroundBitmap(
        source: Bitmap,
        targetAspectRatio: Float,
        textureSize: Int
    ): Bitmap {
        // Calculate output dimensions
        val outputWidth = textureSize
        val outputHeight = (textureSize / targetAspectRatio).toInt()

        // Create output bitmap
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 1. Draw blurred background (scale-to-fill)
        val blurredBg = createBlurredBackground(source, outputWidth, outputHeight)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(blurredBg, 0f, 0f, bgPaint)
        blurredBg.recycle()

        // 2. Calculate scale-to-fit dimensions for foreground
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

        // 3. Draw foreground (scale-to-fit)
        val destRect = RectF(left, top, left + fitWidth, top + fitHeight)
        val srcRect = Rect(0, 0, source.width, source.height)
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, srcRect, destRect, fgPaint)

        return output
    }

    /**
     * Flip bitmap vertically for OpenGL coordinate system
     */
    private fun flipBitmapVertically(source: Bitmap): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.setScale(1f, -1f, source.width / 2f, source.height / 2f)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Create blurred background (scale-to-fill + Gaussian blur)
     */
    private fun createBlurredBackground(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val scaledToFill = createScaledToFillCopy(source, targetWidth, targetHeight)
        return try {
            gaussianBlur4Direction(scaledToFill)
        } catch (e: Exception) {
            android.util.Log.w("CompositionFactory", "Blur failed: ${e.message}")
            scaledToFill
        }
    }

    /**
     * 4-direction Gaussian blur matching GPU implementation
     */
    private fun gaussianBlur4Direction(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = IntArray(width * height)

        val weight0 = 0.2272542543f
        val weight1 = 0.3165327489f
        val weight2 = 0.0703065234f

        val offset1 = 1.3846153846f
        val offset2 = 3.2307692308f

        val blurAmount = 0.025f

        val directions = arrayOf(
            floatArrayOf(1f, 0f),
            floatArrayOf(0f, 1f),
            floatArrayOf(0.707f, 0.707f),
            floatArrayOf(0.707f, -0.707f)
        )

        for (y in 0 until height) {
            for (x in 0 until width) {
                var totalR = 0f
                var totalG = 0f
                var totalB = 0f
                var totalA = 0f

                for (dir in directions) {
                    val stepX = dir[0] * blurAmount * width
                    val stepY = dir[1] * blurAmount * height

                    var pixel = getPixelBilinear(pixels, width, height, x.toFloat(), y.toFloat())
                    var r = ((pixel shr 16) and 0xFF) * weight0
                    var g = ((pixel shr 8) and 0xFF) * weight0
                    var b = (pixel and 0xFF) * weight0
                    var a = ((pixel shr 24) and 0xFF) * weight0

                    val x1p = x + stepX * offset1
                    val y1p = y + stepY * offset1
                    pixel = getPixelBilinear(pixels, width, height, x1p, y1p)
                    r += ((pixel shr 16) and 0xFF) * weight1
                    g += ((pixel shr 8) and 0xFF) * weight1
                    b += (pixel and 0xFF) * weight1
                    a += ((pixel shr 24) and 0xFF) * weight1

                    val x1n = x - stepX * offset1
                    val y1n = y - stepY * offset1
                    pixel = getPixelBilinear(pixels, width, height, x1n, y1n)
                    r += ((pixel shr 16) and 0xFF) * weight1
                    g += ((pixel shr 8) and 0xFF) * weight1
                    b += (pixel and 0xFF) * weight1
                    a += ((pixel shr 24) and 0xFF) * weight1

                    val x2p = x + stepX * offset2
                    val y2p = y + stepY * offset2
                    pixel = getPixelBilinear(pixels, width, height, x2p, y2p)
                    r += ((pixel shr 16) and 0xFF) * weight2
                    g += ((pixel shr 8) and 0xFF) * weight2
                    b += (pixel and 0xFF) * weight2
                    a += ((pixel shr 24) and 0xFF) * weight2

                    val x2n = x - stepX * offset2
                    val y2n = y - stepY * offset2
                    pixel = getPixelBilinear(pixels, width, height, x2n, y2n)
                    r += ((pixel shr 16) and 0xFF) * weight2
                    g += ((pixel shr 8) and 0xFF) * weight2
                    b += (pixel and 0xFF) * weight2
                    a += ((pixel shr 24) and 0xFF) * weight2

                    totalR += r
                    totalG += g
                    totalB += b
                    totalA += a
                }

                val finalR = (totalR * 0.25f + 0.5f).toInt().coerceIn(0, 255)
                val finalG = (totalG * 0.25f + 0.5f).toInt().coerceIn(0, 255)
                val finalB = (totalB * 0.25f + 0.5f).toInt().coerceIn(0, 255)
                val finalA = (totalA * 0.25f + 0.5f).toInt().coerceIn(0, 255)

                result[y * width + x] = (finalA shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }

        bitmap.setPixels(result, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun getPixelBilinear(pixels: IntArray, width: Int, height: Int, x: Float, y: Float): Int {
        val clampedX = x.coerceIn(0f, (width - 1).toFloat())
        val clampedY = y.coerceIn(0f, (height - 1).toFloat())

        val x0 = clampedX.toInt().coerceIn(0, width - 1)
        val y0 = clampedY.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceIn(0, width - 1)
        val y1 = (y0 + 1).coerceIn(0, height - 1)

        val fx = clampedX - x0.toFloat()
        val fy = clampedY - y0.toFloat()

        val p00 = pixels[y0 * width + x0]
        val p10 = pixels[y0 * width + x1]
        val p01 = pixels[y1 * width + x0]
        val p11 = pixels[y1 * width + x1]

        val a = bilinearInterpolate(
            ((p00 shr 24) and 0xFF).toFloat(),
            ((p10 shr 24) and 0xFF).toFloat(),
            ((p01 shr 24) and 0xFF).toFloat(),
            ((p11 shr 24) and 0xFF).toFloat(),
            fx, fy
        ).toInt().coerceIn(0, 255)

        val r = bilinearInterpolate(
            ((p00 shr 16) and 0xFF).toFloat(),
            ((p10 shr 16) and 0xFF).toFloat(),
            ((p01 shr 16) and 0xFF).toFloat(),
            ((p11 shr 16) and 0xFF).toFloat(),
            fx, fy
        ).toInt().coerceIn(0, 255)

        val g = bilinearInterpolate(
            ((p00 shr 8) and 0xFF).toFloat(),
            ((p10 shr 8) and 0xFF).toFloat(),
            ((p01 shr 8) and 0xFF).toFloat(),
            ((p11 shr 8) and 0xFF).toFloat(),
            fx, fy
        ).toInt().coerceIn(0, 255)

        val b = bilinearInterpolate(
            (p00 and 0xFF).toFloat(),
            (p10 and 0xFF).toFloat(),
            (p01 and 0xFF).toFloat(),
            (p11 and 0xFF).toFloat(),
            fx, fy
        ).toInt().coerceIn(0, 255)

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun bilinearInterpolate(p00: Float, p10: Float, p01: Float, p11: Float, fx: Float, fy: Float): Float {
        val top = p00 + (p10 - p00) * fx
        val bottom = p01 + (p11 - p01) * fx
        return top + (bottom - top) * fy
    }

    private fun createScaledToFillCopy(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val sourceAspect = source.width.toFloat() / source.height.toFloat()
        val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()

        val scale: Float
        val srcRect: Rect
        if (sourceAspect > targetAspect) {
            scale = targetHeight.toFloat() / source.height
            val scaledWidth = (source.width * scale).toInt()
            val cropX = ((scaledWidth - targetWidth) / 2 / scale).toInt()
            srcRect = Rect(cropX, 0, source.width - cropX, source.height)
        } else {
            scale = targetWidth.toFloat() / source.width
            val scaledHeight = (source.height * scale).toInt()
            val cropY = ((scaledHeight - targetHeight) / 2 / scale).toInt()
            srcRect = Rect(0, cropY, source.width, source.height - cropY)
        }

        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val destRect = Rect(0, 0, targetWidth, targetHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, srcRect, destRect, paint)

        return result
    }

    private fun loadBitmapFromUri(uri: Uri, maxWidth: Int, maxHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            val sampleSize = calculateSampleSize(
                options.outWidth,
                options.outHeight,
                maxWidth,
                maxHeight
            )

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
     * Create video sequence using pre-processed cache URIs
     */
    private fun createVideoSequence(
        assets: List<Asset>,
        settings: ProjectSettings,
        processedImages: Map<Int, ProcessedImage>,
        transitionBitmaps: Map<Int, Bitmap>
    ): EditedMediaItemSequence {
        val selectedTransition = getTransition(settings)

        var cumulativeStartTimeUs = 0L

        val editedItems = assets.mapIndexed { index, asset ->
            val nextBitmap = transitionBitmaps[index]
            val hasTransition = nextBitmap != null && selectedTransition != null
            val transition = if (hasTransition) selectedTransition else null

            val imageDurationMs = settings.imageDurationMs
            val transitionDurationMs = if (hasTransition) settings.transitionOverlapMs else 0L
            val totalDurationMs = imageDurationMs + transitionDurationMs

            val clipStartTimeUs = cumulativeStartTimeUs

            android.util.Log.d("CompositionFactory", "Clip $index: startTime=${clipStartTimeUs/1000}ms, duration=${totalDurationMs}ms, transition=${transition?.name ?: "none"}")

            // Use CACHE URI instead of original asset URI
            val imageUri = processedImages[index]?.cacheUri ?: asset.uri

            val editedItem = createEditedMediaItem(
                imageUri = imageUri,
                settings = settings,
                transition = transition,
                nextImageBitmap = nextBitmap,
                hasTransition = hasTransition,
                clipStartTimeUs = clipStartTimeUs
            )

            cumulativeStartTimeUs += totalDurationMs * 1000L

            editedItem
        }
        return EditedMediaItemSequence.Builder(editedItems).build()
    }

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
        private const val DEFAULT_FRAME_RATE = 30
        private const val PREVIEW_TEXTURE_SIZE = 360
        private const val EXPORT_TEXTURE_SIZE = 720
        private const val MAX_CONCURRENT_LOADS = 4
    }

    /**
     * Create EditedMediaItem using cache URI
     *
     * NO BlurBackgroundEffect needed - images are already pre-processed
     */
    private fun createEditedMediaItem(
        imageUri: Uri,
        settings: ProjectSettings,
        transition: Transition?,
        nextImageBitmap: Bitmap?,
        hasTransition: Boolean,
        clipStartTimeUs: Long
    ): EditedMediaItem {
        val imageDurationMs = settings.imageDurationMs
        val transitionDurationMs = settings.transitionOverlapMs

        val totalDurationMs = if (hasTransition) {
            imageDurationMs + transitionDurationMs
        } else {
            imageDurationMs
        }
        val totalDurationUs = totalDurationMs * 1000L
        val transitionDurationUs = transitionDurationMs * 1000L

        // Use pre-processed cache URI
        val mediaItem = MediaItem.Builder()
            .setUri(imageUri)
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
            .setDurationUs(totalDurationUs)
            .setFrameRate(DEFAULT_FRAME_RATE)
            .build()
    }

    /**
     * Create effects for transitions and optional frame overlay
     *
     * NO BlurBackgroundEffect - images are pre-processed with blur background
     *
     * Effect chain:
     * 1. TransitionEffect - Blend with next image (direct sampling, no UV transform)
     * 2. FrameOverlayEffect - Overlay frame on top (if selected)
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

        // NO BlurBackgroundEffect - images are already pre-processed!

        // 1. Transition effect (if there's a next image)
        // Both FROM and TO are pre-processed identically, just blend directly
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

        // 2. Optional: overlay frame
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

    private fun createAudioSequence(
        settings: ProjectSettings,
        totalVideoDurationMs: Long
    ): EditedMediaItemSequence? {
        val audioUri = getAudioUri(settings)
        if (audioUri == null) {
            android.util.Log.d("CompositionFactory", "No audio URI available")
            return null
        }

        val totalVideoDurationUs = totalVideoDurationMs * 1000L
        android.util.Log.d("CompositionFactory", "Creating audio sequence with URI: $audioUri, videoDuration: ${totalVideoDurationMs}ms")

        val audioItem = MediaItem.Builder()
            .setUri(audioUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setEndPositionMs(totalVideoDurationMs)
                    .build()
            )
            .build()

        val editedAudioItem = EditedMediaItem.Builder(audioItem)
            .setRemoveVideo(true)
            .setDurationUs(totalVideoDurationUs)
            .build()

        return EditedMediaItemSequence.Builder(listOf(editedAudioItem)).build()
    }

    private fun getAudioUri(settings: ProjectSettings): Uri? {
        android.util.Log.d("CompositionFactory", "getAudioUri: customAudioUri=${settings.customAudioUri}, audioTrackId=${settings.audioTrackId}")

        settings.customAudioUri?.let { return it }

        settings.audioTrackId?.let { trackId ->
            val track = AudioTrackLibrary.getById(trackId)
            android.util.Log.d("CompositionFactory", "Track lookup: trackId=$trackId, found=${track != null}")
            if (track != null) {
                val uri = copyAssetToCache(track.assetPath)
                android.util.Log.d("CompositionFactory", "Audio URI from cache: $uri")
                return uri
            }
        }

        return null
    }

    private fun copyAssetToCache(assetPath: String): Uri? {
        return try {
            val fileName = assetPath.substringAfterLast("/")
            val cacheFile = java.io.File(context.cacheDir, "audio/$fileName")
            android.util.Log.d("CompositionFactory", "Cache file path: ${cacheFile.absolutePath}, exists: ${cacheFile.exists()}")

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
