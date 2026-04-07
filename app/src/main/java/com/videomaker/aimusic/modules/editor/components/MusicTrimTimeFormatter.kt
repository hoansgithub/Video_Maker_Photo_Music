package com.videomaker.aimusic.modules.editor.components

/**
 * Formats milliseconds to m:ss using nearest-second rounding:
 * fractional part >= 0.5s rounds up, otherwise rounds down.
 */
fun formatMusicTrimTime(durationMs: Long): String {
    val safeMs = durationMs.coerceAtLeast(0L)
    val roundedSeconds = (safeMs + 500L) / 1000L
    val minutes = roundedSeconds / 60L
    val seconds = roundedSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
