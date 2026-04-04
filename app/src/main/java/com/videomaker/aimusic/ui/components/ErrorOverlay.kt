package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary

/**
 * Reusable error overlay component for displaying errors across the app
 *
 * Features:
 * - Full-screen blocking overlay with semi-transparent background
 * - Customizable icon, title, message
 * - Optional retry button
 * - Optional dismiss button
 * - Consistent styling across all screens
 *
 * Usage:
 * ```
 * Box(modifier = Modifier.fillMaxSize()) {
 *     // Your content
 *
 *     if (errorState != null) {
 *         ErrorOverlay(
 *             errorType = ErrorType.Network,
 *             message = errorState.message,
 *             onRetry = { viewModel.retry() },
 *             onDismiss = { viewModel.clearError() }
 *         )
 *     }
 * }
 * ```
 */
@Composable
fun ErrorOverlay(
    errorType: ErrorType = ErrorType.Generic,
    title: String? = null,
    message: String,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .background(
                    color = SplashBackground,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Error icon
            Icon(
                imageVector = errorType.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = title ?: errorType.defaultTitle,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Error message
            Text(
                text = message,
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Retry button (if provided)
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.root_try_again),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Dismiss button (if provided)
            if (onDismiss != null) {
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = TextPrimary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.dialog_ok),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Predefined error types with appropriate icons and default titles
 */
enum class ErrorType(
    val icon: ImageVector,
    val defaultTitle: String
) {
    Network(
        icon = Icons.Default.CloudOff,
        defaultTitle = "Network Error"
    ),
    MusicLoading(
        icon = Icons.Default.MusicOff,
        defaultTitle = "Music Loading Failed"
    ),
    Generic(
        icon = Icons.Default.Error,
        defaultTitle = "Error"
    )
}
