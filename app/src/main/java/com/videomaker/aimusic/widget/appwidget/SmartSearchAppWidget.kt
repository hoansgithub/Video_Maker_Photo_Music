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

        // Content
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Header: icon + app name
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(32.dp)
                        .cornerRadius(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.mipmap.ic_launcher),
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = GlanceModifier.width(10.dp))
                Text(
                    text = context.getString(R.string.app_name),
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text_primary),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // Instruction text
            Text(
                text = context.getString(R.string.widget_smart_search_description),
                style = TextStyle(
                    color = ColorProvider(R.color.widget_text_secondary),
                    fontSize = 12.sp
                )
            )

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Search field
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .cornerRadius(21.dp)
                    .background(ColorProvider(R.color.widget_search_field_bg))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.widget_search_hint),
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_hint_text),
                        fontSize = 14.sp
                    )
                )
            }
        }
    }
}