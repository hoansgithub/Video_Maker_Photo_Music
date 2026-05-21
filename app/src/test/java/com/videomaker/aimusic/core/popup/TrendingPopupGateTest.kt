package com.videomaker.aimusic.core.popup

import org.junit.Assert.assertEquals
import org.junit.Test

class TrendingPopupGateTest {

    private val gate = TrendingPopupGate()
    private val nowMs = 1_700_000_000_000L
    private val todayEpochDay = 19_676L

    private fun snapshot(
        epochDay: Long = todayEpochDay,
        count: Int = 0,
        lastShownAtMs: Long = 0L
    ) = TrendingPopupDailySnapshot(
        epochDay = epochDay,
        shownCount = count,
        shownIds = emptyList(),
        lastShownAtMs = lastShownAtMs
    )

    private fun config(intervalMinutes: Long = 300L, cap: Long = 3L) =
        TrendingPopupConfigValues(intervalMinutes = intervalMinutes, dailyCap = cap)

    @Test
    fun `eligible when no prior shows today`() {
        val decision = gate.evaluate(
            snapshot = snapshot(),
            config = config(),
            nowMs = nowMs,
            todayEpochDay = todayEpochDay,
            otherPopupShowing = false
        )
        assertEquals(TrendingPopupGate.Decision.Eligible, decision)
    }

    @Test
    fun `kill switch when interval is zero`() {
        val decision = gate.evaluate(
            snapshot = snapshot(),
            config = config(intervalMinutes = 0L),
            nowMs = nowMs,
            todayEpochDay = todayEpochDay,
            otherPopupShowing = false
        )
        assertEquals(TrendingPopupGate.Decision.BlockedByKillSwitch, decision)
    }

    @Test
    fun `blocked when cap reached`() {
        val decision = gate.evaluate(
            snapshot = snapshot(count = 3),
            config = config(cap = 3L),
            nowMs = nowMs,
            todayEpochDay = todayEpochDay,
            otherPopupShowing = false
        )
        assertEquals(TrendingPopupGate.Decision.BlockedByCap, decision)
    }

    @Test
    fun `blocked when within interval`() {
        val fourHoursAgo = nowMs - 4 * 60 * 60 * 1000L
        val decision = gate.evaluate(
            snapshot = snapshot(count = 1, lastShownAtMs = fourHoursAgo),
            config = config(intervalMinutes = 300L),  // 5 hours
            nowMs = nowMs,
            todayEpochDay = todayEpochDay,
            otherPopupShowing = false
        )
        assertEquals(TrendingPopupGate.Decision.BlockedByInterval, decision)
    }

    @Test
    fun `eligible exactly at interval boundary`() {
        val fiveHoursAgo = nowMs - 5 * 60 * 60 * 1000L
        val decision = gate.evaluate(
            snapshot = snapshot(count = 1, lastShownAtMs = fiveHoursAgo),
            config = config(intervalMinutes = 300L),  // 5 hours
            nowMs = nowMs,
            todayEpochDay = todayEpochDay,
            otherPopupShowing = false
        )
        assertEquals(TrendingPopupGate.Decision.Eligible, decision)
    }

    @Test
    fun `2-minute interval blocks within window`() {
        val ninetySecondsAgo = nowMs - 90 * 1000L
        val decision = gate.evaluate(
            snapshot = snapshot(count = 1, lastShownAtMs = ninetySecondsAgo),
            config = config(intervalMinutes = 2L),
            nowMs = nowMs,
            todayEpochDay = todayEpochDay,
            otherPopupShowing = false
        )
        assertEquals(TrendingPopupGate.Decision.BlockedByInterval, decision)
    }

    @Test
    fun `30-minute interval eligible after 31 minutes`() {
        val thirtyOneMinutesAgo = nowMs - 31 * 60 * 1000L
        val decision = gate.evaluate(
            snapshot = snapshot(count = 1, lastShownAtMs = thirtyOneMinutesAgo),
            config = config(intervalMinutes = 30L),
            nowMs = nowMs,
            todayEpochDay = todayEpochDay,
            otherPopupShowing = false
        )
        assertEquals(TrendingPopupGate.Decision.Eligible, decision)
    }

    @Test
    fun `blocked when other popup is showing`() {
        val decision = gate.evaluate(
            snapshot = snapshot(),
            config = config(),
            nowMs = nowMs,
            todayEpochDay = todayEpochDay,
            otherPopupShowing = true
        )
        assertEquals(TrendingPopupGate.Decision.BlockedByOtherPopup, decision)
    }

    @Test
    fun `stale snapshot from previous day is treated as empty`() {
        val yesterday = todayEpochDay - 1
        val decision = gate.evaluate(
            snapshot = snapshot(epochDay = yesterday, count = 3, lastShownAtMs = nowMs - 1_000L),
            config = config(),
            nowMs = nowMs,
            todayEpochDay = todayEpochDay,
            otherPopupShowing = false
        )
        assertEquals(TrendingPopupGate.Decision.Eligible, decision)
    }
}
