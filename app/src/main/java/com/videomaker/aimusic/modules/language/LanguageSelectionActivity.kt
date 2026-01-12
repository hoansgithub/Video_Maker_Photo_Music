package com.videomaker.aimusic.modules.language

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import co.alcheclub.lib.acccore.di.ACCDI
import co.alcheclub.lib.acccore.di.get
import com.videomaker.aimusic.MainActivity
import com.videomaker.aimusic.RootViewActivity
import com.videomaker.aimusic.core.data.local.LanguageManager
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
 * 2. User taps a language (saves preference for preview, no recreation)
 * 3. UI updates to show localized strings in selected language (preview mode)
 * 4. User taps Continue
 * 5. Language preference is marked as complete
 * 6. Language is applied (may trigger recreation, but we navigate away immediately)
 * 7. Navigate to MainActivity with CLEAR_TASK flag (clears entire Activity stack)
 * 8. This Activity finishes
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
        val languageManager = ACCDI.get<LanguageManager>()
        val getSelectedLanguageUseCase = ACCDI.get<GetSelectedLanguageUseCase>()
        val saveLanguagePreferenceUseCase = ACCDI.get<SaveLanguagePreferenceUseCase>()
        val applyLanguageUseCase = ACCDI.get<ApplyLanguageUseCase>()
        val completeLanguageSelectionUseCase = ACCDI.get<CompleteLanguageSelectionUseCase>()

        val currentLanguage = getSelectedLanguageUseCase()

        setContent {
            VideoMakerTheme {
                LanguageSelectionScreen(
                    currentLanguage = currentLanguage,
                    showBackButton = false, // No back button for first-time selection
                    onLanguageSelected = { languageCode ->
                        // Save preference only (for preview) - does NOT trigger Activity recreation
                        saveLanguagePreferenceUseCase(languageCode)
                    },
                    onContinue = {
                        // 1. Mark language selection as complete
                        completeLanguageSelectionUseCase()

                        // 2. Apply language - this sets the locale
                        // MainActivity will start fresh with correct locale
                        applyLanguageUseCase()

                        // 3. Navigate to MainActivity
                        navigateToMain()
                    },
                    getLocalizedString = { resId, languageCode ->
                        // Provide live preview of strings in selected language
                        languageManager.getLocalizedString(resId, languageCode)
                    }
                )
            }
        }
    }

    /**
     * Navigate to MainActivity with onboarding flag.
     *
     * After language selection, user should see onboarding flow.
     * MainActivity will start fresh with correct locale already applied.
     */
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(RootViewActivity.EXTRA_SHOW_ONBOARDING, true)
        }
        startActivity(intent)
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
