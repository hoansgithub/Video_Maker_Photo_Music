package com.videomaker.aimusic.modules.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
 * Generic composable for onboarding steps in **primary ad state**.
 *
 * Uses Column layout: content area gets `weight(1f)` (excludes ad),
 * ad sits below in the Column flow. This matches the language screen pattern.
 *
 * When the user interacts (via the `onUserInteraction` callback passed to [content]),
 * a swap is triggered after [swapDelayMs] and [onTriggerSwap] is called to flip
 * to the ALT screen.
 *
 * @param placement Primary ad placement ID.
 * @param onTriggerSwap Called after the swap delay completes.
 * @param initialBottomHeight Initial bottom section height in pixels, shared with ALT screen to prevent scroll jump on swap.
 * @param onBottomHeightChanged Called when the bottom section height changes, so the parent can share it with the ALT screen.
 * @param swapDelayMs Delay before triggering the swap (default 500ms).
 * @param content Step-specific UI receiving interaction callback, bottom padding (for CTA overlay), and button enabled state.
 */
@Composable
fun OnboardingNormalScreen(
    placement: String,
    onTriggerSwap: () -> Unit,
    initialBottomHeight: Int = 0,
    onBottomHeightChanged: (Int) -> Unit = {},
    swapDelayMs: Long = 500L,
    content: @Composable (
        onUserInteraction: () -> Unit,
        bottomPadding: Dp,
        buttonEnabled: Boolean,
    ) -> Unit,
) {
    val adClickDetector: AdClickDetector = koinInject()
    val adPlacementConfigService: AdPlacementConfigService = koinInject()
    val density = LocalDensity.current

    var triggered by remember { mutableStateOf(false) }
    var bottomSectionHeight by remember { mutableStateOf(initialBottomHeight) }
    val bottomPaddingDp = with(density) { bottomSectionHeight.toDp() }

    LaunchedEffect(triggered) {
        if (triggered) {
            delay(swapDelayMs)
            onTriggerSwap()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Content area: weight(1f) fills remaining space above the ad.
        // CTA overlays at BottomEnd inside content; bottomPaddingDp (ad height)
        // is passed so LazyColumn can add enough scroll padding for the CTA.
        Box(modifier = Modifier.weight(1f)) {
            content(
                { triggered = true },
                bottomPaddingDp,
                !triggered,
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
            NativeAdView(
                placement = placement,
                modifier = Modifier.fillMaxWidth(),
                isDebug = BuildConfig.DEBUG,
                onAdClicked = { adClickDetector.onAdClick(it) },
            )
        }
    }
}
