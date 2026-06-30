package com.videomaker.aimusic.modules.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.components.PrimaryButton

private val DialogBackground = Color(0xFF2A2A2C)
private val TitleColor = Color(0xFFF8FAFC)
private val DescColor = Color(0xFF8C8C8C)

/**
 * "Free AI credits used up" popup shown on the editor (video generation) for the AI flow.
 * Modal — the only ways out are [onTryAgain] (retry, caller re-arms the 10s loop) or
 * [onTryLater] (return to Home/Gallery).
 */
@Composable
fun AiCreditsErrorDialog(
    onTryAgain: () -> Unit,
    onTryLater: () -> Unit
) {
    // System displays popup (fires on each display since the caller loops it every 10s).
    LaunchedEffect(Unit) { Analytics.trackAiErrorRender() }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DialogBackground)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_gen_video_ai_error),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.ai_credits_title),
                color = TitleColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.W700,
                lineHeight = 31.2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.ai_credits_desc),
                color = DescColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.W500,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryButton(
                text = stringResource(R.string.ai_credits_try_again),
                onClick = {
                    Analytics.trackAiErrorAgain()
                    onTryAgain()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableSingle {
                        Analytics.trackAiErrorLater()
                        onTryLater()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.ai_credits_try_later),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W700
                )
            }
        }
    }
}
