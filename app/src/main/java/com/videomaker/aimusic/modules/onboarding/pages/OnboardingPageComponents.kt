package com.videomaker.aimusic.modules.onboarding.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.modules.language.OnboardingCtaButton

// ============================================
// WELCOME PAGE TEMPLATE
// Banner (edge-to-edge) → left-aligned text → auto-scaling gap → CTA.
// The bottom Column is measured with onSizeChanged so the content above
// it always has enough room — future native ad slots just expand the
// bottom section and the gap adjusts automatically.
// ============================================

@Composable
internal fun WelcomePage(
    imageResId: Int,
    title: String,
    subtitle: String,
    ctaText: String,
    onCta: () -> Unit
) {
    val density = LocalDensity.current
    var bottomSectionHeight by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Scrollable content ────────────────────────────────────────────
        Column(modifier = Modifier.fillMaxSize()) {
            // Banner — top 52%, draws behind status bar (enableEdgeToEdge)
            Image(
                painter = painterResource(imageResId),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.52f),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 28.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = subtitle,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Auto-scaling gap: grows to ensure content never hides under
            // the bottom section. Adding a future ad slot only expands
            // bottomSectionHeight — nothing else needs to change.
            Spacer(
                modifier = Modifier.height(
                    with(density) { bottomSectionHeight.toDp() } + 24.dp
                )
            )
        }

        // ── Bottom section (CTA + future ad slot) ────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomSectionHeight = it.height }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            OnboardingCtaButton(text = ctaText, onClick = onCta)
            // Future native ad slot:
            // Spacer(Modifier.height(16.dp))
            // NativeAdView(placement = NATIVE_ONBOARDING, modifier = Modifier.fillMaxWidth())
        }
    }
}