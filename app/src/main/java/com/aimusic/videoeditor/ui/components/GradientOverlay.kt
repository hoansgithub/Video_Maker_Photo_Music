package com.aimusic.videoeditor.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Common gradient color presets for overlay effects
 */
object GradientColors {
    /**
     * Bottom gradient for text readability on images
     * Transparent at top, dark at bottom
     */
    val BottomFade = listOf(
        Color.Transparent,
        Color.Transparent,
        Color.Black.copy(alpha = 0.7f)
    )

    /**
     * Full vertical gradient from top to bottom
     */
    val FullVertical = listOf(
        Color.Black.copy(alpha = 0.3f),
        Color.Transparent,
        Color.Black.copy(alpha = 0.7f)
    )

    /**
     * Subtle scrim for overlay content
     */
    val Scrim = listOf(
        Color.Black.copy(alpha = 0.5f),
        Color.Black.copy(alpha = 0.5f)
    )
}

/**
 * Modifier extension for bottom gradient overlay
 *
 * Uses drawWithCache for optimal performance (gradient is cached between recompositions)
 *
 * @param colors Gradient colors (default: transparent to dark bottom)
 */
fun Modifier.bottomGradientOverlay(
    colors: List<Color> = GradientColors.BottomFade
): Modifier = this.drawWithCache {
    val brush = Brush.verticalGradient(
        colors = colors,
        startY = 0f,
        endY = size.height
    )
    onDrawBehind {
        drawRect(brush)
    }
}

/**
 * Modifier extension for top gradient overlay
 *
 * @param colors Gradient colors (default: dark top to transparent)
 */
fun Modifier.topGradientOverlay(
    colors: List<Color> = listOf(
        Color.Black.copy(alpha = 0.7f),
        Color.Transparent,
        Color.Transparent
    )
): Modifier = this.drawWithCache {
    val brush = Brush.verticalGradient(
        colors = colors,
        startY = 0f,
        endY = size.height
    )
    onDrawBehind {
        drawRect(brush)
    }
}

/**
 * Modifier extension for custom vertical gradient overlay
 *
 * @param colors Custom gradient colors
 * @param startY Start Y position (default 0f = top)
 * @param endY End Y position (default = size.height = bottom)
 */
fun Modifier.verticalGradientOverlay(
    colors: List<Color>,
    startY: Float = 0f,
    endY: Float? = null
): Modifier = this.drawWithCache {
    val brush = Brush.verticalGradient(
        colors = colors,
        startY = startY,
        endY = endY ?: size.height
    )
    onDrawBehind {
        drawRect(brush)
    }
}
