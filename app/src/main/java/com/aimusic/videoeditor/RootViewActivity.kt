package com.aimusic.videoeditor

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.di.viewModel
import com.aimusic.videoeditor.modules.language.LanguageSelectionActivity
import com.aimusic.videoeditor.modules.root.LoadingScreen
import com.aimusic.videoeditor.modules.root.RootNavigationEvent
import com.aimusic.videoeditor.modules.root.RootViewModel
import com.aimusic.videoeditor.navigation.AppRoute
import com.aimusic.videoeditor.ui.theme.VideoMakerTheme

/**
 * RootViewActivity - Entry point for the app
 *
 * This Activity handles:
 * - Apply saved locale BEFORE UI inflates
 * - Android 12+ splash screen
 * - AdMob SDK initialization (placeholder)
 * - Remote Config loading (placeholder)
 * - App Open ad presentation (placeholder)
 * - Routing to LanguageSelectionActivity or MainActivity
 *
 * Flow:
 * 1. Apply saved locale
 * 2. Show splash screen
 * 3. Initialize ads and load config (placeholders)
 * 4. Present App Open ad (placeholder)
 * 5. Check language selection status:
 *    - If NOT selected → Launch LanguageSelectionActivity
 *    - If selected → Check onboarding:
 *      - If needed → Launch MainActivity with onboarding flag
 *      - If done → Launch MainActivity (Home)
 *
 * This architecture prevents Activity recreation flicker when changing language,
 * as MainActivity is launched fresh with the correct locale already applied.
 */
class RootViewActivity : AppCompatActivity() {

    private val rootViewModel: RootViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Apply saved locale FIRST (before super.onCreate)
        applySavedLocale()

        // 2. Install splash screen BEFORE super.onCreate()
        installSplashScreen()

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // 3. Initialize app (ads, remote config, status checks)
        rootViewModel.initializeApp(this)
        rootViewModel.updateActivityRef(this)

        setContent {
            val isLoading by rootViewModel.isLoading.collectAsStateWithLifecycle()
            val loadingMessage by rootViewModel.loadingMessage.collectAsStateWithLifecycle()
            val navigationEvent by rootViewModel.navigationEvent.collectAsStateWithLifecycle()

            // Handle navigation events
            LaunchedEffect(navigationEvent) {
                navigationEvent?.let { event ->
                    when (event) {
                        is RootNavigationEvent.NavigateTo -> {
                            handleNavigation(event)
                        }
                        is RootNavigationEvent.NavigateBack -> {
                            // Not applicable for RootViewActivity
                        }
                    }
                    rootViewModel.onNavigationHandled()
                }
            }

            VideoMakerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LoadingScreen(
                        isLoading = isLoading,
                        message = loadingMessage
                    )
                }
            }
        }
    }

    private fun handleNavigation(event: RootNavigationEvent.NavigateTo) {
        when (event.route) {
            is AppRoute.LanguageSelection -> {
                // Navigate to LanguageSelectionActivity
                startActivity(Intent(this, LanguageSelectionActivity::class.java))
                applyDefaultTransition()
                finish()
            }
            is AppRoute.Onboarding -> {
                // Navigate to MainActivity with onboarding flag
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra(EXTRA_SHOW_ONBOARDING, true)
                }
                startActivity(intent)
                applyDefaultTransition()
                finish()
            }
            is AppRoute.Home -> {
                // Navigate to MainActivity directly (Home)
                startActivity(Intent(this, MainActivity::class.java))
                applyDefaultTransition()
                finish()
            }
            else -> {
                // Default: go to MainActivity
                startActivity(Intent(this, MainActivity::class.java))
                applyDefaultTransition()
                finish()
            }
        }
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

    /**
     * Apply saved locale before any UI inflation.
     *
     * Uses AppCompat per-app language API for consistency across API levels.
     * On Android 13+, this uses the native per-app language preference.
     * On Android 12 and below, AppCompat handles it via AppLocalesMetadataHolderService.
     */
    private fun applySavedLocale() {
        // Check if AppCompat already has a locale set (via autoStoreLocales=true in manifest)
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty) {
            // First run or locale not stored by AppCompat - check SharedPreferences
            val prefs = getSharedPreferences("language_prefs", MODE_PRIVATE)
            val savedLanguage = prefs.getString("selected_language", null)
            if (savedLanguage != null) {
                val localeList = LocaleListCompat.forLanguageTags(savedLanguage)
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        }
        // If locales already set by autoStoreLocales, nothing needed - AppCompat handles it
    }

    override fun onResume() {
        super.onResume()
        rootViewModel.updateActivityRef(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            rootViewModel.clearActivityRef()
        }
    }

    companion object {
        const val EXTRA_SHOW_ONBOARDING = "extra_show_onboarding"
    }
}
