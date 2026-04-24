package com.videomaker.aimusic.modules.onboarding.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.ui.components.ProvideShimmerEffect
import com.videomaker.aimusic.ui.components.ShimmerBox
import com.videomaker.aimusic.ui.theme.SurfaceDark
import com.videomaker.aimusic.ui.theme.TextSecondary
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.BuildConfig
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
        android.util.Log.d("FullscreenAdStep", "🎬 Ad loading LaunchedEffect started")

        // Check ad status (runs in parallel with close button delay)
        isAdLoaded = adsLoaderService.isNativeAdReady(AdPlacement.NATIVE_ONBOARDING_FULLSCREEN)

        if (isAdLoaded) {
            android.util.Log.d("FullscreenAdStep", "✅ Ad already loaded (preload successful)")
        } else {
            android.util.Log.d("FullscreenAdStep", "⏳ Ad not ready, starting polling (timeout: ${AD_LOADING_TIMEOUT_SECONDS}s)...")

            // Poll for up to 30 seconds (60 × 500ms)
            var pollAttempts = 0
            val maxPolls = AD_LOADING_TIMEOUT_SECONDS * 2

            while (pollAttempts < maxPolls && !isAdLoaded && isActive) {
                delay(500)
                pollAttempts++

                // Log every 5 seconds
                if (pollAttempts % 10 == 0) {
                    android.util.Log.d("FullscreenAdStep", "⏳ Still polling... ${pollAttempts * 0.5}s elapsed")
                }

                if (adsLoaderService.isNativeAdReady(AdPlacement.NATIVE_ONBOARDING_FULLSCREEN)) {
                    isAdLoaded = true
                    android.util.Log.d("FullscreenAdStep", "✅ Fullscreen ad loaded after ${pollAttempts * 0.5}s")
                }
            }

            // Auto-advance if ad failed to load within timeout (only if still on current page)
            if (!isAdLoaded && isActive && isCurrentPage) {
                android.util.Log.w("FullscreenAdStep", "⚠️ Fullscreen ad not loaded after ${AD_LOADING_TIMEOUT_SECONDS}s, auto-advancing")
                android.util.Log.w("FullscreenAdStep", "⚠️ Ad network info: $adNetworkInfo")
                Analytics.track("fullscreen_ad_timeout")
                onClose()
            } else if (!isAdLoaded && !isActive) {
                android.util.Log.w("FullscreenAdStep", "⚠️ LaunchedEffect cancelled before ad loaded")
            } else if (!isAdLoaded && !isCurrentPage) {
                android.util.Log.w("FullscreenAdStep", "⚠️ User navigated away before ad loaded")
            }
        }
    }

    // Track UI recomposition when button visibility changes
    LaunchedEffect(isCloseButtonVisible) {
        android.util.Log.d("FullscreenAdStep", "🎨 UI recomposed - isCloseButtonVisible: $isCloseButtonVisible")
    }

    // Track when ad loaded state changes
    LaunchedEffect(isAdLoaded) {
        android.util.Log.d("FullscreenAdStep", "📊 Ad loaded state changed: isAdLoaded=$isAdLoaded")
        if (isAdLoaded) {
            android.util.Log.d("FullscreenAdStep", "✅ Ad marked as loaded - shimmer should hide, NativeAdView should show")

            // DEBUG: Check if ad is actually retrievable
            val ad = adsLoaderService.getNativeAd(AdPlacement.NATIVE_ONBOARDING_FULLSCREEN)
            android.util.Log.d("FullscreenAdStep", "🔍 getNativeAd() returned: ${if (ad != null) "AD OBJECT" else "NULL"}")
        } else {
            android.util.Log.d("FullscreenAdStep", "⏳ Ad not loaded yet - showing shimmer")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fullscreen native ad (rendered first, at bottom of Z-order)
        NativeAdView(
            placement = AdPlacement.NATIVE_ONBOARDING_FULLSCREEN,
            modifier = Modifier.fillMaxSize(),
            isDebug = BuildConfig.DEBUG,  // Show debug label only in debug build
        )

        // Shimmer loading overlay (rendered second, covers ad until loaded)
        if (!isAdLoaded) {
            FullscreenAdShimmerLayout()
        }

        // Close button (top-right) - our custom overlay button (rendered last, always on top)
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

/**
 * Shimmer loading layout for fullscreen ad
 * Mimics native_full_screen_bait.xml structure
 * Shows while native ad is loading (based on drama app pattern)
 */
@Composable
private fun FullscreenAdShimmerLayout() {
    ProvideShimmerEffect {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceDark)
        ) {
            // Media view shimmer (fullscreen background)
            ShimmerBox(
                modifier = Modifier.fillMaxSize()
            )

            // Centered loading message
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Animated shimmer layer
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )

                    // Text overlay with reduced brightness
                    Text(
                        text = "Loading ad...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Top overlay card (headline + body)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 72.dp, start = 16.dp, end = 16.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                // Headline shimmer (2 lines)
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(24.dp),
                    cornerRadius = 4.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(24.dp),
                    cornerRadius = 4.dp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Body text shimmer (3 lines)
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    cornerRadius = 4.dp
                )
                Spacer(modifier = Modifier.height(6.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(16.dp),
                    cornerRadius = 4.dp
                )
                Spacer(modifier = Modifier.height(6.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(16.dp),
                    cornerRadius = 4.dp
                )
            }

            // Bottom CTA button shimmer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(48.dp),
                    cornerRadius = 24.dp
                )
            }
        }
    }
}
