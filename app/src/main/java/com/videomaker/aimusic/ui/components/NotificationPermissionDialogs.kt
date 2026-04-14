package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.FoundationBlack
import com.videomaker.aimusic.ui.theme.FoundationBlack_200
import com.videomaker.aimusic.ui.theme.Neutral_N500
import com.videomaker.aimusic.ui.theme.Neutral_N700
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

@Composable
fun NotificationPermissionPromoDialog(
    onNotifyMe: () -> Unit,
    onMaybeLater: () -> Unit
) {
    Dialog(
        onDismissRequest = {
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .clickableSingle {
                }
                .fillMaxSize()
                .padding(top = 106.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box {
                Spacer(
                    Modifier
                        .padding(top = 130.dp)
                        .matchParentSize()
                        .background(Color(0xff373737), RoundedCornerShape(16.dp))
                        .align(Alignment.BottomCenter)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Image(
                        painter = painterResource(R.drawable.img_popup_noti_permission),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.notification_permission_trending_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.W600,
                        fontSize = 24.sp,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.notification_permission_trending_message),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.W500,
                        fontSize = 15.sp,
                        color = FoundationBlack_200,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(40.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Primary, RoundedCornerShape(160.dp))
                            .clickableSingle {
                                onNotifyMe.invoke()
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_notification_outline),
                            contentDescription = null,
                            tint = FoundationBlack,
                            modifier = Modifier
                                .size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.notification_permission_notify_me),
                            fontWeight = FontWeight.W600,
                            fontSize = 16.sp,
                            color = FoundationBlack,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.notification_permission_maybe_later),
                        fontWeight = FontWeight.W600,
                        fontSize = 16.sp,
                        color = Neutral_N500,
                        modifier = Modifier
                            .clickableSingle {
                                onMaybeLater.invoke()
                            }
                            .padding(16.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun NotificationPermissionSettingsGuideDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .clickableSingle {
                }
                .fillMaxSize()
                .padding(top = 106.dp, start = 16.dp, end = 16.dp)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xff373737), RoundedCornerShape(16.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Image(
                    painter = painterResource(R.drawable.img_popup_noti_denied),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.notification_permission_settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W600,
                    fontSize = 24.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.notification_permission_settings_message),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.W500,
                    fontSize = 15.sp,
                    color = FoundationBlack_200,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                )
                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .background(Primary, RoundedCornerShape(160.dp))
                        .clickableSingle {
                            onOpenSettings.invoke()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.notification_permission_open_settings),
                        fontWeight = FontWeight.W600,
                        fontSize = 16.sp,
                        color = FoundationBlack,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            Icon(
                painter = painterResource(R.drawable.ic_close_circle),
                contentDescription = null,
                tint = Neutral_N700,
                modifier = Modifier
                    .padding(12.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(0.2f), CircleShape)
                    .clickableSingle{
                        onDismiss.invoke()
                    }
                    .padding(4.dp)
                    .align(Alignment.TopStart)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SuccessContentPreview() {
    VideoMakerTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SplashBackground)
        ) {
            NotificationPermissionSettingsGuideDialog(
                onOpenSettings = {},
                onDismiss = {}
            )
        }
    }
}
