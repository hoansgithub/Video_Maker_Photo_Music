package com.videomaker.aimusic.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary

/**
 * Shared song list row — matches the Station section design.
 * Used in Songs screen (Station section) and Gallery Search results.
 */
@Composable
fun SongListItem(
    name: String,
    artist: String,
    coverUrl: String,
    onSongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    isLoading: Boolean = false
) {
    val dimens = AppDimens.current

    Card(
        onClick = onSongClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.radiusLg),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with overlay for playing/loading state
            Box {
                AppAsyncImage(
                    imageUrl = coverUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(dimens.radiusMd))
                )

                // Playing animation or loading indicator overlay
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(dimens.radiusMd))
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    isPlaying -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(dimens.radiusMd))
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayingAnimationBars()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(dimens.spaceMd))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 15.sp
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(dimens.spaceXxs))
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp
                    ),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Play/Pause indicator
            if (isPlaying || isSelected) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 3 bouncing bars animation for playing state
 */
@Composable
private fun PlayingAnimationBars() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.size(24.dp)
    ) {
        repeat(3) { index ->
            var height by remember { mutableStateOf(0.4f) }

            LaunchedEffect(Unit) {
                while (true) {
                    animate(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 300,
                                delayMillis = index * 100
                            ),
                            repeatMode = RepeatMode.Reverse
                        )
                    ) { value, _ ->
                        height = value
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp * height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )
        }
    }
}

/**
 * Shimmer skeleton matching [SongListItem] dimensions.
 * Requires [ProvideShimmerEffect] ancestor to share the animation.
 */
@Composable
fun SongListItemPlaceholder(modifier: Modifier = Modifier) {
    val dimens = AppDimens.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusLg))
            .background(Color.Transparent)
            .padding(dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(dimens.radiusMd))
        )
        Spacer(modifier = Modifier.width(dimens.spaceMd))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceXxs)
        ) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(15.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}