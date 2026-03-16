package com.videomaker.aimusic.modules.language

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import co.alcheclub.lib.acccore.di.ACCDI
import co.alcheclub.lib.acccore.di.get
import com.videomaker.aimusic.modules.onboarding.OnboardingActivity
import com.videomaker.aimusic.modules.language.domain.usecase.ApplyLanguageUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.CompleteLanguageSelectionUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.GetSelectedLanguageUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.SaveLanguagePreferenceUseCase
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Get dependencies from ACCDI
        val getSelectedLanguageUseCase = ACCDI.get<GetSelectedLanguageUseCase>()
        val saveLanguagePreferenceUseCase = ACCDI.get<SaveLanguagePreferenceUseCase>()
        val applyLanguageUseCase = ACCDI.get<ApplyLanguageUseCase>()
        val completeLanguageSelectionUseCase = ACCDI.get<CompleteLanguageSelectionUseCase>()

        val currentLanguage = getSelectedLanguageUseCase()

        setContent {
            VideoMakerTheme {
                LanguageSelectionScreen(
                    currentLanguage = currentLanguage,
                    showBackButton = false,
                    onLanguageSelected = { languageCode ->
                        // Save preference only — no Activity recreation while browsing
                        saveLanguagePreferenceUseCase(languageCode)
                    },
                    onContinue = {
                        // 1. Mark complete
                        completeLanguageSelectionUseCase()
                        // 2. Apply locale — OnboardingActivity will start with the new locale
                        applyLanguageUseCase()
                        // 3. Navigate
                        navigateToMain()
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
     * Language is already applied so OnboardingActivity starts with the correct locale.
     */
    private fun navigateToMain() {
        startActivity(Intent(this, OnboardingActivity::class.java))
        applyDefaultTransition()
        finish()
    }

    private fun applyDefaultTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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
