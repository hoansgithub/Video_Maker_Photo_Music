package com.videomaker.aimusic.core.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDeepLinkFactoryTest {

    @Test
    fun `factory builds trending-song contract`() {
        val deepLink = NotificationDeepLinkFactory.trendingSong(songId = 99L)

        assertEquals(NotificationDeepLinkFactory.ACTION_NOTIF_TRENDING_SONG, deepLink.action)
        assertEquals(99L, deepLink.songId)
    }

    @Test
    fun `factory builds my-video contract`() {
        val deepLink = NotificationDeepLinkFactory.myVideo(
            projectId = "p_123",
            hintMode = "hint_share"
        )

        assertEquals(NotificationDeepLinkFactory.ACTION_NOTIF_MY_VIDEO, deepLink.action)
        assertEquals("p_123", deepLink.projectId)
        assertEquals("hint_share", deepLink.hintMode)
    }
}
