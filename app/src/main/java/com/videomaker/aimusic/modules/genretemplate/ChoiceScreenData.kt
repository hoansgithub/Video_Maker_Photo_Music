package com.videomaker.aimusic.modules.genretemplate

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.R

/** A Style-B choice card: centered illustration + title + subtitle. */
@Immutable
data class ChoiceItem(
    val id: String,
    @DrawableRes val imageRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val imageSize: Dp,
    val imageFillWidth: Boolean = false,
)

val CONTENT_EXCLUSIVE_ITEMS = listOf(
    ChoiceItem(
        id = "family_friendly",
        imageRes = R.drawable.img_family_friendly,
        titleRes = R.string.content_exclusive_family_title,
        subtitleRes = R.string.content_exclusive_family_subtitle,
        imageSize = 128.dp,
        imageFillWidth = true,
    ),
    ChoiceItem(
        id = "unfiltered",
        imageRes = R.drawable.img_unfiltered,
        titleRes = R.string.content_exclusive_unfiltered_title,
        subtitleRes = R.string.content_exclusive_unfiltered_subtitle,
        imageSize = 144.dp,
    ),
)

val MEDIA_PRIVACY_ITEMS = listOf(
    ChoiceItem(
        id = "private_mode",
        imageRes = R.drawable.img_private_mode,
        titleRes = R.string.media_privacy_private_title,
        subtitleRes = R.string.media_privacy_private_subtitle,
        imageSize = 116.dp,
    ),
    ChoiceItem(
        id = "quick_remake",
        imageRes = R.drawable.img_quick_remake,
        titleRes = R.string.media_privacy_quick_title,
        subtitleRes = R.string.media_privacy_quick_subtitle,
        imageSize = 91.dp,
    ),
)
