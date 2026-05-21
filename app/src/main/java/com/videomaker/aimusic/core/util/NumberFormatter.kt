package com.videomaker.aimusic.core.util

import java.util.Locale

object NumberFormatter {
    /**
     * Format a count (e.g. view count, use count) for display.
     * Examples:
     * - 999 -> "999"
     * - 1000 -> "1K"
     * - 1500 -> "1.5K"
     * - 1000000 -> "1M"
     * - 1500000 -> "1.5M"
     */
    fun formatCount(count: Long): String = when {
        count >= 1_000_000 -> {
            val v = count / 1_000_000.0
            if (v % 1.0 == 0.0) "${v.toLong()}M" else String.format(Locale.US, "%.1fM", v)
        }
        count >= 1_000 -> {
            val v = count / 1_000.0
            if (v % 1.0 == 0.0) "${v.toLong()}K" else String.format(Locale.US, "%.1fK", v)
        }
        else -> count.toString()
    }
}