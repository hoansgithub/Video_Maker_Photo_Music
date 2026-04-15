package com.videomaker.aimusic.core.notification

data class NotificationPayload(
    val type: NotificationType,
    val itemId: String,
    val itemType: String,
    val channelId: String,
    val title: String,
    val body: String,
    val ctaText: String,
    val deepLink: NotificationDeepLink,
    val imageCandidates: List<String> = emptyList(),
    val fallbackImageRes: Int
)

