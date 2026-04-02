package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Gray200
import com.videomaker.aimusic.ui.theme.PlaceholderBackground
import com.videomaker.aimusic.ui.theme.TemplateBadgeBackground
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.White12

/**
 * Card component for suggested songs in the "Suggested for you" section.
 * Displays song cover, name, artist, and usage count badge.
 */
@Composable
fun SuggestSongCard(
    song: MusicSong,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current

    Card(
        onClick = onClick,
        modifier = modifier.width(162.dp),
        shape = RoundedCornerShape(dimens.radiusLg),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column {
            // Cover image with usage count badge — 1:1, 16dp corner radius
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                AppAsyncImage(
                    imageUrl = song.coverUrl,
                    contentDescription = song.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                )

                // Usage count badge — bottom-end
                if (song.usageCount > 0) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(dimens.spaceSm)
                            .background(color = TemplateBadgeBackground, shape = RoundedCornerShape(999.dp))
                            .border(width = 1.dp, color = White12, shape = RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_heart),
                            contentDescription = null,
                            tint = Gray200,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = song.formattedUsageCount,
                            fontSize = 10.sp,
                            color = Gray200,
                            maxLines = 1
                        )
                    }
                }
            }

            // Song info: name + artist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.spaceSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(dimens.spaceXxs))
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp
                        ),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Placeholder shimmer component for SuggestSongCard while loading.
 */
@Composable
fun SuggestSongCardPlaceholder() {
    val dimens = AppDimens.current

    Column(
        modifier = Modifier
            .width(162.dp)
            .clip(RoundedCornerShape(dimens.radiusLg))
            .background(PlaceholderBackground)
    ) {
        // 1:1 thumbnail shimmer
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
        )
        // Title + artist shimmer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceXxs)
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(15.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(13.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
            Spacer(modifier = Modifier.width(dimens.spaceXs))
            ShimmerBox(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}
