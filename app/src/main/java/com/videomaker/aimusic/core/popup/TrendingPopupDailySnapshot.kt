package com.videomaker.aimusic.core.popup

import kotlinx.serialization.Serializable

@Serializable
data class TrendingPopupDailySnapshot(
    val epochDay: Long,
    val shownCount: Int,
    val shownIds: List<String>,
    val lastShownAtMs: Long
) {
    companion object {
        fun empty(epochDay: Long): TrendingPopupDailySnapshot = TrendingPopupDailySnapshot(
            epochDay = epochDay,
            shownCount = 0,
            shownIds = emptyList(),
            lastShownAtMs = 0L
        )
    }
}
