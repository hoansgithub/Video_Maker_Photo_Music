package com.videomaker.aimusic.modules.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.VideoMakerApplication
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.featureselection.FeatureSelectionActivity
import com.videomaker.aimusic.modules.genretemplate.GenreTemplateActivity
import com.videomaker.aimusic.modules.genretemplate.isGenreTemplateFlowAllOff
import com.videomaker.aimusic.modules.onboardingsurvey.OnboardingSurveyActivity
import com.videomaker.aimusic.modules.onboardingsurvey.OnboardingSurveyGate
import com.videomaker.aimusic.modules.root.OnboardingResumeStep
import com.videomaker.aimusic.modules.welcomeback.WelcomeBackScreen
import org.koin.android.ext.android.inject

/**
 * Welcome-back screen for users who killed the app during onboarding.
 *
 * Shows the WelcomeBackScreen composable with the onboarding-specific ad placement,
 * then resumes at the correct onboarding step when the user taps Continue.
 */
class OnboardingWelcomeBackActivity : BaseOnboardingActivity() {

    override val retentionDialogEnabled: Boolean = false

    private val preferencesManager: PreferencesManager by inject()
    private val remoteConfig: RemoteConfig by inject()

    @Composable
    override fun Content() {
        Surface(modifier = Modifier.fillMaxSize()) {
            WelcomeBackScreen(
                onContinue = { navigateToResumePoint() },
                adPlacement = AdPlacement.NATIVE_ONBOARDING_WELCOME_BACK
            )
        }
    }

    private fun navigateToResumePoint() {
        val step = preferencesManager.getOnboardingResumeStep(
            languageSelectionComplete = true // Must be true to have reached this screen
        )

        // Preload ads for the next screen (1-step-ahead pattern)
        when (step) {
            OnboardingResumeStep.WELCOME_PAGES -> {
                VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_PAGE3)
                VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_FULLSCREEN)
            }
            OnboardingResumeStep.FEATURE_SELECTION -> {
                VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION)
            }
            null -> Unit
        }

        val target = when (step) {
            OnboardingResumeStep.WELCOME_PAGES -> {
                if (OnboardingSurveyGate.isAnyEnabled(remoteConfig))
                    OnboardingSurveyActivity::class.java
                else OnboardingActivity::class.java
            }
            OnboardingResumeStep.FEATURE_SELECTION -> {
                if (remoteConfig.isGenreTemplateFlowAllOff())
                    FeatureSelectionActivity::class.java
                else GenreTemplateActivity::class.java
            }
            null -> OnboardingActivity::class.java  // Fallback
        }
        navigateForward(target)
    }
}
