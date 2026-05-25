package com.videomaker.aimusic.core.playback

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPlaybackSessionManagerTest {

    @Test
    fun `markImpressed adds id to set`() = runBlocking {
        val mgr = MusicPlaybackSessionManager()
        mgr.markImpressed(1L)
        mgr.markImpressed(2L)
        assertEquals(setOf(1L, 2L), mgr.impressedSongIds.first())
    }

    @Test
    fun `markImpressed dedupes`() = runBlocking {
        val mgr = MusicPlaybackSessionManager()
        mgr.markImpressed(5L)
        mgr.markImpressed(5L)
        assertEquals(setOf(5L), mgr.impressedSongIds.first())
    }

    @Test
    fun `markGenreUsed accumulates`() = runBlocking {
        val mgr = MusicPlaybackSessionManager()
        mgr.markGenreUsed("pop")
        mgr.markGenreUsed("rock")
        assertEquals(setOf("pop", "rock"), mgr.usedGenreIds.first())
    }

    @Test
    fun `resetUsedGenres clears only genres not impressed`() = runBlocking {
        val mgr = MusicPlaybackSessionManager()
        mgr.markImpressed(10L)
        mgr.markGenreUsed("pop")
        mgr.resetUsedGenres()
        assertEquals(emptySet<String>(), mgr.usedGenreIds.first())
        assertEquals(setOf(10L), mgr.impressedSongIds.first())
    }

    @Test
    fun `resetSession clears both sets`() = runBlocking {
        val mgr = MusicPlaybackSessionManager()
        mgr.markImpressed(10L)
        mgr.markGenreUsed("pop")
        mgr.resetSession()
        assertTrue(mgr.impressedSongIds.first().isEmpty())
        assertTrue(mgr.usedGenreIds.first().isEmpty())
    }
}
