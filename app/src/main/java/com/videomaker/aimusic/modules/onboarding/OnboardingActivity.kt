package com.videomaker.aimusic.modules.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.videomaker.aimusic.VideoMakerApplication
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.modules.featureselection.FeatureSelectionActivity
import com.videomaker.aimusic.modules.onboarding.domain.usecase.CompleteOnboardingUseCase
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.launch

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
class OnboardingActivity : AppCompatActivity() {

    private val completeOnboardingUseCase: CompleteOnboardingUseCase by inject()
    private val onboardingViewModel: OnboardingViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Preload PAGE3 ad + next step (Feature Selection)
        // PAGE1 and PAGE2 were already preloaded in LanguageSelectionActivity (1-step-ahead)
        //
        // CRITICAL: Use Application-scoped preload (NOT lifecycleScope)
        // VideoMakerApplication.preloadNativeAd() uses appScope internally,
        // which survives Activity destruction. This prevents cancellation if user
        // quickly swipes through pages.
        android.util.Log.d("OnboardingActivity", "🔄 Preloading PAGE3 ad")
        VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_PAGE3)

        // Also preload Feature Selection ads (next step)
        android.util.Log.d("OnboardingActivity", "🔄 Preloading Feature Selection ads (1-step-ahead)")
        VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION)
        VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION_ALT)

        setContent {
            VideoMakerTheme {
                var showExitDialog by remember { mutableStateOf(false) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    OnboardingScreen(
                        viewModel = onboardingViewModel,
                        onExitRequested = { showExitDialog = true },
                        onComplete = { completeOnboardingAndNavigate() }
                    )
                }

                if (showExitDialog) {
                    OnboardingExitDialog(
                        onExit = { finish() },
                        onDismiss = { showExitDialog = false }
                    )
                }
            }
        }
    }

    private fun completeOnboardingAndNavigate() {
        // Don't mark onboarding complete here - it's marked at the END (Feature Selection)
        // With simplified flow, onboarding is only complete after ALL steps finish
        navigateToFeatureSelection()
    }

    private fun navigateToFeatureSelection() {
        startActivity(Intent(this, FeatureSelectionActivity::class.java))
        finish()
    }
}
