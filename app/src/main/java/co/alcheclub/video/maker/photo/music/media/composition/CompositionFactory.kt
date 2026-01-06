package co.alcheclub.video.maker.photo.music.media.composition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import co.alcheclub.video.maker.photo.music.domain.model.Asset
import co.alcheclub.video.maker.photo.music.domain.model.Project
import co.alcheclub.video.maker.photo.music.domain.model.ProjectSettings
import co.alcheclub.video.maker.photo.music.domain.model.Transition
import co.alcheclub.video.maker.photo.music.media.audio.VolumeAudioProcessor
import co.alcheclub.video.maker.photo.music.media.effects.FrameOverlayEffect
import co.alcheclub.video.maker.photo.music.media.effects.TransitionEffect
import co.alcheclub.video.maker.photo.music.media.library.AudioTrackLibrary
import co.alcheclub.video.maker.photo.music.media.library.FrameLibrary
import co.alcheclub.video.maker.photo.music.media.library.TransitionShaderLibrary
import java.io.File

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
     * Data class holding both FROM and TO bitmaps for a clip's transition
     */
    private data class TransitionBitmapPair(
        val fromBitmap: Bitmap,
        val toBitmap: Bitmap
    )

    /**
     * Tracks the last set of transition bitmap pairs (FROM + TO) for memory management.
     */
    private var lastTransitionBitmaps: Map<Int, TransitionBitmapPair>? = null

    /**
     * Tracks cache files for cleanup
     */
    private var lastCacheFiles: List<File>? = null

    /**
     * Recycle all tracked transition bitmaps (both FROM and TO).
     */
    fun recycleBitmaps() {
        val bitmapPairs = lastTransitionBitmaps
        lastTransitionBitmaps = null

        if (bitmapPairs != null) {
            bitmapPairs.values.forEach { pair ->
                if (!pair.fromBitmap.isRecycled) {
                    pair.fromBitmap.recycle()
                }
                if (!pair.toBitmap.isRecycled) {
                    pair.toBitmap.recycle()
                }
            }
        }
    }

    /**
     * Clean up pre-processed image cache files
     */
    fun cleanupCacheFiles() {
        val files = lastCacheFiles
        lastCacheFiles = null

        if (files != null) {
            files.forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
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

        // Clean up previous resources
        recycleBitmaps()
        cleanupCacheFiles()

        // STEP 1: Pre-process ALL images with blur background
        val processedImages = preProcessAllImages(project.assets, settings, textureSize)

        // STEP 2: Load transition TO bitmaps from cache files
        val transitionBitmaps = loadTransitionBitmapsFromCache(processedImages, settings)
        lastTransitionBitmaps = transitionBitmaps

        // STEP 3: Create video sequence using cache URIs
        val videoSequence = createVideoSequence(project.assets, settings, processedImages, transitionBitmaps)

        val sequences = mutableListOf(videoSequence)

        // Add audio sequence if enabled
        if (includeAudio) {
            val audioSequence = createAudioSequence(settings, project.totalDurationMs)
            if (audioSequence != null) {
                sequences.add(audioSequence)
            }
        }

        return Composition.Builder(sequences).build()
    }

    /**
     * Pre-process ALL images with GPU blur background effect
     *
     * SINGLE SOURCE OF TRUTH: Every image goes through identical GPU processing:
     * 1. Load original image
     * 2. GPU renders blur background + sharp foreground (same shader as BlurBackgroundEffect)
     * 3. Save GPU output to cache as PNG (lossless)
     * 4. Both Media3 and TransitionEffect use this cached GPU output
     *
     * This ensures identical color handling because both consumers load the same
     * GPU-rendered cached image.
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

        // GPU preprocessing - single source of truth
        val gpuPreprocessor = GPUImagePreprocessor(context)
        try {
            if (!gpuPreprocessor.initialize()) {
                return emptyMap()
            }

            // Process images sequentially on GPU (GPU context is single-threaded)
            assets.forEachIndexed { index, asset ->
                try {
                    val cacheFile = File(cacheDir, "img_${index}_${System.currentTimeMillis()}.png")

                    val success = gpuPreprocessor.preprocessImage(
                        inputUri = asset.uri,
                        outputFile = cacheFile,
                        targetAspectRatio = targetAspectRatio,
                        textureSize = textureSize
                    )

                    if (success) {
                        results[index] = ProcessedImage(Uri.fromFile(cacheFile), cacheFile)
                        cacheFiles.add(cacheFile)
                    }
                } catch (_: Exception) {
                }
            }
        } finally {
            gpuPreprocessor.release()
        }

        // Track cache files for cleanup
        lastCacheFiles = cacheFiles

        return results
    }

    /**
     * Load bitmaps from GPU-cached files for ALL clips
     *
     * SINGLE SOURCE OF TRUTH: All textures come from the SAME GPU-rendered cache files.
     * This ensures identical color handling because:
     * - Same GPU shader produced all images
     * - Same PNG files are loaded using identical BitmapFactory.decodeFile
     * - TransitionEffect loads textures itself (ignores Media3's inputTexId)
     *
     * For clips with transitions: loads FROM + TO bitmaps
     * For last clip (no transition): loads only FROM bitmap (for passthrough rendering)
     *
     * @return Map of clip index to TransitionBitmapPair (from + optional to)
     */
    private suspend fun loadTransitionBitmapsFromCache(
        processedImages: Map<Int, ProcessedImage>,
        settings: ProjectSettings
    ): Map<Int, TransitionBitmapPair> {
        val results = mutableMapOf<Int, TransitionBitmapPair>()
        val sortedIndices = processedImages.keys.sorted()

        // Load bitmaps for ALL clips
        sortedIndices.forEach { index ->
            val currentProcessedImage = processedImages[index]
            val isLastClip = index == sortedIndices.last()
            val hasTransition = !isLastClip && settings.transitionId != null

            if (currentProcessedImage != null) {
                try {
                    // Load FROM bitmap (current clip's image) - needed for ALL clips
                    val fromBitmap = BitmapFactory.decodeFile(currentProcessedImage.cacheFile.absolutePath)

                    if (fromBitmap != null) {
                        if (hasTransition) {
                            // For clips with transition: also load TO bitmap (next clip)
                            val nextIndex = index + 1
                            val nextProcessedImage = processedImages[nextIndex]
                            val toBitmap = nextProcessedImage?.let {
                                BitmapFactory.decodeFile(it.cacheFile.absolutePath)
                            }

                            if (toBitmap != null) {
                                results[index] = TransitionBitmapPair(fromBitmap, toBitmap)
                            } else {
                                // Fallback: use FROM as TO (no visible transition but consistent rendering)
                                results[index] = TransitionBitmapPair(fromBitmap, fromBitmap)
                            }
                        } else {
                            // For last clip (no transition): use FROM for both (passthrough mode)
                            results[index] = TransitionBitmapPair(fromBitmap, fromBitmap)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        return results
    }

    // CPU blur code removed - now using GPU preprocessing via GPUImagePreprocessor
    // This ensures single source of truth for color handling

    /**
     * Create video sequence using pre-processed cache URIs
     *
     * IMPORTANT: ALL clips use TransitionEffect for consistent rendering:
     * - Clips with transition: TransitionEffect with actual transition
     * - Last clip (no transition): TransitionEffect in passthrough mode (shows FROM image)
     *
     * This ensures all images go through our consistent texture loading pipeline,
     * avoiding orientation/color issues from Media3's internal image handling.
     */
    private fun createVideoSequence(
        assets: List<Asset>,
        settings: ProjectSettings,
        processedImages: Map<Int, ProcessedImage>,
        transitionBitmaps: Map<Int, TransitionBitmapPair>
    ): EditedMediaItemSequence {
        val selectedTransition = getTransition(settings)
        // For passthrough mode on last clip, use crossfade (simplest transition)
        val passthroughTransition = TransitionShaderLibrary.getDefault()

        var cumulativeStartTimeUs = 0L

        val editedItems = assets.mapIndexed { index, asset ->
            val bitmapPair = transitionBitmaps[index]
            val isLastClip = index == assets.size - 1

            // Determine transition mode:
            // - Regular clips: use selected transition if available
            // - Last clip: use passthrough mode (TransitionEffect with 0 duration)
            val hasActualTransition = !isLastClip && bitmapPair != null && selectedTransition != null
            val usePassthroughMode = isLastClip && bitmapPair != null

            val transition = when {
                hasActualTransition -> selectedTransition
                usePassthroughMode -> passthroughTransition  // Passthrough uses crossfade at progress=0
                else -> null
            }

            val imageDurationMs = settings.imageDurationMs
            // Last clip has no transition overlap
            val transitionDurationMs = if (hasActualTransition) settings.transitionOverlapMs else 0L
            val totalDurationMs = imageDurationMs + transitionDurationMs

            val clipStartTimeUs = cumulativeStartTimeUs

            // Use CACHE URI instead of original asset URI
            val imageUri = processedImages[index]?.cacheUri ?: asset.uri

            val editedItem = createEditedMediaItem(
                imageUri = imageUri,
                settings = settings,
                transition = transition,
                fromImageBitmap = bitmapPair?.fromBitmap,
                toImageBitmap = bitmapPair?.toBitmap,
                hasTransition = hasActualTransition,
                isPassthroughMode = usePassthroughMode,
                clipStartTimeUs = clipStartTimeUs
            )

            cumulativeStartTimeUs += totalDurationMs * 1000L

            editedItem
        }
        return EditedMediaItemSequence.Builder(editedItems).build()
    }

    private fun getTransition(settings: ProjectSettings): Transition? {
        val transitionId = settings.transitionId ?: return null
        return TransitionShaderLibrary.getById(transitionId) ?: TransitionShaderLibrary.getDefault()
    }

    companion object {
        private const val DEFAULT_FRAME_RATE = 30
        private const val PREVIEW_TEXTURE_SIZE = 360
        private const val EXPORT_TEXTURE_SIZE = 720
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
        fromImageBitmap: Bitmap?,
        toImageBitmap: Bitmap?,
        hasTransition: Boolean,
        isPassthroughMode: Boolean,
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
        // Passthrough mode: 0 transition duration (just show FROM image)
        val transitionDurationUs = if (isPassthroughMode) 0L else transitionDurationMs * 1000L

        // Use pre-processed cache URI
        val mediaItem = MediaItem.Builder()
            .setUri(imageUri)
            .setImageDurationMs(totalDurationMs)
            .build()

        val effects = createEffects(
            settings = settings,
            transition = transition,
            fromImageBitmap = fromImageBitmap,
            toImageBitmap = toImageBitmap,
            transitionDurationUs = transitionDurationUs,
            clipDurationUs = totalDurationUs,
            clipStartTimeUs = clipStartTimeUs,
            isPassthroughMode = isPassthroughMode
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
     * SINGLE SOURCE OF TRUTH: TransitionEffect receives BOTH FROM and TO bitmaps
     * loaded from the same GPU-cached files. This ensures identical colors because
     * both textures are loaded using identical methods.
     *
     * Effect chain:
     * 1. TransitionEffect - Blend FROM to TO (both loaded by us, ignores Media3 input)
     *    - For passthrough mode: renders FROM image only (consistent orientation)
     * 2. FrameOverlayEffect - Overlay frame on top (if selected)
     */
    private fun createEffects(
        settings: ProjectSettings,
        transition: Transition?,
        fromImageBitmap: Bitmap?,
        toImageBitmap: Bitmap?,
        transitionDurationUs: Long,
        clipDurationUs: Long,
        clipStartTimeUs: Long,
        isPassthroughMode: Boolean = false
    ): Effects {
        val aspectRatio = settings.aspectRatio.ratio
        val videoEffects = mutableListOf<Effect>()

        // NO BlurBackgroundEffect - images are already pre-processed!

        // 1. Transition effect - SINGLE SOURCE OF TRUTH
        // Both FROM and TO bitmaps are loaded from GPU-cached files using identical methods
        // TransitionEffect ignores Media3's input texture and uses our textures instead
        // For passthrough mode (last clip): still uses TransitionEffect to ensure consistent rendering
        if (transition != null && fromImageBitmap != null && toImageBitmap != null) {
            videoEffects.add(
                TransitionEffect(
                    transition = transition,
                    fromImageBitmap = fromImageBitmap,
                    toImageBitmap = toImageBitmap,
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
        val audioUri = getAudioUri(settings) ?: return null

        val totalVideoDurationUs = totalVideoDurationMs * 1000L
        val volume = settings.audioVolume.coerceIn(0f, 1f)

        val audioItem = MediaItem.Builder()
            .setUri(audioUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setEndPositionMs(totalVideoDurationMs)
                    .build()
            )
            .build()

        // Create audio effects with volume processor
        val audioEffects = if (volume != 1.0f) {
            Effects(
                /* audioProcessors= */ listOf(VolumeAudioProcessor(volume)),
                /* videoEffects= */ emptyList()
            )
        } else {
            Effects(
                /* audioProcessors= */ emptyList(),
                /* videoEffects= */ emptyList()
            )
        }

        val editedAudioItem = EditedMediaItem.Builder(audioItem)
            .setRemoveVideo(true)
            .setDurationUs(totalVideoDurationUs)
            .setEffects(audioEffects)
            .build()

        return EditedMediaItemSequence.Builder(listOf(editedAudioItem)).build()
    }

    private fun getAudioUri(settings: ProjectSettings): Uri? {
        settings.customAudioUri?.let { return it }

        settings.audioTrackId?.let { trackId ->
            val track = AudioTrackLibrary.getById(trackId)
            if (track != null) {
                return copyAssetToCache(track.assetPath)
            }
        }

        return null
    }

    private fun copyAssetToCache(assetPath: String): Uri? {
        return try {
            val fileName = assetPath.substringAfterLast("/")
            val cacheFile = java.io.File(context.cacheDir, "audio/$fileName")

            if (!cacheFile.exists()) {
                cacheFile.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            Uri.fromFile(cacheFile)
        } catch (_: Exception) {
            null
        }
    }
}
