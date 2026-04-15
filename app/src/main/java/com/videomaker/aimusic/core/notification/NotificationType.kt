package com.videomaker.aimusic.core.notification

enum class NotificationType(val analyticsValue: String) {
    QUICK_SAVE_REMINDER("quick_save_reminder"),
    ABANDONED_SELECT_PHOTOS("abandoned_select_photos"),
    DRAFT_COMPLETION_NUDGE("draft_completion_nudge"),
    FORGOTTEN_MASTERPIECE("forgotten_masterpiece"),
    SHARE_ENCOURAGEMENT("share_encouragement"),
    VIRAL_TEMPLATE("viral_template"),
    TRENDING_SONG("trending_song")
}
