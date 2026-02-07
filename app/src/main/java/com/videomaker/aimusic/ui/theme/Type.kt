package com.videomaker.aimusic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.videomaker.aimusic.R

/**
 * Neue Haas Grotesk Display Pro font family.
 */
val NeueHaasFontFamily = FontFamily(
    Font(R.font.neue_haas_light, FontWeight.Light),
    Font(R.font.neue_haas_regular, FontWeight.Normal),
    Font(R.font.neue_haas_medium, FontWeight.Medium),
    Font(R.font.neue_haas_bold, FontWeight.Bold),
    Font(R.font.neue_haas_black, FontWeight.Black)
)

/**
 * Create scalable Typography based on current dimensions.
 */
@Composable
fun scalableTypography(): Typography {
    val dimens = AppDimens.current

    return Typography(
        // Display styles
        displayLarge = TextStyle(
            fontFamily = NeueHaasFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = dimens.font4Xl // 32sp scaled
        ),
        displayMedium = TextStyle(
            fontFamily = NeueHaasFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = dimens.font3Xl // 28sp scaled
        ),
        displaySmall = TextStyle(
            fontFamily = NeueHaasFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = dimens.font2Xl // 24sp scaled
        ),

        // Headline styles
        headlineLarge = TextStyle(
            fontFamily = NeueHaasFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = dimens.font3Xl // 28sp scaled
        ),
        headlineMedium = TextStyle(
            fontFamily = NeueHaasFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = dimens.font2Xl // 24sp scaled
        ),
        headlineSmall = TextStyle(
            fontFamily = NeueHaasFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = dimens.fontXxl // 20sp scaled
        ),

        // Title styles
        titleLarge = TextStyle(
            fontFamily = NeueHaasFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = dimens.fontXl // 18sp scaled
        ),
        titleMedium = TextStyle(
            fontFamily = NeueHaasFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = dimens.fontLg // 16sp scaled
        ),
        titleSmall = TextStyle(
            fontFamily = NeueHaasFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = dimens.fontMd // 14sp scaled
        ),

        // Body styles
        bodyLarge = TextStyle(
            fontFamily = NeueHaasFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = dimens.fontLg // 16sp scaled
        ),
        bodyMedium = TextStyle(
            fontFamily = NeueHaasFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = dimens.fontMd // 14sp scaled
        ),
        bodySmall = TextStyle(
            fontFamily = NeueHaasFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = dimens.fontSm // 12sp scaled
        ),

        // Label styles
        labelLarge = TextStyle(
            fontFamily = NeueHaasFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = dimens.fontMd // 14sp scaled
        ),
        labelMedium = TextStyle(
            fontFamily = NeueHaasFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = dimens.fontSm // 12sp scaled
        ),
        labelSmall = TextStyle(
            fontFamily = NeueHaasFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = dimens.fontXs // 10sp scaled
        )
    )
}
