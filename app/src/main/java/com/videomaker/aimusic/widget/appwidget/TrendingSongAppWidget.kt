package com.videomaker.aimusic.widget.appwidget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
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
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.videomaker.aimusic.MainActivity
import com.videomaker.aimusic.R
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
        val songs = songRepository.getFeaturedSongs(limit = 3)
            .getOrElse { emptyList() }

        val bitmaps: List<Bitmap?> = songs.map { song ->
            loadSongCoverBitmap(context, song.coverUrl)
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

private suspend fun loadSongCoverBitmap(context: Context, url: String): Bitmap? {
    if (url.isBlank()) return null
    return try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(200, 200)
            .build()

        val result = context.imageLoader.execute(request)
        if (result is SuccessResult) {
            result.drawable.let { drawable ->
                val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        } else null

    } catch (_: Exception) {
        null
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

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Centered header row
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.app_icon_loading),
                    contentDescription = null,
                    modifier = GlanceModifier.size(24.dp),
                    contentScale = ContentScale.FillBounds
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = context.getString(R.string.widget_trending_song),
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text_secondary),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(14.dp))

            // Row of 3 song card columns
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                for (index in 0 until 3) {
                    if (index > 0) {
                        Spacer(modifier = GlanceModifier.width(7.dp))
                    }

                    val song = songs.getOrNull(index)
                    val bitmap = bitmaps.getOrNull(index)
                    val fallback = fallbackCovers[index]

                    val songIntent = if (song != null) {
                        Intent(context, MainActivity::class.java).apply {
                            action = WidgetActions.ACTION_OPEN_TEMPLATE_WITH_SONG
                            putExtra(WidgetActions.EXTRA_SONG_ID, song.id)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                    } else {
                        Intent(context, MainActivity::class.java).apply {
                            action = WidgetActions.ACTION_OPEN_TRENDING_SONG
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                    }

                    val coverProvider = if (bitmap != null) {
                        ImageProvider(bitmap)
                    } else {
                        ImageProvider(fallback)
                    }

                    // Song card column
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .clickable(actionStartActivity(songIntent))
                    ) {
                        // Cover image with play icon overlay
                        Image(
                            provider = coverProvider,
                            contentDescription = song?.name,
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxWidth()
                                .cornerRadius(16.dp),
                            contentScale = ContentScale.FillBounds
                        )

                        Spacer(modifier = GlanceModifier.height(4.dp))

                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            Text(
                                text = song?.name ?: "",
                                style = TextStyle(
                                    color = ColorProvider(R.color.widget_text_secondary),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Text(
                                text = song?.artist ?: "",
                                style = TextStyle(
                                    color = ColorProvider(R.color.widget_text_secondary),
                                    fontSize = 10.sp,
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