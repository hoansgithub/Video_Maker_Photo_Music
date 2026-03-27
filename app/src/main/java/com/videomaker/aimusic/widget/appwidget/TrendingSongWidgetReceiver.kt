package com.videomaker.aimusic.widget.appwidget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Receiver for the Trending Song widget.
 * Required by the Android widget framework.
 */
class TrendingSongWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrendingSongAppWidget()
}