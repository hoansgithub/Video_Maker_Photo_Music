package com.videomaker.aimusic.domain.model

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.videomaker.aimusic.R

/**
 * TextOverlay - Represents a text layer added on top of the video canvas.
 */
data class TextOverlay(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val color: Long = 0xFFFFFFFFL, // ARGB hex value (default White)
    val fontId: String = "system_default",
    val xPercentage: Float = 0.5f, // Center X position on video preview (0f to 1f)
    val yPercentage: Float = 0.5f, // Center Y position on video preview (0f to 1f)
    val scale: Float = 1.0f,
    val rotation: Float = 0.0f // Rotation angle in degrees
)

/**
 * TextFontPreset - Represents a font preset configuration.
 */
data class TextFontPreset(
    val id: String,
    val name: String,
    val fontResId: Int?, // Reference to local resource R.font.*
    val isPremium: Boolean = false,
    val isNew: Boolean = false
) {
    val fontFamily: FontFamily
        get() = if (fontResId != null) {
            FontFamily(Font(fontResId, FontWeight.Normal))
        } else {
            FontFamily.Default
        }
}

/**
 * Global mock font presets mirroring the user's UI.
 */
val mockFontPresets = listOf(
    TextFontPreset("system_default", "System Default", null, isPremium = false, isNew = false),
    TextFontPreset(
        "neue_haas_regular",
        "Haas Regular",
        R.font.neue_haas_regular,
        isPremium = false,
        isNew = false
    ),
    TextFontPreset(
        "neue_haas_bold",
        "Haas Bold",
        R.font.neue_haas_bold,
        isPremium = false,
        isNew = false
    ),
    TextFontPreset(
        "archiv_grotesk",
        "Archiv Grotesk",
        R.font.neue_haas_medium,
        isPremium = true,
        isNew = false
    ),
    TextFontPreset("aloevera", "Aloevera", R.font.neue_haas_light, isPremium = true, isNew = false),
    TextFontPreset(
        "aj_signal",
        "AJ SIGNAL",
        R.font.neue_haas_black,
        isPremium = true,
        isNew = false
    ),
    TextFontPreset("carbon", "Carbon", R.font.neue_haas_medium, isPremium = false, isNew = true)
)