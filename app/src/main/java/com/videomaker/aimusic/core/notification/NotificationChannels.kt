package com.videomaker.aimusic.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class NotificationChannels {

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(
                CHANNEL_TREND_ALERTS,
                "Trend Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Trending songs and viral template alerts"
            },
            NotificationChannel(
                CHANNEL_MY_VIDEO_RETENTION,
                "My Video Retention",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Retention nudges for saved and unfinished videos"
            },
            NotificationChannel(
                CHANNEL_CREATION_REMINDERS,
                "Creation Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to return and create a new video"
            }
        )
        manager.createNotificationChannels(channels)
    }

    companion object {
        const val CHANNEL_TREND_ALERTS = "channel_trend_alerts"
        const val CHANNEL_MY_VIDEO_RETENTION = "channel_my_video_retention"
        const val CHANNEL_CREATION_REMINDERS = "channel_creation_reminders"
    }
}
