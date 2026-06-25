package com.videomaker.aimusic.domain.model

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.videomaker.aimusic.R
import kotlinx.serialization.Serializable
import java.io.File
import android.content.Context

/**
 * TextOverlay - Represents a text layer added on top of the video canvas.
 */
@Serializable
data class TextOverlay(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val color: Long = 0xFFFFFFFFL, // ARGB hex value (default White)
    val fontId: String = "neue_haas_regular",
    val xPercentage: Float = 0.5f, // Center X position on video preview (0f to 1f)
    val yPercentage: Float = 0.5f, // Center Y position on video preview (0f to 1f)
    val scale: Float = 1.0f,
    val rotation: Float = 0.0f, // Rotation angle in degrees
    val zIndex: Int = 0 // Shared stacking order across text + stickers (higher draws on top)
)

/**
 * TextFontPreset - Represents a font preset configuration.
 */
@Serializable
data class TextFontPreset(
    val id: String,
    val name: String,
    val fontResId: Int? = null, // Reference to local resource R.font.*
    val fontUrl: String? = null,
    val fontPath: String? = null,
    val thumbnailUrl: String? = null,
    val thumbnailPath: String? = null,
    val isPremium: Boolean = false,
    val isNew: Boolean = false,
    val geo: List<String> = emptyList()
) {
    val fontFamily: FontFamily
        get() = if (fontResId != null) {
            FontFamily(Font(fontResId, FontWeight.Normal))
        } else {
            FontFamily.Default
        }

    fun getFontFamily(context: Context): FontFamily {
        if (fontResId != null) {
            return FontFamily(Font(fontResId, FontWeight.Normal))
        }
        val file = getFontFile(context)
        return if (file != null && file.exists()) {
            try {
                FontFamily(Font(file))
            } catch (e: Exception) {
                android.util.Log.e("TextFontPreset", "Failed to load font from file: ${file.absolutePath}", e)
                FontFamily.Default
            }
        } else {
            FontFamily.Default
        }
    }

    fun getFontFile(context: Context): File? {
        if (fontPath == null) return null
        val fontsDir = File(context.cacheDir, "fonts").apply { mkdirs() }
        val fileName = fontPath.substringAfterLast('/')
        return File(fontsDir, fileName)
    }
}

/**
 * Global mock font presets mirroring the user's UI.
 */
val mockFontPresets = listOf(
    TextFontPreset("neue_haas_regular", "Neue Haas Regular", R.font.neue_haas_regular, isPremium = false, isNew = false, geo = listOf("GL")),
    TextFontPreset("neue_haas_bold", "Neue Haas Bold", R.font.neue_haas_bold, isPremium = false, isNew = false, geo = listOf("GL"))
)