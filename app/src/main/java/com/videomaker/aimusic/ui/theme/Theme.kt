package com.videomaker.aimusic.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.videomaker.aimusic.ui.components.ProvideShimmerEffect

/**
 * Dark color scheme - the only theme for this app.
 * Background: 0xFF101313
 */
private val DarkColorScheme = darkColorScheme(
    primary = Primary,                      // Lime #A3E635 (from Figma)
    onPrimary = TextOnPrimary,              // Dark text on lime (Zinc-950)
    onPrimaryContainer = TextOnPrimary,     // Dark text on primary container
    primaryContainer = White10,             // 5-10% white overlay (from Figma button inner bg)
    secondary = Secondary,                  // Orange-600 (accent gradient)
    onSecondary = TextOnSecondary,          // White text on orange
    background = BackgroundDark,            // Zinc-950 #09090B
    onBackground = TextPrimary,
    surface = SurfaceDark,                  // Zinc-800 #27272A
    onSurface = TextPrimary,
    surfaceVariant = Gray800,               // Zinc-800
    onSurfaceVariant = Gray300,             // Zinc-300
    tertiary = Tertiary,                    // Blue-700 (purple gradient)
    onTertiary = TextOnTertiary,            // White text on blue
    error = Error
)

/**
 * App theme with:
 * - Dark mode only
 * - Neue Haas Grotesk Display Pro font family
 * - Scalable typography based on screen size
 * - Background color 0xFF101313
 * - Shared shimmer animation for performance
 */
@Composable
fun VideoMakerTheme(
    content: @Composable () -> Unit
) {
    // Configure system bar icons for dark theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view) {
            val activity = view.context as? Activity
            if (activity != null) {
                val insetsController = WindowCompat.getInsetsController(activity.window, view)
                // Light icons on dark background
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
            onDispose { }
        }
    }

    // Provide scalable dimensions and shared shimmer animation
    ProvideDimens {
        ProvideShimmerEffect {
            MaterialTheme(
                colorScheme = DarkColorScheme,
                typography = scalableTypography(),
                content = content
            )
        }
    }
}
