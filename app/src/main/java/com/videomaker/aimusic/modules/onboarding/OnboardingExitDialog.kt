package com.videomaker.aimusic.modules.onboarding

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.videomaker.aimusic.R

@Composable
fun OnboardingExitDialog(onExit: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onboarding_exit_title)) },
        text = { Text(stringResource(R.string.onboarding_exit_message)) },
        confirmButton = {
            TextButton(onClick = onExit) { Text(stringResource(R.string.onboarding_exit_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.onboarding_exit_dismiss)) }
        }
    )
}
