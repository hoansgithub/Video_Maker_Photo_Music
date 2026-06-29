package com.videomaker.aimusic.modules.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.welcomeback.WelcomeBackScreen

/**
 * Welcome-back screen for users who killed the app during onboarding.
 *
 * Shows the WelcomeBackScreen composable with the onboarding-specific ad placement,
 * then resumes at the correct onboarding step via the coordinator.
 */
class OnboardingWelcomeBackActivity : BaseOnboardingActivity() {

    override val retentionDialogEnabled: Boolean = false

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
        val resumeStep = coordinator.resumeStep()
        if (resumeStep != null) {
            // Preload ads for the resume step so they're ready when it renders
            coordinator.preloadAdsForStep(resumeStep)
            navigateForward(coordinator.activityClass(resumeStep))
        } else {
            // Fallback: restart from the first enabled step after language
            val firstStep = coordinator.enabledSteps().firstOrNull {
                it != OnboardingStep.LANGUAGE_SELECTION
            }
            if (firstStep != null) {
                navigateForward(coordinator.activityClass(firstStep))
            }
        }
    }
}
