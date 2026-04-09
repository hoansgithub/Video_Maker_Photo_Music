package com.videomaker.aimusic.modules.onboarding.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.ui.theme.Primary

// ============================================
// WELCOME PAGE TEMPLATE
// Dynamic layout with full-screen image and overlay:
// - Banner image fills entire area (scale aspect fill)
// - Dark gradient overlay at bottom for text readability
// - Title/Subtitle + CTA Button Row overlaid at bottom
// - Text limited to 2 lines max with ellipsis
// - Native ad at bottom (measured dynamically, pushes content up)
// ============================================

@Composable
internal fun WelcomePage(
    imageResId: Int,
    title: String,
    subtitle: String,
    ctaText: String,
    onCta: () -> Unit,
    pageIndex: Int = 0  // 0-based index for ad placement
) {
    val density = LocalDensity.current
    var bottomSectionHeight by remember { mutableIntStateOf(0) }
    val bottomPaddingDp = with(density) { bottomSectionHeight.toDp() }

    // Map page index to ad placement
    val adPlacement = when (pageIndex) {
        0 -> AdPlacement.NATIVE_ONBOARDING_PAGE1
        1 -> AdPlacement.NATIVE_ONBOARDING_PAGE2
        2 -> AdPlacement.NATIVE_ONBOARDING_PAGE3
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Main content (pushed up by bottom section) ──────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPaddingDp)
        ) {
            // Banner image — fills entire area with Crop
            Image(
                painter = painterResource(imageResId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Box with dark gradient background containing title and button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.0f),
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 1.0f)
                            )
                        )
                    )
                    .padding(horizontal = 24.dp)
                    .padding(top = 40.dp, bottom = 32.dp)
            ) {
                // Title/Subtitle + CTA Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Title + Subtitle Column (left side)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = subtitle,
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 22.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // CTA Button (right side, vertically centered with title area)
                    OnboardingCtaButton(
                        text = ctaText,
                        onClick = onCta,
                        color = Primary,
                        icon = R.drawable.ic_right_arrow
                    )
                }
            }
        }

        // ── Bottom section: Native Ad only ──────────────────────────────
        if (adPlacement != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .onSizeChanged { size ->
                        bottomSectionHeight = size.height
                    }
            ) {
                NativeAdView(
                    placement = adPlacement,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}