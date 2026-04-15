package com.videomaker.aimusic.core.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationScheduleConfigServiceTest {

    @Test
    fun `callback fires on first apply and later effective changes only`() {
        var callbackCount = 0
        val service = NotificationScheduleConfigService(
            onEffectiveConfigChanged = { callbackCount++ }
        )

        service.applyRawJsonForTesting(null)
        assertEquals(1, callbackCount)
        assertEquals(NotificationScheduleConfig(), service.current())

        service.applyRawJsonForTesting(null)
        assertEquals(1, callbackCount)

        service.applyRawJsonForTesting(
            """
            {
              "trending_song_daily": {"hour": 21, "minute": 7},
              "viral_template_daily": {"hour": 20, "minute": 2},
              "quick_save_delay_minutes": 30,
              "share_encouragement_delay_minutes": 720,
              "forgotten_masterpiece_delay_minutes": 1440,
              "abandoned_same_session_delay_minutes": 2,
              "abandoned_cold_session_delay_minutes": 15,
              "draft_completion_nudge_delay_minutes": 15
            }
            """.trimIndent()
        )
        assertEquals(2, callbackCount)
        assertEquals(21, service.current().trendingHour)
        assertEquals(7, service.current().trendingMinute)

        service.applyRawJsonForTesting(
            """
            {
              "trending_song_daily": {"hour": 21, "minute": 7},
              "viral_template_daily": {"hour": 20, "minute": 2},
              "quick_save_delay_minutes": 30,
              "share_encouragement_delay_minutes": 720,
              "forgotten_masterpiece_delay_minutes": 1440,
              "abandoned_same_session_delay_minutes": 2,
              "abandoned_cold_session_delay_minutes": 15,
              "draft_completion_nudge_delay_minutes": 15
            }
            """.trimIndent()
        )
        assertEquals(2, callbackCount)
    }
}
