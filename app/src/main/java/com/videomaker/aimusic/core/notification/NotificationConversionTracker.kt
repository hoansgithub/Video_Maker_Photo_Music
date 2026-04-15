package com.videomaker.aimusic.core.notification

import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.data.local.PreferencesManager

class NotificationConversionTracker(
    private val preferencesManager: PreferencesManager
) {

    fun recordTap(type: NotificationType, itemId: String, tappedAtMs: Long = System.currentTimeMillis()) {
        if (itemId.isBlank()) return
        preferencesManager.recordNotificationTap(type.name, itemId, tappedAtMs)
    }

    fun trackConversionIfEligible(
        type: NotificationType,
        itemId: String,
        itemType: String,
        conversionAction: String,
        nowMs: Long = System.currentTimeMillis(),
        attributionWindowMinutes: Long = DEFAULT_ATTRIBUTION_WINDOW_MINUTES
    ) {
        if (itemId.isBlank()) return
        val tappedAt = preferencesManager.getNotificationTapAtMs(type.name, itemId) ?: return
        val deltaMs = (nowMs - tappedAt).coerceAtLeast(0L)
        if (deltaMs > attributionWindowMinutes * 60_000L) return

        Analytics.trackNotificationConversion(
            type = type.analyticsValue,
            itemId = itemId,
            itemType = itemType,
            conversionAction = conversionAction,
            conversionTimeMinutes = deltaMs / 60_000L
        )
        preferencesManager.clearNotificationTap(type.name, itemId)
    }

    companion object {
        private const val DEFAULT_ATTRIBUTION_WINDOW_MINUTES = 24L * 60L
    }
}

