package com.videomaker.aimusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

// Per-bar config: min fraction, max fraction, animation duration range (ms)
private data class BarConfig(
    val minFraction: Float,
    val maxFraction: Float,
    val durationRange: IntRange,
    val startDelay: Long
)

private val BAR_CONFIGS = listOf(
    BarConfig(minFraction = 0.20f, maxFraction = 0.80f, durationRange = 250..450, startDelay = 0L),
    BarConfig(minFraction = 0.35f, maxFraction = 1.00f, durationRange = 200..380, startDelay = 80L),
    BarConfig(minFraction = 0.15f, maxFraction = 0.70f, durationRange = 300..500, startDelay = 160L)
)

/**
 * Animated music playing indicator — 3 vertical bars bouncing at different speeds.
 *
 * @param isPlaying When false the bars freeze at their current height.
 * @param color     Fill color for all bars (defaults to brand Primary lime).
 * @param barWidth  Width of each bar.
 * @param height    Total height of the indicator.
 * @param barSpacing Gap between bars.
 * @param modifier  Standard Compose modifier.
 */
@Composable
fun SongPlayingIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Primary,
    barWidth: Dp = 3.dp,
    height: Dp = 16.dp,
    barSpacing: Dp = 2.dp
) {
    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(barSpacing),
        verticalAlignment = Alignment.Bottom
    ) {
        BAR_CONFIGS.forEachIndexed { index, config ->
            val fraction = remember(index) { Animatable(config.minFraction) }

            LaunchedEffect(isPlaying) {
                if (isPlaying) {
                    delay(config.startDelay)
                    while (isActive) {
                        val target = Random.nextFloat() *
                            (config.maxFraction - config.minFraction) + config.minFraction
                        val duration = config.durationRange.random()
                        fraction.animateTo(
                            targetValue = target,
                            animationSpec = tween(
                                durationMillis = duration,
                                easing = EaseInOutSine
                            )
                        )
                    }
                } else {
                    // Smoothly shrink to resting height when paused
                    fraction.animateTo(
                        targetValue = config.minFraction,
                        animationSpec = tween(durationMillis = 300, easing = EaseInOutSine)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(fraction.value)
                    .background(
                        color = color,
                        shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                    )
            )
        }
    }
}

// ============================================
// PREVIEW
// ============================================

@Preview(name = "Playing", widthDp = 60, backgroundColor = 0xFF101313, showBackground = true)
@Composable
private fun SongPlayingIndicatorPlayingPreview() {
    VideoMakerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SongPlayingIndicator(
                isPlaying = true,
                modifier = Modifier
            )
        }
    }
}

@Preview(name = "Paused", widthDp = 60, backgroundColor = 0xFF101313, showBackground = true)
@Composable
private fun SongPlayingIndicatorPausedPreview() {
    VideoMakerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SongPlayingIndicator(
                isPlaying = false,
                modifier = Modifier
            )
        }
    }
}
