package com.videomaker.aimusic.core.notification

object NotificationPriorityResolver {

    private val priorityOrder = listOf(
        NotificationType.QUICK_SAVE_REMINDER,
        NotificationType.ABANDONED_SELECT_PHOTOS,
        NotificationType.DRAFT_COMPLETION_NUDGE,
        NotificationType.FORGOTTEN_MASTERPIECE,
        NotificationType.SHARE_ENCOURAGEMENT,
        NotificationType.VIRAL_TEMPLATE,
        NotificationType.TRENDING_SONG
    )

    fun resolve(candidates: Iterable<NotificationType>): NotificationType? {
        val candidateSet = candidates.toSet()
        return priorityOrder.firstOrNull { it in candidateSet }
    }
}
