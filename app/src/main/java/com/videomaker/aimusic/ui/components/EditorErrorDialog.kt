package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.CtaText
import com.videomaker.aimusic.ui.theme.NeueHaasFontFamily
import com.videomaker.aimusic.ui.theme.PickerDialogBackground
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary

/**
 * Shared error popup used across the Editor screen (project load/save error,
 * preview error, network/beat-sync error).
 *
 * - Tapping outside the card does NOT dismiss (onDismissRequest is a no-op and the
 *   scrim swallows all clicks) — the user must choose [primaryText] or [secondaryText].
 * - [primaryText] is the lime pill CTA (e.g. "Try again"). Pass null (with null [onPrimary])
 *   to hide it entirely — e.g. after retries are exhausted, only [secondaryText] remains.
 * - [secondaryText] is the plain text CTA (e.g. "Close back to home").
 */
@Composable
fun EditorErrorDialog(
    title: String,
    message: String,
    primaryText: String?,
    onPrimary: (() -> Unit)?,
    secondaryText: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    iconPainter: Painter = painterResource(R.drawable.ic_cancel_music)
) {
    Dialog(
        onDismissRequest = { /* Outside tap must not dismiss */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    enabled = true,
                    onClick = { /* Block all clicks */ },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .background(
                        color = PickerDialogBackground,
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon
                androidx.compose.material3.Icon(
                    painter = iconPainter,
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Title — Neue Haas Grotesk Display Pro, 700, 24sp, 130%, +1sp tracking
                Text(
                    text = title,
                    color = TextPrimary,
                    fontFamily = NeueHaasFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    lineHeight = 31.2.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description — Inter, 500, 15sp, 140%
                Text(
                    text = message,
                    color = TextSecondary,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(28.dp))

                // CTA 1 — lime pill, single click. Hidden when primaryText/onPrimary are null
                // (e.g. retries exhausted — only the Close CTA remains).
                if (primaryText != null && onPrimary != null) {
                    Text(
                        text = primaryText,
                        color = CtaText,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 22.4.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(160.dp))
                            .background(Primary)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = onPrimary
                            )
                            .padding(vertical = 16.dp, horizontal = 49.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // CTA 2 — plain text, single click
                Text(
                    text = secondaryText,
                    color = TextPrimary,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 22.4.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onSecondary
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}
