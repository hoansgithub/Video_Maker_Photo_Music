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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.Black40
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import com.videomaker.aimusic.ui.theme.WarmGradient

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
    // Hardcoded splash screen text
    val appName = "Muvio - AI Music\nVideo Maker & Editor"

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

            // App Name with decoration - auto-scaled to fit two lines
            AutoSizeText(
                text = appName,  // Real app name from manifest
                maxFontSize = 32.sp,
                minFontSize = 16.sp,
                maxLines = 2,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(
                        colors = WarmGradient  // Orange to pink gradient
                    ),
                    shadow = Shadow(
                        color = Black40,  // 40% black shadow
                        offset = Offset(3f, 3f),
                        blurRadius = 6f
                    ),
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 24.dp)
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
 * Auto-sizing text that scales font size to fit available width
 * Uses remember(text) to reset font size when text changes
 */
@Composable
private fun AutoSizeText(
    text: String,
    maxFontSize: androidx.compose.ui.unit.TextUnit,
    minFontSize: androidx.compose.ui.unit.TextUnit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    BoxWithConstraints(modifier = modifier) {
        var fontSize by remember(text) { mutableStateOf(maxFontSize) }
        var readyToDraw by remember(text) { mutableStateOf(false) }

        Text(
            text = text,
            style = style.copy(fontSize = fontSize),
            maxLines = maxLines,
            softWrap = true,
            modifier = Modifier.alpha(if (readyToDraw) 1f else 0f),
            onTextLayout = { textLayoutResult ->
                if (textLayoutResult.hasVisualOverflow && fontSize > minFontSize) {
                    // Reduce font size by 10% until it fits
                    val newSize = (fontSize.value * 0.9f).sp
                    fontSize = if (newSize >= minFontSize) newSize else minFontSize
                } else {
                    // Text fits or reached minimum - ready to show
                    readyToDraw = true
                }
            }
        )
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
