package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Gray400
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary

/**
 * Card component for weekly ranking songs.
 * Displays ranking number, thumbnail, song name, and usage count.
 */
@Composable
fun RankingSongCard(
    song: MusicSong,
    ranking: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.radiusLg),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AppAsyncImage(
                imageUrl = song.coverUrl,
                contentDescription = song.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(dimens.radiusMd))
            )

            Spacer(modifier = Modifier.width(dimens.spaceSm))

            // Ranking label: # (gray, large) with number overlapping to the right (lime)
            Box(modifier = Modifier.width(36.dp)) {
                Text(
                    text = "#",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    color = Gray400,
                )
                Text(
                    text = ranking.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    modifier = Modifier.offset(x = 13.dp, y = 8.dp)
                )
            }

            Spacer(modifier = Modifier.width(dimens.spaceSm))

            // Song name + usage count
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_heart),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(dimens.spaceXxs))
                    Text(
                        text = song.formattedUsageCount,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Placeholder shimmer component for RankingSongCard while loading.
 */
@Composable
fun RankingSongCardPlaceholder() {
    val dimens = AppDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        ShimmerBox(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(dimens.radiusMd))
        )
        Spacer(modifier = Modifier.width(dimens.spaceSm))
        // Rank number box
        ShimmerBox(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(dimens.radiusMd))
        )
        Spacer(modifier = Modifier.width(dimens.spaceSm))
        // Name + usage count lines
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
                    .fillMaxWidth(0.4f)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
        Spacer(modifier = Modifier.width(dimens.spaceXs))
        // Start project button
        ShimmerBox(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}
