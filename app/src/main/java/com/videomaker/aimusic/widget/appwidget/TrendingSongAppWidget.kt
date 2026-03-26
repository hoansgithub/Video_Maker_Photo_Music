package com.videomaker.aimusic.widget.appwidget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
import com.videomaker.aimusic.MainActivity
import com.videomaker.aimusic.R

/**
 * Trending Song widget for home screen.
 * Displays trending song info and opens the app when tapped.
 */
class TrendingSongAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                TrendingSongWidgetContent(context)
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun TrendingSongWidgetContent(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = WidgetActions.ACTION_OPEN_TRENDING_SONG
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .clickable(actionStartActivity(intent))
    ) {
        // Background image
        Image(
            provider = ImageProvider(R.drawable.bg_widget),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp),
            contentScale = ContentScale.FillBounds
        )

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp)
        ) {
            // Header with icon
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(24.dp)
                        .cornerRadius(5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.mipmap.ic_launcher),
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = context.getString(R.string.widget_trending_song),
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text_primary),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Trending song label
            Text(
                text = context.getString(R.string.widget_trending_song_hot),
                style = TextStyle(
                    color = ColorProvider(R.color.widget_text_secondary),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Tap to explore
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .cornerRadius(18.dp)
                    .background(ColorProvider(R.color.widget_search_field_bg)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = context.getString(R.string.widget_smart_search_description),
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_hint_text),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}