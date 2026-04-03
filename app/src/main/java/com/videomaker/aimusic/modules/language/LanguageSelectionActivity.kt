package com.videomaker.aimusic.modules.language

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.videomaker.aimusic.MainActivity
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.data.local.LanguageManager
import com.videomaker.aimusic.modules.language.domain.usecase.ApplyLanguageUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.CompleteLanguageSelectionUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.SaveLanguagePreferenceUseCase
import com.videomaker.aimusic.modules.onboarding.OnboardingViewModel
import com.videomaker.aimusic.modules.onboarding.pages.FeatureSurveyPage
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

/**
 * LanguageSelectionActivity - First-time language selection
 *
 * This Activity is shown only for first-time users who haven't selected a language yet.
 * After selection, it navigates to MainActivity and finishes.
 *
 * Flow:
 * 1. User sees language options grid
 * 2. User taps a language → preference saved (no recreation, just UI selection state)
 * 3. User taps Continue:
 *    a. Mark selection as complete
 *    b. Apply locale via AppCompatDelegate.setApplicationLocales()
 *    c. Navigate to OnboardingActivity → starts fresh with the new locale auto-applied
 *    d. This Activity finishes
 *
 * Why a separate Activity?
 * - Language is applied BEFORE MainActivity loads
 * - No visible recreation/flicker in MainActivity
 * - Clean separation of first-time setup from main app flow
 * - User cannot go back to language selection accidentally
 */
class LanguageSelectionActivity : AppCompatActivity() {

    private val saveLanguagePreferenceUseCase: SaveLanguagePreferenceUseCase by inject()
    private val applyLanguageUseCase: ApplyLanguageUseCase by inject()
    private val completeLanguageSelectionUseCase: CompleteLanguageSelectionUseCase by inject()
    private val languageManager: LanguageManager by inject()
    private val onboardingViewModel: OnboardingViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Initialise from the persisted flag so the genre step survives process death
            var showGenreSelection: Boolean by rememberSaveable {
                mutableStateOf(languageManager.isGenreSelectionPending())
            }

            VideoMakerTheme {
                if (!showGenreSelection) {
                    LanguageSelectionScreen(
                        showBackButton = false,
                        onLanguageSelected = { languageCode ->
                            saveLanguagePreferenceUseCase(languageCode)
                        },
                        onContinue = {
                            completeLanguageSelectionUseCase()
                            applyLanguageUseCase()
                            languageManager.markGenreSelectionPending()
                            showGenreSelection = true
                        }
                    )
                } else {
                    // Genre selection step — shown after language is chosen
                    Box(modifier = Modifier.fillMaxSize()) {
                        FeatureSurveyPage(
                            selectedFeatures = onboardingViewModel.selectedFeatures,
                            onFeatureToggle = onboardingViewModel::toggleFeature
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(horizontal = 24.dp, vertical = 48.dp)
                        ) {
                            OnboardingCtaButton(
                                text = stringResource(R.string.onboarding_get_started),
                                icon = R.drawable.ic_checkmark,
                                color = Primary,
                                onClick = {
                                    onboardingViewModel.saveFeatures(onSaved = {
                                        languageManager.clearGenreSelectionPending()
                                        navigateToMain()
                                    })
                                },
                                enabled = onboardingViewModel.selectedFeatures.isNotEmpty()
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Suppress locale/layoutDirection changes — we declared configChanges so the system
     * won't recreate the Activity. We're finishing it right after applying the locale anyway.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // No-op: Activity is finishing immediately after locale is applied
    }

    /**
     * After language selection, launch MainActivity.
     * Language is already applied so MainActivity starts with the correct locale.
     * Onboarding has already been completed before reaching this screen.
     */
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        applyDefaultTransition()
        finish()
    }

    private fun applyDefaultTransition() {
        if (Build.VERSION.SDK_INT >= 34) {  // Android 14+
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
