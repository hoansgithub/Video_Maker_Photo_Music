package com.videomaker.aimusic.widget.appwidget

/**
 * Intent actions used by home screen widgets to deep-link into the app.
 */
object WidgetActions {
    const val ACTION_OPEN_SEARCH = "com.videomaker.aimusic.action.OPEN_SEARCH"
    const val ACTION_OPEN_TRENDING_SONG = "com.videomaker.aimusic.action.OPEN_TRENDING_SONG"
    const val ACTION_OPEN_TRENDING_TEMPLATE = "com.videomaker.aimusic.action.OPEN_TRENDING_TEMPLATE"
    const val ACTION_OPEN_TEMPLATE_DETAIL = "com.videomaker.aimusic.action.OPEN_TEMPLATE_DETAIL"
    const val ACTION_OPEN_TEMPLATE_WITH_SONG = "com.videomaker.aimusic.action.OPEN_TEMPLATE_WITH_SONG"
    const val EXTRA_TEMPLATE_ID = "extra_template_id"
    const val EXTRA_SONG_ID = "extra_song_id"
}