package com.videomaker.aimusic.core.popup

/**
 * Stateless policy: given the latest snapshot, config, and the current clock,
 * return whether a popup is eligible to fire right now.
 */
class TrendingPopupGate {

    sealed class Decision {
        data object Eligible : Decision()
        data object BlockedByKillSwitch : Decision()
        data object BlockedByCap : Decision()
        data object BlockedByInterval : Decision()
        data object BlockedByOtherPopup : Decision()
    }

    fun evaluate(
        snapshot: TrendingPopupDailySnapshot?,
        config: TrendingPopupConfigValues,
        nowMs: Long,
        todayEpochDay: Long,
        otherPopupShowing: Boolean
    ): Decision {
        if (config.intervalMinutes <= 0L) return Decision.BlockedByKillSwitch
        if (otherPopupShowing) return Decision.BlockedByOtherPopup

        val effective = snapshot
            ?.takeIf { it.epochDay == todayEpochDay }
            ?: TrendingPopupDailySnapshot.empty(todayEpochDay)

        if (effective.shownCount >= config.dailyCap) return Decision.BlockedByCap

        val intervalMs = config.intervalMinutes * 60L * 1000L
        if (effective.lastShownAtMs > 0L && nowMs - effective.lastShownAtMs < intervalMs) {
            return Decision.BlockedByInterval
        }

        return Decision.Eligible
    }
}
