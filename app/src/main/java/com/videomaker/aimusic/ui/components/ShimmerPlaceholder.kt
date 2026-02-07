package com.videomaker.aimusic.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.ui.theme.ShimmerDark
import com.videomaker.aimusic.ui.theme.ShimmerLight

// Shared shimmer offset via CompositionLocal - provides State to avoid recomposition
private val LocalShimmerOffset = compositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/**
 * Provides a shared shimmer animation to all children.
 * Call this once at the top level (e.g., in Theme or root screen).
 * All ShimmerBox instances will share the same animation = maximum performance.
 */
@Composable
fun ProvideShimmerEffect(
    content: @Composable () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    // Keep as State<Float> - don't delegate with 'by'
    val offsetState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_offset"
    )

    CompositionLocalProvider(LocalShimmerOffset provides offsetState) {
        content()
    }
}

/**
 * Shimmer placeholder with rounded corners.
 * Uses shared animation for performance.
 */
@Composable
fun ShimmerPlaceholder(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp
) {
    ShimmerBox(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius))
    )
}

/**
 * Performance-optimized shimmer loading placeholder.
 *
 * Best practices:
 * - Uses shared animation via CompositionLocal (single animation for all instances)
 * - Reads State.value inside drawBehind = NO recomposition during animation
 * - Colors are compile-time constants
 * - Brush created in draw scope (not recomposed)
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp
) {
    // Get State reference (not value) - no recomposition when animation changes
    val offsetState = LocalShimmerOffset.current

    val clippedModifier = if (cornerRadius > 0.dp) {
        modifier.clip(RoundedCornerShape(cornerRadius))
    } else {
        modifier
    }

    Box(
        modifier = clippedModifier.drawBehind {
            // Read value inside draw scope - animation runs without recomposition
            val offset = offsetState.value
            val brush = Brush.linearGradient(
                colors = listOf(ShimmerDark, ShimmerLight, ShimmerDark),
                start = Offset(offset - 500f, 0f),
                end = Offset(offset, size.height)
            )
            drawRect(brush)
        }
    )
}
