package co.alcheclub.video.maker.photo.music.modules.editor.components

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
 * Architecture: Single-player mode using CompositionPlayer with audio included
 *
 * Requirements for multi-sequence support (video + audio):
 * - All EditedMediaItems MUST have setDurationUs() set explicitly
 * - Video and audio sequences MUST be equal length
 * - isLooping is NOT supported
 * See: https://github.com/androidx/media/issues/1560
 *
 * Features:
 * - Renders actual video composition in real-time
 * - Rebuilds composition when project or settings change
 * - Play/pause controls
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

    // State for preview - single player mode (video + audio in one CompositionPlayer)
    var previewState by remember { mutableStateOf<PreviewState>(PreviewState.Building) }
    var player by remember { mutableStateOf<CompositionPlayer?>(null) }

    // Create composition factory
    val compositionFactory = remember { CompositionFactory(context) }

    // Key that changes when project or settings change
    val compositionKey = remember(
        project.id,
        project.assets.map { it.id },
        project.settings
    ) {
        "${project.id}_${project.assets.hashCode()}_${project.settings.hashCode()}"
    }

    // Build composition when key changes
    LaunchedEffect(compositionKey) {
        android.util.Log.d("VideoPreviewPlayer", "=== Rebuilding composition (single-player mode) ===")
        android.util.Log.d("VideoPreviewPlayer", "Key: $compositionKey")
        android.util.Log.d("VideoPreviewPlayer", "Assets: ${project.assets.size}, AudioTrack: ${project.settings.audioTrackId}")
        previewState = PreviewState.Building

        // Store old player to release AFTER new one is ready
        val oldPlayer = player

        try {
            // Check if project has assets
            if (project.assets.isEmpty()) {
                previewState = PreviewState.Error("No assets to preview")
                oldPlayer?.release()
                player = null
                return@LaunchedEffect
            }

            // Create composition WITH audio (single-player mode)
            // CompositionPlayer now supports multi-sequence with explicit durations
            android.util.Log.d("VideoPreviewPlayer", "Creating composition with audio...")
            val composition = compositionFactory.createComposition(project, includeAudio = true)
            android.util.Log.d("VideoPreviewPlayer", "Composition created")

            // Create single player for both video and audio
            android.util.Log.d("VideoPreviewPlayer", "Creating CompositionPlayer...")
            val newPlayer = CompositionPlayer.Builder(context).build()
            newPlayer.setComposition(composition)
            newPlayer.repeatMode = Player.REPEAT_MODE_OFF
            newPlayer.prepare()
            android.util.Log.d("VideoPreviewPlayer", "CompositionPlayer ready")

            // Add listener for playback state changes
            newPlayer.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    onPlaybackStateChange(playing)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    android.util.Log.d("VideoPreviewPlayer", "Playback state: $playbackState")
                    when (playbackState) {
                        Player.STATE_READY -> {
                            android.util.Log.d("VideoPreviewPlayer", "Player STATE_READY, autoPlay=$autoPlay")
                            previewState = PreviewState.Ready
                            if (autoPlay) {
                                newPlayer.play()
                            }
                        }
                        Player.STATE_BUFFERING -> {
                            android.util.Log.d("VideoPreviewPlayer", "Player STATE_BUFFERING")
                        }
                        Player.STATE_ENDED -> {
                            android.util.Log.d("VideoPreviewPlayer", "Player STATE_ENDED, resetting for replay")
                            newPlayer.seekTo(0)
                            onPlaybackStateChange(false)
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("VideoPreviewPlayer", "Player error: ${error.message}", error)
                    previewState = PreviewState.Error(error.message ?: "Playback error")
                }
            })

            // Atomically swap player - assign new one first, then release old
            player = newPlayer

            // Now release old player
            android.util.Log.d("VideoPreviewPlayer", "Releasing old player...")
            oldPlayer?.release()
            android.util.Log.d("VideoPreviewPlayer", "Player swap complete")

        } catch (e: Exception) {
            android.util.Log.e("VideoPreviewPlayer", "Failed to build preview", e)
            previewState = PreviewState.Error(e.message ?: "Failed to build preview")
            // Keep old player if new one failed to create
            if (player == null) {
                player = oldPlayer
            } else {
                oldPlayer?.release()
            }
        }
    }

    // Control playback based on isPlaying
    LaunchedEffect(isPlaying, player) {
        player?.let { p ->
            if (isPlaying && p.playbackState == Player.STATE_READY) {
                p.play()
            } else {
                p.pause()
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
