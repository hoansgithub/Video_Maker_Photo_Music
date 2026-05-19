package com.videomaker.aimusic.core.popup

/** UI state for a popup. T is the content type (template or song). */
sealed class TrendingPopupState<out T> {
    data object Hidden : TrendingPopupState<Nothing>()
    data class Showing<T>(val content: T) : TrendingPopupState<T>()
}
