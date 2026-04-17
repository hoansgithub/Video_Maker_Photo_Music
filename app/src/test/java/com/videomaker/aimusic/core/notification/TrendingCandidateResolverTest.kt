package com.videomaker.aimusic.core.notification

import com.videomaker.aimusic.domain.model.MusicSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TrendingCandidateResolverTest {

    @Test
    fun `trending resolver picks top song when changed from snapshot`() {
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
    fun `trending resolver still picks top song when usage counts are low`() {
        val songs = listOf(
            MusicSong(
                id = 31L,
                name = "Low Usage Top",
                artist = "Artist D",
                usageCount = 8_500
            ),
            MusicSong(
                id = 32L,
                name = "Low Usage Second",
                artist = "Artist E",
                usageCount = 7_200
            )
        )

        val result = TrendingCandidateResolver.resolve(
            featuredSongs = songs,
            snapshot = null,
            currentLocalDate = "2026-04-15"
        )

        assertNotNull(result)
        assertEquals(31L, result?.id)
    }

    @Test
    fun `same top song can still be selected and daily cap handles duplicate prevention`() {
        val songs = listOf(
            MusicSong(
                id = 21L,
                name = "Repeatable Song",
                artist = "Artist C",
                usageCount = 150_000
            )
        )

        val today = "2026-04-15"
        val selectedToday = TrendingCandidateResolver.resolve(
            featuredSongs = songs,
            snapshot = TrendingCandidateResolver.DailySnapshot(
                localDate = today,
                songId = 21L,
                usageCount = 140_000
            ),
            currentLocalDate = today
        )
        val selectedNextDay = TrendingCandidateResolver.resolve(
            featuredSongs = songs,
            snapshot = TrendingCandidateResolver.DailySnapshot(
                localDate = "2026-04-14",
                songId = 21L,
                usageCount = 140_000
            ),
            currentLocalDate = today
        )

        assertNotNull(selectedToday)
        assertEquals(21L, selectedToday?.id)
        assertNotNull(selectedNextDay)
        assertEquals(21L, selectedNextDay?.id)
    }
}
