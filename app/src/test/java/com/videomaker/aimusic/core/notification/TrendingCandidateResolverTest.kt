package com.videomaker.aimusic.core.notification

import com.videomaker.aimusic.domain.model.MusicSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TrendingCandidateResolverTest {

    @Test
    fun `trending resolver picks top song when usage at least 100k and changed from snapshot`() {
        val songs = listOf(
            MusicSong(
                id = 11L,
                name = "Top Song",
                artist = "Artist A",
                usageCount = 160_000
            ),
            MusicSong(
                id = 12L,
                name = "Second Song",
                artist = "Artist B",
                usageCount = 120_000
            )
        )

        val result = TrendingCandidateResolver.resolve(
            featuredSongs = songs,
            snapshot = TrendingCandidateResolver.DailySnapshot(
                localDate = "2026-04-14",
                songId = 10L,
                usageCount = 150_000
            ),
            currentLocalDate = "2026-04-15"
        )

        assertNotNull(result)
        assertEquals(11L, result?.id)
    }

    @Test
    fun `same song is only suppressed for the same local day`() {
        val songs = listOf(
            MusicSong(
                id = 21L,
                name = "Repeatable Song",
                artist = "Artist C",
                usageCount = 150_000
            )
        )

        val today = "2026-04-15"
        val blockedToday = TrendingCandidateResolver.resolve(
            featuredSongs = songs,
            snapshot = TrendingCandidateResolver.DailySnapshot(
                localDate = today,
                songId = 21L,
                usageCount = 140_000
            ),
            currentLocalDate = today
        )
        val allowedNextDay = TrendingCandidateResolver.resolve(
            featuredSongs = songs,
            snapshot = TrendingCandidateResolver.DailySnapshot(
                localDate = "2026-04-14",
                songId = 21L,
                usageCount = 140_000
            ),
            currentLocalDate = today
        )

        assertNull(blockedToday)
        assertNotNull(allowedNextDay)
        assertEquals(21L, allowedNextDay?.id)
    }
}
