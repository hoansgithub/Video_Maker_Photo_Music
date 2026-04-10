package com.videomaker.aimusic.modules.templatepreviewer.components

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.koin.compose.koinInject
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ============================================
// HELPER - Release player async to avoid ANR
// ============================================

/**
 * Release ExoPlayer asynchronously on background thread to avoid ANR.
 * ExoPlayer.release() can block for 10+ seconds when releasing GPU resources.
 */
private fun ExoPlayer.releaseAsync() {
    val playerToRelease = this
    ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
        runCatching {
            android.util.Log.d("TemplateVideoPlayer", "Releasing player on background thread...")
            playerToRelease.release()
            android.util.Log.d("TemplateVideoPlayer", "Player released successfully")
        }.onFailure { e ->
            android.util.Log.e("TemplateVideoPlayer", "Failed to release player", e)
        }
    }
}

/**
 * TemplateVideoPlayer - Lazy loading video player with disk caching
 *
 * Features:
 * - Lazy loading: Only buffers 15s ahead (not entire file)
 * - Disk caching: Rewatching is instant (reads from cache)
 * - Lifecycle aware: Pauses when app goes to background
 * - Error handling: Shows error state if video fails to load
 * - Loading state: Shows spinner while buffering
 *
 * @param videoUrl URL of the video to play (e.g., Supabase signed URL)
 * @param modifier Modifier for the player container
 * @param autoPlay Whether to auto-play when loaded (default: true)
 * @param loop Whether to loop the video (default: true)
 * @param onError Callback when video loading fails
 */
@Composable
fun TemplateVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    loop: Boolean = true,
    showControls: Boolean = false,
    onError: ((String) -> Unit)? = null,
    cacheDataSourceFactory: CacheDataSource.Factory = koinInject()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isPrepared by remember { mutableStateOf(false) }

    val player = remember(videoUrl) {
        // Configure instant playback with minimal buffer
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 1000,           // Min 1s buffer
                /* maxBufferMs */ 5000,           // Max 5s buffer (instant play!)
                /* bufferForPlaybackMs */ 500,   // Start after 0.5s buffered (instant!)
                /* bufferForPlaybackAfterRebufferMs */ 1000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(cacheDataSourceFactory) // ⭐ Disk caching enabled
            )
            .setLoadControl(loadControl)
            .build()
            .apply {
                repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            }
    }

    // Create listener as a separate remembered value to ensure proper cleanup
    val listener = remember(videoUrl) {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isLoading = true
                        android.util.Log.d("TemplateVideoPlayer", "STATE_BUFFERING: $videoUrl")
                    }
                    Player.STATE_READY -> {
                        isLoading = false
                        hasError = false
                        isPrepared = true
                        // Log buffered duration to verify lazy loading
                        val bufferedMs = player.bufferedPosition - player.currentPosition
                        android.util.Log.d("TemplateVideoPlayer", "STATE_READY - Buffered: ${bufferedMs}ms, playing=${player.isPlaying}, autoPlay=$autoPlay")
                    }
                    Player.STATE_IDLE -> {
                        isLoading = false
                        isPrepared = false
                        android.util.Log.d("TemplateVideoPlayer", "STATE_IDLE")
                    }
                    Player.STATE_ENDED -> {
                        isLoading = false
                        android.util.Log.d("TemplateVideoPlayer", "STATE_ENDED")
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                hasError = true
                isLoading = false
                isPrepared = false
                errorMessage = error.message ?: "Failed to load video"
                android.util.Log.e("TemplateVideoPlayer", "Player error: ${error.message}")
                onError?.invoke(errorMessage)
            }
        }
    }

    // Add listener after player creation
    LaunchedEffect(player, listener) {
        player.addListener(listener)
    }

    // Load and prepare video (don't play yet)
    LaunchedEffect(videoUrl) {
        try {
            android.util.Log.d("TemplateVideoPlayer", "Loading video URL: $videoUrl")
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(videoUrl))
                .build()

            player.setMediaItem(mediaItem)
            player.prepare()
            android.util.Log.d("TemplateVideoPlayer", "Player prepared")
        } catch (e: Exception) {
            android.util.Log.e("TemplateVideoPlayer", "Error loading video: ${e.message}", e)
            hasError = true
            errorMessage = e.message ?: "Failed to load video"
            onError?.invoke(errorMessage)
        }
    }

    // Auto-play when on-screen AND player is ready, pause when off-screen
    LaunchedEffect(autoPlay, isPrepared) {
        android.util.Log.d("TemplateVideoPlayer", "autoPlay=$autoPlay, isPrepared=$isPrepared, playbackState=${player.playbackState}")
        if (autoPlay && isPrepared) {
            android.util.Log.d("TemplateVideoPlayer", "✅ Playing video (on-screen + prepared)")
            player.play()
        } else if (!autoPlay) {
            android.util.Log.d("TemplateVideoPlayer", "⏸️ Pausing video (off-screen)")
            player.pause()
        } else {
            android.util.Log.d("TemplateVideoPlayer", "⏳ Waiting for player to be ready...")
        }
    }

    // Lifecycle management - pause when app goes to background, release on dispose
    DisposableEffect(lifecycleOwner, player, listener) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    android.util.Log.d("TemplateVideoPlayer", "App backgrounded - pausing")
                    player.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Only resume if this page is currently visible
                    if (autoPlay) {
                        android.util.Log.d("TemplateVideoPlayer", "App resumed - playing (visible page)")
                        player.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            android.util.Log.d("TemplateVideoPlayer", "Disposing player for: $videoUrl")
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)  // Remove listener before release to prevent leaks
            player.releaseAsync()  // ✅ Release async to avoid ANR
        }
    }

    Box(modifier = modifier) {
        // Video player view
        AndroidView(
            factory = { ctx ->
                android.util.Log.d("TemplateVideoPlayer", "Creating PlayerView")
                PlayerView(ctx).apply {
                    this.player = player
                    useController = showControls
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading indicator
        if (isLoading && !hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Error state
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Failed to load video\n$errorMessage",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

    }
}
