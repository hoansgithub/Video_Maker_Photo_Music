package com.videomaker.aimusic.modules.onboarding

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.onboarding.pages.WelcomePageDynamic
import org.koin.android.ext.android.inject

class WelcomePage1Activity : BaseOnboardingActivity() {

    override val onboardingStep = OnboardingStep.WELCOME_PAGE_1

    private val contentViewModel: OnboardingContentViewModel by inject()

    override fun onSetupComplete(savedInstanceState: Bundle?) {
        Analytics.track(
            name = "onboarding_1",
            params = mapOf("onboarding_screen" to "ob1")
        )
    }

    @Composable
    override fun Content() {
        val contentState by contentViewModel.contentState.collectAsStateWithLifecycle()

        WelcomePageDynamic(
            thumbnailUrl = contentState.page1ThumbnailUrl,
            nameSong = contentState.nameSong,
            nameArtist = contentState.nameArtist,
            videoUrl = contentState.page1VideoUrl,
            localFallbackResId = contentState.page1LocalFallback ?: R.drawable.ob_page1,
            title = stringResource(R.string.onboarding_page1_title),
            subtitle = stringResource(R.string.onboarding_india_page1_subtitle),
            ctaText = stringResource(R.string.onboarding_next),
            onCta = {
                Analytics.track(name = "onboarding_1_next", params = emptyMap())
                navigateToNextStep()
            },
            pageIndex = 0
        )
    }
}
