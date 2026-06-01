package com.videomaker.aimusic.core.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.constants.AdPlacement

/**
 * Fullscreen native ad shown when ready while the rewarded ad is potentially still on screen.
 *
 * Displayed immediately when loaded, as long as the rewarded ad hasn't closed yet.
 * Uses the native_full_screen_bait layout for maximum engagement.
 * Includes a close button (shown immediately, no delay).
 *
 * @param onClose Called when user taps the close button.
 */
@Composable
fun PostRewardNativeAd(
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Fullscreen native ad (already loaded, no shimmer needed)
        NativeAdView(
            placement = AdPlacement.NATIVE_POST_REWARD,
            modifier = Modifier.fillMaxSize(),
            isDebug = BuildConfig.DEBUG
        )

        // Close button (top-right corner, shown immediately)
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .size(40.dp)
                .zIndex(10f)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close_circle),
                contentDescription = "Close",
                tint = Color.White
            )
        }
    }
}
