package com.videomaker.aimusic

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import co.alcheclub.lib.acccore.di.ACCDI
import co.alcheclub.lib.acccore.di.get
import com.videomaker.aimusic.modules.onboarding.domain.usecase.CompleteOnboardingUseCase
import com.videomaker.aimusic.navigation.AppNavigation
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.launch

/**
 * MainActivity - Main App Content
 *
 * Hosts the main app navigation including:
 * - Onboarding flow (first-time users after language selection)
 * - Home screen
 * - All feature screens (Editor, Preview, Export, etc.)
 *
 * IMPORTANT: Extends AppCompatActivity (not ComponentActivity) to support
 * AppCompatDelegate.setApplicationLocales() for per-app language changes.
 *
 * This Activity is launched by:
 * - RootViewActivity (after loading/ads) with optional EXTRA_SHOW_ONBOARDING
 * - LanguageSelectionActivity (after language selection) with EXTRA_SHOW_ONBOARDING=true
 *
 * The language is already set BEFORE this Activity starts, so there's no
 * Activity recreation flicker when changing language.
 *
 * Flow:
 * 1. Check if EXTRA_SHOW_ONBOARDING is true → start at Onboarding route
 * 2. Otherwise → start at Home route
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Check if we should show onboarding
        val showOnboarding = intent.getBooleanExtra(RootViewActivity.EXTRA_SHOW_ONBOARDING, false)

        // Get CompleteOnboardingUseCase from ACCDI
        val completeOnboardingUseCase = ACCDI.get<CompleteOnboardingUseCase>()

        setContent {
            // Use coroutine scope for async operations (avoids blocking main thread)
            val coroutineScope = rememberCoroutineScope()

            VideoMakerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        startWithOnboarding = showOnboarding,
                        onOnboardingComplete = {
                            // Mark onboarding as complete in preferences asynchronously
                            coroutineScope.launch {
                                completeOnboardingUseCase()
                            }
                        }
                    )
                }
            }
        }
    }
}
