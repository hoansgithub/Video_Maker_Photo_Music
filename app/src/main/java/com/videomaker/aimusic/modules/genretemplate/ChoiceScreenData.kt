package com.videomaker.aimusic.modules.genretemplate

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.Primary

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

@Immutable
data class PrivacyItem(
    val id: String,
    val colorContent: Color,
    @DrawableRes val imageRes: Int,
    @DrawableRes val imageRes2: Int,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
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
    PrivacyItem(
        id = "private_mode",
        colorContent = Primary,
        imageRes = R.drawable.img_private_mode,
        imageRes2 = R.drawable.img_private_mode1,
        titleRes = R.string.media_privacy_private_title,
        subtitleRes = R.string.media_privacy_private_subtitle,
    ),
    PrivacyItem(
        id = "quick_remake",
        colorContent = Color(0xFFFF8C00),
        imageRes = R.drawable.img_quick_remake,
        imageRes2 = R.drawable.img_quick_remake1,
        titleRes = R.string.media_privacy_quick_title,
        subtitleRes = R.string.media_privacy_quick_subtitle,
    ),
)
