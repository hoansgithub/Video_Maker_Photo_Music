package com.videomaker.aimusic.modules.onboardingsurvey

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.OnboardingSurveyBackground
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

@Composable
fun AiPromoteScreen(
    modifier: Modifier = Modifier,
    bottomContainerTopPx: Float = 0f
) {
    LaunchedEffect(Unit) {
        com.videomaker.aimusic.core.analytics.Analytics.track(OnboardingSurveyAnalytics.EVENT_AI_PROMOTE_RENDER)
    }

    var totalHeightPx by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingSurveyBackground)
    ) {
        // Rounded card container holding the background collage and overlay card
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { totalHeightPx = it.height }
        ) {
            // 1. Background Collage Image
            Image(
                painter = painterResource(R.drawable.ai_promote_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E1E)
@Composable
private fun AiPromoteScreenPreview() {
    VideoMakerTheme {
        AiPromoteScreen()
    }
}
