package com.videomaker.aimusic.modules.language

import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.language.domain.usecase.ApplyLanguageUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.CompleteLanguageSelectionUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.SaveLanguagePreferenceUseCase
import com.videomaker.aimusic.modules.onboarding.OnboardingContentViewModel
import com.videomaker.aimusic.modules.onboarding.OnboardingStep
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * LanguageSelectionActivity - First-time language selection
 *
 * This Activity is shown only for first-time users who haven't selected a language yet.
 * After selection, it navigates to the next enabled onboarding step via the coordinator.
 */
class LanguageSelectionActivity : BaseOnboardingActivity() {

    override val onboardingStep = OnboardingStep.LANGUAGE_SELECTION

    private val saveLanguagePreferenceUseCase: SaveLanguagePreferenceUseCase by inject()
    private val applyLanguageUseCase: ApplyLanguageUseCase by inject()
    private val completeLanguageSelectionUseCase: CompleteLanguageSelectionUseCase by inject()
    private val onboardingContentViewModel: OnboardingContentViewModel by inject()

    override fun onSetupComplete(savedInstanceState: Bundle?) {
        // Pre-fetch onboarding thumbnails (data ready when welcome pages open)
        onboardingContentViewModel.preloadContent()
    }

    @Composable
    override fun Content() {
        LanguageSelectionScreen(
            showBackButton = false,
            onLanguageSelected = { languageCode ->
                lifecycleScope.launch {
                    saveLanguagePreferenceUseCase(languageCode)
                }
            },
            onContinue = {
                lifecycleScope.launch {
                    completeLanguageSelectionUseCase()
                    applyLanguageUseCase()
                    navigateToNextStep()
                    applyFadeTransition()
                }
            }
        )
    }

    /**
     * Suppress locale/layoutDirection changes — we declared configChanges so the system
     * won't recreate the Activity. We're finishing it right after applying the locale anyway.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}
