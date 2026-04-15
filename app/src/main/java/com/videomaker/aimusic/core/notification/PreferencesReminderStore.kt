package com.videomaker.aimusic.core.notification

import com.videomaker.aimusic.core.data.local.PreferencesManager

/**
 * Reconciler-focused read/remove adapter over PreferencesManager.
 *
 * Scheduling add-path remains in the scheduler layer by design.
 */
class PreferencesReminderStore(
    private val prefs: PreferencesManager
) : NotificationReminderStore {

    override fun getActiveVideoReminderIds(): Set<String> = prefs.getActiveVideoReminderIds()

    override fun getActiveDraftReminderIds(): Set<String> = prefs.getActiveDraftReminderIds()

    override fun getVideoReminderState(projectId: String): PreferencesManager.VideoReminderState? {
        return prefs.getVideoReminderState(projectId)
    }

    override fun getDraftReminderState(draftId: String): PreferencesManager.DraftReminderState? {
        return prefs.getDraftReminderState(draftId)
    }

    override fun removeActiveVideoReminderId(projectId: String) {
        prefs.removeActiveVideoReminderId(projectId)
    }

    override fun removeActiveDraftReminderId(draftId: String) {
        prefs.removeActiveDraftReminderId(draftId)
    }
}
