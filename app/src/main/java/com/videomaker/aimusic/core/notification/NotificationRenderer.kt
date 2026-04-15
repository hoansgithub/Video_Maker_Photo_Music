package com.videomaker.aimusic.core.notification

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
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
        val notification = NotificationCompat.Builder(context, payload.channelId)
            .setSmallIcon(R.drawable.ic_notification_outline)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setLargeIcon(heroBitmap)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(heroBitmap)
                    .setSummaryText(payload.body)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .addAction(
                R.drawable.ic_notification_outline,
                payload.ctaText,
                actionIntent
            )
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        }.getOrElse { false }
    }

    private suspend fun loadHeroBitmap(payload: NotificationPayload): Bitmap = withContext(Dispatchers.IO) {
        if (payload.type == NotificationType.VIRAL_TEMPLATE && payload.imageCandidates.size > 1) {
            val bitmaps = payload.imageCandidates.take(3).mapNotNull { loadBitmapFromSource(it) }
            if (bitmaps.isNotEmpty()) {
                return@withContext composeTemplateCollage(bitmaps)
            }
        }

        payload.imageCandidates.forEach { source ->
            loadBitmapFromSource(source)?.let { return@withContext it }
        }
        BitmapFactory.decodeResource(context.resources, payload.fallbackImageRes)
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
