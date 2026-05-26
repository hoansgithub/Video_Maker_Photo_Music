package com.videomaker.aimusic.modules.songs

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class MusicPlayerQueueLogicTest {

    private fun song(id: Long) = MusicSong(
        id = id, name = "S$id", artist = "A", durationMs = 1000, usageCount = 0
    )

    // ── findNextUnimpressedSong ─────────────────────────────────────

    @Test
    fun `findNextUnimpressedSong returns first non-current, non-impressed song`() {
        val queue = listOf(song(1), song(2), song(3))
        val result = findNextUnimpressedSong(queue, currentId = 1L, impressed = emptySet())
        assertEquals(2L, result?.id)
    }

    @Test
    fun `findNextUnimpressedSong skips impressed songs`() {
        val queue = listOf(song(1), song(2), song(3))
        val result = findNextUnimpressedSong(queue, currentId = 1L, impressed = setOf(2L))
        assertEquals(3L, result?.id)
    }

    @Test
    fun `findNextUnimpressedSong skips current even if not impressed`() {
        val queue = listOf(song(1), song(2))
        val result = findNextUnimpressedSong(queue, currentId = 2L, impressed = emptySet())
        assertEquals(1L, result?.id)
    }

    @Test
    fun `findNextUnimpressedSong returns null when all queue songs are impressed`() {
        val queue = listOf(song(1), song(2))
        val result = findNextUnimpressedSong(queue, currentId = 1L, impressed = setOf(2L))
        assertNull(result)
    }

    @Test
    fun `findNextUnimpressedSong returns null on empty queue`() {
        val result = findNextUnimpressedSong(emptyList(), currentId = 1L, impressed = emptySet())
        assertNull(result)
    }

    // ── pickRandomAvailableGenre ───────────────────────────────────

    @Test
    fun `pickRandomAvailableGenre excludes used genres`() {
        val all = listOf(
            SongGenre("pop", "Pop"),
            SongGenre("rock", "Rock"),
            SongGenre("jazz", "Jazz")
        )
        val result = pickRandomAvailableGenre(
            allGenres = all,
            usedGenres = setOf("pop", "rock"),
            initialGenre = null,
            random = Random(0)
        )
        assertEquals("jazz", result?.id)
    }

    @Test
    fun `pickRandomAvailableGenre excludes initial genre`() {
        val all = listOf(SongGenre("pop", "Pop"), SongGenre("rock", "Rock"))
        val result = pickRandomAvailableGenre(
            allGenres = all,
            usedGenres = emptySet(),
            initialGenre = "pop",
            random = Random(0)
        )
        assertEquals("rock", result?.id)
    }

    @Test
    fun `pickRandomAvailableGenre returns null when all exhausted`() {
        val all = listOf(SongGenre("pop", "Pop"))
        val result = pickRandomAvailableGenre(
            allGenres = all,
            usedGenres = setOf("pop"),
            initialGenre = null,
            random = Random(0)
        )
        assertNull(result)
    }

    @Test
    fun `pickRandomAvailableGenre returns null on empty input`() {
        val result = pickRandomAvailableGenre(
            allGenres = emptyList(),
            usedGenres = emptySet(),
            initialGenre = null,
            random = Random(0)
        )
        assertNull(result)
    }

    // ── appendCapped ──────────────────────────────────────────────

    @Test
    fun `appendCapped under cap returns concatenation`() {
        val result = appendCapped(listOf(song(1), song(2)), listOf(song(3)), cap = 10)
        assertEquals(listOf(1L, 2L, 3L), result.map { it.id })
    }

    @Test
    fun `appendCapped at cap drops oldest from front`() {
        val result = appendCapped(
            existing = listOf(song(1), song(2), song(3)),
            toAppend = listOf(song(4), song(5)),
            cap = 3
        )
        assertEquals(listOf(3L, 4L, 5L), result.map { it.id })
    }

    @Test
    fun `appendCapped large append truncates to cap`() {
        val result = appendCapped(
            existing = listOf(song(1)),
            toAppend = (10L..15L).map { song(it) },
            cap = 3
        )
        assertEquals(listOf(13L, 14L, 15L), result.map { it.id })
    }

    // ── pickReplaySong ─────────────────────────────────────────────

    @Test
    fun `pickReplaySong returns first initialPlaylist song not equal to current`() {
        val initial = listOf(song(1), song(2), song(3))
        val result = pickReplaySong(initial, currentId = 1L)
        assertEquals(2L, result?.id)
    }

    @Test
    fun `pickReplaySong returns current when initial list has only current`() {
        val initial = listOf(song(7))
        val result = pickReplaySong(initial, currentId = 7L)
        assertEquals(7L, result?.id)
    }

    @Test
    fun `pickReplaySong returns null on empty initial list`() {
        val result = pickReplaySong(emptyList(), currentId = 1L)
        assertNull(result)
    }
}
