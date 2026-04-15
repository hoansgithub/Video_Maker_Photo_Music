package com.videomaker.aimusic.core.notification

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.videomaker.aimusic.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Locale
import kotlin.math.absoluteValue

class NotificationRenderer(
    private val context: Context,
    private val notificationChannels: NotificationChannels
) {

    suspend fun show(payload: NotificationPayload): Boolean {
        notificationChannels.ensureCreated(context)

        val stableSeed = payload.itemId.hashCode().toLong().absoluteValue
        val notificationId = buildNotificationId(payload.type.ordinal.toLong(), stableSeed)
        val deepLinkIntent = NotificationDeepLinkFactory.toMainActivityIntent(context, payload.deepLink)
        val contentIntent = PendingIntent.getBroadcast(
            context,
            buildRequestCode(stableSeed, 11),
            NotificationActionReceiver.buildIntent(
                context = context,
                type = payload.type,
                itemId = payload.itemId,
                itemType = payload.itemType,
                notificationId = notificationId,
                cta = "open_notification",
                deepLinkDestination = payload.deepLink.deepLinkDestination,
                deepLinkIntent = deepLinkIntent
            ),
            PENDING_INTENT_FLAGS
        )
        val actionIntent = PendingIntent.getBroadcast(
            context,
            buildRequestCode(stableSeed, 17),
            NotificationActionReceiver.buildIntent(
                context = context,
                type = payload.type,
                itemId = payload.itemId,
                itemType = payload.itemType,
                notificationId = notificationId,
                cta = payload.ctaText.lowercase(Locale.ROOT).replace(" ", "_"),
                deepLinkDestination = payload.deepLink.deepLinkDestination,
                deepLinkIntent = deepLinkIntent
            ),
            PENDING_INTENT_FLAGS
        )
        val deleteIntent = PendingIntent.getBroadcast(
            context,
            buildRequestCode(stableSeed, 23),
            NotificationDismissReceiver.buildIntent(
                context = context,
                type = payload.type,
                itemId = payload.itemId,
                itemType = payload.itemType,
                notificationId = notificationId
            ),
            PENDING_INTENT_FLAGS
        )

        val heroBitmap = loadHeroBitmap(payload)
        val notification = buildTrendingSongCustomNotification(
            payload = payload,
            heroBitmap = heroBitmap,
            contentIntent = contentIntent,
            actionIntent = actionIntent,
            deleteIntent = deleteIntent
        )

        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        }.getOrElse { false }
    }

    private suspend fun loadHeroBitmap(payload: NotificationPayload): Bitmap = withContext(Dispatchers.IO) {
        val baseBitmap = if (payload.type == NotificationType.VIRAL_TEMPLATE && payload.imageCandidates.size > 1) {
            val bitmaps = payload.imageCandidates.take(3).mapNotNull { loadBitmapFromSource(it) }
            if (bitmaps.isNotEmpty()) composeTemplateCollage(bitmaps) else null
        } else {
            null
        }

        val resolvedBitmap = baseBitmap ?: run {
            payload.imageCandidates.forEach { source ->
                loadBitmapFromSource(source)?.let { return@run it }
            }
            BitmapFactory.decodeResource(context.resources, payload.fallbackImageRes)
        }
        if (payload.type == NotificationType.TRENDING_SONG) {
            return@withContext resolvedBitmap
        }
        decorateHeroBitmap(payload, resolvedBitmap)
    }

    private fun buildTrendingSongCustomNotification(
        payload: NotificationPayload,
        heroBitmap: Bitmap,
        contentIntent: PendingIntent,
        actionIntent: PendingIntent,
        deleteIntent: PendingIntent
    ): android.app.Notification {
        val collapsedView = RemoteViews(
            context.packageName,
            R.layout.notification_trending_song_collapsed
        ).apply {
            setTextViewText(R.id.tvTitle, payload.title)
            setTextViewText(R.id.tvBody, payload.body)
            setImageViewBitmap(R.id.ivThumb, scaleBitmapToFit(heroBitmap, dp(56), dp(56)))
            setOnClickPendingIntent(R.id.rootContainer, contentIntent)
            setOnClickPendingIntent(R.id.ivThumb, contentIntent)
        }

        val expandedView = RemoteViews(
            context.packageName,
            R.layout.notification_trending_song_expanded
        ).apply {
            setTextViewText(R.id.tvTitle, payload.title)
            setTextViewText(R.id.tvBody, payload.body)
            setTextViewText(R.id.tvCtaText, payload.ctaText)
            setImageViewResource(R.id.ivCtaIcon, payload.ivCtaIcon)
            setImageViewBitmap(R.id.ivHero, scaleBitmapToFit(heroBitmap, dp(384), dp(216)))
            setOnClickPendingIntent(R.id.rootContainer, contentIntent)
            setOnClickPendingIntent(R.id.ivHero, contentIntent)
            setOnClickPendingIntent(R.id.btnCta, actionIntent)
        }

        return NotificationCompat.Builder(context, payload.channelId)
            .setSmallIcon(R.drawable.app_icon_loading)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setCustomHeadsUpContentView(collapsedView)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .build()
    }

    private fun scaleBitmapToFit(source: Bitmap, maxWidthPx: Int, maxHeightPx: Int): Bitmap {
        if (maxWidthPx <= 0 || maxHeightPx <= 0) return source
        if (source.width <= maxWidthPx && source.height <= maxHeightPx) return source

        val widthScale = maxWidthPx.toFloat() / source.width.toFloat()
        val heightScale = maxHeightPx.toFloat() / source.height.toFloat()
        val scale = minOf(widthScale, heightScale)
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun dp(value: Int): Int {
        val density = context.resources.displayMetrics.density
        return (value * density).toInt()
    }

    private fun loadBitmapFromSource(source: String): Bitmap? {
        if (source.isBlank()) return null
        return runCatching {
            when {
                source.startsWith("http://", ignoreCase = true) ||
                    source.startsWith("https://", ignoreCase = true) -> {
                    val connection = URL(source).openConnection().apply {
                        connectTimeout = 3_000
                        readTimeout = 3_000
                    }
                    connection.getInputStream().use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }
                source.startsWith("content://", ignoreCase = true) ||
                    source.startsWith("file://", ignoreCase = true) -> {
                    context.contentResolver.openInputStream(Uri.parse(source)).use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }
                else -> BitmapFactory.decodeFile(source)
            }
        }.getOrNull()
    }

    private fun composeTemplateCollage(covers: List<Bitmap>): Bitmap {
        val size = 720
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawARGB(255, 15, 17, 24)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val width = size / covers.size.coerceAtLeast(1)
        covers.forEachIndexed { index, bitmap ->
            val left = index * width
            val right = if (index == covers.lastIndex) size else left + width
            val target = Rect(left, 0, right, size)
            canvas.drawBitmap(bitmap, null, target, paint)
        }
        return output
    }

    private fun decorateHeroBitmap(payload: NotificationPayload, base: Bitmap): Bitmap {
        val style = resolveHeroStyle(payload.type) ?: return base
        val output = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val density = context.resources.displayMetrics.density
        val width = output.width.toFloat()
        val height = output.height.toFloat()

        val gradientHeight = (height * 0.36f).coerceAtLeast(84f * density)
        val gradientTop = (height - gradientHeight).coerceAtLeast(0f)
        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                gradientTop,
                0f,
                height,
                Color.TRANSPARENT,
                style.gradientColor,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, gradientTop, width, height, gradientPaint)

        drawTopBadge(
            canvas = canvas,
            density = density,
            badgeText = style.badgeText,
            badgeColor = style.badgeColor
        )
        drawCta(
            canvas = canvas,
            density = density,
            ctaText = payload.ctaText.ifBlank { defaultCtaForType(payload.type) },
            ctaColor = style.ctaColor,
            ctaTextColor = style.ctaTextColor
        )
        return output
    }

    private fun resolveHeroStyle(type: NotificationType): HeroStyle? {
        return when (type) {
            NotificationType.TRENDING_SONG -> HeroStyle(
                badgeText = "TREND",
                badgeColor = Color.parseColor("#FF4C2E"),
                ctaColor = Color.parseColor("#FFD55A"),
                ctaTextColor = Color.parseColor("#111111"),
                gradientColor = Color.argb(220, 0, 0, 0)
            )

            NotificationType.VIRAL_TEMPLATE -> HeroStyle(
                badgeText = "HOT x1M",
                badgeColor = Color.parseColor("#FF6B2C"),
                ctaColor = Color.parseColor("#F5FF55"),
                ctaTextColor = Color.parseColor("#111111"),
                gradientColor = Color.argb(210, 0, 0, 0)
            )

            NotificationType.FORGOTTEN_MASTERPIECE -> HeroStyle(
                badgeText = "MASTERPIECE",
                badgeColor = Color.parseColor("#FF8A3D"),
                ctaColor = Color.parseColor("#FFFFFF"),
                ctaTextColor = Color.parseColor("#111111"),
                gradientColor = Color.argb(210, 0, 0, 0)
            )

            NotificationType.QUICK_SAVE_REMINDER -> HeroStyle(
                badgeText = "UNSAVED",
                badgeColor = Color.parseColor("#FF4C2E"),
                ctaColor = Color.parseColor("#FFD55A"),
                ctaTextColor = Color.parseColor("#111111"),
                gradientColor = Color.argb(215, 0, 0, 0)
            )

            NotificationType.SHARE_ENCOURAGEMENT -> HeroStyle(
                badgeText = "READY TO POST",
                badgeColor = Color.parseColor("#0CCF7A"),
                ctaColor = Color.parseColor("#FFFFFF"),
                ctaTextColor = Color.parseColor("#111111"),
                gradientColor = Color.argb(215, 0, 0, 0)
            )

            NotificationType.ABANDONED_SELECT_PHOTOS -> HeroStyle(
                badgeText = "ALMOST DONE",
                badgeColor = Color.parseColor("#FF8A3D"),
                ctaColor = Color.parseColor("#FFFFFF"),
                ctaTextColor = Color.parseColor("#111111"),
                gradientColor = Color.argb(215, 0, 0, 0)
            )

            NotificationType.DRAFT_COMPLETION_NUDGE -> HeroStyle(
                badgeText = "1 STEP LEFT",
                badgeColor = Color.parseColor("#FF8A00"),
                ctaColor = Color.parseColor("#FFFFFF"),
                ctaTextColor = Color.parseColor("#111111"),
                gradientColor = Color.argb(215, 0, 0, 0)
            )
        }
    }

    private fun defaultCtaForType(type: NotificationType): String {
        return when (type) {
            NotificationType.TRENDING_SONG -> "Play Song"
            NotificationType.VIRAL_TEMPLATE -> "Discover Templates"
            NotificationType.FORGOTTEN_MASTERPIECE -> "Continue Editing"
            NotificationType.QUICK_SAVE_REMINDER -> "Continue Editing"
            NotificationType.SHARE_ENCOURAGEMENT -> "Continue Editing"
            NotificationType.ABANDONED_SELECT_PHOTOS -> "View Template"
            NotificationType.DRAFT_COMPLETION_NUDGE -> "View Template"
        }
    }

    private fun drawTopBadge(
        canvas: Canvas,
        density: Float,
        badgeText: String,
        badgeColor: Int
    ) {
        val horizontalPadding = 14f * density
        val verticalPadding = 6f * density
        val startX = 14f * density
        val startY = 14f * density
        val cornerRadius = 18f * density

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f * density
            typeface = Typeface.DEFAULT_BOLD
        }
        val textWidth = textPaint.measureText(badgeText)
        val textHeight = textPaint.fontMetrics.run { descent - ascent }
        val badgeWidth = textWidth + horizontalPadding * 2f
        val badgeHeight = textHeight + verticalPadding * 2f
        val badgeRect = RectF(
            startX,
            startY,
            startX + badgeWidth,
            startY + badgeHeight
        )
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = badgeColor
        }
        canvas.drawRoundRect(badgeRect, cornerRadius, cornerRadius, badgePaint)

        val baseline = badgeRect.centerY() - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
        canvas.drawText(badgeText, badgeRect.centerX() - textWidth / 2f, baseline, textPaint)
    }

    private fun drawCta(
        canvas: Canvas,
        density: Float,
        ctaText: String,
        ctaColor: Int,
        ctaTextColor: Int
    ) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ctaTextColor
            textSize = 13f * density
            typeface = Typeface.DEFAULT_BOLD
        }
        val textWidth = textPaint.measureText(ctaText)
        val textHeight = textPaint.fontMetrics.run { descent - ascent }
        val horizontalPadding = 18f * density
        val verticalPadding = 9f * density
        val cornerRadius = 22f * density
        val marginRight = 14f * density
        val marginBottom = 14f * density
        val buttonWidth = textWidth + horizontalPadding * 2f
        val buttonHeight = textHeight + verticalPadding * 2f
        val right = canvas.width - marginRight
        val left = right - buttonWidth
        val bottom = canvas.height - marginBottom
        val top = bottom - buttonHeight

        val buttonRect = RectF(left, top, right, bottom)
        val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ctaColor
        }
        canvas.drawRoundRect(buttonRect, cornerRadius, cornerRadius, buttonPaint)

        val baseline = buttonRect.centerY() - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
        canvas.drawText(ctaText, buttonRect.centerX() - textWidth / 2f, baseline, textPaint)
    }

    private data class HeroStyle(
        val badgeText: String,
        val badgeColor: Int,
        val ctaColor: Int,
        val ctaTextColor: Int,
        val gradientColor: Int
    )

    private fun buildNotificationId(typeOrdinal: Long, seed: Long): Int {
        return NOTIFICATION_ID_BASE + ((typeOrdinal * 10_000L) + (seed % 10_000L)).toInt()
    }

    private fun buildRequestCode(seed: Long, salt: Int): Int {
        return ((seed % Int.MAX_VALUE.toLong()).toInt().absoluteValue + salt)
    }

    companion object {
        private const val NOTIFICATION_ID_BASE = 6_000
        private const val PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
