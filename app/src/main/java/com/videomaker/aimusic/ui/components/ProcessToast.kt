package com.videomaker.aimusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.Neutral_Black
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.White12
import kotlinx.coroutines.delay

/**
 * ProcessToast - Reusable toast component with loading indicator and completion state
 *
 * Shows a toast capsule with:
 * - Loading state: Loading indicator + message
 * - Success state: Checkmark + message (auto-dismisses after 3 seconds)
 * - Error state: Error icon + message (auto-dismisses after 3 seconds)
 *
 * Usage:
 * ```kotlin
 * var toastState by remember { mutableStateOf<ProcessToastState?>(null) }
 *
 * ProcessToast(
 *     state = toastState,
 *     onDismiss = { toastState = null }
 * )
 *
 * // Show loading
 * toastState = ProcessToastState.Loading("Downloading...")
 *
 * // Show success
 * toastState = ProcessToastState.Success("Downloaded")
 * ```
 */

sealed class ProcessToastState {
    data class Loading(val message: String) : ProcessToastState()
    data class Success(val message: String) : ProcessToastState()
    data class Error(val message: String) : ProcessToastState()
}

@Composable
fun ProcessToast(
    state: ProcessToastState?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto-dismiss after 3 seconds for success/error states
    LaunchedEffect(state) {
        when (state) {
            is ProcessToastState.Success,
            is ProcessToastState.Error -> {
                delay(3000)
                onDismiss()
            }
            else -> Unit
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Dim overlay - only show during loading
        AnimatedVisibility(
            visible = state is ProcessToastState.Loading,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }

        // Toast capsule
        AnimatedVisibility(
            visible = state != null,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            state?.let { toastState ->
                Box(
                    modifier = Modifier
                        .padding(bottom = 32.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Neutral_Black)
                        .border(
                            width = 1.dp,
                            color = White12,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (toastState) {
                            is ProcessToastState.Loading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            }
                            is ProcessToastState.Success -> {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_circle_checkmark),
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            is ProcessToastState.Error -> {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_circle_cross),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Text(
                            text = when (toastState) {
                                is ProcessToastState.Loading -> toastState.message
                                is ProcessToastState.Success -> toastState.message
                                is ProcessToastState.Error -> toastState.message
                            },
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
