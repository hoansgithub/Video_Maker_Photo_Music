package com.videomaker.aimusic.modules.onboarding.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import com.videomaker.aimusic.ui.theme.White12
import com.videomaker.aimusic.ui.theme.White20
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================
// FEATURE DATA
// ============================================

internal data class FeatureItem(val id: String, val emoji: String, val nameResId: Int)

internal val featureItems = listOf(
    FeatureItem("music_video_instant",  "🎬", R.string.feature_music_video_instant),
    FeatureItem("photos_to_video",      "📸", R.string.feature_photos_to_video),
    FeatureItem("trending_templates",   "✨", R.string.feature_trending_templates),
    FeatureItem("trending_music",       "🎵", R.string.feature_trending_music),
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
            .padding(top = 120.dp, bottom = 200.dp)
    ) {
        Text(
            text = stringResource(R.string.onboarding_page4_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.onboarding_page4_subtitle),
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(36.dp))

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

            if (index < featureItems.lastIndex) Spacer(Modifier.height(12.dp))
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
    val cardShape = RoundedCornerShape(16.dp)
    val selectedColor = MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                if (isSelected) selectedColor.copy(alpha = 0.15f) else White12
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) selectedColor else White20,
                shape = cardShape
            )
            .clickable { onFeatureToggle(item.id) }
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = item.emoji,
            fontSize = 26.sp,
            modifier = Modifier.padding(end = 16.dp)
        )

        Text(
            text = stringResource(item.nameResId),
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = selectedColor,
                modifier = Modifier.size(22.dp)
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