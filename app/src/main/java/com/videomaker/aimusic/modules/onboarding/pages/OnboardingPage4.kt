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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================
// GENRE DATA
// ============================================

internal data class MusicGenreItem(
    val id: String,
    val emoji: String,
    val nameResId: Int
)

internal val musicGenreItems = listOf(
    MusicGenreItem("pop",        "🎵", R.string.genre_pop),
    MusicGenreItem("rock",       "🎸", R.string.genre_rock),
    MusicGenreItem("hip-hop",    "🎤", R.string.genre_hiphop),
    MusicGenreItem("r&b",        "💎", R.string.genre_rnb),
    MusicGenreItem("electronic", "⚡", R.string.genre_electronic),
    MusicGenreItem("jazz",       "🎷", R.string.genre_jazz),
    MusicGenreItem("classical",  "🎻", R.string.genre_classical),
    MusicGenreItem("country",    "🤠", R.string.genre_country),
    MusicGenreItem("latin",      "💃", R.string.genre_latin),
    MusicGenreItem("k-pop",      "⭐", R.string.genre_kpop),
    MusicGenreItem("dance",      "💃", R.string.genre_dance),
    MusicGenreItem("edm",        "⚡", R.string.genre_edm),
    MusicGenreItem("blues",      "🎶", R.string.genre_blues),
    MusicGenreItem("metal",      "🔥", R.string.genre_metal),
    MusicGenreItem("soul",       "✨", R.string.genre_soul),
)

// ============================================
// PAGE
// ============================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingPage4(
    selectedGenres: List<String>,
    onGenreToggle: (String) -> Unit
) {
    // Per-chip entrance animations (fade + scale + translateY)
    val chipAnimations = remember {
        musicGenreItems.map {
            Triple(Animatable(0f), Animatable(0.7f), Animatable(24f))
        }
    }

    LaunchedEffect(Unit) {
        chipAnimations.forEachIndexed { index, (alpha, scale, translateY) ->
            launch {
                delay(index * 40L)
                launch {
                    alpha.animateTo(
                        1f, spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                launch {
                    scale.animateTo(
                        1f, spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                launch {
                    translateY.animateTo(
                        0f, spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1a1a2e), Color(0xFF16213e))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp)
                .padding(top = 120.dp, bottom = 200.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(R.string.onboarding_page4_title),
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                lineHeight = 44.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_page4_subtitle),
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.6f),
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                musicGenreItems.forEachIndexed { index, genreItem ->
                    val (alpha, scale, translateY) = chipAnimations[index]
                    Box(
                        modifier = Modifier
                            .alpha(alpha.value)
                            .graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                                translationY = translateY.value
                            }
                    ) {
                        MusicGenreChip(
                            genreItem = genreItem,
                            isSelected = selectedGenres.contains(genreItem.id),
                            onClick = { onGenreToggle(genreItem.id) }
                        )
                    }
                }
            }
        }
    }
}

// ============================================
// CHIP
// ============================================

@Composable
private fun MusicGenreChip(
    genreItem: MusicGenreItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val chipShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .clip(chipShape)
            .background(
                if (isSelected) Color.White
                else Color.White.copy(alpha = 0.08f)
            )
            .then(
                if (isSelected) Modifier
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), chipShape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = "${genreItem.emoji} ${stringResource(genreItem.nameResId)}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) Color(0xFF1a1a2e) else Color.White
        )
    }
}

// ============================================
// PREVIEW
// ============================================

@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun OnboardingPage4Preview() {
    VideoMakerTheme {
        OnboardingPage4(
            selectedGenres = listOf("pop", "jazz", "k-pop"),
            onGenreToggle = {}
        )
    }
}
