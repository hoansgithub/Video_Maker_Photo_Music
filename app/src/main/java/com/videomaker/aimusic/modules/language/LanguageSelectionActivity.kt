package com.videomaker.aimusic.modules.language

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import com.videomaker.aimusic.VideoMakerApplication
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.modules.language.domain.usecase.ApplyLanguageUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.CompleteLanguageSelectionUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.SaveLanguagePreferenceUseCase
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.modules.onboarding.OnboardingActivity
import com.videomaker.aimusic.modules.onboarding.OnboardingContentViewModel
import com.videomaker.aimusic.modules.onboardingsurvey.OnboardingSurveyActivity
import com.videomaker.aimusic.modules.onboardingsurvey.OnboardingSurveyGate
import com.videomaker.aimusic.modules.onboardingsurvey.altAdPlacement
import com.videomaker.aimusic.modules.onboardingsurvey.primaryAdPlacement
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
    private val onboardingContentViewModel: OnboardingContentViewModel by inject()
    private val remoteConfig: RemoteConfig by inject()
    private val onboardingMusicPlayer: com.videomaker.aimusic.core.playback.OnboardingMusicPlayer by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Onboarding entry point: start the looping geo top-1 background song.
        onboardingMusicPlayer.start()

        // True 1-step-ahead: only preload ads for the IMMEDIATE next screen.
        // Downstream screens handle their own step-ahead preloading.
        val firstSurveyStep = OnboardingSurveyGate.enabledSteps(remoteConfig).firstOrNull()
        if (firstSurveyStep != null) {
            // Survey is next → preload first survey step's ads only
            android.util.Log.d("LanguageSelection", "🔄 1-step-ahead: preloading ${firstSurveyStep.name} ads")
            VideoMakerApplication.preloadNativeAd(firstSurveyStep.primaryAdPlacement())
            firstSurveyStep.altAdPlacement()?.let {
                VideoMakerApplication.preloadNativeAdDelayed(it, 1000L)
            }
        } else {
            // No survey → welcome pager is next, preload first two page ads
            android.util.Log.d("LanguageSelection", "🔄 1-step-ahead: preloading welcome pager page1+page2 ads")
            VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_PAGE1)
            VideoMakerApplication.preloadNativeAdDelayed(AdPlacement.NATIVE_ONBOARDING_PAGE2, 1000L)
        }

        // Pre-fetch onboarding thumbnails (data ready when OnboardingActivity opens)
        onboardingContentViewModel.preloadContent()

        setContent {
            VideoMakerTheme {
                var showExitDialog by remember { mutableStateOf(false) }

                BackHandler { showExitDialog = true }

                if (showExitDialog) {
                    com.videomaker.aimusic.ui.components.RetentionDialog(
                        onClose = { finish() },
                        onStay = { showExitDialog = false }
                    )
                }

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
                            navigateToOnboarding()
                        }
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
        // Route through the survey screens when at least one is enabled; otherwise go straight
        // to the welcome pager (avoids launching an Activity that would immediately finish).
        val target = if (OnboardingSurveyGate.isAnyEnabled(remoteConfig)) {
            OnboardingSurveyActivity::class.java
        } else {
            OnboardingActivity::class.java
        }
        startActivity(Intent(this, target))
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
