package com.videomaker.aimusic.modules.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.core.ads.AdClickDetector
import com.videomaker.aimusic.core.ads.AdPlacementConfigService
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * Generic composable for onboarding steps in **ALT ad state**.
 *
 * Uses Column layout: content area gets `weight(1f)` (excludes ad),
 * ALT ad sits below in the Column flow. This matches the language screen pattern.
 *
 * Shows the ALT NativeAdView with reload support via [AdReloadState].
 * Subsequent user interactions trigger force-reloads using the "_last_only" placement.
 * Button is disabled for 500ms after the ALT screen appears (IAB viewability window).
 *
 * @param altPlacement ALT ad placement ID.
 * @param initialBottomHeight Initial bottom section height in pixels, shared with normal screen to prevent scroll jump on swap.
 * @param onBottomHeightChanged Called when the bottom section height changes, so the parent can share it with the normal screen.
 * @param content Step-specific UI receiving interaction callback, bottom padding (for CTA overlay), and button enabled state.
 */
@Composable
fun OnboardingAltScreen(
    altPlacement: String,
    initialBottomHeight: Int = 0,
    onBottomHeightChanged: (Int) -> Unit = {},
    content: @Composable (
        onUserInteraction: () -> Unit,
        bottomPadding: Dp,
        buttonEnabled: Boolean,
    ) -> Unit,
) {
    val adClickDetector: AdClickDetector = koinInject()
    val adPlacementConfigService: AdPlacementConfigService = koinInject()
    val density = LocalDensity.current

    val adReloadState = rememberAdReloadState(altPlacement)

    var bottomSectionHeight by remember { mutableStateOf(initialBottomHeight) }
    val bottomPaddingDp = with(density) { bottomSectionHeight.toDp() }

    // IAB viewability: button disabled for 500ms after ALT screen appears
    var delayedButtonEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(500L)
        delayedButtonEnabled = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Content area: weight(1f) fills remaining space above the ad.
        Box(modifier = Modifier.weight(1f)) {
            content(
                { adReloadState.onUserInteraction() },
                bottomPaddingDp,
                delayedButtonEnabled,
            )
        }

        // Ad section: in Column flow, below content area.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    if (size.height > bottomSectionHeight) {
                        bottomSectionHeight = size.height
                        onBottomHeightChanged(size.height)
                    }
                }
                .then(
                    if (adPlacementConfigService.adBottomNavPaddingEnabled) Modifier.navigationBarsPadding()
                    else Modifier
                )
        ) {
            key(adReloadState.reloadKey) {
                NativeAdView(
                    placement = adReloadState.currentPlacement,
                    modifier = Modifier.fillMaxWidth(),
                    isDebug = BuildConfig.DEBUG,
                    onAdClicked = { adClickDetector.onAdClick(it) },
                )
            }
        }
    }
}
