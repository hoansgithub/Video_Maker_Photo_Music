package com.videomaker.aimusic.core.notification

import java.time.ZonedDateTime

class SchedulerRescheduleController(
    private val scheduler: NotificationScheduler
) : NotificationRescheduleController {

    override fun rescheduleDaily(now: ZonedDateTime) {
        scheduler.rescheduleTrendingSongDaily(now)
        scheduler.rescheduleViralTemplateDaily(now)
    }

    override fun rescheduleVideoReminders(projectId: String, generatedAtMs: Long) {
        scheduler.cancelProjectReminders(projectId)
        scheduler.scheduleQuickSaveReminder(projectId, generatedAtMs)
        scheduler.scheduleShareEncouragement(projectId, generatedAtMs)
        scheduler.scheduleForgottenMasterpiece(projectId, generatedAtMs)
    }

    override fun rescheduleDraftReminders(draftId: String, exitSessionId: Long, exitedAtMs: Long) {
        scheduler.cancelDraftReminders(draftId)
        scheduler.scheduleAbandonedSelectPhotos(draftId, exitSessionId, exitedAtMs)
        scheduler.scheduleDraftCompletionNudge(draftId, exitedAtMs)
    }
}
