package com.videomaker.aimusic.domain.model

/**
 * AspectRatio - Video aspect ratios optimized for social media platforms
 *
 * Based on 2025 social media video specifications:
 * - 9:16: TikTok, Instagram Reels, YouTube Shorts
 * - 4:5: Instagram Feed, Facebook Feed (portrait)
 * - 1:1: Instagram Feed, Facebook Feed (square)
 * - 16:9: YouTube, Facebook (landscape)
 */
enum class AspectRatio(val width: Int, val height: Int, val displayName: String) {
    // Portrait - Most popular for short-form content
    RATIO_9_16(1080, 1920, "9:16 TikTok/Reels"),

    // Portrait - Instagram/Facebook Feed
    RATIO_4_5(1080, 1350, "4:5 Instagram"),

    // Square - Universal feed format
    RATIO_1_1(1080, 1080, "1:1 Square"),

    // Landscape - YouTube/Facebook
    RATIO_16_9(1920, 1080, "16:9 YouTube");

    val ratio: Float get() = width.toFloat() / height.toFloat()

    companion object {
        fun fromString(value: String): AspectRatio {
            // Try enum name first (e.g., "RATIO_9_16")
            entries.find { it.name == value }?.let { return it }

            // Try ratio string (e.g., "9:16", "16:9")
            return when (value) {
                "9:16" -> RATIO_9_16
                "4:5" -> RATIO_4_5
                "1:1" -> RATIO_1_1
                "16:9" -> RATIO_16_9
                else -> RATIO_9_16 // Default
            }
        }
    }
}
