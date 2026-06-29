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

class WelcomePage2Activity : BaseOnboardingActivity() {

    override val onboardingStep = OnboardingStep.WELCOME_PAGE_2

    private val contentViewModel: OnboardingContentViewModel by inject()

    override fun onSetupComplete(savedInstanceState: Bundle?) {
        Analytics.track(
            name = "onboarding_2",
            params = mapOf("onboarding_screen" to "ob2")
        )
    }

    @Composable
    override fun Content() {
        val contentState by contentViewModel.contentState.collectAsStateWithLifecycle()

        WelcomePageDynamic(
            thumbnailUrl = contentState.page2ThumbnailUrl,
            nameSong = contentState.nameSong,
            nameArtist = contentState.nameArtist,
            localFallbackResId = contentState.page2LocalFallback ?: R.drawable.ob_page2,
            title = stringResource(R.string.onboarding_page2_title),
            subtitle = stringResource(R.string.onboarding_page2_subtitle),
            ctaText = stringResource(R.string.onboarding_next),
            onCta = {
                Analytics.track(name = "onboarding_2_next", params = emptyMap())
                navigateToNextStep()
            },
            pageIndex = 1
        )
    }
}
