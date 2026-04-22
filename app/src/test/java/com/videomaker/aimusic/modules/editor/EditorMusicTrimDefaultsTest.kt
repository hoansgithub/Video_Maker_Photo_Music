package com.videomaker.aimusic.modules.editor

import com.videomaker.aimusic.domain.model.MusicSong
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorMusicTrimDefaultsTest {

    @Test
    fun `null song falls back to start at zero`() {
        assertEquals(0L, resolveDefaultMusicTrimStartMs(song = null))
    }

    @Test
    fun `positive hook without duration keeps hook value`() {
        val song = baseSong(
            hookStartTimeMs = 12_500L,
            durationMs = null
        )

        assertEquals(12_500L, resolveDefaultMusicTrimStartMs(song))
    }

    @Test
    fun `hook beyond duration clamps inside song range`() {
        val song = baseSong(
            hookStartTimeMs = 12_000L,
            durationMs = 10_000
        )

        assertEquals(9_999L, resolveDefaultMusicTrimStartMs(song))
    }

    @Test
    fun `negative hook clamps to zero`() {
        val song = baseSong(
            hookStartTimeMs = -250L,
            durationMs = 8_000
        )

        assertEquals(0L, resolveDefaultMusicTrimStartMs(song))
    }

    private fun baseSong(
        hookStartTimeMs: Long,
        durationMs: Int?
    ) = MusicSong(
        id = 1L,
        name = "Song",
        artist = "Artist",
        mp3Url = "https://example.com/song.mp3",
        durationMs = durationMs,
        hookStartTimeMs = hookStartTimeMs
    )
}
