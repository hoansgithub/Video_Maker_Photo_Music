package com.videomaker.aimusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.state.AdsLoadingState

/**
 * Global ad loading overlay
 *
 * Displays a full-screen overlay with loading indicator when ads are being loaded.
 * Observes [AdsLoadingState] to show/hide automatically.
 *
 * Usage:
 * ```kotlin
 * @Composable
 * fun MyScreen() {
 *     Box {
 *         // Your content
 *         MyContent()
 *
 *         // Add overlay at the end (renders on top)
 *         AdsLoadingOverlay()
 *     }
 * }
 * ```
 *
 * Features:
 * - Animated fade in/out
 * - Blocks all user interactions while visible
 * - Customizable message from AdsLoadingState
 * - Semi-transparent background
 */
@Composable
fun AdsLoadingOverlay(
    modifier: Modifier = Modifier
) {
    val isLoading by AdsLoadingState.isLoading.collectAsStateWithLifecycle()
    val message by AdsLoadingState.message.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = isLoading,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        // Full-screen overlay that blocks all clicks
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    enabled = true,
                    onClick = { /* Block clicks */ },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Loading card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = message.ifEmpty { "Loading ad..." },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
