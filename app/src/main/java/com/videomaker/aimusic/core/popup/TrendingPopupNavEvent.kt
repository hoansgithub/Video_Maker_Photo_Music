package com.videomaker.aimusic.core.popup

/**
 * One-shot navigation event emitted by the coordinator when the user taps the CTA.
 * Consumed via Channel.receiveAsFlow() by HomeScreen.
 */
sealed class TrendingPopupNavEvent {
    data class OpenTemplatePreviewer(
        val templateId: String,
        val overrideSongId: Long,
        val sourceLocation: String
    ) : TrendingPopupNavEvent()
}
