package com.videomaker.aimusic.modules.root

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

/**
 * LoadingScreen - Initial loading screen for app startup
 *
 * Features animated app icon with:
 * - Breathing/pulse animation
 * - Fade-in effect
 * - Gradient shimmer background
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
    modifier: Modifier = Modifier
) {
    val message = when (loadingStep) {
        LoadingStep.INITIALIZING -> stringResource(R.string.root_loading_initializing)
        LoadingStep.FETCHING_CONFIG -> stringResource(R.string.root_loading_fetching_config)
        LoadingStep.LOADING_AD -> stringResource(R.string.root_loading_ad)
        LoadingStep.CHECKING_STATUS -> stringResource(R.string.root_loading_checking_status)
        LoadingStep.PREPARING -> stringResource(R.string.root_loading_preparing)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SplashBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Animated App Icon
            AnimatedAppIcon()

            Spacer(modifier = Modifier.height(24.dp))

            // App Name
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.weight(1f))

            // Loading Indicator
            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    CircularProgressIndicator(
                        color = Primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val defaultLoadingText = stringResource(R.string.loading)
                    Text(
                        text = message.ifEmpty { defaultLoadingText },
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Animated app icon with breathing/pulse effect
 */
@Composable
private fun AnimatedAppIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "icon")

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

    // Subtle alpha pulse (oscillates 0.85–1.0)
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
        painter = painterResource(id = R.drawable.app_icon_loading),
        contentDescription = "App Icon",
        modifier = Modifier
            .size(180.dp)
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
