package com.videomaker.aimusic.modules.onboarding.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

@Composable
fun OnboardingPage3(ctaText: String, onCta: () -> Unit) {
    WelcomePage(
        imageResId = R.drawable.ob_page3,
        title = stringResource(R.string.onboarding_page3_title),
        subtitle = stringResource(R.string.onboarding_page3_subtitle),
        ctaText = ctaText,
        onCta = onCta
    )
}

@Preview(showBackground = true)
@Composable
private fun OnboardingPage3Preview() {
    VideoMakerTheme { OnboardingPage3(stringResource(R.string.onboarding_get_started), {}) }
}
