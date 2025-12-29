package co.alcheclub.video.maker.photo.music.domain.model

/**
 * TransitionSet - A curated collection of transition animations
 *
 * Each set contains multiple transitions (20+) that share a visual theme.
 * The system randomly applies transitions from the selected set between images.
 *
 * @param id Unique identifier
 * @param name Display name
 * @param description Brief description of the set's style
 * @param thumbnailRes Drawable resource for preview thumbnail
 * @param isPremium Whether this set requires premium access
 */
data class TransitionSet(
    val id: String,
    val name: String,
    val description: String,
    val thumbnailRes: Int,
    val isPremium: Boolean = false
)

/**
 * TransitionCategory - Categories of transition effects
 */
enum class TransitionCategory {
    SIMPLE_2D,      // Fade, Dissolve, CrossFade
    DIRECTIONAL_2D, // Wipe, Slide, Push
    GEOMETRIC_2D,   // Circle, Diamond, Heart, Star
    EFFECT_3D,      // Cube, Page Curl, Flip, Fold
    ARTISTIC        // Pixelize, Morph, Glitch, Burn
}
