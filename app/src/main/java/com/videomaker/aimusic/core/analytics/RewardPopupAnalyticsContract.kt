package com.videomaker.aimusic.core.analytics

import java.util.Locale

object RewardPopupAnalyticsContract {

    fun normalizePreviousAction(raw: String): String = raw.trim()

    fun normalizeBtnType(raw: String): String? {
        val normalized = raw.trim().lowercase(Locale.ROOT)
        return when (normalized) {
            AnalyticsEvent.Value.RewardPopupType.YES,
            AnalyticsEvent.Value.RewardPopupType.NO,
            AnalyticsEvent.Value.RewardPopupType.EXIT -> normalized
            else -> null
        }
    }
}
