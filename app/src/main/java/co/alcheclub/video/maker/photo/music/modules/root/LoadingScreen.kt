package co.alcheclub.video.maker.photo.music.modules.root

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.alcheclub.video.maker.photo.music.R
import co.alcheclub.video.maker.photo.music.ui.theme.VideoMakerTheme

/**
 * LoadingScreen - Initial loading screen for app startup
 *
 * Displays while:
 * - Onboarding status is being checked
 * - Initial data is being loaded
 *
 * UI Components:
 * - Full-screen background image
 * - Loading progress indicator at bottom
 * - Loading message text
 *
 * Navigation is handled by RootViewModel - this screen just displays loading state.
 */
@Composable
fun LoadingScreen(
    isLoading: Boolean,
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Background Image - fills entire screen
        Image(
            painter = painterResource(id = R.drawable.loading_background),
            contentDescription = "App background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Content on top of background - positioned at bottom with safe area padding
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(bottom = 48.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = message.ifEmpty { "Loading..." },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}

// ============================================
// PREVIEW
// ============================================

@Preview(name = "Loading Screen", showBackground = true)
@Composable
private fun LoadingScreenPreview() {
    VideoMakerTheme {
        LoadingScreen(
            isLoading = true,
            message = "Loading..."
        )
    }
}
