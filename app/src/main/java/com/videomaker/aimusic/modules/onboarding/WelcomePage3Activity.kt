package com.videomaker.aimusic.modules.onboarding

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.onboarding.pages.DynamicCarousel
import org.koin.android.ext.android.inject

class WelcomePage3Activity : BaseOnboardingActivity() {

    override val onboardingStep = OnboardingStep.WELCOME_PAGE_3

    private val contentViewModel: OnboardingContentViewModel by inject()
    private val preferencesManager: PreferencesManager by inject()

    override fun onSetupComplete(savedInstanceState: Bundle?) {
        Analytics.track(
            name = "onboarding_3",
            params = mapOf("onboarding_screen" to "ob3")
        )
    }

    @Composable
    override fun Content() {
        val contentState by contentViewModel.contentState.collectAsStateWithLifecycle()

        DynamicCarousel(
            thumbnailUrls = contentState.page3Thumbnails,
            localFallbackResIds = contentState.page3LocalFallbacks,
            title = stringResource(R.string.onboarding_india_page3_title),
            subtitle = stringResource(R.string.onboarding_india_page3_subtitle),
            ctaText = stringResource(R.string.onboarding_next),
            onCta = {
                Analytics.track(name = "onboarding_3_next", params = emptyMap())
                preferencesManager.setOnboardingWelcomeComplete(true)
                navigateToNextStep()
            },
            pageIndex = 2
        )
    }
}
