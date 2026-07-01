package com.videomaker.aimusic.modules.featureselection

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Black20
import com.videomaker.aimusic.ui.theme.Gray700
import com.videomaker.aimusic.ui.theme.NewBadgeGradientEnd
import com.videomaker.aimusic.ui.theme.NewBadgeGradientStart
import com.videomaker.aimusic.ui.theme.Neutral_N100
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================
// FEATURE DATA
// ============================================

internal data class FeatureItem(
    val id: String,
    val icon: Int,
    val nameResId: Int,
    val subtitleResId: Int,
    val isNew: Boolean = false,
)

internal val featureItems = listOf(
    FeatureItem(
        id = "music_video_instant",
        icon = R.drawable.ic_ob_template,
        nameResId = R.string.feature_music_video_instant,
        subtitleResId = R.string.feature_music_video_instant_subtitle,
    ),
    FeatureItem(
        id = "photos_to_video",
        icon = R.drawable.ic_ob_song,
        nameResId = R.string.feature_photos_to_video,
        subtitleResId = R.string.feature_photos_to_video_subtitle,
    ),
    FeatureItem(
        id = "create_with_ai",
        icon = R.drawable.ic_ob_ai,
        nameResId = R.string.feature_create_ai,
        subtitleResId = R.string.feature_create_ai_subtitle,
        isNew = true,
    ),
)

// ============================================
// PAGE
// ============================================

@Composable
fun FeatureSurveyPage(
    selectedFeatures: List<String>,
    onFeatureToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomPaddingDp: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val isPreview = LocalInspectionMode.current
    val cardAnimations = remember {
        featureItems.map {
            Pair(Animatable(if (isPreview) 1f else 0f), Animatable(if (isPreview) 0f else 32f))
        }
    }

    if (!isPreview) {
        LaunchedEffect(Unit) {
            cardAnimations.forEachIndexed { index, (alpha, translateY) ->
                launch {
                    delay(index * 80L)
                    launch { alpha.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
                    launch { translateY.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(
                top = 26.dp,
                bottom = bottomPaddingDp + 24.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onboarding_page4_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.onboarding_page4_subtitle),
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(32.dp))

        featureItems.forEachIndexed { index, item ->
            val (alpha, translateY) = cardAnimations[index]
            val isSelected = selectedFeatures.contains(item.id)

            FeatureCard(
                item = item,
                isSelected = isSelected,
                onFeatureToggle = onFeatureToggle,
                modifier = Modifier
                    .alpha(alpha.value)
                    .graphicsLayer { translationY = translateY.value },
            )

            if (index < featureItems.lastIndex) Spacer(Modifier.height(16.dp))
        }
    }
}

// ============================================
// CARD
// ============================================

@Composable
internal fun FeatureCard(
    item: FeatureItem,
    isSelected: Boolean,
    onFeatureToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(32.dp)
    val selectedColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Black20)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) selectedColor else Gray700,
                shape = cardShape,
            )
            .clickableSingle { onFeatureToggle(item.id) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Image(
                painter = painterResource(item.icon),
                contentDescription = null,
                modifier = Modifier.size(52.dp),
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(item.nameResId),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Primary else Color.White,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(item.subtitleResId),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }

            Spacer(Modifier.width(10.dp))

            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = selectedColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // "NEW !!" badge
        if (item.isNew) {
            Text(
                text = stringResource(R.string.feature_new_badge),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
                color = Neutral_N100,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(bottomStart = 22.dp, topEnd = 32.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                NewBadgeGradientStart,
                                NewBadgeGradientEnd,
                            ),
                        ),
                    )
                    .padding(start = 14.dp, end = 24.dp, top = 6.dp, bottom = 6.dp),
            )
        }
    }
}

// ============================================
// PREVIEW
// ============================================

@Preview(showBackground = true, widthDp = 375, heightDp = 812, backgroundColor = 0xFF1A1A1A)
@Composable
private fun FeatureSurveyPagePreview() {
    VideoMakerTheme {
        FeatureSurveyPage(
            selectedFeatures = listOf("music_video_instant"),
            onFeatureToggle = {},
        )
    }
}
