package com.videomaker.aimusic.modules.onboarding.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

@Composable
fun OnboardingPage2(ctaText: String, onCta: () -> Unit) {
    WelcomePage(
        imageResId = R.drawable.ob_page2,
        title = stringResource(R.string.onboarding_page2_title),
        subtitle = stringResource(R.string.onboarding_page2_subtitle),
        ctaText = ctaText,
        onCta = onCta
    )
}

@Preview(showBackground = true)
@Composable
private fun OnboardingPage2Preview() {
    VideoMakerTheme { OnboardingPage2(stringResource(R.string.onboarding_next), {}) }
}
