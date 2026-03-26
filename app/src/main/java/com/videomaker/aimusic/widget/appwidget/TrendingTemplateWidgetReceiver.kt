package com.videomaker.aimusic.widget.appwidget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Receiver for the Trending Template widget.
 * Required by the Android widget framework.
 */
class TrendingTemplateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrendingTemplateAppWidget()
}