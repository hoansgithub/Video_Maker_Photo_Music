package com.videomaker.aimusic.domain.model

import kotlinx.serialization.Serializable

/**
 * StickerCategory - A tab in the sticker picker (e.g. "Smile", "Flower").
 *
 * Source: Supabase table `sticker_categories`.
 */
data class StickerCategory(
    val id: String,
    val name: String,
    val iconUrl: String = "",
    val thumbnailUrl: String = "",
    val isPremium: Boolean = false,
    val isNew: Boolean = false,
    val sortOrder: Int = 0
)

/**
 * Sticker - A single sticker item (static or animated WebP/GIF).
 *
 * Source: Supabase table `stickers` (joined via `category_sticker`).
 * [thumbnailUrl] is shown in the grid and used as the rendered asset.
 */
data class Sticker(
    val id: String,
    val name: String,
    val iconUrl: String = "",
    val thumbnailUrl: String = "",
    val geo: String? = null,
    val isPremium: Boolean = false,
    val isNew: Boolean = false,
    val sortOrder: Int = 0
) {
    /** 128px thumbnail — shown in the picker grid for fast loading. */
    val displayUrl: String get() = thumbnailUrl.ifEmpty { iconUrl }

    /** 512px original — downloaded and used on the video (preview + export). */
    val fullUrl: String get() = iconUrl.ifEmpty { thumbnailUrl }
}

/**
 * StickerPlacement - An instance of a sticker placed on the video.
 *
 * Persisted in [ProjectSettings.stickers] so it survives save and is read by the
 * export pipeline. Coordinates are NORMALIZED to the video rect (0f..1f), so the
 * exported MP4 matches the preview at any output resolution.
 *
 * @param instanceId Unique id for this placement (multiple instances of one sticker allowed)
 * @param stickerId Source sticker id
 * @param assetUrl Remote URL of the sticker image (animated or static)
 * @param centerXNorm Center X in video space (0f = left, 1f = right)
 * @param centerYNorm Center Y in video space (0f = top, 1f = bottom)
 * @param widthFractionOfVideo Sticker width as a fraction of the frame's SHORT side (which is
 *   the same 1080 dimension in every aspect ratio), so the rendered size stays constant across
 *   ratios. Height follows the sticker's intrinsic aspect.
 * @param rotationDeg Clockwise rotation in degrees
 * @param opacity 0f..1f
 * @param zIndex Stacking order; higher draws on top
 */
@Serializable
data class StickerPlacement(
    val instanceId: String,
    val stickerId: String,
    val assetUrl: String,
    val centerXNorm: Float = 0.5f,
    val centerYNorm: Float = 0.5f,
    val widthFractionOfVideo: Float = 1f / 3f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    val zIndex: Int = 0
)
