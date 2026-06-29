package com.videomaker.aimusic.modules.root

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

/**
 * LoadingScreen - Initial loading screen for app startup
 *
 * Features animated banner with:
 * - Breathing/pulse animation
 * - Fade-in effect
 * - Linear progress bar matching loading steps
 *
 * Displays while:
 * - Onboarding status is being checked
 * - Initial data is being loaded
 *
 * Navigation is handled by RootViewModel - this screen just displays loading state.
 */
@Composable
fun LoadingScreen(
    isLoading: Boolean,
    loadingStep: LoadingStep,
    modifier: Modifier = Modifier,
    initialProgress: Float = 0f
) {
    val message = when (loadingStep) {
        LoadingStep.INITIALIZING -> stringResource(R.string.root_loading_initializing)
        LoadingStep.FETCHING_CONFIG -> stringResource(R.string.root_loading_fetching_config)
        LoadingStep.LOADING_AD -> stringResource(R.string.root_loading_ad)
        LoadingStep.CHECKING_STATUS -> stringResource(R.string.root_loading_checking_status)
        LoadingStep.PREPARING -> stringResource(R.string.root_loading_preparing)
    }

    // Each step fills ~20% of the bar, slowly rising over 5 seconds.
    // When a step completes early, the bar retargets and keeps rising.
    val targetProgress = when (loadingStep) {
        LoadingStep.INITIALIZING -> 0.20f
        LoadingStep.FETCHING_CONFIG -> 0.40f
        LoadingStep.CHECKING_STATUS -> 0.60f
        LoadingStep.LOADING_AD -> 0.80f
        LoadingStep.PREPARING -> 0.95f
    }

    val progress = remember { Animatable(initialProgress) }
    LaunchedEffect(targetProgress) {
        progress.animateTo(
            targetValue = targetProgress,
            animationSpec = tween(durationMillis = 5_000, easing = LinearEasing)
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Full-bleed background image (ignores safe area)
        Image(
            painter = painterResource(id = R.drawable.splash_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Animated Banner
            AnimatedAppBanner()

            Spacer(modifier = Modifier.height(16.dp))

            // App subtitle
            Text(
                text = stringResource(R.string.splash_subtitle),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            // Loading Indicator
            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress.value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val defaultLoadingText = stringResource(R.string.loading)
                    Text(
                        text = message.ifEmpty { defaultLoadingText },
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Animated app banner with breathing/pulse effect
 */
@Composable
private fun AnimatedAppBanner() {
    val infiniteTransition = rememberInfiniteTransition(label = "banner")

    // Breathing/pulse animation
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Subtle alpha pulse (oscillates 0.85-1.0)
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Image(
        painter = painterResource(id = R.drawable.app_splash_banner),
        contentDescription = "App Banner",
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .scale(scale)
            .alpha(alpha)
    )
}

// ============================================
// PREVIEW
// ============================================

@Preview(name = "Loading Screen", showBackground = true)
@Composable
private fun LoadingScreenPreview() {
    VideoMakerTheme {
        LoadingScreen(
            isLoading = true,
            loadingStep = LoadingStep.FETCHING_CONFIG
        )
    }
}
