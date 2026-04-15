package com.videomaker.aimusic.core.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationScheduleConfigParserTest {

    @Test
    fun `null json returns defaults`() {
        val result = NotificationScheduleConfigParser.parse(null)

        assertEquals(NotificationScheduleConfig(), result)
    }

    @Test
    fun `malformed json returns defaults`() {
        val result = NotificationScheduleConfigParser.parse("{")

        assertEquals(NotificationScheduleConfig(), result)
    }

    @Test
    fun `partial invalid fields keep valid values and fall back per field`() {
        val result = NotificationScheduleConfigParser.parse(
            """
            {
              "trending_song_daily": {"hour": 21, "minute": 99},
              "viral_template_daily": {"hour": 24, "minute": 15},
              "quick_save_delay_minutes": 31,
              "share_encouragement_delay_minutes": 721,
              "forgotten_masterpiece_delay_minutes": 1441,
              "abandoned_same_session_delay_minutes": 3,
              "abandoned_cold_session_delay_minutes": 16,
              "draft_completion_nudge_delay_minutes": 16
            }
            """.trimIndent()
        )

        assertEquals(21, result.trendingHour)
        assertEquals(NotificationScheduleConfig.DEFAULT_TRENDING_MINUTE, result.trendingMinute)
        assertEquals(NotificationScheduleConfig.DEFAULT_VIRAL_HOUR, result.viralHour)
        assertEquals(15, result.viralMinute)
        assertEquals(31L * 60_000L, result.quickSaveDelayMs)
        assertEquals(721L * 60_000L, result.shareEncouragementDelayMs)
        assertEquals(1441L * 60_000L, result.forgottenMasterpieceDelayMs)
        assertEquals(3L * 60_000L, result.abandonedSameDelayMs)
        assertEquals(16L * 60_000L, result.abandonedColdDelayMs)
        assertEquals(16L * 60_000L, result.draftCompletionDelayMs)
    }

    @Test
    fun `delay boundaries are validated and converted`() {
        val result = NotificationScheduleConfigParser.parse(
            """
            {
              "quick_save_delay_minutes": 0,
              "share_encouragement_delay_minutes": 10080,
              "forgotten_masterpiece_delay_minutes": 10081
            }
            """.trimIndent()
        )

        assertEquals(0L, result.quickSaveDelayMs)
        assertEquals(10080L * 60_000L, result.shareEncouragementDelayMs)
        assertEquals(NotificationScheduleConfig.DEFAULT_FORGOTTEN_MASTERPIECE_DELAY_MS, result.forgottenMasterpieceDelayMs)
    }

    @Test
    fun `fingerprint changes when config changes`() {
        val baseline = NotificationScheduleConfigParser.parse(
            """
            {
              "trending_song_daily": {"hour": 19, "minute": 2},
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
        val changed = NotificationScheduleConfigParser.parse(
            """
            {
              "trending_song_daily": {"hour": 20, "minute": 2},
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

        assertNotEquals(baseline.fingerprint(), changed.fingerprint())
    }
}
