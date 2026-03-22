package com.videomaker.aimusic.modules.editor.components

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
import org.koin.compose.koinInject
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.media.composition.CompositionFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
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
 * Use ProcessLifecycleOwner scope instead of GlobalScope.
 * ProcessLifecycleOwner is tied to the process lifetime (not Activity),
 * so release() completes even after the composable is disposed.
 */
private fun CompositionPlayer.releaseAsync() {
    val playerToRelease = this
    // CompositionPlayer.release() must be called on the main thread (Media3 threading contract).
    // Post via Handler so it runs on main without blocking the calling thread.
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        runCatching { playerToRelease.release() }
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
    onPreviewStateChange: (PreviewState) -> Unit = {},
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val aspectRatio = project.settings.aspectRatio.ratio

    // State for preview - single player mode (video + audio in one CompositionPlayer)
    var previewState by remember { mutableStateOf<PreviewState>(PreviewState.Building) }
    var player by remember { mutableStateOf<CompositionPlayer?>(null) }

    // Track composition building job so we can cancel it when app goes to background
    var compositionBuildJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Notify parent of preview state changes
    LaunchedEffect(previewState) {
        onPreviewStateChange(previewState)
    }

    // Flow to signal when player is fully initialized and safe to play
    val playerReadyFlow = remember { MutableStateFlow(false) }

    // Create composition factory
    val compositionFactory: CompositionFactory = koinInject()

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
                // Only re-schedule while a player exists — stops ghost ticks when player is null
                if (player != null) {
                    positionHandler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
                }
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

    // Key that changes when project or settings change (EXCEPT volume)
    // Volume changes are handled separately via player.setVolume() for instant feedback
    val compositionKey = remember(
        project.id,
        project.assets.joinToString(",") { it.id },
        project.settings.effectSetId,
        project.settings.imageDurationMs,
        project.settings.transitionPercentage,
        project.settings.overlayFrameId,
        project.settings.musicSongId,
        project.settings.customAudioUri,
        project.settings.aspectRatio
        // audioVolume intentionally excluded - handled separately
    ) {
        "${project.id}_${project.assets.joinToString(",") { it.id }}_${project.settings.effectSetId}_${project.settings.imageDurationMs}_${project.settings.transitionPercentage}_${project.settings.overlayFrameId}_${project.settings.musicSongId}_${project.settings.customAudioUri}_${project.settings.aspectRatio}"
    }

    // Build composition when key changes
    LaunchedEffect(compositionKey) {
        // Cancel any previous composition building
        compositionBuildJob?.cancel()

        // Store this job so it can be cancelled on background
        compositionBuildJob = coroutineContext[kotlinx.coroutines.Job]

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
            newPlayer.volume = project.settings.audioVolume // Set initial volume
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
            // Don't show error if cancelled (expected when app goes to background)
            if (e !is CancellationException) {
                previewState = PreviewState.Error(e.message ?: "Failed to build preview")
                playerReadyFlow.value = false
                if (player == null) {
                    player = oldPlayer
                } else {
                    oldPlayer?.releaseAsync()
                }
            }
        } finally {
            compositionBuildJob = null
        }
    }

    // Real-time volume control - NO composition rebuild required!
    // Uses player.setVolume() for instant feedback without re-processing
    LaunchedEffect(project.settings.audioVolume, player, previewState) {
        // THREAD SAFETY: Ensure player access happens on main thread
        withContext(Dispatchers.Main.immediate) {
            val currentPlayer = player ?: return@withContext

            // Don't wait if player is still building - will auto-apply when ready
            if (previewState is PreviewState.Building) return@withContext

            // Don't try to set volume if player is in error state
            if (previewState is PreviewState.Error) return@withContext

            // Only proceed if player is ready (no timeout needed - we check state above)
            if (!playerReadyFlow.value) return@withContext

            // Set player volume (0.0 to 1.0)
            // This is instant - no composition rebuild needed!
            currentPlayer.volume = project.settings.audioVolume
        }
    }

    // Control playback based on isPlaying state
    LaunchedEffect(isPlaying, player, previewState) {
        val currentPlayer = player ?: return@LaunchedEffect

        // Don't wait if player is still building - will auto-play when ready (if autoPlay is true)
        if (previewState is PreviewState.Building) return@LaunchedEffect

        // Don't try to play if player is in error state
        if (previewState is PreviewState.Error) return@LaunchedEffect

        if (isPlaying) {
            try {
                // Only proceed if player is ready (no timeout - we check state above)
                // Large projects with many images can take a long time to build, so we don't wait with timeout
                if (!playerReadyFlow.value) return@LaunchedEffect

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

    // Cancel all processing when app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // Pause playback
                    player?.pause()
                    onPlaybackStateChange(false)

                    // Cancel composition building to free resources
                    compositionBuildJob?.cancel()
                    compositionBuildJob = null

                    // Release player to free memory
                    player?.releaseAsync()
                    player = null

                    // Reset state
                    previewState = PreviewState.Building
                    playerReadyFlow.value = false
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
            when (val state = previewState) {
                is PreviewState.Error -> {
                    Text(
                        text = state.message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {}
            }

            // Play/Pause button removed - using the one in Music Section instead

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
        }
    }
}

private const val POSITION_UPDATE_INTERVAL_MS = 500L  // Reduced from 200ms to minimize UI recomposition
