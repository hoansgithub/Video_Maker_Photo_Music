package com.videomaker.aimusic.widget.appwidget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.SongRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Trending Song widget for home screen.
 * Displays 3 featured songs with cover art loaded dynamically via Coil.
 */
class TrendingSongAppWidget : GlanceAppWidget(), KoinComponent {

    private val songRepository: SongRepository by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Analytics.trackWidgetImpression(
            widgetType = "trending_song",
            widgetSize = "4x3"
        )
        val songs = songRepository.getFeaturedSongs(limit = 3)
            .getOrElse { emptyList() }

        val bitmaps: List<Bitmap?> = songs.map { song ->
            WidgetBitmapLoader.loadSongCoverBitmap(context, song.coverUrl)
        }

        provideContent {
            GlanceTheme {
                TrendingSongWidgetContent(
                    context = context,
                    songs = songs,
                    bitmaps = bitmaps
                )
            }
        }
    }
}


@SuppressLint("RestrictedApi")
@Composable
private fun TrendingSongWidgetContent(
    context: Context,
    songs: List<MusicSong>,
    bitmaps: List<Bitmap?>
) {
    val fallbackCovers = listOf(
        R.drawable.img_song1,
        R.drawable.img_song2,
        R.drawable.img_song3
    )

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
    ) {
        // Background image
        Image(
            provider = ImageProvider(R.drawable.bg_item_widget),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp),
            contentScale = ContentScale.FillBounds
        )

        // 4×2 layout: compact header + 3 square cover images
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Compact header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.app_icon_loading),
                    contentDescription = null,
                    modifier = GlanceModifier.size(20.dp),
                    contentScale = ContentScale.FillBounds
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = context.getString(R.string.widget_trending_song),
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text_secondary),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.size(16.dp))

            // 3 song cards: 1:1 square cover + name + artist
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                for (index in 0 until 3) {

                    val song = songs.getOrNull(index)
                    val bitmap = bitmaps.getOrNull(index)
                    val fallback = fallbackCovers[index]

                    val songIntent = if (song != null) {
                        WidgetActions.openSongPlayerIntent(context, song.id)
                    } else {
                        WidgetActions.openTrendingSongIntent(context)
                    }

                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .clickable(actionStartActivity(songIntent)),
                    ) {
                        // 1:1 square cover: fillMaxWidth + equal height via aspect-ratio trick
                        Box(
                            modifier = GlanceModifier
                                .size(100.dp)
                                .cornerRadius(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = if (bitmap != null) ImageProvider(bitmap)
                                    else ImageProvider(fallback),
                                contentDescription = song?.name,
                                modifier = GlanceModifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Image(
                                provider = ImageProvider(R.drawable.ic_play),
                                contentDescription = null,
                                modifier = GlanceModifier.size(18.dp),
                                contentScale = ContentScale.FillBounds
                            )
                        }

                        Spacer(modifier = GlanceModifier.height(3.dp))
                        Column(
                            modifier = GlanceModifier
                                .width(111.dp),
                        ) {
                            Text(
                                text = song?.name ?: "",
                                style = TextStyle(
                                    color = ColorProvider(R.color.widget_text_secondary),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = song?.artist ?: "",
                                style = TextStyle(
                                    color = ColorProvider(R.color.widget_text_secondary),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
