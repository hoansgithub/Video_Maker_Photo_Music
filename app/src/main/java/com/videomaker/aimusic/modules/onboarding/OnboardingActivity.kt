package com.videomaker.aimusic.modules.onboarding

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.VideoMakerApplication
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.featureselection.FeatureSelectionActivity
import com.videomaker.aimusic.modules.genretemplate.GenreTemplateActivity
import com.videomaker.aimusic.modules.genretemplate.isGenreTemplateFlowAllOff
import com.videomaker.aimusic.modules.onboarding.domain.usecase.CompleteOnboardingUseCase
import com.videomaker.aimusic.ui.components.RetentionDialog
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * OnboardingActivity — First-time user onboarding flow
 *
 * Separate Activity for onboarding because:
 * - One-time flow that does not need to live in the main navigation back stack
 * - Cleaner separation: user can never "back" into it from the main app
 * - Follows single-responsibility principle
 *
 * Flow:
 * 1. WELCOME step: HorizontalPager with pages 1-3
 * 2. CompleteOnboardingUseCase marks onboarding as done
 * 3. Launch FeatureSelectionActivity and finish this Activity
 */
class OnboardingActivity : BaseOnboardingActivity() {

    override val retentionDialogEnabled: Boolean = false

    private val completeOnboardingUseCase: CompleteOnboardingUseCase by inject()
    private val preferencesManager: PreferencesManager by inject()
    private val remoteConfig: RemoteConfig by inject()
    private val onboardingViewModel: OnboardingViewModel by viewModel()
    private val onboardingContentViewModel: OnboardingContentViewModel by inject()

    override fun onSetupComplete(savedInstanceState: Bundle?) {
        // Safety net: PAGE3 + FULLSCREEN also preloaded in LanguageSelectionActivity (2-ahead).
        // This ensures ads are loaded even if user skips language selection or deep-links.
        // Idempotent — won't re-fetch if already loaded from Language step.
        //
        // Feature Selection ads will be preloaded dynamically when user reaches near-end page
        // (triggered by OnboardingScreen with primary immediate, ALT delayed 1s)
        //
        // CRITICAL: Use Application-scoped preload (NOT lifecycleScope)
        // VideoMakerApplication.preloadNativeAd() uses appScope internally,
        // which survives Activity destruction. This prevents cancellation if user
        // quickly swipes through pages.
        android.util.Log.d("OnboardingActivity", "\uD83D\uDD04 Preloading PAGE3 ad + Fullscreen ad (safety net)")
        VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_PAGE3)
        VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_FULLSCREEN)
        // Independent onboarding interstitial (only shows if enabled on Firebase)
        VideoMakerApplication.preloadInterstitial(AdPlacement.INTERSTITIAL_ONBOARDING)
    }

    @Composable
    override fun Content() {
        var showExitDialog by remember { mutableStateOf(false) }

        Surface(modifier = Modifier.fillMaxSize()) {
            OnboardingScreen(
                viewModel = onboardingViewModel,
                contentViewModel = onboardingContentViewModel,
                onExitRequested = { showExitDialog = true },
                onComplete = { completeOnboardingAndNavigate() }
            )
        }

        if (showExitDialog) {
            RetentionDialog(
                onClose = { finish() },
                onStay = { showExitDialog = false }
            )
        }
    }

    private fun completeOnboardingAndNavigate() {
        // Mark welcome pages as complete (checkpoint for resume-on-kill flow)
        preferencesManager.setOnboardingWelcomeComplete(true)
        // Don't mark onboarding complete here - it's marked at the END (Feature Selection)
        // With simplified flow, onboarding is only complete after ALL steps finish
        navigateToFeatureSelection()
    }

    private fun navigateToFeatureSelection() {
        val destination = if (remoteConfig.isGenreTemplateFlowAllOff()) {
            FeatureSelectionActivity::class.java
        } else {
            GenreTemplateActivity::class.java
        }
        navigateForward(destination)
    }
}
