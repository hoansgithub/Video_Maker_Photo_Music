package co.alcheclub.video.maker.photo.music.media.composition

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import co.alcheclub.video.maker.photo.music.domain.model.Asset
import co.alcheclub.video.maker.photo.music.domain.model.Project
import co.alcheclub.video.maker.photo.music.domain.model.ProjectSettings
import co.alcheclub.video.maker.photo.music.media.effects.BlurBackgroundEffect
import co.alcheclub.video.maker.photo.music.media.effects.FrameOverlayEffect
import co.alcheclub.video.maker.photo.music.media.library.AudioTrackLibrary
import co.alcheclub.video.maker.photo.music.media.library.FrameLibrary
import androidx.media3.common.Effect

/**
 * CompositionFactory - Creates Media3 Composition from Project domain model
 *
 * Builds the composition with:
 * - Image sequence with configured duration
 * - Aspect ratio presentation effect
 * - Background music (looped if needed)
 */
class CompositionFactory(private val context: Context) {

    /**
     * Create a Media3 Composition from a Project
     *
     * @param project The project to create composition from
     * @param includeAudio Whether to include audio track (false for preview, true for export)
     */
    fun createComposition(project: Project, includeAudio: Boolean = true): Composition {
        val settings = project.settings
        android.util.Log.d("CompositionFactory", "createComposition: ${project.assets.size} assets, includeAudio=$includeAudio")

        // Create video/image sequence
        android.util.Log.d("CompositionFactory", "Creating video sequence...")
        val videoSequence = createVideoSequence(project.assets, settings)
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
     * Create the video/image sequence from assets
     */
    private fun createVideoSequence(
        assets: List<Asset>,
        settings: ProjectSettings
    ): EditedMediaItemSequence {
        val editedItems = assets.map { asset ->
            createEditedMediaItem(asset, settings)
        }
        return EditedMediaItemSequence(editedItems)
    }

    companion object {
        // Default frame rate for image sequences (30 fps is standard)
        private const val DEFAULT_FRAME_RATE = 30
    }

    /**
     * Create an EditedMediaItem from an asset (image)
     *
     * For images in Media3 Transformer, we MUST set:
     * - setImageDurationMs() on MediaItem.Builder - how long to display
     * - setFrameRate() on EditedMediaItem.Builder - required for Transformer export
     * - setDurationUs() on EditedMediaItem.Builder - explicit duration for ImageAssetLoader
     *
     * See: ImageAssetLoader line 115 checks both durationUs and frameRate
     */
    private fun createEditedMediaItem(
        asset: Asset,
        settings: ProjectSettings
    ): EditedMediaItem {
        val durationMs = settings.transitionDurationMs
        val durationUs = durationMs * 1000L

        // For images, use setImageDurationMs on MediaItem.Builder
        val mediaItem = MediaItem.Builder()
            .setUri(asset.uri)
            .setImageDurationMs(durationMs)
            .build()

        val effects = createEffects(settings)

        return EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .setDurationUs(durationUs)     // Required for Transformer ImageAssetLoader
            .setFrameRate(DEFAULT_FRAME_RATE) // Required for Transformer ImageAssetLoader
            .build()
    }

    /**
     * Create effects for aspect ratio presentation and optional frame overlay
     *
     * Uses BlurBackgroundEffect to:
     * 1. Show a blurred, scaled-to-fill version of the image as background
     * 2. Overlay the original image scaled-to-fit on top (no cropping)
     *
     * Optionally adds FrameOverlayEffect on top if a frame is selected.
     */
    private fun createEffects(settings: ProjectSettings): Effects {
        val aspectRatio = settings.aspectRatio.ratio
        val videoEffects = mutableListOf<Effect>()

        // Base effect: blur background with fit-inside content
        videoEffects.add(BlurBackgroundEffect(aspectRatio))

        // Optional: overlay frame on top (scale-to-fill)
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

        return EditedMediaItemSequence(listOf(editedAudioItem))
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
