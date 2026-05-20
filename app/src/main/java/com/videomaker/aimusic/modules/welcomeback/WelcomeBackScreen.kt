package com.videomaker.aimusic.modules.welcomeback

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import androidx.compose.ui.tooling.preview.Preview
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import com.videomaker.aimusic.ui.theme.WelcomeBackBackground
import com.videomaker.aimusic.ui.theme.WelcomeBackVibeBg
import com.videomaker.aimusic.ui.theme.WelcomeBackVibeBorder

@Composable
fun WelcomeBackScreen(
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WelcomeBackBackground)
    ) {
        // Background Image and bottom gradient fade
        Image(
            painter = painterResource(id = R.drawable.bg_welcome),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.48f)
                .align(Alignment.TopCenter)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.48f)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            WelcomeBackBackground
                        )
                    )
                )
                .align(Alignment.TopCenter)
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            BoxWithConstraints(
                modifier = Modifier.weight(1f)
            ) {
                // Adjust spacers dynamically when ad loads and reduces available height
                val isCompact = maxHeight < 600.dp
                val topSpacerHeight = if (isCompact) 48.dp else 150.dp
                val bottomSpacerHeight = if (isCompact) 24.dp else 40.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                            )
                        )
                        .padding(
                            top = 16.dp,
                            bottom = 24.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(topSpacerHeight))

                    // Center Icon with soft premium glow
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(120.dp)
                            .drawBehind {
                                val glowColor = Primary
                                for (i in 1..8) {
                                    val spread = i.dp.toPx()
                                    val opacity = 0.08f * (1f - i.toFloat() / 9f)
                                    drawRoundRect(
                                        color = glowColor.copy(alpha = opacity),
                                        topLeft = androidx.compose.ui.geometry.Offset(
                                            -spread,
                                            -spread
                                        ),
                                        size = androidx.compose.ui.geometry.Size(
                                            size.width + 2 * spread,
                                            size.height + 2 * spread
                                        ),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                            (24.dp.toPx() + spread),
                                            (24.dp.toPx() + spread)
                                        )
                                    )
                                }
                            }
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_icon_loading),
                            contentDescription = null,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(24.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = stringResource(R.string.welcome_back_title),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.welcome_back_subtitle),
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Vibe tags
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(WelcomeBackVibeBg)
                            .border(1.dp, WelcomeBackVibeBorder, RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.welcome_back_vibe_tags),
                            fontSize = 16.sp,
                            color = Primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(bottomSpacerHeight))
                }
            }

            // Bottom section: CTA + Native ad
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WelcomeBackBackground.copy(alpha = 0.95f))
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        WelcomeBackCtaButton(
                            text = stringResource(R.string.welcome_back_continue),
                            onClick = onContinue
                        )
                    }

                    NativeAdView(
                        placement = AdPlacement.NATIVE_WELCOME_BACK,
                        modifier = Modifier.fillMaxWidth(),
                        isDebug = BuildConfig.DEBUG
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeBackCtaButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val glowColor = Primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .drawBehind {
                for (i in 1..10) {
                    val spread = i.dp.toPx()
                    val opacity = 0.12f * (1f - i.toFloat() / 11f)
                    drawRoundRect(
                        color = glowColor.copy(alpha = opacity),
                        topLeft = androidx.compose.ui.geometry.Offset(-spread, -spread),
                        size = androidx.compose.ui.geometry.Size(
                            size.width + 2 * spread,
                            size.height + 2 * spread
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            (28.dp.toPx() + spread),
                            (28.dp.toPx() + spread)
                        )
                    )
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .clickableSingle(enabled = enabled, onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 20.dp)
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = WelcomeBackBackground
            )

            Icon(
                painter = painterResource(R.drawable.ic_right_arrow),
                contentDescription = null,
                tint = WelcomeBackBackground,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeBackScreenPreview() {
    VideoMakerTheme {
        WelcomeBackScreen(
            onContinue = {}
        )
    }
}
