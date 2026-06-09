package com.videomaker.aimusic.modules.featureselection

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.MainActivity
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.LocalAsyncImage
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.modules.onboarding.OnboardingViewModel
import com.videomaker.aimusic.modules.onboarding.pages.FeatureSurveyPage
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.delay
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.lifecycle.lifecycleScope
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import kotlinx.coroutines.launch
import com.videomaker.aimusic.core.ads.AdClickDetector
import org.koin.compose.koinInject
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.core.constants.RemoteConfigKeys
import com.videomaker.aimusic.modules.genretemplate.getStepEnabled

private enum class FeatureSelectionStep { FEATURE_SELECT, PERSONALIZING }

class FeatureSelectionActivity : AppCompatActivity() {

    private val preferencesManager: PreferencesManager by inject()
    private val onboardingViewModel: OnboardingViewModel by viewModel()
    private val onboardingMusicPlayer: com.videomaker.aimusic.core.playback.OnboardingMusicPlayer by inject()
    private val adsLoaderService: AdsLoaderService by inject()
    private val remoteConfig: RemoteConfig by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Analytics.track(name = EVENT_GENRE_SHOW)

        // Idempotent safety net — keep the onboarding song looping on this last step.
        onboardingMusicPlayer.start()

        setContent {
            val adClickDetector: AdClickDetector = koinInject()
            val density = LocalDensity.current
            var isSaving by remember { mutableStateOf(false) }

            var step by remember { mutableStateOf(FeatureSelectionStep.FEATURE_SELECT) }
            val personalizingEnabled = remember {
                remoteConfig.getStepEnabled(RemoteConfigKeys.ONBOARDING_PERSONALIZING_ENABLED)
            }

            // Track bottom section height dynamically (button + ad)
            var bottomSectionHeight by remember { mutableStateOf(0) }
            val bottomPaddingDp = with(density) { bottomSectionHeight.toDp() }

            // Delayed states for ad viewability compliance (0.5-second per ad)
            // Sequential delays ensuring EACH ad gets at least 0.5 second of display time
            // Pipeline: FIRST user interaction → PRIMARY shows 0.5s → ALT shows 0.5s → Button enables
            // Total 1s delay for faster UX while maintaining ad viewability
            var delayedHasSelection by remember { mutableStateOf(false) }
            var delayedButtonEnabled by remember { mutableStateOf(false) }
            var hasStartedDelay by remember { mutableStateOf(false) }

            // Sequential delays ensuring EACH ad gets at least 0.5 second of display time
            // Timer starts on FIRST selection and does NOT reset on subsequent selections
            LaunchedEffect(hasStartedDelay) {
                if (hasStartedDelay) {
                    // Step 1: Wait 0.5s from FIRST interaction before switching to ALT ad
                    // NATIVE_ONBOARDING_FEATURE_SELECTION (PRIMARY) gets guaranteed 0.5s visibility
                    delay(500)
                    delayedHasSelection = true

                    // Step 2: Wait another 0.5s before enabling button
                    // NATIVE_ONBOARDING_FEATURE_SELECTION_ALT gets guaranteed 0.5s visibility
                    delay(500)
                    delayedButtonEnabled = true
                }
            }

            // Watch for first interaction and reset when all deselected
            LaunchedEffect(onboardingViewModel.selectedFeatures.size) {
                if (onboardingViewModel.selectedFeatures.isNotEmpty() && !hasStartedDelay) {
                    // First interaction - start the timer (only happens once)
                    hasStartedDelay = true
                    android.util.Log.d("FeatureSelection", "🎬 Started IAB viewability timer")
                } else if (onboardingViewModel.selectedFeatures.isEmpty() && hasStartedDelay) {
                    // User deselected all - reset everything
                    hasStartedDelay = false
                    delayedHasSelection = false
                    delayedButtonEnabled = false
                    android.util.Log.d("FeatureSelection", "🔄 Reset IAB viewability timer")
                }
            }

            VideoMakerTheme {
                var showExitDialog by remember { mutableStateOf(false) }

                BackHandler { showExitDialog = true }

                if (showExitDialog) {
                    com.videomaker.aimusic.modules.onboarding.OnboardingExitDialog(
                        onExit = { finish() },
                        onDismiss = { showExitDialog = false }
                    )
                }

                if (step == FeatureSelectionStep.PERSONALIZING) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1A1A1A))
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            PersonalizingScreen()
                        }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            NativeAdView(
                                placement = AdPlacement.NATIVE_ONBOARDING_PERSONALIZING,
                                modifier = Modifier.fillMaxWidth(),
                                isDebug = BuildConfig.DEBUG,
                                onAdClicked = { adClickDetector.onAdClick(it) }
                            )
                        }
                    }
                    return@VideoMakerTheme
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A1A))
                ) {
                    // Scrollable content with dynamic bottom padding
                    Box(modifier = Modifier.weight(1f)) {
                        FeatureSurveyPage(
                            selectedFeatures = onboardingViewModel.selectedFeatures,
                            onFeatureToggle = { selectedFeature ->
                                onboardingViewModel.toggleFeature(selectedFeature)
                                onboardingViewModel.selectedFeatures.firstOrNull()?.let { genre ->
                                    Analytics.track(
                                        name = EVENT_GENRE_SELECT,
                                        params = mapOf(PARAM_GENRE_SELECT to if (genre == "music_video_instant") "music" else "photo")
                                    )
                                }
                            },
                            bottomPaddingDp = bottomPaddingDp  // Pass dynamic padding
                        )
                        // Button at top right (outside measured section)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomEnd)
                                .then(
                                    if (bottomSectionHeight == 0) Modifier.navigationBarsPadding()
                                    else Modifier
                                )
                                .clickableSingle{}
                        ) {
                            LocalAsyncImage(
                                resId = R.drawable.img_bg_cta_onboard,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(top = 10.dp, bottom = 12.dp)
                            ) {
                                OnboardingCtaButton(
                                    text = stringResource(R.string.onboarding_get_started),
                                    onClick = {
                                        if (isSaving) return@OnboardingCtaButton
                                        isSaving = true
                                        Analytics.track(
                                            name = EVENT_GENRE_NEXT,
                                            params = mapOf(
                                                PARAM_GENRE_SELECT to toGenreAnalyticsValue(
                                                    onboardingViewModel.selectedFeatures
                                                )
                                            )
                                        )
                                        onboardingViewModel.saveFeatures { result ->
                                            runOnUiThread {
                                                result.onSuccess {
                                                    val selectedFeature =
                                                        onboardingViewModel.selectedFeatures.firstOrNull()
                                                    val initialTab =
                                                        mapFeatureToInitialTab(selectedFeature)
                                                    preferencesManager.setHomeInitialTabFromOnboarding(
                                                        initialTab
                                                    )

                                                    if (personalizingEnabled) {
                                                        // Show the PERSONALIZING screen, then complete after a
                                                        // 2.5s min-duration. The timer runs in lifecycleScope so
                                                        // it is decoupled from recomposition.
                                                        step = FeatureSelectionStep.PERSONALIZING
                                                        lifecycleScope.launch {
                                                            delay(2500)
                                                            preferencesManager.setOnboardingComplete(true)
                                                            InterstitialAdHelperExt.showInterstitial(
                                                                adsLoaderService = adsLoaderService,
                                                                activity = this@FeatureSelectionActivity,
                                                                placement = AdPlacement.INTERSTITIAL_ONBOARDING_COMPLETE,
                                                                action = { navigateToMain(initialTab) },
                                                                bypassFrequencyCap = true,
                                                                showLoadingOverlay = false
                                                            )
                                                        }
                                                    } else {
                                                        // No personalizing → mark complete and navigate now.
                                                        lifecycleScope.launch {
                                                            preferencesManager.setOnboardingComplete(true)
                                                            InterstitialAdHelperExt.showInterstitial(
                                                                adsLoaderService = adsLoaderService,
                                                                activity = this@FeatureSelectionActivity,
                                                                placement = AdPlacement.INTERSTITIAL_ONBOARDING_COMPLETE,
                                                                action = { navigateToMain(initialTab) },
                                                                bypassFrequencyCap = true,
                                                                showLoadingOverlay = false
                                                            )
                                                        }
                                                    }
                                                }.onFailure {
                                                    isSaving = false
                                                    Toast.makeText(
                                                        this@FeatureSelectionActivity,
                                                        getString(R.string.root_try_again),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = onboardingViewModel.selectedFeatures.isNotEmpty() && delayedButtonEnabled && !isSaving,
                                    color = Primary,
                                    icon = R.drawable.ic_checkmark
                                )
                            }
                        }
                    }

                    // Bottom section: Native ad only (measures its own height)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                bottomSectionHeight =
                                    size.height  // Measure actual height dynamically!
                            }
                    ) {
                        if (delayedHasSelection) {
                            // ALT ad - shown after user selects a feature
                            NativeAdView(
                                placement = AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION_ALT,
                                modifier = Modifier.fillMaxWidth(),
                                isDebug = BuildConfig.DEBUG,
                                onAdClicked = { adClickDetector.onAdClick(it) }
                            )
                        } else {
                            // PRIMARY ad - shown before user selects
                            NativeAdView(
                                placement = AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION,
                                modifier = Modifier.fillMaxWidth(),
                                isDebug = BuildConfig.DEBUG,
                                onAdClicked = { adClickDetector.onAdClick(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun navigateToMain(initialTab: Int) {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_INITIAL_TAB, initialTab)
            .putExtra(MainActivity.EXTRA_FROM_ONBOARDING, true)
        startActivity(intent)
        finish()
    }
}
