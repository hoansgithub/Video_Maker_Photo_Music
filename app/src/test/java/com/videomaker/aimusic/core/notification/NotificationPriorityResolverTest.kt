package com.videomaker.aimusic.core.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPriorityResolverTest {

    @Test
    fun `priority resolver picks quick-save over trending`() {
        val result = NotificationPriorityResolver.resolve(
            listOf(
                NotificationType.TRENDING_SONG,
                NotificationType.QUICK_SAVE_REMINDER
            )
        )

        assertEquals(NotificationType.QUICK_SAVE_REMINDER, result)
    }
}
