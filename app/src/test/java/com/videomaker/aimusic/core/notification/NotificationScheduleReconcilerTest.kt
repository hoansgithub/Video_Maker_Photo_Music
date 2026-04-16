package com.videomaker.aimusic.core.notification

import com.videomaker.aimusic.core.data.local.PreferencesManager
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationScheduleReconcilerTest {

    @Test
    fun `reconcile reschedules daily and valid one-time reminders`() {
        val store = FakeNotificationReminderStore(
            videoStates = linkedMapOf(
                "video_1" to PreferencesManager.VideoReminderState(
                    projectId = "video_1",
                    generatedAtMs = 111L,
                    templateId = "template_a",
                    songId = null,
                    thumbnailUri = null,
                    savedAtMs = null,
                    sharedAtMs = null,
                    lastOpenedAtMs = null
                )
            ),
            draftStates = linkedMapOf(
                "draft_1" to PreferencesManager.DraftReminderState(
                    draftId = "draft_1",
                    templateId = "template_a",
                    songId = null,
                    exitedAtMs = 222L,
                    exitSessionId = 333L,
                    selectedPhotoCount = 0,
                    lastAbandonedShownAtMs = null,
                    lastDraftNudgeShownAtMs = null
                )
            )
        )
        val controller = FakeNotificationRescheduleController()
        val reconciler = NotificationScheduleReconciler(store, controller)
        val now = ZonedDateTime.of(2026, 4, 15, 10, 30, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"))

        reconciler.reconcileOnConfigChanged(now)

        assertEquals(listOf(now), controller.dailyReschedules)
        assertEquals(listOf("video_1" to 111L), controller.videoReschedules)
        assertEquals(listOf("draft_1" to (333L to 222L)), controller.draftReschedules)
        assertEquals(emptyList<String>(), store.removedVideoIds)
        assertEquals(emptyList<String>(), store.removedDraftIds)
    }

    @Test
    fun `reconcile prunes stale ids`() {
        val store = FakeNotificationReminderStore(
            videoStates = linkedMapOf(
                "video_missing" to null,
                "video_saved" to PreferencesManager.VideoReminderState(
                    projectId = "video_saved",
                    generatedAtMs = 111L,
                    templateId = null,
                    songId = null,
                    thumbnailUri = null,
                    savedAtMs = 555L,
                    sharedAtMs = null,
                    lastOpenedAtMs = null
                ),
                "video_shared" to PreferencesManager.VideoReminderState(
                    projectId = "video_shared",
                    generatedAtMs = 222L,
                    templateId = null,
                    songId = null,
                    thumbnailUri = null,
                    savedAtMs = null,
                    sharedAtMs = 666L,
                    lastOpenedAtMs = null
                ),
                "video_valid" to PreferencesManager.VideoReminderState(
                    projectId = "video_valid",
                    generatedAtMs = 777L,
                    templateId = null,
                    songId = null,
                    thumbnailUri = null,
                    savedAtMs = null,
                    sharedAtMs = null,
                    lastOpenedAtMs = null
                )
            ),
            draftStates = linkedMapOf(
                "draft_missing" to null,
                "draft_selected" to PreferencesManager.DraftReminderState(
                    draftId = "draft_selected",
                    templateId = "template_a",
                    songId = null,
                    exitedAtMs = 123L,
                    exitSessionId = 456L,
                    selectedPhotoCount = 1,
                    lastAbandonedShownAtMs = null,
                    lastDraftNudgeShownAtMs = null
                ),
                "draft_blank" to PreferencesManager.DraftReminderState(
                    draftId = "draft_blank",
                    templateId = "   ",
                    songId = null,
                    exitedAtMs = 321L,
                    exitSessionId = 654L,
                    selectedPhotoCount = 0,
                    lastAbandonedShownAtMs = null,
                    lastDraftNudgeShownAtMs = null
                ),
                "draft_valid" to PreferencesManager.DraftReminderState(
                    draftId = "draft_valid",
                    templateId = null,
                    songId = 42L,
                    exitedAtMs = 888L,
                    exitSessionId = 999L,
                    selectedPhotoCount = 0,
                    lastAbandonedShownAtMs = null,
                    lastDraftNudgeShownAtMs = null
                )
            )
        )
        val controller = FakeNotificationRescheduleController()
        val reconciler = NotificationScheduleReconciler(store, controller)
        val now = ZonedDateTime.of(2026, 4, 15, 10, 30, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"))

        reconciler.reconcileOnConfigChanged(now)

        assertEquals(setOf("video_missing", "video_saved", "video_shared"), store.removedVideoIds.toSet())
        assertEquals(setOf("draft_missing", "draft_selected", "draft_blank"), store.removedDraftIds.toSet())
        assertEquals(listOf("video_valid" to 777L), controller.videoReschedules)
        assertEquals(listOf("draft_valid" to (999L to 888L)), controller.draftReschedules)
    }

    private class FakeNotificationRescheduleController : NotificationRescheduleController {
        val dailyReschedules = mutableListOf<ZonedDateTime>()
        val videoReschedules = mutableListOf<Pair<String, Long>>()
        val draftReschedules = mutableListOf<Pair<String, Pair<Long, Long>>>()

        override fun rescheduleDaily(now: ZonedDateTime) {
            dailyReschedules += now
        }

        override fun rescheduleVideoReminders(projectId: String, generatedAtMs: Long) {
            videoReschedules += projectId to generatedAtMs
        }

        override fun rescheduleDraftReminders(draftId: String, exitSessionId: Long, exitedAtMs: Long) {
            draftReschedules += draftId to (exitSessionId to exitedAtMs)
        }
    }

    private class FakeNotificationReminderStore(
        videoStates: Map<String, PreferencesManager.VideoReminderState?> = emptyMap(),
        draftStates: Map<String, PreferencesManager.DraftReminderState?> = emptyMap()
    ) : NotificationReminderStore {
        private val videoStates = linkedMapOf<String, PreferencesManager.VideoReminderState?>().apply {
            putAll(videoStates)
        }
        private val draftStates = linkedMapOf<String, PreferencesManager.DraftReminderState?>().apply {
            putAll(draftStates)
        }

        val removedVideoIds = mutableListOf<String>()
        val removedDraftIds = mutableListOf<String>()

        override fun getActiveVideoReminderIds(): Set<String> = videoStates.keys

        override fun getVideoReminderState(projectId: String): PreferencesManager.VideoReminderState? {
            return videoStates[projectId]
        }

        override fun removeActiveVideoReminderId(projectId: String) {
            removedVideoIds += projectId
            videoStates.remove(projectId)
        }

        override fun getActiveDraftReminderIds(): Set<String> = draftStates.keys

        override fun getDraftReminderState(draftId: String): PreferencesManager.DraftReminderState? {
            return draftStates[draftId]
        }

        override fun removeActiveDraftReminderId(draftId: String) {
            removedDraftIds += draftId
            draftStates.remove(draftId)
        }
    }
}
