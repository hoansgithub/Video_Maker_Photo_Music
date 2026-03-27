package com.videomaker.aimusic.widget.appwidget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Receiver for the Smart Search widget.
 * Required by the Android widget framework.
 */
class SmartSearchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmartSearchAppWidget()
}