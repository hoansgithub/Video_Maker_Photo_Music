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
    primary = Primary,
    onPrimary = TextOnPrimary,              // White icons/text on orange
    onPrimaryContainer = TextOnPrimary,     // White text on primary container
    primaryContainer = PrimaryOverlay20,    // Translucent orange background
    secondary = Secondary,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = Gray800,
    onSurfaceVariant = Gray300,
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
