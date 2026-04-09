package com.videomaker.aimusic.modules.onboarding.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.constants.AdPlacement
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// ============================================
// FULLSCREEN NATIVE AD STEP
// Displays a fullscreen native ad with close button
// - Configurable close button delay (0s for Meta ads, 2s default)
// - Auto-advance after 30s if ad not loaded
// - Close button: top-right circular with semi-transparent black background
// ============================================

private const val DEFAULT_CLOSE_DELAY_SECONDS = 2  // Default delay
private const val META_AD_CLOSE_DELAY_SECONDS = 0  // Meta ads must show close immediately (policy)
private const val AD_LOADING_TIMEOUT_SECONDS = 30  // Max wait time for ad to load

@Composable
fun FullscreenAdStep(
    isCurrentPage: Boolean,
    onClose: () -> Unit,
    adsLoaderService: AdsLoaderService = koinInject()
) {
    // Detect if ad is from Meta Audience Network
    val adNetworkInfo = remember {
        adsLoaderService.getNativeAdNetworkInfo(AdPlacement.NATIVE_ONBOARDING_FULLSCREEN)
    }
    val isMetaAd = remember(adNetworkInfo) {
        adNetworkInfo?.let {
            it.contains("facebook", ignoreCase = true) || it.contains("meta", ignoreCase = true)
        } == true
    }

    // Get close button delay from remote config extras
    val closeDelaySeconds = remember(isMetaAd) {
        when {
            isMetaAd -> {
                android.util.Log.d("FullscreenAdStep", "📋 Meta ad detected, close delay: 0s (policy)")
                META_AD_CLOSE_DELAY_SECONDS  // 0 seconds for Meta ads
            }
            else -> {
                val config = adsLoaderService.getPlacementConfig(AdPlacement.NATIVE_ONBOARDING_FULLSCREEN)
                val closeDelayValue = config?.extras?.get("close_delay")

                // Support both string "2" and number 2 formats
                val delay = when {
                    closeDelayValue == null -> DEFAULT_CLOSE_DELAY_SECONDS
                    else -> {
                        // Try parsing: handles both "2" (string) and 2 (number)
                        val stringValue = closeDelayValue.toString().trim('"')
                        stringValue.toIntOrNull() ?: DEFAULT_CLOSE_DELAY_SECONDS
                    }
                }

                android.util.Log.d("FullscreenAdStep", "📋 Close button delay: ${delay}s (raw value: $closeDelayValue)")
                delay
            }
        }
    }

    // Initialize close button visibility (true if delay is 0, false otherwise)
    var isCloseButtonVisible by remember { mutableStateOf(closeDelaySeconds == 0) }
    var isAdLoaded by remember { mutableStateOf(false) }

    // Track if timer has been started (survives rotation with rememberSaveable)
    var hasTimerStarted by rememberSaveable { mutableStateOf(false) }

    // Start close button delay timer ONLY when page becomes visible
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage && !hasTimerStarted) {
            hasTimerStarted = true
            android.util.Log.d("FullscreenAdStep", "📊 Page NOW VISIBLE - starting timer")
            android.util.Log.d("FullscreenAdStep", "📊 Ad served by: $adNetworkInfo")
            android.util.Log.d("FullscreenAdStep", "📋 Close button delay: ${closeDelaySeconds}s (Meta ad: $isMetaAd)")

            // Launch close button delay timer in parallel (separate coroutine)
            // Timer starts when page becomes VISIBLE, not when composed
            launch {
                if (closeDelaySeconds > 0) {
                    android.util.Log.d("FullscreenAdStep", "⏱️ Starting close button timer (${closeDelaySeconds}s delay)")
                    delay(closeDelaySeconds * 1000L)
                    if (isActive && isCurrentPage) {
                        isCloseButtonVisible = true
                        android.util.Log.d("FullscreenAdStep", "✅ Close button now visible (after ${closeDelaySeconds}s)")
                    }
                }
            }
        } else if (!isCurrentPage && hasTimerStarted) {
            // Reset timer flag when leaving page (allows timer restart on return)
            android.util.Log.d("FullscreenAdStep", "📄 Page left - resetting timer flag")
            hasTimerStarted = false
            isCloseButtonVisible = closeDelaySeconds == 0  // Reset button visibility
        } else if (!isCurrentPage) {
            android.util.Log.d("FullscreenAdStep", "📄 Page composed but NOT VISIBLE yet (pre-rendered by pager)")
        }
    }

    // Ad loading logic - runs once when composed (can start early)
    LaunchedEffect(Unit) {

        // Check ad status (runs in parallel with close button delay)
        isAdLoaded = adsLoaderService.isNativeAdReady(AdPlacement.NATIVE_ONBOARDING_FULLSCREEN)

        if (isAdLoaded) {
            android.util.Log.d("FullscreenAdStep", "✅ Ad already loaded (preload successful)")
        } else {
            android.util.Log.d("FullscreenAdStep", "⏳ Ad not ready, polling...")

            // Poll for up to 30 seconds (60 × 500ms)
            var pollAttempts = 0
            val maxPolls = AD_LOADING_TIMEOUT_SECONDS * 2

            while (pollAttempts < maxPolls && !isAdLoaded && isActive) {
                delay(500)
                pollAttempts++

                if (adsLoaderService.isNativeAdReady(AdPlacement.NATIVE_ONBOARDING_FULLSCREEN)) {
                    isAdLoaded = true
                    android.util.Log.d("FullscreenAdStep", "✅ Fullscreen ad loaded after ${pollAttempts * 0.5}s")
                }
            }

            // Auto-advance if ad failed to load within timeout (only if still on current page)
            if (!isAdLoaded && isActive && isCurrentPage) {
                android.util.Log.w("FullscreenAdStep", "⚠️ Fullscreen ad not loaded after ${AD_LOADING_TIMEOUT_SECONDS}s, auto-advancing")
                Analytics.track("fullscreen_ad_timeout")
                onClose()
            }
        }
    }

    // Track UI recomposition when button visibility changes
    LaunchedEffect(isCloseButtonVisible) {
        android.util.Log.d("FullscreenAdStep", "🎨 UI recomposed - isCloseButtonVisible: $isCloseButtonVisible")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)  // Black background for fullscreen
    ) {
        // Fullscreen native ad
        NativeAdView(
            placement = AdPlacement.NATIVE_ONBOARDING_FULLSCREEN,
            modifier = Modifier.fillMaxSize()
        )

        // Close button (top-right) - our custom overlay button
        if (isCloseButtonVisible) {
            android.util.Log.d("FullscreenAdStep", "🔘 Custom close button VISIBLE in composition")
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f)  // Semi-transparent black background
            ) {
                // Log when Surface is actually composed
                LaunchedEffect(Unit) {
                    android.util.Log.d("FullscreenAdStep", "🎯 Surface ACTUALLY RENDERED - button is now visible")
                }

                IconButton(
                    onClick = {
                        Analytics.track("fullscreen_ad_close")
                        android.util.Log.d("FullscreenAdStep", "🚪 User closed fullscreen ad")
                        onClose()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
