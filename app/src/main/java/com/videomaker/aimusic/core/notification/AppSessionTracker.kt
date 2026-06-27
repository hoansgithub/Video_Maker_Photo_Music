package com.videomaker.aimusic.core.notification

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.core.constants.RemoteConfigKeys
import com.videomaker.aimusic.core.data.local.PreferencesManager
import java.time.ZonedDateTime

class AppSessionTracker(
    private val preferencesManager: PreferencesManager,
    private val notificationScheduler: NotificationScheduler,
    private val remoteConfig: RemoteConfig
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        preferencesManager.bumpAppSessionId()
        // Arm the OB-resume work WHILE the app is alive & foreground, so it is already
        // persisted in WorkManager before the user can swipe-kill the app. The worker
        // itself skips firing while the app is in the foreground (see OnboardingResumeWorker).
        refreshOnboardingResume()
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
        // Refresh the timer on a clean background (e.g. Home press) for accurate "5 min after
        // leaving" timing. On a hard swipe-kill this may not run — that's fine, the onStart-armed
        // work already persisted and will fire.
        refreshOnboardingResume()
    }

    /**
     * Arms (or refreshes) the next OB-resume notification, or cancels any pending one when
     * nothing should fire (onboarding finished, feature disabled, or all attempts done).
     * Visible for testing of the pure decision lives in [OnboardingResumeNotifications].
     */
    internal fun refreshOnboardingResume() {
        val resolution = OnboardingResumeNotifications.resolveScheduleRequest(
            onboardingComplete = preferencesManager.isOnboardingComplete(),
            enabled = remoteConfig.getBoolean(RemoteConfigKeys.OB_RESUME_NOTI_ENABLED, true),
            firedCount = preferencesManager.obResumeFiredCount,
            delayMinutesFor = { attempt ->
                remoteConfig.getLong(
                    OnboardingResumeNotifications.delayMinutesKey(attempt),
                    OnboardingResumeNotifications.DEFAULT_DELAY_MINUTES
                )
            }
        )
        // Persist any attempts skipped because their delay is configured to 0 (disabled).
        if (resolution.firedCount != preferencesManager.obResumeFiredCount) {
            preferencesManager.obResumeFiredCount = resolution.firedCount
        }
        val request = resolution.request
        if (request == null) {
            // OB complete / disabled / all fired → drop any pending work.
            notificationScheduler.cancelOnboardingResume()
            return
        }
        notificationScheduler.scheduleOnboardingResume(attempt = request.attempt, delayMs = request.delayMs)
    }
}
