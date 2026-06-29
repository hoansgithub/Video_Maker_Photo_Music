package com.videomaker.aimusic.modules.onboardingsurvey

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.videomaker.aimusic.R

/** A single AI_LEVEL (creation-style) card: image + title + colored banner. */
@Immutable
data class AiLevelItem(
    val id: String,
    @DrawableRes val imageRes: Int,
    @StringRes val titleRes: Int,
    val bannerTextColor: Long,   // 0xFFRRGGBB
    val bannerBgColor: Long,     // 0xFFRRGGBB (alpha applied in the composable)
    val bannerBgAlpha: Float,
)

val AI_LEVEL_ITEMS = listOf(
    AiLevelItem(
        id = "light_touch",
        imageRes = R.drawable.img_ai_light_touch,
        titleRes = R.string.ai_level_light_touch_title,
        bannerTextColor = 0xFF58DD3A,
        bannerBgColor = 0xFF64F044,
        bannerBgAlpha = 0.08f,
    ),
    AiLevelItem(
        id = "deep_swap",
        imageRes = R.drawable.img_ai_deep_swap,
        titleRes = R.string.ai_level_deep_swap_title,
        bannerTextColor = 0xFFE52BF3,
        bannerBgColor = 0xFFEE00FF,
        bannerBgAlpha = 0.12f,
    ),
)
