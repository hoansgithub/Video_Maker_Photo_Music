package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.ui.theme.CtaText
import com.videomaker.aimusic.ui.theme.Primary

private val CtaShape = RoundedCornerShape(999.dp)

/**
 * App-wide primary CTA button — lime background, 16sp semibold dark text, glow shadow.
 *
 * @param text       Button label.
 * @param onClick    Click handler.
 * @param modifier   Applied to the Button itself (size, padding, etc.).
 * @param enabled    Pass false to disable; container dims to 70% opacity.
 * @param isLoading  Replaces content with a spinner (keeps button dimensions).
 * @param leadingIcon Optional slot rendered before the text label.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = CtaShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Primary,
            contentColor = CtaText,
            disabledContainerColor = Primary.copy(alpha = 0.7f),
            disabledContentColor = CtaText
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
        modifier = modifier.shadow(
            elevation = 12.dp,
            shape = CtaShape,
            ambientColor = Primary,
            spotColor = Primary
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = CtaText,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            leadingIcon?.invoke()
            if (leadingIcon != null) Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = CtaText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}