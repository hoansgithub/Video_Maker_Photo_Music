package com.videomaker.aimusic.modules.onboarding

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun OnboardingExitDialog(onExit: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exit Setup?") },
        text = { Text("You can always change your preferences later in Settings.") },
        confirmButton = {
            TextButton(onClick = onExit) { Text("Exit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Continue") }
        }
    )
}