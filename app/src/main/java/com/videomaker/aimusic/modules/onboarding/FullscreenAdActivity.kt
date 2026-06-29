package com.videomaker.aimusic.modules.onboarding

import androidx.compose.runtime.Composable
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.onboarding.pages.FullscreenAdStep

/**
 * Standalone Activity for the fullscreen native ad step (NATIVE_ONBOARDING_FULLSCREEN).
 *
 * Gated by the ad placement's `enabled` flag on Firebase (not a Remote Config key).
 * Positioned between WELCOME_PAGE_2 and WELCOME_PAGE_3 in the onboarding flow.
 *
 * Wraps [FullscreenAdStep] which handles:
 * - Fullscreen native ad display with shimmer loading
 * - Configurable close-button delay (0s for Meta ads, 2s default)
 * - Auto-advance after 30s timeout if ad fails to load
 * - Music pause/resume during ad display
 *
 * When the user closes this ad, [navigateToNextStep] fires. The coordinator
 * may also show INTERSTITIAL_ONBOARDING as a transition ad before proceeding.
 */
class FullscreenAdActivity : BaseOnboardingActivity() {

    override val onboardingStep = OnboardingStep.FULLSCREEN_AD
    override val retentionDialogEnabled = false

    @Composable
    override fun Content() {
        FullscreenAdStep(
            isCurrentPage = true,
            onClose = { navigateToNextStep() }
        )
    }
}
