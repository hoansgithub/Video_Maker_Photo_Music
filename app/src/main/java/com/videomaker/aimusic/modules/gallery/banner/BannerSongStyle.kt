package com.videomaker.aimusic.modules.gallery.banner

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.ui.components.AppAsyncImage
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import com.videomaker.aimusic.ui.theme.Neutral_N100
import com.videomaker.aimusic.ui.theme.Neutral_N500
import com.videomaker.aimusic.ui.theme.PlaceholderBackground
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import com.videomaker.aimusic.ui.theme.White16

@Composable
fun BannerSongStyle(
    song: MusicSong,
    style: BannerSong,
    isPlaying: Boolean,
    placeholderImageUrl: String? = null,
    onPlay: () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .fillMaxWidth()
            .aspectRatio(388 / 200f)
            .border(2.dp, Color.White.copy(0.12f), RoundedCornerShape(16.dp))
            .clickableSingle { onClick.invoke() }
    ) {
        if (style.style == 1) {
            Image(
                painter = painterResource( R.drawable.img_bg_banner_song1),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
                modifier = Modifier
                    .fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .padding(top = 20.dp, end = 16.dp)
                    .rotate(-14.85f)
                    .align(Alignment.TopEnd),
                verticalAlignment = Alignment.CenterVertically
            ){
                AppAsyncImage(
                    imageUrl = song.coverUrl,
                    contentDescription = song.name,
                    contentScale = ContentScale.Crop,
                    placeholderUrl = placeholderImageUrl,
                    modifier = Modifier
                        .size(125.dp)
                        .background(PlaceholderBackground)
                        .clip(RoundedCornerShape(8.dp))
                )
                Box{
                    Image(
                        painter = painterResource( R.drawable.img_banner_song1),
                        contentDescription = null,
                        contentScale = ContentScale.FillHeight,
                        modifier = Modifier
                            .height(112.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .align(Alignment.CenterStart),
                        contentAlignment = Alignment.Center
                    ){
                        AppAsyncImage(
                            imageUrl = song.coverUrl,
                            contentDescription = song.name,
                            contentScale = ContentScale.Crop,
                            placeholderUrl = placeholderImageUrl,
                            modifier = Modifier.fillMaxSize()
                        )

                        Spacer(Modifier.clip(CircleShape).size(5.dp).background(Color.Black))
                    }
                }
            }
            Image(
                painter = painterResource( R.drawable.img_bg_banner_song1_1),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
                modifier = Modifier
                    .fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource( R.drawable.img_bg_banner_song2),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
                modifier = Modifier
                    .fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .padding(top = 2.dp, end = 45.dp)
                    .size(149.67.dp)
                    .clip(CircleShape)
                    .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center
            ) {
                AppAsyncImage(
                    imageUrl = song.coverUrl,
                    contentDescription = song.name,
                    contentScale = ContentScale.Crop,
                    placeholderUrl = placeholderImageUrl,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PlaceholderBackground)
                )
                Spacer(Modifier.clip(CircleShape).size(10.dp).background(Color.Black))
            }
        }

        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 35.dp)
                .fillMaxWidth(0.45f)
                .background(Color.Black.copy(0.4f),RoundedCornerShape(55.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                tint = Color(0xFFF8FAFC),
                modifier = Modifier.size(18.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = song.name,
                    color = Color(0xFFF6F6F6),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artist,
                    color = Neutral_N500,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.W500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause
                else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color(0xFFF6F6F6),
                modifier = Modifier
                    .size(26.dp)
                    .background(Color.Black.copy(0.4f), CircleShape)
                    .clickableSingle {
                        onPlay.invoke()
                    }
                    .padding(4.dp)
            )
        }


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .background(
                            if (style.style == 1) Color(0xFF69D4FA) else Color(0xFFB183FB),
                            RoundedCornerShape(24.dp)
                        )
                        .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 6.dp, vertical = 3.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (style.style == 1) {
                                R.drawable.ic_hot_filled
                            } else {
                                R.drawable.ic_sparkle_filled
                            }
                        ),
                        contentDescription = null,
                        tint = if (style.style == 1) Color(0xFF056E94) else Color(0xFF5007C5)
                    )

                    Text(
                        text = if (style.style == 1) "HIT SONG" else "FEATURED",
                        color = if (style.style == 1) Color(0xFF056E94) else Color(0xFF5007C5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W700,
                        fontStyle = FontStyle.Italic
                    )
                }
                Text(
                    text = style.name,
                    color = Color(0xFFF6F6F6),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W800,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier
                    .background(Neutral_N100, RoundedCornerShape(160.dp))
                    .clickableSingle {
                        onClick.invoke()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Try it",
                    color = Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_right_arrow),
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Preview(widthDp = 375, heightDp = 812)
@Composable
private fun GalleryLoadingPreview() {
    VideoMakerTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            BannerSongStyle(
                song = MusicSong(
                    1L,
                    "Blinding Lights",
                    "The Weeknd",
                    durationMs = 200000,
                    usageCount = 1850000
                ),
                style = BannerSong(
                    name = "tesstttt",
                    id = 10L,
                    style = 1
                ),
                isPlaying = false,
                onPlay = {

                },
            ) { }
        }
    }
}

data class BannerSong(
    val name: String,
    val id: Long,
    val style: Int,
)