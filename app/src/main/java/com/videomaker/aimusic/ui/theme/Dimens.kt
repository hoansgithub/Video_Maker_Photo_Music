package com.videomaker.aimusic.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Scalable dimensions based on screen width.
 * Base design width: 375dp (iPhone standard)
 * Fonts and spacing scale proportionally to screen size.
 */
private const val BASE_SCREEN_WIDTH = 375f

/**
 * Dimension provider for scalable UI.
 */
data class Dimensions(
    val scaleFactor: Float,

    // Font sizes (scaled)
    val fontXs: TextUnit,    // 10sp base
    val fontSm: TextUnit,    // 12sp base
    val fontMd: TextUnit,    // 14sp base
    val fontLg: TextUnit,    // 16sp base
    val fontXl: TextUnit,    // 18sp base
    val fontXxl: TextUnit,   // 20sp base
    val font2Xl: TextUnit,   // 24sp base
    val font3Xl: TextUnit,   // 28sp base
    val font4Xl: TextUnit,   // 32sp base

    // Spacing (scaled)
    val spaceXxs: Dp,  // 2dp base
    val spaceXs: Dp,   // 4dp base
    val spaceSm: Dp,   // 8dp base
    val spaceMd: Dp,   // 12dp base
    val spaceLg: Dp,   // 16dp base
    val spaceXl: Dp,   // 20dp base
    val spaceXxl: Dp,  // 24dp base
    val space2Xl: Dp,  // 32dp base
    val space3Xl: Dp,  // 48dp base

    // Border radius (scaled)
    val radiusSm: Dp,  // 4dp base
    val radiusMd: Dp,  // 8dp base
    val radiusLg: Dp,  // 12dp base
    val radiusXl: Dp,  // 16dp base
    val radiusFull: Dp // 999dp (circle)
)

/**
 * CompositionLocal for accessing scalable dimensions.
 */
val LocalDimens = compositionLocalOf { defaultDimensions() }

/**
 * Default dimensions (1x scale factor).
 */
private fun defaultDimensions() = createDimensions(1f)

/**
 * Create dimensions with given scale factor.
 */
private fun createDimensions(scaleFactor: Float): Dimensions {
    // Clamp scale factor to reasonable range (0.85x - 1.4x)
    val clampedScale = scaleFactor.coerceIn(0.85f, 1.4f)

    return Dimensions(
        scaleFactor = clampedScale,

        // Font sizes
        fontXs = (10f * clampedScale).sp,
        fontSm = (12f * clampedScale).sp,
        fontMd = (14f * clampedScale).sp,
        fontLg = (16f * clampedScale).sp,
        fontXl = (18f * clampedScale).sp,
        fontXxl = (20f * clampedScale).sp,
        font2Xl = (24f * clampedScale).sp,
        font3Xl = (28f * clampedScale).sp,
        font4Xl = (32f * clampedScale).sp,

        // Spacing
        spaceXxs = (2f * clampedScale).dp,
        spaceXs = (4f * clampedScale).dp,
        spaceSm = (8f * clampedScale).dp,
        spaceMd = (12f * clampedScale).dp,
        spaceLg = (16f * clampedScale).dp,
        spaceXl = (20f * clampedScale).dp,
        spaceXxl = (24f * clampedScale).dp,
        space2Xl = (32f * clampedScale).dp,
        space3Xl = (48f * clampedScale).dp,

        // Border radius
        radiusSm = (4f * clampedScale).dp,
        radiusMd = (8f * clampedScale).dp,
        radiusLg = (12f * clampedScale).dp,
        radiusXl = (16f * clampedScale).dp,
        radiusFull = 999.dp
    )
}

/**
 * Calculate scale factor based on screen width.
 */
@Composable
fun rememberDimensions(): Dimensions {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat()

    return remember(screenWidthDp) {
        val scaleFactor = screenWidthDp / BASE_SCREEN_WIDTH
        createDimensions(scaleFactor)
    }
}

/**
 * Provider for scalable dimensions.
 * Wrap your content with this to enable scaled dimensions.
 */
@Composable
fun ProvideDimens(content: @Composable () -> Unit) {
    val dimensions = rememberDimensions()
    CompositionLocalProvider(LocalDimens provides dimensions) {
        content()
    }
}

/**
 * Quick access to current dimensions.
 */
object AppDimens {
    val current: Dimensions
        @Composable
        get() = LocalDimens.current
}
