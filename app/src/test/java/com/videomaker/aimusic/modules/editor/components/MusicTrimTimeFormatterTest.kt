package com.videomaker.aimusic.modules.editor.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicTrimTimeFormatterTest {

    @Test
    fun `0 milliseconds formats to 0 colon 00`() {
        assertEquals("0:00", formatMusicTrimTime(0L))
    }

    @Test
    fun `499 milliseconds rounds down`() {
        assertEquals("0:00", formatMusicTrimTime(499L))
    }

    @Test
    fun `500 milliseconds rounds up`() {
        assertEquals("0:01", formatMusicTrimTime(500L))
    }

    @Test
    fun `65499 milliseconds formats to 1 colon 05`() {
        assertEquals("1:05", formatMusicTrimTime(65_499L))
    }

    @Test
    fun `65500 milliseconds formats to 1 colon 06`() {
        assertEquals("1:06", formatMusicTrimTime(65_500L))
    }
}
