package com.videomaker.aimusic.core.notification

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.videomaker.aimusic.core.data.local.PreferencesManager
import java.time.ZonedDateTime

class AppSessionTracker(
    private val preferencesManager: PreferencesManager,
    private val notificationScheduler: NotificationScheduler
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        preferencesManager.bumpAppSessionId()
        // Recompute daily schedule on every foreground entry so clock/date changes
        // while the app is alive don't keep stale delayed works.
        val now = ZonedDateTime.now()
        notificationScheduler.rescheduleTrendingSongDaily(
            now = now,
            allowImmediateIfMissed = true
        )
        notificationScheduler.rescheduleViralTemplateDaily(
            now = now,
            allowImmediateIfMissed = true
        )
    }

    override fun onStop(owner: LifecycleOwner) {
        preferencesManager.setLastAppBackgroundAtMs(System.currentTimeMillis())
    }
}
