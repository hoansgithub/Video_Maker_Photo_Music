package com.videomaker.aimusic.media.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class HookStartTimePolicyTest {

    @Test
    fun `null duration returns non-negative hook`() {
        assertEquals(0L, HookStartTimePolicy.resolve(-42L, null))
        assertEquals(17L, HookStartTimePolicy.resolve(17L, null))
    }

    @Test
    fun `non positive duration does not clamp normalized hook`() {
        assertEquals(0L, HookStartTimePolicy.resolve(-42L, 0L))
        assertEquals(17L, HookStartTimePolicy.resolve(17L, 0L))
        assertEquals(17L, HookStartTimePolicy.resolve(17L, -1L))
    }

    @Test
    fun `duration clamps hook to valid range`() {
        assertEquals(0L, HookStartTimePolicy.resolve(-42L, 10_000L))
        assertEquals(9_999L, HookStartTimePolicy.resolve(15_000L, 10_000L))
        assertEquals(5_000L, HookStartTimePolicy.resolve(5_000L, 10_000L))
    }

    @Test
    fun `duration one only allows zero start`() {
        assertEquals(0L, HookStartTimePolicy.resolve(0L, 1L))
        assertEquals(0L, HookStartTimePolicy.resolve(1L, 1L))
        assertEquals(0L, HookStartTimePolicy.resolve(10L, 1L))
    }
}
