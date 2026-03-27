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
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.TemplateRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Smart Search widget for home screen.
 * Displays a search bar and 3 trending template thumbnails loaded dynamically.
 */
class SmartSearchAppWidget : GlanceAppWidget(), KoinComponent {

    private val templateRepository: TemplateRepository by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val templates = templateRepository.getTemplates(limit = 3, offset = 0)
            .getOrElse { emptyList() }

        val bitmaps: List<Bitmap?> = templates.map { template ->
            WidgetBitmapLoader.loadTemplateBitmap(context, template.thumbnailPath)
        }

        provideContent {
            GlanceTheme {
                SmartSearchWidgetContent(
                    context = context,
                    templates = templates,
                    bitmaps = bitmaps
                )
            }
        }
    }
}


@SuppressLint("RestrictedApi")
@Composable
private fun SmartSearchWidgetContent(
    context: Context,
    templates: List<VideoTemplate>,
    bitmaps: List<Bitmap?>
) {
    val searchIntent = WidgetActions.openSearchIntent(context)

    val fallbackDrawables = listOf(
        R.drawable.img_template1,
        R.drawable.img_template2,
        R.drawable.img_template3
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
            contentScale = ContentScale.Crop
        )

        // Content
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
        ) {
            // Search bar row — clickable to open search
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(R.color.widget_search_field_bg))
                    .cornerRadius(16.dp)
                    .padding(vertical = 12.dp, horizontal = 16.dp)
                    .clickable(actionStartActivity(searchIntent)),
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

            // Row of 3 template images
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
            ) {
                for (index in 0 until 3) {
                    if (index > 0) {
                        Spacer(modifier = GlanceModifier.width(7.dp))
                    }

                    val template = templates.getOrNull(index)
                    val bitmap = bitmaps.getOrNull(index)
                    val fallback = fallbackDrawables[index]

                    val templateIntent = if (template != null) {
                        WidgetActions.openTemplateDetailIntent(context, template.id)
                    } else {
                        searchIntent
                    }

                    val imageProvider = if (bitmap != null) {
                        ImageProvider(bitmap)
                    } else {
                        ImageProvider(fallback)
                    }

                    Image(
                        provider = imageProvider,
                        contentDescription = template?.name,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .cornerRadius(18.dp)
                            .clickable(actionStartActivity(templateIntent)),
                        contentScale = ContentScale.FillBounds
                    )
                }
            }
        }
    }
}