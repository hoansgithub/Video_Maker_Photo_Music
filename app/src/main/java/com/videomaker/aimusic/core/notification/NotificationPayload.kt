package com.videomaker.aimusic.core.notification

import android.content.Context
import androidx.annotation.StringRes
import com.videomaker.aimusic.R

data class NotificationPayload(
    val type: NotificationType,
    val itemId: String,
    val itemType: String,
    val channelId: String,
    val title: NotificationText,
    val body: NotificationText,
    val ctaText: NotificationText,
    val deepLink: NotificationDeepLink,
    val imageCandidates: List<String> = emptyList(),
    val fallbackImageRes: Int,
    val ivCtaIcon: Int = R.drawable.ic_play,
    // Optional pre-designed bundled images. When set, they are used directly (no badge/gradient
    // decoration) instead of [imageCandidates]/[fallbackImageRes]. Lets a notification show a
    // different small (collapsed) vs large (expanded) artwork.
    val collapsedImageRes: Int? = null,
    val expandedImageRes: Int? = null,
)

data class NotificationText(
    @param:StringRes val resId: Int,
    val formatArgs: List<Any> = emptyList()
) {
    fun resolve(context: Context): String {
        return if (formatArgs.isEmpty()) {
            context.getString(resId)
        } else {
            context.getString(resId, *formatArgs.toTypedArray())
        }
    }
}
