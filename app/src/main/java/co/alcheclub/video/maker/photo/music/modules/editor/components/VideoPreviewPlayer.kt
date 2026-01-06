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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.PlayerView
import co.alcheclub.video.maker.photo.music.domain.model.Project
import co.alcheclub.video.maker.photo.music.media.composition.CompositionFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Preview state for the video player
 */
sealed class PreviewState {
    data object Building : PreviewState()
    data object Ready : PreviewState()
    data class Error(val message: String) : PreviewState()
}

/**
 * Release a CompositionPlayer asynchronously to avoid blocking the main thread.
 * CompositionPlayer.release() can block for 10+ seconds causing ANR.
 *
 * Note: GlobalScope is used here intentionally for fire-and-forget cleanup.
 * The player must be released even if the composable is disposed, and we don't
 * have access to a ViewModel scope in this extension function.
 */
@Suppress("OPT_IN_USAGE")
private fun CompositionPlayer.releaseAsync() {
    val playerToRelease = this
    GlobalScope.launch(Dispatchers.IO) {
        try {
            playerToRelease.release()
        } catch (_: Exception) {
        }
    }
}

/**
 * Await for player to reach STATE_READY using suspendCancellableCoroutine
 * This is the proper async/await pattern instead of using delay()
 */
private suspend fun CompositionPlayer.awaitReady(): Boolean {
    // If already ready, return immediately
    if (playbackState == Player.STATE_READY) {
        return true
    }

    return suspendCancellableCoroutine { continuation ->
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        removeListener(this)
                        if (continuation.isActive) {
                            continuation.resume(true)
                        }
                    }
                    Player.STATE_IDLE -> {
                        removeListener(this)
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                removeListener(this)
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }

        addListener(listener)

        continuation.invokeOnCancellation {
            removeListener(listener)
        }
    }
}

/**
 * Safely play the player, awaiting for it to be ready first
 * Returns true if playback started successfully
 */
private suspend fun CompositionPlayer.safePlay(): Boolean {
    return try {
        if (awaitReady()) {
            play()
            true
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }
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
    onPositionUpdate: (currentMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    seekToPosition: Long? = null,
    scrubToPosition: Long? = null,
    onSeekComplete: () -> Unit = {},
    onScrubComplete: () -> Unit = {},
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val aspectRatio = project.settings.aspectRatio.ratio

    // State for preview - single player mode (video + audio in one CompositionPlayer)
    var previewState by remember { mutableStateOf<PreviewState>(PreviewState.Building) }
    var player by remember { mutableStateOf<CompositionPlayer?>(null) }

    // Flow to signal when player is fully initialized and safe to play
    val playerReadyFlow = remember { MutableStateFlow(false) }

    // Create composition factory
    val compositionFactory = remember { CompositionFactory(context) }

    // Handler for periodic position updates
    val positionHandler = remember { Handler(Looper.getMainLooper()) }
    val positionUpdateRunnable = remember {
        object : Runnable {
            override fun run() {
                player?.let { p ->
                    if (p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING) {
                        val currentPos = p.currentPosition.coerceAtLeast(0)
                        val duration = p.duration.coerceAtLeast(0)
                        onPositionUpdate(currentPos, duration)
                    }
                }
                positionHandler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    // Start/stop position updates based on player state
    DisposableEffect(player) {
        player?.let {
            positionHandler.post(positionUpdateRunnable)
        }
        onDispose {
            positionHandler.removeCallbacks(positionUpdateRunnable)
        }
    }

    // Handle seek requests (final seek when user releases slider)
    LaunchedEffect(seekToPosition) {
        if (seekToPosition != null && seekToPosition >= 0) {
            player?.let { p ->
                try {
                    p.seekTo(seekToPosition)
                    kotlinx.coroutines.delay(50)
                } catch (_: Exception) {
                }
            }
            onSeekComplete()
        }
    }

    // Handle scrub requests (frame preview while dragging - no delay needed)
    LaunchedEffect(scrubToPosition) {
        if (scrubToPosition != null && scrubToPosition >= 0) {
            player?.let { p ->
                try {
                    p.seekTo(scrubToPosition)
                } catch (_: Exception) {
                }
            }
            onScrubComplete()
        }
    }

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
        // Reset ready state
        playerReadyFlow.value = false
        previewState = PreviewState.Building

        // Yield to allow UI to update and show "Processing" indicator
        kotlinx.coroutines.yield()

        // Store old player to release AFTER new one is ready
        val oldPlayer = player

        try {
            // Check if project has assets
            if (project.assets.isEmpty()) {
                previewState = PreviewState.Error("No assets to preview")
                oldPlayer?.releaseAsync()
                player = null
                return@LaunchedEffect
            }

            // Create composition on background thread to not block UI
            val composition = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                compositionFactory.createComposition(project, includeAudio = true)
            }

            // Create single player for both video and audio (must be on main thread)
            val newPlayer = CompositionPlayer.Builder(context).build()
            newPlayer.setComposition(composition)
            newPlayer.repeatMode = Player.REPEAT_MODE_OFF
            newPlayer.prepare()

            // Add listener for playback state changes
            newPlayer.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    onPlaybackStateChange(playing)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            previewState = PreviewState.Ready
                            playerReadyFlow.value = true
                        }
                        Player.STATE_ENDED -> {
                            newPlayer.seekTo(0)
                            onPlaybackStateChange(false)
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    previewState = PreviewState.Error(error.message ?: "Playback error")
                    playerReadyFlow.value = false
                }
            })

            // Atomically swap player - assign new one first, then release old
            player = newPlayer
            oldPlayer?.releaseAsync()

            // Await for player to be truly ready using suspend function (no delay!)
            val isReady = newPlayer.awaitReady()

            // Handle autoPlay after player is confirmed ready
            if (isReady && autoPlay) {
                newPlayer.play()
            }

        } catch (e: Exception) {
            previewState = PreviewState.Error(e.message ?: "Failed to build preview")
            playerReadyFlow.value = false
            if (player == null) {
                player = oldPlayer
            } else {
                oldPlayer?.releaseAsync()
            }
        }
    }

    // Control playback based on isPlaying state
    LaunchedEffect(isPlaying, player) {
        val currentPlayer = player ?: return@LaunchedEffect

        if (isPlaying) {
            try {
                if (!playerReadyFlow.value) {
                    playerReadyFlow.first { it }
                }
                currentPlayer.play()
            } catch (_: Exception) {
            }
        } else {
            try {
                currentPlayer.pause()
            } catch (_: Exception) {
            }
        }
    }

    // Cleanup player and bitmaps on dispose
    DisposableEffect(Unit) {
        onDispose {
            // Release player asynchronously to avoid ANR
            player?.releaseAsync()
            player = null
            // Recycle transition bitmaps to free memory
            compositionFactory.recycleBitmaps()
        }
    }

    // Pause video when app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    player?.pause()
                    onPlaybackStateChange(false)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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

private const val POSITION_UPDATE_INTERVAL_MS = 500L  // Reduced from 200ms to minimize UI recomposition
