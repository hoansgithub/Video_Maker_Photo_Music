package com.videomaker.aimusic.modules.templatepreviewer.components

import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
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
import kotlinx.coroutines.delay
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
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isPrepared by remember { mutableStateOf(false) }

    // Retry mechanism state
    var retryCount by remember(videoUrl) { mutableStateOf(0) }
    var isRetrying by remember { mutableStateOf(false) }
    var maxRetries = 5  // Not state - used only for logic, not UI rendering

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
                        isRetrying = false
                        retryCount = 0  // Reset retry count on successful load
                        // Log buffered duration to verify lazy loading
                        val bufferedMs = player.bufferedPosition - player.currentPosition
                        android.util.Log.d("TemplateVideoPlayer", "STATE_READY - Buffered: ${bufferedMs}ms, playing=${player.isPlaying}, autoPlay=$autoPlay")
                        if (retryCount > 0) {
                            android.util.Log.i("TemplateVideoPlayer", "✅ RETRY SUCCESS after $retryCount attempts - URL: $videoUrl")
                        } else {
                            android.util.Log.d("TemplateVideoPlayer", "✅ Video loaded successfully on first try - URL: $videoUrl")
                        }
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

            override fun onPlayerError(error: PlaybackException) {
                isPrepared = false

                // Map error code to readable name for debugging
                val errorCodeName = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "IO_NETWORK_CONNECTION_FAILED"
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "IO_NETWORK_CONNECTION_TIMEOUT"
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "IO_UNSPECIFIED"
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "IO_BAD_HTTP_STATUS"
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "DECODER_INIT_FAILED"
                    PlaybackException.ERROR_CODE_DECODING_FAILED -> "DECODING_FAILED"
                    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "BEHIND_LIVE_WINDOW"
                    PlaybackException.ERROR_CODE_TIMEOUT -> "TIMEOUT"
                    PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "IO_CLEARTEXT_NOT_PERMITTED"
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "IO_FILE_NOT_FOUND"
                    else -> "UNKNOWN_${error.errorCode}"
                }

                // Enhanced logging with detailed error information for scroll debugging
                android.util.Log.e("TemplateVideoPlayer", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                android.util.Log.e("TemplateVideoPlayer", "❌ PLAYBACK ERROR (Scroll Retry Debugging)")
                android.util.Log.e("TemplateVideoPlayer", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                android.util.Log.e("TemplateVideoPlayer", "📹 Video URL: $videoUrl")
                android.util.Log.e("TemplateVideoPlayer", "🔢 Error Code: ${error.errorCode} ($errorCodeName)")
                android.util.Log.e("TemplateVideoPlayer", "💬 Error Message: ${error.message}")
                android.util.Log.e("TemplateVideoPlayer", "🔍 Error Cause: ${error.cause?.javaClass?.simpleName} - ${error.cause?.message}")
                android.util.Log.e("TemplateVideoPlayer", "📱 Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
                android.util.Log.e("TemplateVideoPlayer", "🔄 Retry Count: $retryCount / $maxRetries")
                android.util.Log.e("TemplateVideoPlayer", "⏱️  Timestamp: ${System.currentTimeMillis()}")
                android.util.Log.e("TemplateVideoPlayer", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                android.util.Log.e("TemplateVideoPlayer", "Full stack trace:", error)

                // Check for network-specific errors (like VideoPreviewPlayer.kt does)
                val isNetworkError = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                                     error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                                     error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                                     error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS

                val isDecoderError = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED

                // Smart retry strategy based on error type
                val shouldRetry: Boolean
                val retryLimit: Int

                when {
                    isNetworkError -> {
                        android.util.Log.w("TemplateVideoPlayer", "⚠️ NETWORK ERROR - Poor connection or download failed")
                        shouldRetry = true
                        retryLimit = 5  // Network errors: retry 5 times
                    }
                    isDecoderError -> {
                        android.util.Log.e("TemplateVideoPlayer", "⚠️ DECODER INIT FAILED - Device codec issue (might be temporary)")
                        shouldRetry = true
                        retryLimit = 3  // Decoder errors: retry 3 times (might be temporary resource issue)
                    }
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                        android.util.Log.e("TemplateVideoPlayer", "⚠️ DECODING FAILED - Video format/codec incompatible or corrupted")
                        shouldRetry = true
                        retryLimit = 2  // Decoding errors: retry 2 times
                    }
                    else -> {
                        android.util.Log.e("TemplateVideoPlayer", "⚠️ OTHER ERROR - Unknown playback issue")
                        shouldRetry = true
                        retryLimit = 3  // Other errors: retry 3 times
                    }
                }

                maxRetries = retryLimit

                // Attempt auto-retry if within limits
                if (shouldRetry && retryCount < retryLimit) {
                    retryCount++
                    isRetrying = true
                    hasError = false
                    isLoading = true

                    // Exponential backoff: 1s, 2s, 4s
                    val delayMs = (1000L * (1 shl (retryCount - 1))).coerceAtMost(4000L)

                    android.util.Log.w(
                        "TemplateVideoPlayer",
                        "🔄 RETRY #$retryCount/$retryLimit TRIGGERED - Delay: ${delayMs}ms - Error: $errorCodeName - URL: $videoUrl"
                    )

                    coroutineScope.launch {
                        try {
                            delay(delayMs)

                            // Stop and release current player state
                            player.stop()
                            player.clearMediaItems()

                            // Small delay to ensure cleanup
                            delay(100)

                            // Reload video
                            android.util.Log.d("TemplateVideoPlayer", "🔄 Retrying video load...")
                            val mediaItem = MediaItem.Builder()
                                .setUri(Uri.parse(videoUrl))
                                .build()

                            player.setMediaItem(mediaItem)
                            player.prepare()
                        } catch (e: Exception) {
                            android.util.Log.e("TemplateVideoPlayer", "Retry failed with exception", e)
                            hasError = true
                            isLoading = false
                            isRetrying = false
                            errorMessage = context.getString(com.videomaker.aimusic.R.string.error_video_playback_failed)
                        }
                    }
                } else {
                    // Max retries exhausted or non-retryable error
                    hasError = true
                    isLoading = false
                    isRetrying = false

                    if (retryCount >= retryLimit) {
                        android.util.Log.e("TemplateVideoPlayer", "❌ Max retries ($retryLimit) exhausted - giving up")
                    }

                    android.util.Log.e("TemplateVideoPlayer", "=======================================")

                    // User-friendly message based on error type
                    errorMessage = when {
                        isNetworkError -> context.getString(com.videomaker.aimusic.R.string.error_video_network)
                        else -> context.getString(com.videomaker.aimusic.R.string.error_video_playback_failed)
                    }

                    onError?.invoke(errorMessage)
                }
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

        // Loading indicator with retry status
        if (isLoading && !hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )

                    // Show retry status if retrying
                    if (isRetrying && retryCount > 0) {
                        Text(
                            text = context.getString(com.videomaker.aimusic.R.string.video_retrying),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
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
                    text = errorMessage,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }

    }
}
