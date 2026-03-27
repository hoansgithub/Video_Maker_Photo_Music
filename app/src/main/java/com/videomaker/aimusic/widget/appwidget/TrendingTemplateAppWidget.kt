package com.videomaker.aimusic.widget.appwidget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.videomaker.aimusic.MainActivity
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.TemplateRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import androidx.core.graphics.createBitmap

/**
 * Trending Template widget for home screen.
 * Displays 2 featured template thumbnails loaded dynamically via Coil.
 */
class TrendingTemplateAppWidget : GlanceAppWidget(), KoinComponent {

    private val templateRepository: TemplateRepository by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val templates = templateRepository.getFeaturedTemplates(limit = 2)
            .getOrElse { emptyList() }

        val bitmaps: List<Bitmap?> = templates.map { template ->
            loadTemplateThumbnailBitmap(context, template.thumbnailPath)
        }

        provideContent {
            GlanceTheme {
                TrendingTemplateWidgetContent(
                    context = context,
                    templates = templates,
                    bitmaps = bitmaps
                )
            }
        }
    }
}

private suspend fun loadTemplateThumbnailBitmap(context: Context, url: String): Bitmap? {
    if (url.isBlank()) return null
    return try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(300, 400)
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
private fun TrendingTemplateWidgetContent(
    context: Context,
    templates: List<VideoTemplate>,
    bitmaps: List<Bitmap?>
) {
    val searchIntent = Intent(context, MainActivity::class.java).apply {
        action = WidgetActions.ACTION_OPEN_SEARCH
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    val fallbackDrawables = listOf(
        R.drawable.img_template1,
        R.drawable.img_template2
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
                    text = context.getString(R.string.widget_recently),
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text_secondary),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(14.dp))

            // Row: add button + 2 template images
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                // Add button — opens search
                Image(
                    provider = ImageProvider(R.drawable.img_template_add),
                    contentDescription = null,
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .cornerRadius(18.dp)
                        .clickable(actionStartActivity(searchIntent)),
                    contentScale = ContentScale.FillBounds
                )

                for (index in 0 until 2) {
                    Spacer(modifier = GlanceModifier.width(7.dp))

                    val template = templates.getOrNull(index)
                    val bitmap = bitmaps.getOrNull(index)
                    val fallback = fallbackDrawables[index]

                    val templateIntent = if (template != null) {
                        Intent(context, MainActivity::class.java).apply {
                            action = WidgetActions.ACTION_OPEN_TEMPLATE_DETAIL
                            putExtra(WidgetActions.EXTRA_TEMPLATE_ID, template.id)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
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