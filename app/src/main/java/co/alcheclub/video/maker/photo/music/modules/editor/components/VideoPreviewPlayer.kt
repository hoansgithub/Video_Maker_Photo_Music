package co.alcheclub.video.maker.photo.music.modules.editor.components

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.PlayerView
import co.alcheclub.video.maker.photo.music.domain.model.Project
import co.alcheclub.video.maker.photo.music.media.composition.CompositionFactory

/**
 * Preview state for the video player
 */
sealed class PreviewState {
    data object Building : PreviewState()
    data object Ready : PreviewState()
    data class Error(val message: String) : PreviewState()
}

/**
 * VideoPreviewPlayer - Real-time video preview using Media3 CompositionPlayer
 *
 * Features:
 * - Renders actual video composition in real-time
 * - Rebuilds composition when project or settings change
 * - Play/pause controls
 * - Loops playback
 * - Auto-play when ready (optional)
 */
@Composable
fun VideoPreviewPlayer(
    project: Project,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onPlaybackStateChange: (Boolean) -> Unit,
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val aspectRatio = project.settings.aspectRatio.ratio

    // State for preview
    var previewState by remember { mutableStateOf<PreviewState>(PreviewState.Building) }
    var player by remember { mutableStateOf<CompositionPlayer?>(null) }

    // Create composition factory
    val compositionFactory = remember { CompositionFactory(context) }

    // Key that changes when project or settings change
    // This triggers composition rebuild
    val compositionKey = remember(
        project.id,
        project.assets.map { it.id },
        project.settings
    ) {
        "${project.id}_${project.assets.hashCode()}_${project.settings.hashCode()}"
    }

    // Build composition when key changes
    LaunchedEffect(compositionKey) {
        previewState = PreviewState.Building

        try {
            // Release existing player
            player?.release()
            player = null

            // Check if project has assets
            if (project.assets.isEmpty()) {
                previewState = PreviewState.Error("No assets to preview")
                return@LaunchedEffect
            }

            // Create new composition
            val composition = compositionFactory.createComposition(project)

            // Create new player
            val newPlayer = CompositionPlayer.Builder(context).build()
            newPlayer.setComposition(composition)
            newPlayer.repeatMode = Player.REPEAT_MODE_ALL
            newPlayer.prepare()

            // Add listener for state changes
            newPlayer.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    onPlaybackStateChange(playing)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            // Only set Ready when player is actually ready
                            previewState = PreviewState.Ready
                            // Auto-play when ready
                            if (autoPlay) {
                                newPlayer.play()
                            }
                        }
                        Player.STATE_BUFFERING -> {
                            // Show processing while buffering/preparing
                            previewState = PreviewState.Building
                        }
                        Player.STATE_ENDED -> {
                            // Loop handled by repeatMode
                        }
                    }
                }
            })

            // Keep Building state until player callback fires with STATE_READY
            player = newPlayer

        } catch (e: Exception) {
            previewState = PreviewState.Error(e.message ?: "Failed to build preview")
        }
    }

    // Control playback based on isPlaying
    LaunchedEffect(isPlaying, player) {
        player?.let {
            if (isPlaying && it.playbackState == Player.STATE_READY) {
                it.play()
            } else {
                it.pause()
            }
        }
    }

    // Cleanup player on dispose
    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Box(
        modifier = modifier.padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Preview Container
        Box(
            modifier = Modifier
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Always show the player if available (keeps last frame visible during rebuild)
            player?.let { compositionPlayer ->
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = compositionPlayer
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { playerView ->
                        playerView.player = compositionPlayer
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Show error message if error
            if (previewState is PreviewState.Error) {
                Text(
                    text = (previewState as PreviewState.Error).message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // PROCESSING OVERLAY - Full overlay when building composition
            if (previewState is PreviewState.Building) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Processing...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // Play/Pause overlay button (only show when ready)
            if (previewState is PreviewState.Ready) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Asset counter overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${project.assets.size} photos",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            // Status indicator
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(
                        color = when (previewState) {
                            is PreviewState.Building -> Color(0xFFFF9800).copy(alpha = 0.9f) // Orange
                            is PreviewState.Ready -> if (isPlaying) Color.Red.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.6f)
                            is PreviewState.Error -> Color.Red.copy(alpha = 0.9f)
                        },
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = when (previewState) {
                        is PreviewState.Building -> "PROCESSING"
                        is PreviewState.Ready -> if (isPlaying) "PLAYING" else "PAUSED"
                        is PreviewState.Error -> "ERROR"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
