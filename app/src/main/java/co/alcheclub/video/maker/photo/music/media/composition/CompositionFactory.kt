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
import co.alcheclub.video.maker.photo.music.media.library.AudioTrackLibrary

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
     */
    fun createComposition(project: Project): Composition {
        val settings = project.settings

        // Create video/image sequence
        val videoSequence = createVideoSequence(project.assets, settings)

        val sequences = mutableListOf(videoSequence)

        // Add audio sequence if music is selected
        val audioSequence = createAudioSequence(settings, project.totalDurationMs)
        if (audioSequence != null) {
            sequences.add(audioSequence)
        }

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

    /**
     * Create an EditedMediaItem from an asset (image)
     *
     * For images in Media3, we must use setImageDurationMs() on MediaItem.Builder
     * to specify how long each image should be displayed.
     */
    private fun createEditedMediaItem(
        asset: Asset,
        settings: ProjectSettings
    ): EditedMediaItem {
        // For images, use setImageDurationMs on MediaItem.Builder
        val mediaItem = MediaItem.Builder()
            .setUri(asset.uri)
            .setImageDurationMs(settings.transitionDurationMs)
            .build()

        val effects = createEffects(settings)

        return EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .build()
    }

    /**
     * Create effects for aspect ratio presentation
     *
     * Uses BlurBackgroundEffect to:
     * 1. Show a blurred, scaled-to-fill version of the image as background
     * 2. Overlay the original image scaled-to-fit on top (no cropping)
     *
     * This gives a professional look without black bars or cropping.
     */
    private fun createEffects(settings: ProjectSettings): Effects {
        val aspectRatio = settings.aspectRatio.ratio

        // Use blur background effect
        val blurEffect = BlurBackgroundEffect(aspectRatio)

        return Effects(
            /* audioProcessors= */ emptyList(),
            /* videoEffects= */ listOf(blurEffect)
        )
    }

    /**
     * Create audio sequence from bundled track or custom URI
     *
     * Loops the audio if it's shorter than the video duration
     */
    private fun createAudioSequence(
        settings: ProjectSettings,
        totalVideoDurationMs: Long
    ): EditedMediaItemSequence? {
        // Get audio URI from custom audio or bundled track
        val audioUri = getAudioUri(settings) ?: return null

        // For MVP, create a single audio item
        // TODO: Implement proper looping with clipping for last iteration
        val audioItem = MediaItem.fromUri(audioUri)

        val editedAudioItem = EditedMediaItem.Builder(audioItem)
            .setRemoveVideo(true) // Audio only
            .build()

        return EditedMediaItemSequence(listOf(editedAudioItem))
    }

    /**
     * Get audio URI from settings
     */
    private fun getAudioUri(settings: ProjectSettings): Uri? {
        // Custom audio takes precedence
        settings.customAudioUri?.let { return it }

        // Otherwise, get bundled track
        settings.audioTrackId?.let { trackId ->
            val track = AudioTrackLibrary.getById(trackId)
            if (track != null) {
                // Create asset URI for bundled audio
                return Uri.parse("file:///android_asset/${track.assetPath}")
            }
        }

        return null
    }
}
