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
import com.videomaker.aimusic.domain.model.AudioNode
import com.videomaker.aimusic.domain.model.BeatSyncData
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.domain.model.Transition
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.media.audio.VolumeAudioProcessor
import com.videomaker.aimusic.media.audio.FadeoutAudioProcessor
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
    private val context: Context
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
     * Track beat-sync bitmaps loaded dynamically in createBeatSyncVideoSequence().
     * These are separate from lastTransitionBitmaps and need separate cleanup.
     * Thread-safe using AtomicReference for concurrent access.
     */
    private val beatSyncLoadedBitmaps = AtomicReference<MutableList<Bitmap>?>(null)

    /**
     * Recycle all tracked transition bitmaps (both FROM and TO).
     * Thread-safe - can be called from any thread.
     */
    fun recycleBitmaps() {
        // Recycle legacy transition bitmaps
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

        // Recycle beat-sync dynamically loaded bitmaps
        val beatSyncBitmaps = beatSyncLoadedBitmaps.getAndSet(null)
        if (beatSyncBitmaps != null) {
            beatSyncBitmaps.forEach { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
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
    suspend fun createComposition(project: Project, includeAudio: Boolean = true, forExport: Boolean = false, exportQuality: VideoQuality? = null): Composition {
        val settings = project.settings
        val textureSize = if (forExport) getExportTextureSize(exportQuality) else PREVIEW_TEXTURE_SIZE
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

        // STEP 3: Create video sequence - BEAT-SYNC ONLY
        val beatData = settings.beatSyncData
        if (beatData == null) {
            throw IllegalStateException("Please select music to preview your video")
        }

        android.util.Log.i("CompositionFactory", "Using BEAT-SYNC mode")
        val videoSequence = createBeatSyncVideoSequence(
            assets = project.assets,
            settings = settings,
            beatData = beatData,
            processedImages = processedImages,
            transitionBitmaps = transitionBitmaps,
            includeWatermark = includeWatermark
        )

        val sequences = mutableListOf(videoSequence)

        // Add audio sequences from audioNodes if enabled
        if (includeAudio && settings.audioNodes.isNotEmpty()) {
            val audioSequences = createMultiTrackAudioSequences(
                settings.audioNodes,
                project.totalDurationMs
            )
            sequences.addAll(audioSequences)
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
                    // Verify file exists before loading
                    val fromBitmap = if (currentProcessedImage.cacheFile.exists() && currentProcessedImage.cacheFile.length() > 0) {
                        BitmapFactory.decodeFile(currentProcessedImage.cacheFile.absolutePath)
                    } else {
                        android.util.Log.w("CompositionFactory", "Cache file missing: ${currentProcessedImage.cacheFile.name}")
                        null
                    }

                    if (fromBitmap != null) {
                        if (hasTransition) {
                            // For clips with transition: also load TO bitmap (next clip)
                            val nextIndex = index + 1
                            val nextProcessedImage = processedImages[nextIndex]
                            val toBitmap = nextProcessedImage?.let { nextImg ->
                                if (nextImg.cacheFile.exists() && nextImg.cacheFile.length() > 0) {
                                    BitmapFactory.decodeFile(nextImg.cacheFile.absolutePath)
                                } else {
                                    android.util.Log.w("CompositionFactory", "Next cache file missing: ${nextImg.cacheFile.name}")
                                    null
                                }
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
     * Create video sequence using beat-sync timing (variable clip durations).
     *
     * Beat-sync mode:
     * - Transitions land on every 4th beat
     * - Transition duration = min(60000/BPM, 1000)ms (same for all)
     * - Transition STARTS at beat time (not centered)
     * - Last image holds for 6 beats with audio fadeout
     *
     * Uses BeatSyncTimingCalculator to convert beat positions into clip timings.
     */
    private fun createBeatSyncVideoSequence(
        assets: List<Asset>,
        settings: ProjectSettings,
        beatData: BeatSyncData,
        processedImages: Map<Int, ProcessedImage>,
        transitionBitmaps: Map<Int, TransitionBitmapPair>,
        includeWatermark: Boolean
    ): EditedMediaItemSequence {
        // Track all bitmaps loaded in this method for cleanup
        val loadedBitmaps = mutableListOf<Bitmap>()

        // Get all transitions from the effect set
        val effectSetTransitions = getTransitionsFromEffectSet(settings)
        val passthroughTransition = TransitionShaderLibrary.getDefault()

        // Calculate clip timings from beat positions
        val calculator = BeatSyncTimingCalculator()
        val imageSequence = assets.indices.toList()  // [0, 1, 2, 3, ...]

        val clips = calculator.calculateClips(
            beatData = beatData,
            imageSequence = imageSequence,
            trimStartMs = settings.hookStartTimeMs,
            trimEndMs = null,  // Auto from image count
            numShaders = effectSetTransitions.size.coerceAtLeast(1)
        )

        if (clips.isEmpty()) {
            throw IllegalStateException("No clips generated from beat-sync. Check beat data and image count.")
        }

        android.util.Log.d("CompositionFactory", "Beat-sync generated ${clips.size} clips, BPM: ${beatData.bpm}")

        var cumulativeStartTimeUs = 0L

        val editedItems = clips.mapIndexed { clipIdx, clip ->
            val imageIdx = clip.imageIndex
            val asset = assets[imageIdx]
            val bitmapPair = transitionBitmaps[imageIdx]
            val isLast = !clip.hasTransition

            // Get transition for this clip (cycles through effect set)
            val selectedTransition = if (!clip.hasTransition) {
                null
            } else if (effectSetTransitions.isNotEmpty()) {
                effectSetTransitions[clip.transitionShaderIndex % effectSetTransitions.size]
            } else {
                null  // No transitions available - skip transition effect
            }

            val transition = when {
                clip.hasTransition && selectedTransition != null -> selectedTransition
                isLast && bitmapPair != null -> passthroughTransition  // Last clip: passthrough mode
                else -> null
            }

            val totalDurationMs = clip.totalDurationMs
            val transitionDurationMs = clip.transitionDurationMs
            val clipStartTimeUs = cumulativeStartTimeUs

            // Use CACHE URI instead of original asset URI
            val imageUri = processedImages[imageIdx]?.cacheUri ?: asset.uri

            // Load TO bitmap for transition with size limit (prevents OOM)
            val toBitmap = if (clip.hasTransition && clipIdx + 1 < clips.size) {
                val nextImgIdx = clips[clipIdx + 1].imageIndex
                processedImages[nextImgIdx]?.cacheFile?.let { cacheFile ->
                    // Verify file exists before loading
                    if (cacheFile.exists() && cacheFile.length() > 0) {
                        // Load with options to limit size (max 720px as per EXPORT_TEXTURE_SIZE)
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 2  // Reduce memory by 4x
                        }
                        BitmapFactory.decodeFile(cacheFile.absolutePath, options)?.also { bitmap ->
                            loadedBitmaps.add(bitmap)  // Track for cleanup
                        }
                    } else {
                        android.util.Log.w("CompositionFactory", "Beat-sync cache file missing: ${cacheFile.name}")
                        null
                    }
                }
            } else {
                bitmapPair?.toBitmap
            }

            val editedItem = createEditedMediaItem(
                imageUri = imageUri,
                settings = settings,
                transition = transition,
                fromImageBitmap = bitmapPair?.fromBitmap,
                toImageBitmap = toBitmap,
                hasTransition = clip.hasTransition,
                isPassthroughMode = isLast,
                clipStartTimeUs = clipStartTimeUs,
                includeWatermark = includeWatermark,
                totalDurationMs = totalDurationMs,
                transitionDurationMs = transitionDurationMs
            )

            cumulativeStartTimeUs += totalDurationMs * 1000L
            editedItem
        }

        // Store loaded bitmaps for cleanup
        beatSyncLoadedBitmaps.set(loadedBitmaps)

        return EditedMediaItemSequence.Builder(editedItems).build()
    }

    /**
     * Get the list of transitions from the selected effect set
     * Transitions will be cycled through for each image pair
     */
    private fun getTransitionsFromEffectSet(settings: ProjectSettings): List<Transition> {
        val effectSetId = settings.effectSetId ?: return emptyList()
        val effectSet = TransitionSetLibrary.getById(effectSetId)
        if (effectSet == null) {
            // Effect set not found in local JSON - fall back to default transition
            android.util.Log.w("CompositionFactory", "Effect set '$effectSetId' not found, using default")
            return listOfNotNull(TransitionShaderLibrary.getDefault())
        }
        return effectSet.transitions.ifEmpty {
            // Effect set exists but has no transitions - fall back to default
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

        internal fun getExportTextureSize(quality: VideoQuality?): Int {
            return quality?.height ?: EXPORT_TEXTURE_SIZE
        }

        internal fun shouldApplyWatermark(forExport: Boolean, isWatermarkFree: Boolean): Boolean {
            return forExport && !isWatermarkFree
        }

        /**
         * Clean up stale preprocessed image cache files.
         * Safe to call on app start — preprocessing always creates fresh files.
         */
        fun cleanupStaleCacheFiles(context: Context) {
            try {
                val cacheDir = File(context.cacheDir, "preprocessed_images")
                if (cacheDir.exists()) {
                    val files = cacheDir.listFiles() ?: return
                    var deletedCount = 0
                    files.forEach { file ->
                        if (file.delete()) deletedCount++
                    }
                    if (deletedCount > 0) {
                        android.util.Log.d("CompositionFactory", "Cleaned up $deletedCount stale cache files")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("CompositionFactory", "Cache cleanup error: ${e.message}")
            }
        }
    }

    /**
     * Create EditedMediaItem using cache URI (BEAT-SYNC ONLY)
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
        includeWatermark: Boolean,
        totalDurationMs: Long,          // Beat-sync calculated duration (REQUIRED)
        transitionDurationMs: Long      // Beat-sync calculated transition (REQUIRED)
    ): EditedMediaItem {
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

    /**
     * Create multiple audio sequences from AudioNode list (multi-track timeline).
     *
     * Each AudioNode becomes a separate EditedMediaItemSequence with its own:
     * - Source URI and clipping (trim start/end)
     * - Volume processor
     * - Duration set to match video
     *
     * @param audioNodes List of audio nodes on the timeline
     * @param totalVideoDurationMs Total video duration for matching
     * @return List of audio sequences, one per node
     */
    /**
     * Create audio sequences from AudioNode list.
     *
     * Each AudioNode becomes a separate EditedMediaItemSequence with its own
     * source URI, trim, volume, and duration.
     *
     * Source priority: processedAudioUri (already trimmed + faded) > customAudioUri > songUrl
     */
    private fun createMultiTrackAudioSequences(
        audioNodes: List<AudioNode>,
        totalVideoDurationMs: Long
    ): List<EditedMediaItemSequence> {
        return audioNodes.mapNotNull { node ->
            // Prefer preprocessed audio (has fadeout baked in)
            // Verify cached files still exist (LRU eviction safety)
            val resolvedUri: String?
            val isPreprocessed: Boolean
            if (node.processedAudioUri != null) {
                val processedUri = android.net.Uri.parse(node.processedAudioUri)
                val fileExists = processedUri.scheme == "file" &&
                    processedUri.path?.let { java.io.File(it).exists() } == true
                if (fileExists) {
                    resolvedUri = node.processedAudioUri
                    isPreprocessed = true
                } else {
                    android.util.Log.w("CompositionFactory", "Preprocessed cache evicted for node ${node.id}, falling back to source")
                    resolvedUri = node.customAudioUri ?: node.songUrl
                    isPreprocessed = false
                }
            } else {
                resolvedUri = node.customAudioUri ?: node.songUrl
                isPreprocessed = false
            }

            if (resolvedUri == null) {
                android.util.Log.w("CompositionFactory", "Skipping audio node ${node.id}: no URI")
                return@mapNotNull null
            }

            // Build media item — skip clipping if using preprocessed audio (trim already baked in)
            val mediaItemBuilder = MediaItem.Builder().setUri(resolvedUri)
            if (!isPreprocessed) {
                val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(node.trimStartMs)

                if (node.trimEndMs != null) {
                    clippingBuilder.setEndPositionMs(node.trimEndMs)
                }

                mediaItemBuilder.setClippingConfiguration(clippingBuilder.build())
            }

            // Per-node volume processor
            val volumeProcessors = if (node.volume != 1.0f) {
                listOf(VolumeAudioProcessor(node.volume))
            } else {
                emptyList()
            }
            val audioEffects = Effects(volumeProcessors, emptyList())

            val editedAudioItem = EditedMediaItem.Builder(mediaItemBuilder.build())
                .setRemoveVideo(true)
                .setEffects(audioEffects)
                .setDurationUs(totalVideoDurationMs * 1000L)
                .build()

            EditedMediaItemSequence.Builder(listOf(editedAudioItem)).build()
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
