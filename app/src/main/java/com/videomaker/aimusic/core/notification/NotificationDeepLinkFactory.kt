package com.videomaker.aimusic.core.notification

import android.content.Context
import android.content.Intent
import com.videomaker.aimusic.MainActivity

data class NotificationDeepLink(
    val action: String,
    val deepLinkDestination: String,
    val songId: Long = -1L,
    val templateId: String? = null,
    val projectId: String? = null,
    val draftId: String? = null,
    val hintMode: String? = null
)

object NotificationDeepLinkFactory {
    const val ACTION_NOTIF_TRENDING_SONG = "com.videomaker.aimusic.action.NOTIF_TRENDING_SONG"
    const val ACTION_NOTIF_VIRAL_TEMPLATE = "com.videomaker.aimusic.action.NOTIF_VIRAL_TEMPLATE"
    const val ACTION_NOTIF_MY_VIDEO = "com.videomaker.aimusic.action.NOTIF_MY_VIDEO"
    const val ACTION_NOTIF_RESUME_TEMPLATE = "com.videomaker.aimusic.action.NOTIF_RESUME_TEMPLATE"

    const val EXTRA_SONG_ID = "extra_song_id"
    const val EXTRA_TEMPLATE_ID = "extra_template_id"
    const val EXTRA_PROJECT_ID = "extra_project_id"
    const val EXTRA_DRAFT_ID = "extra_draft_id"
    const val EXTRA_HINT_MODE = "extra_hint_mode"

    fun trendingSong(songId: Long): NotificationDeepLink {
        return NotificationDeepLink(
            action = ACTION_NOTIF_TRENDING_SONG,
            deepLinkDestination = "song_preview",
            songId = songId
        )
    }

    fun viralTemplate(templateId: String): NotificationDeepLink {
        return NotificationDeepLink(
            action = ACTION_NOTIF_VIRAL_TEMPLATE,
            deepLinkDestination = "template_preview",
            templateId = templateId
        )
    }

    fun myVideo(projectId: String, hintMode: String): NotificationDeepLink {
        return NotificationDeepLink(
            action = ACTION_NOTIF_MY_VIDEO,
            deepLinkDestination = "my_video",
            projectId = projectId,
            hintMode = hintMode
        )
    }

    fun resumeTemplate(
        templateId: String?,
        songId: Long,
        draftId: String?
    ): NotificationDeepLink {
        return NotificationDeepLink(
            action = ACTION_NOTIF_RESUME_TEMPLATE,
            deepLinkDestination = "select_photos",
            songId = songId,
            templateId = templateId,
            draftId = draftId
        )
    }

    fun toMainActivityIntent(context: Context, deepLink: NotificationDeepLink): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = deepLink.action
            putExtra(EXTRA_SONG_ID, deepLink.songId)
            putExtra(EXTRA_TEMPLATE_ID, deepLink.templateId)
            putExtra(EXTRA_PROJECT_ID, deepLink.projectId)
            putExtra(EXTRA_DRAFT_ID, deepLink.draftId)
            putExtra(EXTRA_HINT_MODE, deepLink.hintMode)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }
}

