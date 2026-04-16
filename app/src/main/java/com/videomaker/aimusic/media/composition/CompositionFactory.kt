package com.videomaker.aimusic.media.composition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.Asset
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.domain.model.Transition
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.media.audio.VolumeAudioProcessor
import com.videomaker.aimusic.media.effects.FrameOverlayEffect
import com.videomaker.aimusic.media.effects.TransitionEffect
import com.videomaker.aimusic.media.effects.WatermarkOverlayEffect
import com.videomaker.aimusic.media.library.FrameLibrary
import com.videomaker.aimusic.media.library.TransitionSetLibrary
import com.videomaker.aimusic.media.library.TransitionShaderLibrary
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
class CompositionFactory(
    private val context: Context,
    private val songRepository: SongRepository
) {

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
     * Thread-safe using AtomicReference for concurrent access from UI and background threads.
     */
    private val lastTransitionBitmaps = AtomicReference<Map<Int, TransitionBitmapPair>?>(null)

    /**
     * Tracks cache files for cleanup.
     * Thread-safe using AtomicReference for concurrent access.
     */
    private val lastCacheFiles = AtomicReference<List<File>?>(null)

    /**
     * Recycle all tracked transition bitmaps (both FROM and TO).
     * Thread-safe - can be called from any thread.
     */
    fun recycleBitmaps() {
        val bitmapPairs = lastTransitionBitmaps.getAndSet(null)

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
     * Clean up pre-processed image cache files.
     * Thread-safe - can be called from any thread.
     */
    fun cleanupCacheFiles() {
        val files = lastCacheFiles.getAndSet(null)

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
        val includeWatermark = shouldApplyWatermark(
            forExport = forExport,
            isWatermarkFree = project.isWatermarkFree
        )

        // Clean up previous resources
        recycleBitmaps()
        cleanupCacheFiles()

        // STEP 1: Pre-process ALL images with blur background
        val processedImages = preProcessAllImages(project.assets, settings, textureSize)

        // VALIDATE: Ensure ALL images were processed successfully
        if (processedImages.size != project.assets.size) {
            android.util.Log.e("CompositionFactory",
                "GPU preprocessing incomplete: ${project.assets.size} assets, ${processedImages.size} processed")
            throw IllegalStateException(
                "GPU preprocessing failed: ${project.assets.size} assets, ${processedImages.size} processed"
            )
        }

        // STEP 2: Load transition TO bitmaps from cache files
        val transitionBitmaps = loadTransitionBitmapsFromCache(processedImages, settings)
        lastTransitionBitmaps.set(transitionBitmaps)

        // STEP 3: Create video sequence using cache URIs
        val videoSequence = createVideoSequence(
            assets = project.assets,
            settings = settings,
            processedImages = processedImages,
            transitionBitmaps = transitionBitmaps,
            includeWatermark = includeWatermark
        )

        val sequences = mutableListOf(videoSequence)

        // Add audio sequence if enabled
        if (includeAudio) {
            val audioSequence = createAudioSequence(settings, project.totalDurationMs, forExport)
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
    ): Map<Int, ProcessedImage> = withContext(Dispatchers.IO) {
        // ✅ Explicit IO dispatcher to avoid ANR during bitmap operations
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
                android.util.Log.e("CompositionFactory", "Failed to initialize GPU preprocessor")
                throw IllegalStateException("Failed to initialize GPU preprocessor")
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
                    } else {
                        android.util.Log.e("CompositionFactory", "Failed to preprocess asset $index: ${asset.uri}")
                        throw IllegalStateException("GPU preprocessing failed for asset $index")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CompositionFactory", "Exception preprocessing asset $index", e)
                    throw e
                }
            }
        } finally {
            gpuPreprocessor.release()
        }

        // Track cache files for cleanup
        lastCacheFiles.set(cacheFiles)

        results  // Return from withContext
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
    ): Map<Int, TransitionBitmapPair> = withContext(Dispatchers.IO) {
        // ✅ Explicit IO dispatcher to avoid ANR during bitmap loading
        val results = mutableMapOf<Int, TransitionBitmapPair>()
        val sortedIndices = processedImages.keys.sorted()

        // Load bitmaps for ALL clips
        sortedIndices.forEach { index ->
            val currentProcessedImage = processedImages[index]
            val isLastClip = index == sortedIndices.last()
            val hasTransition = !isLastClip && settings.effectSetId != null

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

        results  // Return from withContext
    }

    // CPU blur code removed - now using GPU preprocessing via GPUImagePreprocessor
    // This ensures single source of truth for color handling

    /**
     * Create video sequence using pre-processed cache URIs
     *
     * IMPORTANT: ALL clips use TransitionEffect for consistent rendering:
     * - Clips with transition: TransitionEffect with actual transition (cycles through effect set)
     * - Last clip (no transition): TransitionEffect in passthrough mode (shows FROM image)
     *
     * This ensures all images go through our consistent texture loading pipeline,
     * avoiding orientation/color issues from Media3's internal image handling.
     */
    private fun createVideoSequence(
        assets: List<Asset>,
        settings: ProjectSettings,
        processedImages: Map<Int, ProcessedImage>,
        transitionBitmaps: Map<Int, TransitionBitmapPair>,
        includeWatermark: Boolean
    ): EditedMediaItemSequence {
        // Get all transitions from the effect set - these will be cycled through
        val effectSetTransitions = getTransitionsFromEffectSet(settings)
        // For passthrough mode on last clip, use crossfade (simplest transition)
        val passthroughTransition = TransitionShaderLibrary.getDefault()

        var cumulativeStartTimeUs = 0L

        val editedItems = assets.mapIndexed { index, asset ->
            val bitmapPair = transitionBitmaps[index]
            val isLastClip = index == assets.size - 1

            // Get the transition for this specific image (cycles through effect set)
            val selectedTransition = getTransitionForIndex(effectSetTransitions, index)

            // Determine transition mode:
            // - Regular clips: use transition from effect set (cycling)
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
                clipStartTimeUs = clipStartTimeUs,
                includeWatermark = includeWatermark
            )

            cumulativeStartTimeUs += totalDurationMs * 1000L

            editedItem
        }
        return EditedMediaItemSequence.Builder(editedItems).build()
    }

    /**
     * Get the list of transitions from the selected effect set
     * Transitions will be cycled through for each image pair
     */
    private fun getTransitionsFromEffectSet(settings: ProjectSettings): List<Transition> {
        val effectSetId = settings.effectSetId ?: return emptyList()
        val effectSet = TransitionSetLibrary.getById(effectSetId) ?: return emptyList()
        return effectSet.transitions.ifEmpty {
            // Fallback to default if effect set has no transitions
            listOfNotNull(TransitionShaderLibrary.getDefault())
        }
    }

    /**
     * Get transition for a specific image index by cycling through the effect set
     */
    private fun getTransitionForIndex(transitions: List<Transition>, index: Int): Transition? {
        if (transitions.isEmpty()) return null
        return transitions[index % transitions.size]
    }

    companion object {
        private const val DEFAULT_FRAME_RATE = 30
        private const val PREVIEW_TEXTURE_SIZE = 360
        private const val EXPORT_TEXTURE_SIZE = 720

        internal fun shouldApplyWatermark(forExport: Boolean, isWatermarkFree: Boolean): Boolean {
            return forExport && !isWatermarkFree
        }
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
        clipStartTimeUs: Long,
        includeWatermark: Boolean
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
            isPassthroughMode = isPassthroughMode,
            includeWatermark = includeWatermark
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
        isPassthroughMode: Boolean = false,
        includeWatermark: Boolean = false
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
                videoEffects.add(FrameOverlayEffect(context, frame.frameUrl))
            }
        }

        if (includeWatermark) {
            videoEffects.add(WatermarkOverlayEffect(context, R.drawable.app_icon_loading))
        }

        return Effects(
            /* audioProcessors= */ emptyList(),
            /* videoEffects= */ videoEffects
        )
    }

    private suspend fun createAudioSequence(
        settings: ProjectSettings,
        totalVideoDurationMs: Long,
        forExport: Boolean
    ): EditedMediaItemSequence? {
        val volume = settings.audioVolume.coerceIn(0f, 1f)

        // Volume effects (preview uses player.volume, export bakes it in)
        val audioEffects = if (forExport && volume != 1.0f) {
            Effects(listOf(VolumeAudioProcessor(volume)), emptyList())
        } else {
            Effects(emptyList(), emptyList())
        }

        // Priority 1: Use processed audio if available
        // This can be either:
        // - Transcoded AAC from MP3 (already looped to video duration)
        // - Extracted segment from AAC (needs manual looping)
        if (settings.processedAudioUri != null) {
            val processedFilename = settings.processedAudioUri.lastPathSegment ?: ""

            // Check if this is a transcoded file (contains "looped_aac") or extracted segment
            val isTranscodedLooped = processedFilename.contains("looped_aac")

            if (isTranscodedLooped) {
                // Transcoded file - already looped to match video duration
                android.util.Log.d("CompositionFactory", "Using transcoded looped audio: ${settings.processedAudioUri}")

                val mediaItem = MediaItem.Builder()
                    .setUri(settings.processedAudioUri)
                    .build()

                val editedAudioItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveVideo(true)
                    .setEffects(audioEffects)
                    .setDurationUs(totalVideoDurationMs * 1000L)
                    .build()

                return EditedMediaItemSequence.Builder(listOf(editedAudioItem)).build()
            } else {
                // Extracted segment - needs manual looping
                android.util.Log.d("CompositionFactory", "Using extracted segment with manual looping: ${settings.processedAudioUri}")

                // Calculate segment duration from trim positions
                val segmentDurationMs = if (settings.musicTrimEndMs != null && settings.musicTrimStartMs > 0) {
                    settings.musicTrimEndMs - settings.musicTrimStartMs
                } else {
                    totalVideoDurationMs // Fallback
                }

                // Calculate how many times to loop
                val loopCount = if (segmentDurationMs > 0) {
                    kotlin.math.ceil(totalVideoDurationMs.toDouble() / segmentDurationMs.toDouble()).toInt()
                } else {
                    1
                }

                android.util.Log.d("CompositionFactory", "Looping segment: segmentDuration=${segmentDurationMs}ms, videoDuration=${totalVideoDurationMs}ms, loops=$loopCount")

                // Create multiple MediaItem instances for looping
                val loopedItems = List(loopCount) {
                    val mediaItem = MediaItem.Builder()
                        .setUri(settings.processedAudioUri)
                        .build()

                    EditedMediaItem.Builder(mediaItem)
                        .setRemoveVideo(true)
                        .setEffects(audioEffects)
                        .build()
                }

                return EditedMediaItemSequence.Builder(loopedItems).build()
            }
        }

        // Priority 2: Get source audio URI (song or custom)
        val audioUri = getAudioUri(settings) ?: return null
        val trimStartMs = settings.musicTrimStartMs
        val trimEndMs = settings.musicTrimEndMs

        // Check if trimming is applied
        val hasTrim = trimStartMs > 0 || trimEndMs != null

        android.util.Log.d("CompositionFactory", "Audio sequence: trimStart=$trimStartMs, trimEnd=$trimEndMs, hasTrim=$hasTrim, forExport=$forExport, videoLength=${totalVideoDurationMs}ms")

        if (hasTrim) {
            android.util.Log.d("CompositionFactory", "Using trimmed audio: start=$trimStartMs, end=$trimEndMs (${if (forExport) "export" else "preview"})")

            if (forExport) {
                // EXPORT: Smart looping based on segment vs video duration
                val segmentDurationMs = if (trimEndMs != null) {
                    trimEndMs - trimStartMs
                } else {
                    totalVideoDurationMs
                }

                val loopPlan = ExportAudioLoopPlanner.plan(
                    segmentDurationMs = segmentDurationMs,
                    totalVideoDurationMs = totalVideoDurationMs
                )

                if (loopPlan.shouldLoop) {
                    // LOOP: Segment shorter than video - loop to fill duration exactly
                    android.util.Log.d("CompositionFactory", "Export: Looping segment (segment=${segmentDurationMs}ms < video=${totalVideoDurationMs}ms) - fullLoops=${loopPlan.fullLoops}, remainingMs=${loopPlan.remainingMs}")

                    val loopedItems = mutableListOf<EditedMediaItem>()

                    // Add full loops
                    repeat(loopPlan.fullLoops) { index ->
                        val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(trimStartMs)

                        if (trimEndMs != null) {
                            clippingBuilder.setEndPositionMs(trimEndMs)
                        }

                        val mediaItem = MediaItem.Builder()
                            .setUri(audioUri)
                            .setMediaId("loop_${index}_${audioUri.hashCode()}")
                            .setClippingConfiguration(clippingBuilder.build())
                            .build()

                        loopedItems.add(
                            EditedMediaItem.Builder(mediaItem)
                                .setRemoveVideo(true)
                                .setEffects(audioEffects)
                                .build()
                        )
                    }

                    // Add partial loop if there's remaining time
                    if (loopPlan.remainingMs > 0) {
                        val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(trimStartMs)
                            .setEndPositionMs(trimStartMs + loopPlan.remainingMs) // Clip to exact remaining duration

                        val mediaItem = MediaItem.Builder()
                            .setUri(audioUri)
                            .setMediaId("loop_partial_${audioUri.hashCode()}")
                            .setClippingConfiguration(clippingBuilder.build())
                            .build()

                        loopedItems.add(
                            EditedMediaItem.Builder(mediaItem)
                                .setRemoveVideo(true)
                                .setEffects(audioEffects)
                                .build()
                        )
                    }

                    return EditedMediaItemSequence.Builder(loopedItems).build()
                } else {
                    // NO LOOP: Segment equal or longer - clip to video duration
                    android.util.Log.d("CompositionFactory", "Export: No loop (segment=${segmentDurationMs}ms >= video=${totalVideoDurationMs}ms), clipping to video duration")

                    // Clip audio to match video duration exactly
                    val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(trimStartMs)
                        .setEndPositionMs(trimStartMs + totalVideoDurationMs) // Stop at video end

                    val mediaItem = MediaItem.Builder()
                        .setUri(audioUri)
                        .setClippingConfiguration(clippingBuilder.build())
                        .build()

                    val editedAudioItem = EditedMediaItem.Builder(mediaItem)
                        .setRemoveVideo(true)
                        .setEffects(audioEffects)
                        .build()

                    return EditedMediaItemSequence.Builder(listOf(editedAudioItem)).build()
                }
            } else {
                // PREVIEW: Single trimmed item, preview player handles stopping at video end
                android.util.Log.d("CompositionFactory", "Preview: Single trimmed audio (player stops at video end)")

                val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(trimStartMs)

                if (trimEndMs != null) {
                    clippingBuilder.setEndPositionMs(trimEndMs)
                }

                val mediaItem = MediaItem.Builder()
                    .setUri(audioUri)
                    .setClippingConfiguration(clippingBuilder.build())
                    .build()

                val editedAudioItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveVideo(true)
                    .setEffects(audioEffects)
                    .build()

                return EditedMediaItemSequence.Builder(listOf(editedAudioItem)).build()
            }
        } else {
            // No manual trim - get actual audio duration and apply smart logic
            android.util.Log.d("CompositionFactory", "No manual trim detected - detecting actual audio duration for smart logic")

            // Get actual audio duration
            val actualAudioDurationMs = getAudioDuration(audioUri)

            if (actualAudioDurationMs != null && actualAudioDurationMs > 0) {
                android.util.Log.d("CompositionFactory", "Actual audio duration: ${actualAudioDurationMs}ms, video: ${totalVideoDurationMs}ms")

                if (forExport) {
                    // EXPORT: Apply smart looping/clipping based on actual duration
                    val loopPlan = ExportAudioLoopPlanner.plan(
                        segmentDurationMs = actualAudioDurationMs,
                        totalVideoDurationMs = totalVideoDurationMs
                    )

                    if (loopPlan.shouldLoop) {
                        // LOOP: Audio shorter than video - loop to fill duration exactly
                        android.util.Log.d("CompositionFactory", "Export (untrimmed): Looping (audio=${actualAudioDurationMs}ms < video=${totalVideoDurationMs}ms) - fullLoops=${loopPlan.fullLoops}, remainingMs=${loopPlan.remainingMs}")

                        val loopedItems = mutableListOf<EditedMediaItem>()

                        // Add full loops
                        repeat(loopPlan.fullLoops) { index ->
                            val mediaItem = MediaItem.Builder()
                                .setUri(audioUri)
                                .setMediaId("loop_${index}_${audioUri.hashCode()}")
                                .build()

                            loopedItems.add(
                                EditedMediaItem.Builder(mediaItem)
                                    .setRemoveVideo(true)
                                    .setEffects(audioEffects)
                                    .build()
                            )
                        }

                        // Add partial loop if there's remaining time
                        if (loopPlan.remainingMs > 0) {
                            val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(0L)
                                .setEndPositionMs(loopPlan.remainingMs) // Clip to exact remaining duration

                            val mediaItem = MediaItem.Builder()
                                .setUri(audioUri)
                                .setMediaId("loop_partial_${audioUri.hashCode()}")
                                .setClippingConfiguration(clippingBuilder.build())
                                .build()

                            loopedItems.add(
                                EditedMediaItem.Builder(mediaItem)
                                    .setRemoveVideo(true)
                                    .setEffects(audioEffects)
                                    .build()
                            )
                        }

                        return EditedMediaItemSequence.Builder(loopedItems).build()
                    } else {
                        // NO LOOP: Audio equal or longer - clip to video duration
                        android.util.Log.d("CompositionFactory", "Export (untrimmed): Clipping to video duration (audio=${actualAudioDurationMs}ms >= video=${totalVideoDurationMs}ms)")

                        val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(0L)
                            .setEndPositionMs(totalVideoDurationMs) // Clip to exact video duration

                        val mediaItem = MediaItem.Builder()
                            .setUri(audioUri)
                            .setClippingConfiguration(clippingBuilder.build())
                            .build()

                        val editedAudioItem = EditedMediaItem.Builder(mediaItem)
                            .setRemoveVideo(true)
                            .setEffects(audioEffects)
                            .build()

                        return EditedMediaItemSequence.Builder(listOf(editedAudioItem)).build()
                    }
                } else {
                    // PREVIEW: Single item (preview player handles looping/stopping)
                    android.util.Log.d("CompositionFactory", "Preview (untrimmed): Single audio item (player handles looping)")

                    val mediaItem = MediaItem.Builder()
                        .setUri(audioUri)
                        .build()

                    val editedAudioItem = EditedMediaItem.Builder(mediaItem)
                        .setRemoveVideo(true)
                        .setEffects(audioEffects)
                        .build()

                    return EditedMediaItemSequence.Builder(listOf(editedAudioItem)).build()
                }
            } else {
                // Fallback: Could not determine duration - clip to video duration for safety
                android.util.Log.w("CompositionFactory", "Could not determine audio duration - clipping to video duration: ${totalVideoDurationMs}ms")

                val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0L)
                    .setEndPositionMs(totalVideoDurationMs) // Hard clip to exact video duration

                val mediaItem = MediaItem.Builder()
                    .setUri(audioUri)
                    .setClippingConfiguration(clippingBuilder.build())
                    .build()

                val editedAudioItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveVideo(true)
                    .setEffects(audioEffects)
                    .build()

                return EditedMediaItemSequence.Builder(listOf(editedAudioItem)).build()
            }
        }
    }

    /**
     * Get actual audio duration in milliseconds
     * Uses ExoPlayer for remote URLs (HTTP/HTTPS) and MediaMetadataRetriever for local files
     * Returns null if duration cannot be determined
     */
    private suspend fun getAudioDuration(audioUri: Uri): Long? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val uriScheme = audioUri.scheme?.lowercase()

                if (uriScheme == "http" || uriScheme == "https") {
                    // Remote URL: Use ExoPlayer to get duration
                    android.util.Log.d("CompositionFactory", "Getting duration for remote URL: $audioUri")

                    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                        val player = ExoPlayer.Builder(context).build()

                        val listener = object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_READY) {
                                    val duration = player.duration

                                    // Always remove listener and release player first
                                    player.removeListener(this)
                                    player.release()

                                    // Then resume if continuation is still active
                                    if (continuation.isActive) {
                                        android.util.Log.d("CompositionFactory", "Remote audio duration: ${duration}ms")
                                        continuation.resume(if (duration > 0) duration else null)
                                    }
                                }
                            }

                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                // Always remove listener and release player first
                                player.removeListener(this)
                                player.release()

                                // Then resume if continuation is still active
                                if (continuation.isActive) {
                                    android.util.Log.e("CompositionFactory", "Failed to get remote audio duration", error)
                                    continuation.resume(null)
                                }
                            }
                        }

                        player.addListener(listener)
                        player.setMediaItem(MediaItem.fromUri(audioUri))
                        player.prepare()

                        continuation.invokeOnCancellation {
                            player.release()
                        }
                    }
                } else {
                    // Local file: Use MediaMetadataRetriever
                    android.util.Log.d("CompositionFactory", "Getting duration for local file: $audioUri")

                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, audioUri)
                        val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val duration = durationStr?.toLongOrNull()
                        android.util.Log.d("CompositionFactory", "Local audio duration: ${duration}ms")
                        duration
                    } finally {
                        retriever.release()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CompositionFactory", "Failed to get audio duration for $audioUri", e)
                null
            }
        }
    }

    private suspend fun getAudioUri(settings: ProjectSettings): Uri? {
        // Priority 1: Custom audio from device (local file)
        settings.customAudioUri?.let { return it }

        // Priority 2: Music song from Supabase (remote URL)
        settings.musicSongId?.let { songId ->
            // Look up song from repository to get mp3Url
            val result = songRepository.getSongById(songId)
            result.onSuccess { song ->
                // Use mp3Url (full track) for export, not previewUrl
                if (song.mp3Url.isNotBlank()) {
                    return Uri.parse(song.mp3Url)
                }
            }
        }

        // Fallback: Use cached musicSongUrl if available (backward compatibility)
        settings.musicSongUrl?.let { url ->
            if (url.isNotBlank()) {
                return Uri.parse(url)
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
