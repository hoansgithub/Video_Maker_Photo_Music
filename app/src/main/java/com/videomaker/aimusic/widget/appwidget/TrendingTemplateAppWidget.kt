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
import androidx.compose.ui.graphics.Color
import androidx.glance.layout.fillMaxHeight
import androidx.glance.unit.ColorProvider
import com.videomaker.aimusic.MainActivity
import com.videomaker.aimusic.R

/**
 * Trending Template widget for home screen.
 * Displays trending template info and opens the app when tapped.
 */
class TrendingTemplateAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                TrendingTemplateWidgetContent(context)
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun TrendingTemplateWidgetContent(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = WidgetActions.ACTION_OPEN_TRENDING_TEMPLATE
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
            contentScale = ContentScale.FillBounds
        )

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Centered Row: [app_icon_loading 24dp] [spacer 4dp] [widget_recently text]
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
                    text = context.getString(R.string.widget_recently),
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text_secondary),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(14.dp))

            // Row of 3 boxes: add button + 2 placeholders
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                Image(
                    provider = ImageProvider(R.drawable.img_template_add),
                    contentDescription = null,
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .cornerRadius(18.dp),
                    contentScale = ContentScale.FillBounds
                )

                Spacer(modifier = GlanceModifier.width(7.dp))
                // Placeholder 1
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
                // Placeholder 2
                Image(
                    provider = ImageProvider(R.drawable.img_template2),
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