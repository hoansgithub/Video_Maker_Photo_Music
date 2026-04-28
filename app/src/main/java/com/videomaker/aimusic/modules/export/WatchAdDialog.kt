package com.videomaker.aimusic.modules.export

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.FoundationBlack
import com.videomaker.aimusic.ui.theme.Neutral_Black
import com.videomaker.aimusic.ui.theme.Neutral_N500
import com.videomaker.aimusic.ui.theme.Neutral_N700
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

/**
 * WatchAdDialog - Dialog asking user to watch an ad for a reward
 *
 * Follows patterns from android-short-drama-app RewardedInterstitialIntroDialog:
 * - [AD] label badge for visual engagement
 * - Clear title and subtitle (customizable)
 * - Two buttons: "Close" (secondary) and "Watch Ad" with icon (primary, wider)
 * - Prevents tap-to-dismiss - forces explicit user choice
 *
 * @param title Dialog title text (e.g., "Watch an ad to download your video?")
 * @param subtitle Dialog subtitle text (e.g., "Support us by watching a short ad")
 * @param onDismiss Callback when user taps "Close" button
 * @param onWatchAd Callback when user taps "Watch Ad" button
 */
@Composable
fun WatchAdDialog(
    type: String,
    onDismiss: () -> Unit,
    onWatchAd: () -> Unit
) {
    LaunchedEffect(Unit) {
        Analytics.trackRewardPopupRender(type)
    }

    Dialog(
        onDismissRequest = { /* Prevent tap-to-dismiss */ }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ){
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Neutral_Black,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // [AD] label badge
                Image(
                    painter = painterResource(R.drawable.img_ad_content_popup),
                    contentDescription = null,
                    modifier = Modifier.size(140.dp)
                )

                Spacer(modifier = Modifier.height(30.dp))

                // Title
                Text(
                    text = stringResource(R.string.watch_ad_dialog_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Subtitle
                Text(
                    text = stringResource(R.string.watch_ad_dialog_subtitle),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Buttons row - Close + Watch Ad
                Row(
                    modifier = Modifier
                        .background(Primary, RoundedCornerShape(160.dp))
                        .fillMaxWidth()
                        .clickableSingle{
                            Analytics.trackRewardPopupBtn(AnalyticsEvent.Value.RewardPopupType.YES)
                            onWatchAd.invoke()
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_fluent_movies),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = FoundationBlack
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.export_watch_ad_button),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W500,
                        color = FoundationBlack
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.notification_permission_maybe_later),
                    fontSize = 16.sp,
                    color = Neutral_N500,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickableSingle{
                            Analytics.trackRewardPopupBtn(AnalyticsEvent.Value.RewardPopupType.NO)
                            onDismiss.invoke()
                        }
                        .padding(vertical = 14.dp, horizontal = 24.dp)
                )
            }

            Icon(
                painter = painterResource(R.drawable.ic_close_circle),
                contentDescription = null,
                tint = Neutral_N700,
                modifier = Modifier
                    .padding(16.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(0.2f), CircleShape)
                    .clickableSingle{
                        Analytics.trackRewardPopupBtn(AnalyticsEvent.Value.RewardPopupType.EXIT)
                        onDismiss.invoke()
                    }
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
        }
    }
}

// ============================================
// PREVIEW
// ============================================

@Preview
@Composable
private fun WatchAdDialogPreview() {
    VideoMakerTheme {
        WatchAdDialog(
            type = "",
            onDismiss = {},
            onWatchAd = {}
        )
    }
}
