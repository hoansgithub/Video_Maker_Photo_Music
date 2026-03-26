package com.videomaker.aimusic.widget.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.videomaker.aimusic.R
import kotlin.coroutines.EmptyCoroutineContext.get

/**
 * Widget type enum for the pager.
 */
enum class WidgetType(val titleRes: Int) {
    SEARCH(R.string.widget_smart_search),
    SONG(R.string.widget_trending_song),
    TEMPLATE(R.string.widget_trending_template)
}