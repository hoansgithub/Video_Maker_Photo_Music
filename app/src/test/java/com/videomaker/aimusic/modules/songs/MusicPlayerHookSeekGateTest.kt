package com.videomaker.aimusic.modules.songs

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPlayerHookSeekGateTest {

    @Test
    fun `apply hook seek on first ready only`() {
        assertTrue(
            shouldApplyMusicPlayerHookSeek(
                playbackState = Player.STATE_READY,
                hasAppliedHookSeek = false
            )
        )
        assertFalse(
            shouldApplyMusicPlayerHookSeek(
                playbackState = Player.STATE_READY,
                hasAppliedHookSeek = true
            )
        )
    }

    @Test
    fun `ignore non ready states`() {
        assertFalse(
            shouldApplyMusicPlayerHookSeek(
                playbackState = Player.STATE_BUFFERING,
                hasAppliedHookSeek = false
            )
        )
        assertFalse(
            shouldApplyMusicPlayerHookSeek(
                playbackState = Player.STATE_ENDED,
                hasAppliedHookSeek = false
            )
        )
    }

    @Test
    fun `ready buffering ready lifecycle applies hook only once`() {
        var hasAppliedHookSeek = false

        val firstReady = shouldApplyMusicPlayerHookSeek(Player.STATE_READY, hasAppliedHookSeek)
        if (firstReady) hasAppliedHookSeek = true

        val buffering = shouldApplyMusicPlayerHookSeek(Player.STATE_BUFFERING, hasAppliedHookSeek)
        val secondReady = shouldApplyMusicPlayerHookSeek(Player.STATE_READY, hasAppliedHookSeek)

        assertTrue(firstReady)
        assertFalse(buffering)
        assertFalse(secondReady)
    }

    @Test
    fun `resolve hook start prefers player duration and falls back to song duration`() {
        assertEquals(
            4_999L,
            resolveMusicPlayerHookStartPositionMs(
                hookStartTimeMs = 8_000L,
                playerDurationMs = 5_000L,
                songDurationMs = 10_000
            )
        )
        assertEquals(
            9_999L,
            resolveMusicPlayerHookStartPositionMs(
                hookStartTimeMs = 12_000L,
                playerDurationMs = null,
                songDurationMs = 10_000
            )
        )
    }
}
