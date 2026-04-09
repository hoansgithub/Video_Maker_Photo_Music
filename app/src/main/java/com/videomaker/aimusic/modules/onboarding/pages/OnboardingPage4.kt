package com.videomaker.aimusic.modules.onboarding.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import com.videomaker.aimusic.ui.theme.Black20
import com.videomaker.aimusic.ui.theme.Gray700
import com.videomaker.aimusic.ui.theme.Primary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================
// FEATURE DATA
// ============================================

internal data class FeatureItem(val id: String, val icon: Int, val nameResId: Int)

internal val featureItems = listOf(
    FeatureItem("music_video_instant", R.drawable.ic_lead_search,     R.string.feature_music_video_instant),
    FeatureItem("photos_to_video",     R.drawable.ic_music_note, R.string.feature_photos_to_video),
//    FeatureItem("trending_templates",  Icons.Default.AutoAwesome,  R.string.feature_trending_templates),
//    FeatureItem("trending_music",      Icons.Default.MusicNote,    R.string.feature_trending_music),
)

// ============================================
// PAGE
// ============================================

@Composable
fun FeatureSurveyPage(
    selectedFeatures: List<String>,
    onFeatureToggle: (String) -> Unit
) {
    val cardAnimations = remember {
        featureItems.map { Pair(Animatable(0f), Animatable(32f)) }
    }

    LaunchedEffect(Unit) {
        cardAnimations.forEachIndexed { index, (alpha, translateY) ->
            launch {
                delay(index * 80L)
                launch { alpha.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
                launch { translateY.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(
                top = 140.dp,
                // Reserve space for: native ad (~320dp) + ad padding from bottom (160dp) + extra spacing (16dp)
                // Total: 496dp to prevent ad from overlapping feature cards
                bottom = 496.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.onboarding_page4_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.onboarding_page4_subtitle),
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))  // Reduced from 36dp to 32dp

        featureItems.forEachIndexed { index, item ->
            val (alpha, translateY) = cardAnimations[index]
            val isSelected = selectedFeatures.contains(item.id)

            FeatureCard(
                item = item,
                isSelected = isSelected,
                onFeatureToggle = onFeatureToggle,
                modifier = Modifier
                    .alpha(alpha.value)
                    .graphicsLayer { translationY = translateY.value }
            )

            if (index < featureItems.lastIndex) Spacer(Modifier.height(16.dp))  // Consistent 16dp spacing
        }
    }
}

// ============================================
// CARD
// ============================================

@Composable
private fun FeatureCard(
    item: FeatureItem,
    isSelected: Boolean, onFeatureToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(40)
    val selectedColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(if (isSelected) selectedColor.copy(alpha = 0.15f) else Black20)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) selectedColor else Gray700,
                shape = cardShape
            )
            .clickableSingle { onFeatureToggle(item.id) }
            .padding(vertical = 20.dp, horizontal = 16.dp)  // Reduced from 24dp to 20dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(16.dp),  // Reduced from 24dp to 16dp
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(item.icon),
                contentDescription = null,
                tint = if (isSelected) Primary else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(32.dp)  // Reduced from 36dp to 32dp for more compact layout
            )

            Text(
                text = stringResource(item.nameResId),
                fontSize = 17.sp,  // Reduced from 18sp to 17sp
                fontWeight = FontWeight.W500,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),  // Reduced from 50dp to 40dp
                textAlign = TextAlign.Center
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = selectedColor,
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.TopEnd)
            )
        }
    }
}

// ============================================
// PREVIEW
// ============================================

@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun FeatureSurveyPagePreview() {
    VideoMakerTheme {
        FeatureSurveyPage(
            selectedFeatures = listOf("photos_to_video", "trending_music"),
            onFeatureToggle = {}
        )
    }
}