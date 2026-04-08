package com.videomaker.aimusic

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.videomaker.aimusic.modules.featureselection.FeatureSelectionActivity
import com.videomaker.aimusic.modules.language.LanguageSelectionActivity
import com.videomaker.aimusic.modules.onboarding.OnboardingActivity
import com.videomaker.aimusic.modules.root.LoadingScreen
import com.videomaker.aimusic.modules.root.LoadingStep
import com.videomaker.aimusic.modules.root.RootNavigationEvent
import com.videomaker.aimusic.modules.root.RootViewModel
import com.videomaker.aimusic.navigation.AppRoute
import com.videomaker.aimusic.ui.theme.FoundationBlack
import com.videomaker.aimusic.ui.theme.FoundationBlack_100
import com.videomaker.aimusic.ui.theme.PlayerCardBackground
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

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
 * 5. Resolve startup gate in order:
 *    - LanguageSelectionActivity
 *    - OnboardingActivity
 *    - FeatureSelectionActivity
 *    - MainActivity
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

        // 3. Initialize app (UMP consent, ads, remote config, status checks)
        // CRITICAL: Pass Activity for UMP consent form
        rootViewModel.initializeApp(this)

        setContent {
            Analytics.trackScreenView(
                screenName = "splash_show",
                screenClass = "LoadingScreen"
            )
            val isLoading by rootViewModel.isLoading.collectAsStateWithLifecycle()
            val loadingStep by rootViewModel.loadingStep.collectAsStateWithLifecycle()
            val navigationEvent by rootViewModel.navigationEvent.collectAsStateWithLifecycle()
            val showNoInternetDialog by rootViewModel.showNoInternetDialog.collectAsStateWithLifecycle()

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
                        loadingStep = loadingStep
                    )

                    if (showNoInternetDialog) {
                        Dialog(
                            {}
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PlayerCardBackground, RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.img_error_server),
                                    contentDescription = "",
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f),
                                    contentScale = ContentScale.FillWidth
                                )

                                Text(
                                    text = stringResource(R.string.root_no_internet_title),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.W700,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = stringResource(R.string.root_no_internet_message),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.W500,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(top = 12.dp, bottom = 24.dp)
                                )

                                TextButton(
                                    onClick = { rootViewModel.retryInitialization() },
                                    modifier = Modifier
                                        .background(Primary,RoundedCornerShape(160.dp))
                                        .padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.root_try_again),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = FoundationBlack
                                    )
                                }
                            }
                        }
                    }
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
                // Navigate to OnboardingActivity (separate one-time flow)
                startActivity(Intent(this, OnboardingActivity::class.java))
                applyDefaultTransition()
                finish()
            }
            is AppRoute.FeatureSelection -> {
                startActivity(Intent(this, FeatureSelectionActivity::class.java))
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
        if (Build.VERSION.SDK_INT >= 34) {  // Android 14+
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
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

}
