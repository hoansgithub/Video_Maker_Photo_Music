package com.videomaker.aimusic.core.notification

import com.videomaker.aimusic.core.data.local.PreferencesManager
import java.time.ZonedDateTime

interface NotificationReminderStore {
    fun getActiveVideoReminderIds(): Set<String>
    fun getActiveDraftReminderIds(): Set<String>
    fun getVideoReminderState(projectId: String): PreferencesManager.VideoReminderState?
    fun getDraftReminderState(draftId: String): PreferencesManager.DraftReminderState?
    fun removeActiveVideoReminderId(projectId: String)
    fun removeActiveDraftReminderId(draftId: String)

}

interface NotificationRescheduleController {
    fun rescheduleDaily(now: ZonedDateTime)
    fun rescheduleVideoReminders(projectId: String, generatedAtMs: Long)
    fun rescheduleDraftReminders(draftId: String, exitSessionId: Long, exitedAtMs: Long)
}

class NotificationScheduleReconciler(
    private val store: NotificationReminderStore,
    private val controller: NotificationRescheduleController
) {
    fun reconcileOnConfigChanged(now: ZonedDateTime) {
        controller.rescheduleDaily(now)

        store.getActiveVideoReminderIds().toList().forEach { projectId ->
            val state = store.getVideoReminderState(projectId)
            if (state == null || state.savedAtMs != null || state.sharedAtMs != null) {
                store.removeActiveVideoReminderId(projectId)
                return@forEach
            }

            controller.rescheduleVideoReminders(projectId, state.generatedAtMs)
        }

        store.getActiveDraftReminderIds().toList().forEach { draftId ->
            val state = store.getDraftReminderState(draftId)
            val invalid = state == null ||
                state.selectedPhotoCount > 0 ||
                (state.templateId.isNullOrBlank() && state.songId == null)

            if (invalid) {
                store.removeActiveDraftReminderId(draftId)
                return@forEach
            }

            controller.rescheduleDraftReminders(
                draftId = draftId,
                exitSessionId = state.exitSessionId,
                exitedAtMs = state.exitedAtMs
            )
        }
    }
}
