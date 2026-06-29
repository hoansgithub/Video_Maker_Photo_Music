package com.videomaker.aimusic.modules.featureselection

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.MainActivity
import com.videomaker.aimusic.core.ads.AdClickDetector
import com.videomaker.aimusic.core.ads.AdPlacementConfigService
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.onboarding.OnboardingStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

/**
 * Personalizing screen: shows a loading spinner + native ad while we wait for
 * the interstitial to be ready. Polls for ad readiness (max 10s timeout, 200ms
 * poll interval, 1s minimum display time once ad is loaded).
 *
 * On done: shows INTERSTITIAL_ONBOARDING_COMPLETE, saves onboardingComplete = true,
 * navigates to MainActivity.
 */
class PersonalizingActivity : BaseOnboardingActivity() {

    override val onboardingStep = OnboardingStep.PERSONALIZING
    override val retentionDialogEnabled: Boolean = false

    private val preferencesManager: PreferencesManager by inject()
    private val adsLoaderService: AdsLoaderService by inject()

    override fun onSetupComplete(savedInstanceState: Bundle?) {
        Analytics.track(name = EVENT_PERSONALIZE_RENDER)

        // Start the ad polling + navigation timer immediately
        lifecycleScope.launch {
            val adShown = withTimeoutOrNull(PERSONALIZING_AD_TIMEOUT_MS) {
                while (!adsLoaderService.isNativeAdReady(AdPlacement.NATIVE_ONBOARDING_PERSONALIZING)) {
                    delay(PERSONALIZING_AD_POLL_MS)
                }
                true
            } ?: false

            // Ad became visible -> keep it on screen briefly so it registers an impression
            if (adShown) {
                delay(PERSONALIZING_AD_MIN_DISPLAY_MS)
            }

            Analytics.track(name = EVENT_PERSONALIZE_NEXT)

            preferencesManager.setOnboardingComplete(true)
            val initialTab = preferencesManager.getHomeInitialTabFromOnboarding()
            InterstitialAdHelperExt.showInterstitial(
                adsLoaderService = adsLoaderService,
                activity = this@PersonalizingActivity,
                placement = AdPlacement.INTERSTITIAL_ONBOARDING_COMPLETE,
                action = { navigateToMain(initialTab) },
                bypassFrequencyCap = true,
                showLoadingOverlay = false
            )
        }
    }

    @Composable
    override fun Content() {
        val adClickDetector: AdClickDetector = koinInject()
        val adPlacementConfigService: AdPlacementConfigService = koinInject()
        val postInterNativeAdManager = koinInject<com.videomaker.aimusic.core.ads.PostInterNativeAdManager>()
        val showPostInterNativeAd by postInterNativeAdManager.showNativeAd
            .collectAsStateWithLifecycle()

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    PersonalizingScreen()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (adPlacementConfigService.adBottomNavPaddingEnabled) Modifier.navigationBarsPadding() else Modifier)
                ) {
                    NativeAdView(
                        placement = AdPlacement.NATIVE_ONBOARDING_PERSONALIZING,
                        modifier = Modifier.fillMaxWidth(),
                        isDebug = BuildConfig.DEBUG,
                        onAdClicked = { adClickDetector.onAdClick(it) }
                    )
                }
            }

            // Post-interstitial native ad overlay (shown after onboarding-complete interstitial closes)
            if (showPostInterNativeAd) {
                postInterNativeAdManager.getActiveNativePlacement()?.let { placement ->
                    com.videomaker.aimusic.core.ads.PostInterNativeAd(
                        placement = placement,
                        onClose = postInterNativeAdManager::onNativeAdClosed
                    )
                }
            }
        }
    }

    private fun navigateToMain(initialTab: Int) {
        navigateForward(MainActivity::class.java) {
            putExtra(MainActivity.EXTRA_INITIAL_TAB, initialTab)
            putExtra(MainActivity.EXTRA_FROM_ONBOARDING, true)
        }
    }

    companion object {
        private const val PERSONALIZING_AD_TIMEOUT_MS = 10_000L
        private const val PERSONALIZING_AD_POLL_MS = 200L
        private const val PERSONALIZING_AD_MIN_DISPLAY_MS = 1_000L
    }
}
