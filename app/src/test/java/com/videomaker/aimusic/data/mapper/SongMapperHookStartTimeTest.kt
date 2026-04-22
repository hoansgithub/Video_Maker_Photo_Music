package com.videomaker.aimusic.data.mapper

import com.videomaker.aimusic.data.remote.dto.SongDto
import org.junit.Assert.assertEquals
import org.junit.Test

class SongMapperHookStartTimeTest {

    @Test
    fun `null hook start time maps to zero`() {
        val song = baseDto(hookStartTimeMs = null).toMusicSong()

        assertEquals(0L, song.hookStartTimeMs)
    }

    @Test
    fun `negative hook start time maps to zero`() {
        val song = baseDto(hookStartTimeMs = -1500L).toMusicSong()

        assertEquals(0L, song.hookStartTimeMs)
    }

    @Test
    fun `positive hook start time is preserved`() {
        val song = baseDto(hookStartTimeMs = 12_345L).toMusicSong()

        assertEquals(12_345L, song.hookStartTimeMs)
    }

    private fun baseDto(hookStartTimeMs: Long?) = SongDto(
        id = 1L,
        name = "Song",
        artist = "Artist",
        mp3Url = "https://example.com/song.mp3",
        hookStartTimeMs = hookStartTimeMs
    )
}
