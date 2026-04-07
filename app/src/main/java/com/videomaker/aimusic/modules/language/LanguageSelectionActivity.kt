package com.videomaker.aimusic.modules.language

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import com.videomaker.aimusic.modules.language.domain.usecase.ApplyLanguageUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.CompleteLanguageSelectionUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.SaveLanguagePreferenceUseCase
import com.videomaker.aimusic.modules.onboarding.OnboardingActivity
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

/**
 * LanguageSelectionActivity - First-time language selection
 *
 * This Activity is shown only for first-time users who haven't selected a language yet.
 * After selection, it navigates to OnboardingActivity and finishes.
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
 * - Language is applied before entering the rest of setup flow
 * - No visible recreation/flicker when moving to next setup step
 * - Clean separation of first-time setup from main app flow
 * - User cannot go back to language selection accidentally
 */
class LanguageSelectionActivity : AppCompatActivity() {

    private val saveLanguagePreferenceUseCase: SaveLanguagePreferenceUseCase by inject()
    private val applyLanguageUseCase: ApplyLanguageUseCase by inject()
    private val completeLanguageSelectionUseCase: CompleteLanguageSelectionUseCase by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VideoMakerTheme {
                LanguageSelectionScreen(
                    showBackButton = false,
                    onLanguageSelected = { languageCode ->
                        lifecycleScope.launch {
                            saveLanguagePreferenceUseCase(languageCode)
                        }
                    },
                    onContinue = {
                        completeLanguageSelectionUseCase()
                        applyLanguageUseCase()
                        navigateToOnboarding()
                    }
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
        // No-op: Activity is finishing immediately after locale is applied
    }

    /**
     * After language selection, launch OnboardingActivity.
     */
    private fun navigateToOnboarding() {
        startActivity(Intent(this, OnboardingActivity::class.java))
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
