package com.videomaker.aimusic.media.composition

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Effects
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.Asset
import com.videomaker.aimusic.domain.model.AudioNode
import com.videomaker.aimusic.domain.model.BeatSyncData
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.domain.model.TextFontPreset
import com.videomaker.aimusic.domain.model.Transition
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.domain.repository.TextRepository
import com.videomaker.aimusic.media.audio.VolumeAudioProcessor
import com.videomaker.aimusic.domain.model.StickerPlacement
import com.videomaker.aimusic.media.effects.AnimatedStickerDecoder
import com.videomaker.aimusic.media.effects.CompositeStickerOverlay
import com.videomaker.aimusic.media.effects.DecodedSticker
import com.videomaker.aimusic.media.effects.FrameOverlayEffect
import com.videomaker.aimusic.media.effects.TextOverlayEffect
import com.videomaker.aimusic.media.effects.TransitionEffect
import com.videomaker.aimusic.media.effects.WatermarkOverlayEffect
import com.videomaker.aimusic.media.library.FrameLibrary
import com.videomaker.aimusic.media.library.TransitionSetLibrary
import com.videomaker.aimusic.media.library.TransitionShaderLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

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
    private val textRepository: TextRepository
) {

    /**
     * Data class for pre-processed image info
     */
    private data class ProcessedImage(
        val cacheUri: Uri,
        val cacheFile: File
    )

    /**
     * Tracks cache files for cleanup.
     * Thread-safe using AtomicReference for concurrent access.
     */
    private val lastCacheFiles = AtomicReference<List<File>?>(null)

    /** Decodes sticker assets (incl. animated GIF frames) for export compositing. */
    private val stickerDecoder by lazy { AnimatedStickerDecoder(context) }

    /**
     * Decoded stickers for the current composition, shared across all per-clip effects.
     * Thread-safe; recycled in [recycleBitmaps].
     */
    private val lastDecodedStickers =
        AtomicReference<List<Pair<StickerPlacement, DecodedSticker>>?>(null)

    /**
     * Recycle tracked bitmap resources.
     * Thread-safe - can be called from any thread.
     *
     * Transition bitmaps no longer need tracking — TransitionEffect loads from
     * file paths on demand and recycles immediately after GPU upload.
     *
     * Sticker bitmaps are NOT explicitly recycled here. CompositeStickerOverlay
     * reads them every frame via DecodedSticker.frameAt() for the entire export
     * duration, and Media3's Effect:DefaultV thread may still be processing frames
     * when this method is called. Explicit recycle() causes SIGABRT:
     *   "cannot access an invalid/free'd bitmap here!"
     * Instead, we clear the AtomicReference so GC collects them once Media3
     * releases its overlay references.
     */
    fun recycleBitmaps() {
        lastDecodedStickers.set(null)
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
    /**
     * @param onProgress Optional callback reporting composition build progress (0..100).
     *   Called on the IO dispatcher; safe to forward to [setProgressAsync].
     */
    suspend fun createComposition(
        project: Project,
        includeAudio: Boolean = true,
        forExport: Boolean = false,
        exportQuality: VideoQuality? = null,
        onProgress: ((Int) -> Unit)? = null
    ): Composition {
        val settings = project.settings
        val textureSize = if (forExport) getExportTextureSize(exportQuality) else PREVIEW_TEXTURE_SIZE
        val includeWatermark = shouldApplyWatermark(
            forExport = forExport,
            isWatermarkFree = project.isWatermarkFree
        )
        val fontPresets = textRepository.getFonts().getOrNull() ?: emptyList()

        // Clean up previous resources
        recycleBitmaps()
        cleanupCacheFiles()

        onProgress?.invoke(0)

        // STEP 1: Pre-process ALL images with blur background  (0→80% of composition)
        val processedImages = preProcessAllImages(project.assets, settings, textureSize) { imgPercent ->
            onProgress?.invoke(imgPercent * 80 / 100)
        }

        // VALIDATE: Ensure ALL images were processed successfully
        if (processedImages.size != project.assets.size) {
            android.util.Log.e("CompositionFactory",
                "GPU preprocessing incomplete: ${project.assets.size} assets, ${processedImages.size} processed")
            throw IllegalStateException(
                "GPU preprocessing failed: ${project.assets.size} assets, ${processedImages.size} processed"
            )
        }

        // No bulk bitmap loading — TransitionEffect loads lazily from cache file paths.
        // This reduces peak memory from ~161 MB (all bitmaps) to ~7.4 MB (2 at a time).
        onProgress?.invoke(80)

        // STEP 2: Decode sticker assets (incl. animated GIF frames) for overlay compositing.
        // Shared across all clips so each per-clip effect chain reuses the same frames.
        val decodedStickers = if (settings.stickers.isNotEmpty()) {
            stickerDecoder.decodeAll(settings.stickers)
        } else {
            emptyList()
        }
        lastDecodedStickers.set(decodedStickers)
        onProgress?.invoke(90)

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
            includeWatermark = includeWatermark,
            fontPresets = fontPresets
        )

        val sequences = mutableListOf(videoSequence)

        // Add audio sequences from audioNodes if enabled
        if (includeAudio && settings.audioNodes.isNotEmpty()) {
            val audioSequences = createMultiTrackAudioSequences(
                settings.audioNodes,
                project.totalDurationMs,
                settings.hookStartTimeMs
            )
            sequences.addAll(audioSequences)
        }

        onProgress?.invoke(100)
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
        textureSize: Int,
        onProgress: ((Int) -> Unit)? = null
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
                        onProgress?.invoke((index + 1) * 100 / assets.size)
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

    // Transition bitmaps are no longer loaded upfront — TransitionEffect loads
    // lazily from cache file paths in configure(). See TransitionEffect.kt.

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
        includeWatermark: Boolean,
        fontPresets: List<TextFontPreset>
    ): EditedMediaItemSequence {
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
            val isLast = !clip.hasTransition

            // Get transition for this clip (cycles through effect set)
            val selectedTransition = if (!clip.hasTransition) {
                null
            } else if (effectSetTransitions.isNotEmpty()) {
                effectSetTransitions[clip.transitionShaderIndex % effectSetTransitions.size]
            } else {
                null  // No transitions available - skip transition effect
            }

            // File path for current image's cache
            val fromImagePath = processedImages[imageIdx]?.cacheFile?.absolutePath

            val transition = when {
                clip.hasTransition && selectedTransition != null -> selectedTransition
                isLast && fromImagePath != null -> passthroughTransition  // Last clip: passthrough mode
                else -> null
            }

            val totalDurationMs = clip.totalDurationMs
            val transitionDurationMs = clip.transitionDurationMs
            val clipStartTimeUs = cumulativeStartTimeUs

            // Use CACHE URI instead of original asset URI
            val imageUri = processedImages[imageIdx]?.cacheUri ?: assets[imageIdx].uri

            // File path for next image (transition TO target)
            val toImagePath = if (clip.hasTransition && clipIdx + 1 < clips.size) {
                val nextImgIdx = clips[clipIdx + 1].imageIndex
                processedImages[nextImgIdx]?.cacheFile?.absolutePath
            } else if (isLast) {
                fromImagePath  // Passthrough: same as from
            } else {
                null
            }

            val editedItem = createEditedMediaItem(
                imageUri = imageUri,
                settings = settings,
                transition = transition,
                fromImagePath = fromImagePath,
                toImagePath = toImagePath,
                hasTransition = clip.hasTransition,
                isPassthroughMode = isLast,
                clipStartTimeUs = clipStartTimeUs,
                includeWatermark = includeWatermark,
                totalDurationMs = totalDurationMs,
                transitionDurationMs = transitionDurationMs,
                fontPresets = fontPresets
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
        val effectSet = TransitionSetLibrary.getById(effectSetId)
        if (effectSet == null) {
            // Effect set not found in local JSON - fall back to default transition
            android.util.Log.w("CompositionFactory", "Effect set '$effectSetId' not found, using default")
            return listOfNotNull(TransitionShaderLibrary.getDefault())
        }
        // Resolve transitions FRESH from TransitionShaderLibrary using transitionIds.
        // Don't use effectSet.transitions — it's frozen at registration time and misses
        // shaders downloaded after the EffectSet was cached in TransitionSetLibrary.
        val resolved = effectSet.transitionIds.mapNotNull {
            TransitionShaderLibrary.getById(it)
        }
        return resolved.ifEmpty {
            // No transitions resolved - fall back to default
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
        fromImagePath: String?,
        toImagePath: String?,
        hasTransition: Boolean,
        isPassthroughMode: Boolean,
        clipStartTimeUs: Long,
        includeWatermark: Boolean,
        totalDurationMs: Long,          // Beat-sync calculated duration (REQUIRED)
        transitionDurationMs: Long,     // Beat-sync calculated transition (REQUIRED)
        fontPresets: List<TextFontPreset>
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
            fromImagePath = fromImagePath,
            toImagePath = toImagePath,
            transitionDurationUs = transitionDurationUs,
            clipDurationUs = totalDurationUs,
            clipStartTimeUs = clipStartTimeUs,
            isPassthroughMode = isPassthroughMode,
            includeWatermark = includeWatermark,
            fontPresets = fontPresets
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
        fromImagePath: String?,
        toImagePath: String?,
        transitionDurationUs: Long,
        clipDurationUs: Long,
        clipStartTimeUs: Long,
        isPassthroughMode: Boolean = false,
        includeWatermark: Boolean = false,
        fontPresets: List<TextFontPreset> = emptyList()
    ): Effects {
        val aspectRatio = settings.aspectRatio.ratio
        val videoEffects = mutableListOf<Effect>()

        // NO BlurBackgroundEffect - images are already pre-processed!

        // 1. Transition effect - SINGLE SOURCE OF TRUTH
        // FROM and TO textures are lazy-loaded from GPU-cached files in configure()
        // TransitionEffect ignores Media3's input texture and uses our textures instead
        // For passthrough mode (last clip): still uses TransitionEffect to ensure consistent rendering
        if (transition != null && fromImagePath != null && toImagePath != null) {
            videoEffects.add(
                TransitionEffect(
                    transition = transition,
                    fromImagePath = fromImagePath,
                    toImagePath = toImagePath,
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

        // 3+4. Text + Sticker overlays, interleaved by shared z-order (add order). Media3
        // applies effects in order (later draws on top), so emitting one effect per z-run
        // reproduces the preview's interleaving (OverlayInterleaveLayer) in the export.
        run {
            val decodedById = lastDecodedStickers.get().orEmpty().associate { (placement, decoded) ->
                placement.instanceId to decoded
            }
            val overlayRuns = com.videomaker.aimusic.modules.editor.overlay.buildOverlayRuns(
                textOverlays = settings.textOverlays,
                stickers = settings.stickers
            )
            overlayRuns.forEach { overlayRun ->
                when (overlayRun) {
                    is com.videomaker.aimusic.modules.editor.overlay.OverlayRun.TextRun -> {
                        if (overlayRun.overlays.isNotEmpty()) {
                            videoEffects.add(
                                TextOverlayEffect(context, overlayRun.overlays, fontPresets)
                            )
                        }
                    }

                    is com.videomaker.aimusic.modules.editor.overlay.OverlayRun.StickerRun -> {
                        // Composite all stickers in this run into a single BitmapOverlay
                        // to avoid exhausting GPU texture memory (1 GL texture vs N).
                        val resolved = overlayRun.stickers.mapNotNull { placement ->
                            decodedById[placement.instanceId]?.let { decoded ->
                                placement to decoded
                            }
                        }
                        if (resolved.isNotEmpty()) {
                            videoEffects.add(
                                OverlayEffect(
                                    listOf(CompositeStickerOverlay(resolved, clipStartTimeUs))
                                )
                            )
                        }
                    }
                }
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
        totalVideoDurationMs: Long,
        hookStartTimeMs: Long = 0L
    ): List<EditedMediaItemSequence> {
        return audioNodes.mapNotNull { node ->
            val isPrimaryNode = node == audioNodes.firstOrNull()

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

                if (node.trimStartMs > 0 || node.trimEndMs != null) {
                    // Node has explicit trim range
                    clippingBuilder.setStartPositionMs(node.trimStartMs)
                    if (node.trimEndMs != null) {
                        clippingBuilder.setEndPositionMs(node.trimEndMs)
                    }
                } else if (isPrimaryNode && hookStartTimeMs > 0) {
                    // Primary node without trim: use hookStartTimeMs for beat-sync
                    clippingBuilder.setStartPositionMs(hookStartTimeMs)
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
