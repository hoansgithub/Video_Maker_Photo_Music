package com.videomaker.aimusic.widget.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.ui.components.AppAsyncImage
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.TextOnSecondary
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.utils.innerShadowCustom

@Composable
fun TrendingSongWidget(
    listSongs: List<MusicSong>,
    onClick: (MusicSong) -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .paint(
                painter = painterResource(R.drawable.bg_item_widget),
            )
            .padding(top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.app_icon_loading),
                contentDescription = "",
                modifier = Modifier.width(24.dp),
                contentScale = ContentScale.FillWidth
            )
            Text(
                text = stringResource(R.string.widget_trending_song),
                color = TextOnSecondary,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.W600,
                    fontSize = 10.sp
                ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listSongs.take(3).forEach { song ->
                WidgetSongCard(
                    modifier = Modifier.weight(1f),
                    song = song,
                    onClick = { onClick(song) }
                )
            }
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF0A0A0A,
)
@Composable
private fun TrendingWidgetPreview() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        TrendingSongWidget(
            listSongs = listOf(
                MusicSong(
                   id = 12000L,
                    name = "qqw",
                    artist = "sdas"
                ),
                MusicSong(
                   id = 12000L,
                    name = "qqw",
                    artist = "sdas"
                ),
                MusicSong(
                   id = 12000L,
                    name = "qqw",
                    artist = "sdas"
                ),
                MusicSong(
                   id = 12000L,
                    name = "qqw",
                    artist = "sdas"
                ),
            ),
            onClick = { _ -> }
        )
    }
}

@Composable
private fun WidgetSongCard(
    song: MusicSong,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
){
    val dimens = AppDimens.current

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(dimens.radiusLg),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column {
            // Cover image — 1:1, 16dp corner radius
            Box{
                AppAsyncImage(
                    imageUrl = song.coverUrl,
                    contentDescription = song.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(122.dp)
                        .clip(RoundedCornerShape(16.dp))
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.widget_trending_song_hot),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.W700,
                            fontSize = 8.sp,
                            fontStyle = FontStyle.Italic
                        ),
                        color = TextPrimary,
                        modifier = Modifier
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFED4523),
                                        Color(0xFFF751C8)
                                    )
                                ),
                                shape = RoundedCornerShape(
                                    topEnd = 7.dp,
                                    bottomStart = 7.dp,
                                    bottomEnd = 7.dp
                                )
                            )
                            .border(
                                width = 0.5.dp, // 0.37px → làm tròn lên cho Android
                                color = Color.White.copy(alpha = 0.56f),
                                shape = RoundedCornerShape(
                                    topEnd = 7.dp,
                                    bottomStart = 7.dp,
                                    bottomEnd = 7.dp
                                )
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )

                    Icon(
                        painterResource(R.drawable.ic_more_menu),
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color.Black.copy(0.12f), CircleShape)
                            .border(0.75.dp,Color.White.copy(0.12f), CircleShape)
                            .innerShadowCustom(
                                color = Color(0xFFF8F8F80F).copy(0.06f),
                                borderRadius = 360.dp,
                                blurRadius = 12.dp,
                                offsetY = 3.dp,
                                offsetX = 1.5.dp,
                            )
                            .padding(3.dp)
                    )
                }

                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.Black.copy(0.4f),CircleShape)
                        .padding(5.dp)
                        .align(Alignment.Center)
                )
            }

            // Song info: [name + artist column] | [start-project button]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.spaceXs),
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
                Icon(
                    painter = painterResource(R.drawable.ic_lead_search),
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}