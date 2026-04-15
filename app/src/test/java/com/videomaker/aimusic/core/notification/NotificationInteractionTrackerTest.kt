package com.videomaker.aimusic.core.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationInteractionTrackerTest {

    @Test
    fun `consume returns true once after mark clicked`() {
        val notificationId = "notif_9001"

        NotificationInteractionTracker.markClicked(notificationId)

        assertTrue(NotificationInteractionTracker.consumeIfClicked(notificationId))
        assertFalse(NotificationInteractionTracker.consumeIfClicked(notificationId))
    }
}
