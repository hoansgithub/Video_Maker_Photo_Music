package com.videomaker.aimusic.modules.language

import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.lifecycleScope
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.language.domain.usecase.ApplyLanguageUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.CompleteLanguageSelectionUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.SaveLanguagePreferenceUseCase
import com.videomaker.aimusic.modules.onboarding.OnboardingAltScreen
import com.videomaker.aimusic.modules.onboarding.OnboardingContentViewModel
import com.videomaker.aimusic.modules.onboarding.OnboardingNormalScreen
import com.videomaker.aimusic.modules.onboarding.OnboardingStep
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * LanguageSelectionActivity - First-time language selection
 *
 * This Activity is shown only for first-time users who haven't selected a language yet.
 * After selection, it navigates to the next enabled onboarding step via the coordinator.
 *
 * Uses the centralized dual screen ad swap pattern (OnboardingNormalScreen / OnboardingAltScreen)
 * via BaseOnboardingActivity for IAB viewability compliance.
 */
class LanguageSelectionActivity : BaseOnboardingActivity() {

    override val onboardingStep = OnboardingStep.LANGUAGE_SELECTION

    private val saveLanguagePreferenceUseCase: SaveLanguagePreferenceUseCase by inject()
    private val applyLanguageUseCase: ApplyLanguageUseCase by inject()
    private val completeLanguageSelectionUseCase: CompleteLanguageSelectionUseCase by inject()
    private val onboardingContentViewModel: OnboardingContentViewModel by inject()

    private var sharedBottomHeight by mutableStateOf(0)
    private var selectedLanguageState by mutableStateOf<String?>(null)

    override fun onSetupComplete(savedInstanceState: Bundle?) {
        // Pre-fetch onboarding thumbnails (data ready when welcome pages open)
        onboardingContentViewModel.preloadContent()
    }

    @Composable
    override fun Content() {
        val placements = coordinator.adPlacements(onboardingStep!!)

        val stepContent: @Composable (
            onUserInteraction: () -> Unit,
            bottomPadding: Dp,
            buttonEnabled: Boolean,
        ) -> Unit = { onUserInteraction, bottomPadding, buttonEnabled ->
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
                },
                onUserInteraction = onUserInteraction,
                bottomPaddingDp = bottomPadding,
                externalButtonEnabled = buttonEnabled,
                externalSelectedLanguage = selectedLanguageState,
                onSelectedLanguageChanged = { selectedLanguageState = it },
            )
        }

        Box(Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
            if (showAlt && placements.size > 1) {
                OnboardingAltScreen(
                    altPlacement = placements[1],
                    initialBottomHeight = sharedBottomHeight,
                    onBottomHeightChanged = { sharedBottomHeight = it },
                    content = stepContent,
                )
            } else {
                OnboardingNormalScreen(
                    placement = placements.firstOrNull().orEmpty(),
                    onTriggerSwap = ::triggerAltSwap,
                    initialBottomHeight = sharedBottomHeight,
                    onBottomHeightChanged = { sharedBottomHeight = it },
                    content = stepContent,
                )
            }
        }
    }

    /**
     * Suppress locale/layoutDirection changes — we declared configChanges so the system
     * won't recreate the Activity. We're finishing it right after applying the locale anyway.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}
