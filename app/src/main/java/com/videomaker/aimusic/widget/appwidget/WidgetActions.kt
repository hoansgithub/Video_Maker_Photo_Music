package com.videomaker.aimusic.widget.appwidget

import android.content.Context
import android.content.Intent
import com.videomaker.aimusic.MainActivity

/**
 * Intent actions used by home screen widgets to deep-link into the app.
 */
object WidgetActions {
    const val ACTION_OPEN_SEARCH = "com.videomaker.aimusic.action.OPEN_SEARCH"
    const val ACTION_OPEN_TRENDING_SONG = "com.videomaker.aimusic.action.OPEN_TRENDING_SONG"
    const val ACTION_OPEN_TRENDING_TEMPLATE = "com.videomaker.aimusic.action.OPEN_TRENDING_TEMPLATE"
    const val ACTION_OPEN_TEMPLATE_DETAIL = "com.videomaker.aimusic.action.OPEN_TEMPLATE_DETAIL"
    const val ACTION_OPEN_TEMPLATE_WITH_SONG = "com.videomaker.aimusic.action.OPEN_TEMPLATE_WITH_SONG"
    const val ACTION_OPEN_SONG_PLAYER = "com.videomaker.aimusic.action.OPEN_SONG_PLAYER"
    const val EXTRA_TEMPLATE_ID = "extra_template_id"
    const val EXTRA_SONG_ID = "extra_song_id"

    fun openSearchIntent(context: Context): Intent =
        baseIntent(context, ACTION_OPEN_SEARCH)

    fun openTrendingSongIntent(context: Context): Intent =
        baseIntent(context, ACTION_OPEN_TRENDING_SONG)

    fun openTemplateDetailIntent(context: Context, templateId: String): Intent =
        baseIntent(context, ACTION_OPEN_TEMPLATE_DETAIL).apply {
            putExtra(EXTRA_TEMPLATE_ID, templateId)
        }

    fun openSongPlayerIntent(context: Context, songId: Long): Intent =
        baseIntent(context, ACTION_OPEN_SONG_PLAYER).apply {
            putExtra(EXTRA_SONG_ID, songId)
        }

    private fun baseIntent(context: Context, action: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
}