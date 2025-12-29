package co.alcheclub.video.maker.photo.music.domain.model

/**
 * OverlayFrame - Decorative frame overlay for videos
 *
 * Rendered as the top layer over the video content.
 *
 * @param id Unique identifier
 * @param name Display name
 * @param drawableRes Drawable resource ID (R.drawable.frame1, etc.)
 * @param isPremium Whether this frame requires premium access
 */
data class OverlayFrame(
    val id: String,
    val name: String,
    val drawableRes: Int,
    val isPremium: Boolean = false
)
