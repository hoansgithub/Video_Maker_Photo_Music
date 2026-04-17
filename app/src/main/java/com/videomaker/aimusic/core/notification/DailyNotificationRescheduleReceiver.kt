package com.videomaker.aimusic.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.context.GlobalContext
import java.time.ZonedDateTime

class DailyNotificationRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in RESCHEDULE_ACTIONS) return

        runCatching {
            val scheduler = GlobalContext.getOrNull()?.get<NotificationScheduler>() ?: return
            val now = ZonedDateTime.now()
            scheduler.rescheduleTrendingSongDaily(
                now = now,
                allowImmediateIfMissed = true
            )
            scheduler.rescheduleViralTemplateDaily(
                now = now,
                allowImmediateIfMissed = true
            )
        }
    }

    companion object {
        private val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
