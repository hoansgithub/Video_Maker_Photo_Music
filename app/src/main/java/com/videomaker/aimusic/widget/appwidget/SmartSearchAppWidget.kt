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
import com.videomaker.aimusic.MainActivity
import com.videomaker.aimusic.R

/**
 * Smart Search widget for home screen.
 * Displays a search bar that opens the app's search screen when tapped.
 */
class SmartSearchAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                SmartSearchWidgetContent(context)
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun SmartSearchWidgetContent(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = WidgetActions.ACTION_OPEN_SEARCH
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .clickable(actionStartActivity(intent))
    ) {
        // Background image
        Image(
            provider = ImageProvider(R.drawable.bg_item_widget),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp),
            contentScale = ContentScale.Crop
        )

        // Content
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
        ) {
            // Search bar: [app_icon_loading] [description text - flexible] [ic_lead_search]
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(R.color.widget_search_field_bg))
                    .cornerRadius(16.dp)
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.app_icon_loading),
                    contentDescription = null,
                    modifier = GlanceModifier.size(24.dp),
                    contentScale = ContentScale.FillBounds
                )
                Spacer(modifier = GlanceModifier.width(12.dp))
                Text(
                    text = context.getString(R.string.widget_smart_search_description),
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text_secondary),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Image(
                    provider = ImageProvider(R.drawable.ic_search),
                    contentDescription = null,
                    modifier = GlanceModifier.size(24.dp),
                    contentScale = ContentScale.FillBounds
                )
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Trending label
            Text(
                text = context.getString(R.string.widget_smart_search_trending),
                style = TextStyle(
                    color = ColorProvider(R.color.widget_text_secondary),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Row of 3 equal placeholder boxes
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
            ) {
                Image(
                    provider = ImageProvider(R.drawable.img_template1),
                    contentDescription = null,
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .cornerRadius(18.dp),
                    contentScale = ContentScale.FillBounds
                )
                Spacer(modifier = GlanceModifier.width(7.dp))
                Image(
                    provider = ImageProvider(R.drawable.img_template2),
                    contentDescription = null,
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .cornerRadius(18.dp),
                    contentScale = ContentScale.FillBounds
                )
                Spacer(modifier = GlanceModifier.width(7.dp))
                Image(
                    provider = ImageProvider(R.drawable.img_template3),
                    contentDescription = null,
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .cornerRadius(18.dp),
                    contentScale = ContentScale.FillBounds
                )
            }
        }
    }
}