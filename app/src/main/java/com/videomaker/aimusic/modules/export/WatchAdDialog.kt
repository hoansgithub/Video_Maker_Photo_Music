package com.videomaker.aimusic.modules.export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.BackgroundLight
import com.videomaker.aimusic.ui.theme.SurfaceDark
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

/**
 * WatchAdDialog - Dialog asking user to watch an ad before downloading video
 *
 * Follows patterns from android-short-drama-app RewardedInterstitialIntroDialog:
 * - [AD] label badge for visual engagement
 * - Clear title and subtitle
 * - Two buttons: "Close" (secondary) and "Watch Ad" with icon (primary, wider)
 * - Prevents tap-to-dismiss - forces explicit user choice
 *
 * @param onDismiss Callback when user taps "Close" button
 * @param onWatchAd Callback when user taps "Watch Ad" button
 */
@Composable
fun WatchAdDialog(
    onDismiss: () -> Unit,
    onWatchAd: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* Prevent tap-to-dismiss */ }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // [AD] label badge
            Box(
                modifier = Modifier
                    .background(
                        color = BackgroundLight,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 2.dp,
                        color = BackgroundLight.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AD",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SurfaceDark,
                    letterSpacing = 4.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = stringResource(R.string.export_watch_ad_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle
            Text(
                text = stringResource(R.string.export_watch_ad_subtitle),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Buttons row - Close (1/3 width) + Watch Ad (2/3 width)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Close button (secondary, 1/3 width)
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = stringResource(R.string.export_watch_ad_close),
                        fontSize = 13.sp,  // Reduced from 14sp to prevent truncation
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Watch Ad button (primary, 2/3 width, with icon)
                Button(
                    onClick = onWatchAd,
                    modifier = Modifier
                        .weight(2f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BackgroundLight,
                        contentColor = SurfaceDark
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.export_watch_ad_button),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
            onDismiss = {},
            onWatchAd = {}
        )
    }
}
