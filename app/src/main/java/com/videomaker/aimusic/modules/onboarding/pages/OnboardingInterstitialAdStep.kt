package com.videomaker.aimusic.modules.onboarding.pages

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.VideoMakerApplication
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.ui.theme.SurfaceDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.concurrent.atomic.AtomicBoolean

// ============================================
// ONBOARDING INTERSTITIAL AD STEP
// Independent of the native fullscreen ad (NATIVE_ONBOARDING_FULLSCREEN).
// Injected as its own onboarding page only when INTERSTITIAL_ONBOARDING is enabled.
//
// Shows the (preloaded) fullscreen-image interstitial each time the page becomes
// current, then advances the pager once the ad closes/fails. Re-arms when the page
// is left, so swiping back shows the ad again (a fresh one is preloaded after each
// show, since interstitials are single-use). Never gets stuck on the spinner.
// ============================================

private const val AD_LOADING_TIMEOUT_MS = 6_000L  // Max wait if ad not ready
private const val FALLBACK_BUFFER_MS = 1_500L     // Extra buffer after timeout

@Composable
fun OnboardingInterstitialAdStep(
    isCurrentPage: Boolean,
    onClose: () -> Unit,
    adsLoaderService: AdsLoaderService = koinInject()
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Guard so the interstitial is presented at most once per visit. Reset when the
    // page is left, so swiping back to this page presents the ad again.
    var presentedThisVisit by remember { mutableStateOf(false) }

    // Neutral background shown behind the AdMob interstitial overlay.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            // Re-arm so the next time the user lands here the ad shows again.
            presentedThisVisit = false
            return@LaunchedEffect
        }
        if (presentedThisVisit) return@LaunchedEffect
        presentedThisVisit = true

        if (activity == null) {
            android.util.Log.w("OnboardingInterAd", "Activity unavailable - advancing")
            onClose()
            return@LaunchedEffect
        }

        // Advance the pager exactly once per visit (whether ad shows, closes, or fails).
        val advanced = AtomicBoolean(false)
        fun advanceOnce() {
            if (advanced.compareAndSet(false, true)) onClose()
        }

        val ready = runCatching {
            adsLoaderService.isInterstitialReady(AdPlacement.INTERSTITIAL_ONBOARDING)
        }.getOrDefault(false)
        android.util.Log.d("OnboardingInterAd", "Page current - interstitial ready=$ready, showing...")

        InterstitialAdHelperExt.showInterstitial(
            adsLoaderService = adsLoaderService,
            activity = activity,
            placement = AdPlacement.INTERSTITIAL_ONBOARDING,
            action = { advanceOnce() },          // called when ad closes OR fails to show
            onShown = {
                // Interstitials are single-use: immediately preload a fresh one so a
                // later swipe-back to this page can present the ad again.
                VideoMakerApplication.preloadInterstitial(AdPlacement.INTERSTITIAL_ONBOARDING)
            },
            bypassFrequencyCap = true,           // always show at the onboarding ad step
            loadTimeoutMillis = AD_LOADING_TIMEOUT_MS,
            showLoadingOverlay = false           // we render our own neutral background
        )

        // WORKAROUND for ACCCore bug: InterstitialAdHelper does NOT call the action
        // callback on load timeout (see RootViewModel splash handling). Without this the
        // page would spin forever. Force-advance if action wasn't called in time.
        launch {
            delay(AD_LOADING_TIMEOUT_MS + FALLBACK_BUFFER_MS)
            if (!advanced.get()) {
                android.util.Log.w("OnboardingInterAd", "Ad callback not called within timeout - advancing")
                advanceOnce()
            }
        }
    }
}
