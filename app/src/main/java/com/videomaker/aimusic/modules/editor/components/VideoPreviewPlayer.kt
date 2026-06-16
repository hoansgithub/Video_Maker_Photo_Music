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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.R
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
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
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import android.net.Uri
import kotlin.math.abs

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
 * CRITICAL: release() must run on a BACKGROUND thread, not main thread!
 * Unlike prepare(), play(), pause() which require main thread, release() can be called from any thread.
 * Running on background thread prevents ANR.
 *
 * Use ProcessLifecycleOwner scope instead of GlobalScope.
 * ProcessLifecycleOwner is tied to the process lifetime (not Activity),
 * so release() completes even after the composable is disposed.
 */
private fun CompositionPlayer.releaseAsync() {
    val playerToRelease = this
    runCatching {
        // Stop immediately on caller thread so old player cannot keep producing audio/video while waiting for async release.
        playerToRelease.playWhenReady = false
        playerToRelease.pause()
        playerToRelease.stop()
        playerToRelease.clearMediaItems()
    }
    // Run release() on background thread using ProcessLifecycleOwner scope
    // This prevents blocking the main thread for 10+ seconds
    androidx.lifecycle.ProcessLifecycleOwner.get().lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            android.util.Log.d("VideoPreviewPlayer", "Releasing CompositionPlayer on background thread...")
            playerToRelease.release()
            android.util.Log.d("VideoPreviewPlayer", "CompositionPlayer released successfully")
        }.onFailure { e ->
            android.util.Log.e("VideoPreviewPlayer", "Failed to release CompositionPlayer", e)
        }
    }
}

/**
 * Release an ExoPlayer asynchronously to avoid blocking the main thread.
 * Same as CompositionPlayer.releaseAsync() but for ExoPlayer.
 */
private fun androidx.media3.exoplayer.ExoPlayer.releaseAsync() {
    val playerToRelease = this
    runCatching {
        // Stop immediately on caller thread so old audio cannot overlap with newly created player.
        playerToRelease.playWhenReady = false
        playerToRelease.pause()
        playerToRelease.stop()
        playerToRelease.clearMediaItems()
    }
    androidx.lifecycle.ProcessLifecycleOwner.get().lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            android.util.Log.d("VideoPreviewPlayer", "Releasing ExoPlayer on background thread...")
            playerToRelease.release()
            android.util.Log.d("VideoPreviewPlayer", "ExoPlayer released successfully")
        }.onFailure { e ->
            android.util.Log.e("VideoPreviewPlayer", "Failed to release ExoPlayer", e)
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
    onPreviewStateChange: (PreviewState) -> Unit = {},
    autoPlay: Boolean = false,
    // Gate for playback: while false (e.g. composing overlay visible) the preview stays paused.
    // Playback auto-starts the moment this flips to true and the player is ready (if autoPlay).
    canPlay: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val aspectRatio = project.settings.aspectRatio.ratio

    // State for preview - TWO PLAYER MODE (video + audio separate)
    var previewState by remember { mutableStateOf<PreviewState>(PreviewState.Building) }
    var videoPlayer by remember { mutableStateOf<CompositionPlayer?>(null) }
    var audioPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Track composition building job so we can cancel it when app goes to background
    var compositionBuildJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Trigger to rebuild composition on explicit user action (e.g., error retry)
    // Not used for automatic rebuilds on app resume
    var rebuildTrigger by remember { mutableStateOf(0) }

    // Actual music segment duration (updated when audio player is ready with actual duration)
    var actualMusicSegmentDurationMs by remember { mutableStateOf<Long?>(null) }
    var lastBoundaryLoopRestartAtMs by remember { mutableStateOf(0L) }
    val latestUserWantsPlayback by rememberUpdatedState(isPlaying)

    // Notify parent of preview state changes
    LaunchedEffect(previewState) {
        onPreviewStateChange(previewState)
    }

    // Flow to signal when player is fully initialized and safe to play
    val playerReadyFlow = remember { MutableStateFlow(false) }

    // Create composition factory
    val compositionFactory: CompositionFactory = koinInject()

    // Audio cache for automatic music caching (prevents 403 errors from expired URLs)
    val audioCache: com.videomaker.aimusic.media.audio.AudioPreviewCache = koinInject()

    // Calculate actual video duration (not composition duration which includes long audio)
    val videoDurationMs = remember(project.id, project.assets.size, project.settings.totalDurationMs) {
        project.totalDurationMs
    }

    // Calculate music segment duration for sync logic
    // Beat-sync mode: Use video duration directly (no trim ranges in legacy sense)
    // Priority 1: Use actual duration from player when available
    // Priority 2: Fallback to video duration
    val musicSegmentDurationMs = remember(
        actualMusicSegmentDurationMs,
        videoDurationMs
    ) {
        PreviewLoopPolicy.resolveSegmentDurationMs(
            trimStartMs = 0L,  // Beat-sync mode: no trim start
            trimEndMs = 0L,     // Beat-sync mode: no trim end
            detectedDurationMs = actualMusicSegmentDurationMs ?: 0L,
            videoDurationMs = videoDurationMs
        )
    }

    // Helper function to sync audio position to video position
    // IMPORTANT: ClippingConfiguration makes audio timeline RELATIVE to clipped range
    // e.g., if clipped from 10s-20s, audio player sees 0-10s (not 10s-20s)
    // Only call this on user actions (seek, play start), NOT continuously!
    fun syncAudioToVideo(videoPositionMs: Long) {
        // ✅ THREAD-SAFE: Capture player reference atomically
        val audio = audioPlayer ?: return

        try {
            val segmentDuration = musicSegmentDurationMs

            val targetAudioPos = PreviewLoopPolicy.mapVideoToAudioPosition(
                videoPositionMs = videoPositionMs,
                segmentDurationMs = segmentDuration,
                videoDurationMs = videoDurationMs
            )

            // Only seek if drift is significant to avoid audio glitching
            val currentAudioPos = audio.currentPosition
            val drift = abs(currentAudioPos - targetAudioPos)
            if (drift > 100) {
                android.util.Log.d("VideoPreviewPlayer", "Syncing audio: drift=${drift}ms, seeking to ${targetAudioPos}ms")
                audio.seekTo(targetAudioPos)
            }
        } catch (e: IllegalStateException) {
            // Player was released between null check and usage - safe to ignore
            android.util.Log.w("VideoPreviewPlayer", "Audio player released during sync")
        }
    }

    fun restartPreviewAtBeginning(
        video: CompositionPlayer,
        audio: ExoPlayer?,
        userWantsPlayback: Boolean
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBoundaryLoopRestartAtMs < 250L) return

        lastBoundaryLoopRestartAtMs = now
        val shouldContinuePlayback = PreviewLoopPolicy.shouldRestartLoopPlayback(
            userWantsPlayback = userWantsPlayback,
            playerPlayWhenReady = video.playWhenReady
        )
        video.seekTo(0)
        audio?.seekTo(0) // Audio timeline is relative to clipped range
        if (shouldContinuePlayback) {
            video.play()
            audio?.play()
            onPlaybackStateChange(true)
        } else {
            video.pause()
            audio?.pause()
            onPlaybackStateChange(false)
        }
    }

    // Handler for periodic position updates
    val positionHandler = remember { Handler(Looper.getMainLooper()) }
    val positionUpdateRunnable = remember {
        object : Runnable {
            override fun run() {
                videoPlayer?.let { vp ->
                    if (vp.playbackState == Player.STATE_READY || vp.playbackState == Player.STATE_BUFFERING) {
                        val currentPos = vp.currentPosition.coerceAtLeast(0)
                        val duration = vp.duration.coerceAtLeast(0)
                        onPositionUpdate(currentPos, duration)
                    }
                }
                // Re-schedule while player exists (cost is negligible — just reads in-memory position)
                if (videoPlayer != null) {
                    positionHandler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
                }
            }
        }
    }

    // Start/stop position updates based on player state
    DisposableEffect(videoPlayer) {
        videoPlayer?.let {
            positionHandler.post(positionUpdateRunnable)
        }
        onDispose {
            positionHandler.removeCallbacks(positionUpdateRunnable)
        }
    }

    // Handle seek requests (final seek when user releases slider) - sync both players
    LaunchedEffect(seekToPosition) {
        if (seekToPosition != null && seekToPosition >= 0) {
            videoPlayer?.let { vp ->
                try {
                    vp.seekTo(seekToPosition)
                    // Sync audio to new video position
                    syncAudioToVideo(seekToPosition)
                    kotlinx.coroutines.delay(50)
                } catch (_: Exception) {
                }
            }
            onSeekComplete()
        }
    }

    // Handle scrub requests (frame preview while dragging) - sync both players
    LaunchedEffect(scrubToPosition) {
        if (scrubToPosition != null && scrubToPosition >= 0) {
            videoPlayer?.let { vp ->
                try {
                    vp.seekTo(scrubToPosition)
                    // Sync audio immediately for smooth scrubbing
                    syncAudioToVideo(scrubToPosition)
                } catch (_: Exception) {
                }
            }
            onScrubComplete()
        }
    }

    // Key that changes when project or settings change (EXCEPT volume)
    // Volume changes are handled separately via player.setVolume() for instant feedback
    // Beat-sync mode: Composition key for triggering rebuilds
    val compositionKey = remember(
        project.id,
        project.assets.joinToString(",") { it.id },
        project.settings.effectSetId,
        project.settings.overlayFrameId,
        project.settings.musicSongId,
        project.settings.customAudioUri,
        project.settings.aspectRatio,
        project.settings.beatSyncData?.bpm,  // Beat-sync: rebuild when BPM changes
        project.settings.hookStartTimeMs,     // Beat-sync: rebuild when hook start changes
        project.settings.totalDurationMs,     // Rebuild when duration changes (e.g., asset count changed)
        project.settings.processedAudioUri,   // Beat-sync: rebuild when preprocessed audio ready
        rebuildTrigger
        // audioVolume intentionally excluded - handled separately
    ) {
        "${project.id}_${project.assets.joinToString(",") { it.id }}_${project.settings.effectSetId}_${project.settings.overlayFrameId}_${project.settings.musicSongId}_${project.settings.customAudioUri}_${project.settings.aspectRatio}_${project.settings.beatSyncData?.bpm}_${project.settings.hookStartTimeMs}_${project.settings.totalDurationMs}_${project.settings.processedAudioUri}_${rebuildTrigger}"
    }

    // Build composition when key changes
    LaunchedEffect(compositionKey) {
        android.util.Log.d("VideoPreviewPlayer", "Composition rebuild triggered. Key: $compositionKey")
        android.util.Log.d("VideoPreviewPlayer", "Beat-sync mode: BPM=${project.settings.beatSyncData?.bpm}, hook=${project.settings.hookStartTimeMs}ms")

        // Cancel any previous composition building
        compositionBuildJob?.cancel()

        // Store this job so it can be cancelled on background
        compositionBuildJob = coroutineContext[kotlinx.coroutines.Job]

        // Reset ready state and actual duration
        playerReadyFlow.value = false
        previewState = PreviewState.Building
        actualMusicSegmentDurationMs = null // Reset for new composition

        // Yield to allow UI to update and show "Processing" indicator
        kotlinx.coroutines.yield()

        // Store old players to release AFTER new ones are ready
        val oldVideoPlayer = videoPlayer
        val oldAudioPlayer = audioPlayer
        oldVideoPlayer?.playWhenReady = false
        oldVideoPlayer?.pause()
        oldAudioPlayer?.playWhenReady = false
        oldAudioPlayer?.pause()

        try {
            // Check if project has assets
            if (project.assets.isEmpty()) {
                previewState = PreviewState.Error("No assets to preview")
                oldVideoPlayer?.releaseAsync()
                oldAudioPlayer?.releaseAsync()
                videoPlayer = null
                audioPlayer = null
                return@LaunchedEffect
            }

            android.util.Log.d("VideoPreviewPlayer", "TWO-PLAYER MODE: Building video composition (no audio)")

            // Create VIDEO ONLY composition on IO thread (involves file I/O for bitmap loading)
            val composition = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                compositionFactory.createComposition(project, includeAudio = false) // NO AUDIO in composition!
            }

            // Create VIDEO PLAYER (must be on main thread)
            val newVideoPlayer = CompositionPlayer.Builder(context).build()
            newVideoPlayer.setComposition(composition)
            newVideoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            newVideoPlayer.volume = 0f // Video player has no audio
            newVideoPlayer.prepare()

            // Create AUDIO PLAYER if music is selected
            // CRITICAL: Check musicSongUrl too, not just musicSongId (for existing projects after migration)
            val newAudioPlayer = if (project.settings.musicSongId != null ||
                                     project.settings.musicSongUrl != null ||
                                     project.settings.customAudioUri != null) {
                android.util.Log.d("VideoPreviewPlayer", "TWO-PLAYER MODE: Creating audio player")
                android.util.Log.d("VideoPreviewPlayer", "Music settings: songId=${project.settings.musicSongId}, songUrl=${project.settings.musicSongUrl}, customUri=${project.settings.customAudioUri}, processedUri=${project.settings.processedAudioUri}")

                // Get audio URI - prioritize preprocessed audio (has fadeout baked in)
                val audioUri = when {
                    project.settings.processedAudioUri != null -> {
                        android.util.Log.d("VideoPreviewPlayer", "Using preprocessed audio with fadeout")
                        project.settings.processedAudioUri
                    }
                    project.settings.customAudioUri != null -> project.settings.customAudioUri
                    project.settings.musicSongUrl != null -> Uri.parse(project.settings.musicSongUrl)
                    else -> null
                }

                android.util.Log.d("VideoPreviewPlayer", "Audio URI resolved to: $audioUri")

                if (audioUri != null) {
                    val isPreprocessedAudio = audioUri == project.settings.processedAudioUri

                    // Build MediaItem - Beat-sync mode uses preprocessed audio
                    val mediaItem = if (isPreprocessedAudio) {
                        android.util.Log.d("VideoPreviewPlayer", "Preprocessed audio: using as-is (no clipping, fadeout baked in)")
                        MediaItem.Builder()
                            .setUri(audioUri)
                            .build()
                    } else {
                        // Source audio: Use hookStartTimeMs for clipping start
                        val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(project.settings.hookStartTimeMs)

                        android.util.Log.d("VideoPreviewPlayer", "Source audio: applying hook start (start=${project.settings.hookStartTimeMs}ms)")
                        MediaItem.Builder()
                            .setUri(audioUri)
                            .setClippingConfiguration(clippingBuilder.build())
                            .build()
                    }

                    // Create ExoPlayer for audio with cache support
                    // CRITICAL: Use CacheDataSource to automatically cache remote music URLs
                    // This prevents 403 errors from expired CloudFront URLs!
                    val audio = ExoPlayer.Builder(context)
                        .setMediaSourceFactory(
                            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                                audioCache.cacheDataSourceFactory
                            )
                        )
                        .build()
                    audio.setMediaItem(mediaItem)

                    // Set looping based on duration comparison
                    if (isPreprocessedAudio) {
                        // Preprocessed audio: already looped to exact video duration with fadeout
                        // NO looping needed (would restart and break fadeout)
                        audio.repeatMode = Player.REPEAT_MODE_OFF
                        actualMusicSegmentDurationMs = videoDurationMs
                        android.util.Log.d("VideoPreviewPlayer", "Preprocessed audio: no looping (already exact duration with fadeout)")
                    } else {
                        // Source audio: wait for player to be ready to get actual duration
                        // OPTIMISTIC default: assume loop (safer - ensures all images are shown)
                        // Will update to REPEAT_MODE_OFF if actual duration >= video duration
                        audio.repeatMode = Player.REPEAT_MODE_ALL
                        android.util.Log.d("VideoPreviewPlayer", "Music no trim: defaulting to REPEAT_MODE_ALL, will update when duration known")

                        // Track if duration update was applied
                        var durationUpdateApplied = false

                        // Add listener to update repeat mode when actual duration is known
                        // AND handle audio loading errors
                        audio.addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_READY && !durationUpdateApplied) {
                                    val actualDuration = audio.duration.coerceAtLeast(0L)

                                    if (actualDuration > 0) {
                                        android.util.Log.d("VideoPreviewPlayer", "Audio ready: actual duration=${actualDuration}ms, video=${videoDurationMs}ms")

                                        val resolvedSegmentDuration = PreviewLoopPolicy.resolveSegmentDurationMs(
                                            trimStartMs = 0L,  // Beat-sync mode: no trim
                                            trimEndMs = 0L,
                                            detectedDurationMs = actualDuration,
                                            videoDurationMs = videoDurationMs
                                        )

                                        // Update segment duration state
                                        actualMusicSegmentDurationMs = resolvedSegmentDuration

                                        // Update repeat mode based on actual duration
                                        if (PreviewLoopPolicy.shouldLoopAudio(resolvedSegmentDuration, videoDurationMs)) {
                                            audio.repeatMode = Player.REPEAT_MODE_ALL
                                            android.util.Log.d("VideoPreviewPlayer", "Music looping enabled (untrimmed): actual=${actualDuration}ms < video=${videoDurationMs}ms")
                                        } else {
                                            audio.repeatMode = Player.REPEAT_MODE_OFF
                                            android.util.Log.d("VideoPreviewPlayer", "Music no loop (untrimmed): actual=${actualDuration}ms >= video=${videoDurationMs}ms")
                                        }

                                        durationUpdateApplied = true
                                        // Remove this listener after first update
                                        audio.removeListener(this)
                                    } else {
                                        audio.repeatMode = Player.REPEAT_MODE_ALL
                                        // Duration detection failed (duration = 0) - keep REPEAT_MODE_ALL
                                        android.util.Log.w("VideoPreviewPlayer", "Audio duration = 0, keeping REPEAT_MODE_ALL as fallback")
                                        durationUpdateApplied = true
                                        audio.removeListener(this)
                                    }
                                }
                            }

                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                android.util.Log.e("VideoPreviewPlayer", "Audio player error occurred", error)
                                android.util.Log.e("VideoPreviewPlayer", "Audio error code: ${error.errorCode}")
                                android.util.Log.e("VideoPreviewPlayer", "Audio error message: ${error.message}")

                                // Pause video player so it doesn't keep playing silently
                                videoPlayer?.pause()
                                onPlaybackStateChange(false)

                                audio.repeatMode = Player.REPEAT_MODE_ALL
                                // On error, keep REPEAT_MODE_ALL (already set as default)
                                android.util.Log.w("VideoPreviewPlayer", "Audio error, keeping REPEAT_MODE_ALL as fallback")
                                durationUpdateApplied = true

                                // Set error state - will show error overlay with localized message
                                val errorMsg = when {
                                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                                        context.getString(R.string.error_preview_music_network)
                                    else ->
                                        context.getString(R.string.error_preview_music_failed)
                                }

                                previewState = PreviewState.Error(errorMsg)
                                playerReadyFlow.value = false
                                audio.removeListener(this)
                            }
                        })
                    }

                    audio.volume = project.settings.audioVolume
                    audio.prepare()
                    audio
                } else {
                    null
                }
            } else {
                null
            }

            // Add listener for video playback state changes
            newVideoPlayer.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    if (!PreviewLoopPolicy.shouldPropagatePlaybackStateToUi(
                            isPlaying = playing,
                            isPlayWhenReady = newVideoPlayer.playWhenReady
                        )
                    ) {
                        return
                    }

                    // Sync audio player play/pause with video
                    if (playing) {
                        newAudioPlayer?.play()
                    } else {
                        newAudioPlayer?.pause()
                    }
                    onPlaybackStateChange(playing)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            previewState = PreviewState.Ready
                            playerReadyFlow.value = true
                        }
                        Player.STATE_ENDED -> {
                            restartPreviewAtBeginning(
                                video = newVideoPlayer,
                                audio = newAudioPlayer,
                                userWantsPlayback = latestUserWantsPlayback
                            )
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("VideoPreviewPlayer", "Player error occurred", error)
                    android.util.Log.e("VideoPreviewPlayer", "Error code: ${error.errorCode}")
                    android.util.Log.e("VideoPreviewPlayer", "Error message: ${error.message}")
                    android.util.Log.e("VideoPreviewPlayer", "Error cause: ${error.cause}")
                    previewState = PreviewState.Error(error.message ?: "Playback error")
                    playerReadyFlow.value = false
                }
            })

            // Atomically swap players with rollback on error
            try {
                val oldVideo = videoPlayer
                val oldAudio = audioPlayer

                videoPlayer = newVideoPlayer
                audioPlayer = newAudioPlayer

                // Release old players - guaranteed execution
                oldVideo?.releaseAsync()
                oldAudio?.releaseAsync()
            } catch (e: Exception) {
                // Rollback on error - release new players and restore old state
                android.util.Log.e("VideoPreviewPlayer", "Player swap failed, rolling back", e)
                newVideoPlayer.releaseAsync()
                newAudioPlayer?.releaseAsync()
                throw e
            }

            // Await for video player to be truly ready using suspend function (no delay!)
            val isReady = newVideoPlayer.awaitReady()

            // Handle autoPlay after player is confirmed ready.
            // Only auto-start if playback is currently allowed (overlay hidden);
            // otherwise the canPlay effect below starts it once the overlay disappears.
            if (isReady && autoPlay && canPlay) {
                newVideoPlayer.play()
                newAudioPlayer?.play() // Audio synced with video
            }

        } catch (e: Exception) {
            // Don't show error if cancelled (expected when app goes to background)
            if (e !is CancellationException) {
                android.util.Log.e("VideoPreviewPlayer", "Failed to build composition", e)
                android.util.Log.e("VideoPreviewPlayer", "Exception type: ${e.javaClass.simpleName}")
                android.util.Log.e("VideoPreviewPlayer", "Exception message: ${e.message}")
                android.util.Log.e("VideoPreviewPlayer", "Exception cause: ${e.cause}")
                previewState = PreviewState.Error(e.message ?: "Failed to build preview")
                playerReadyFlow.value = false
                if (videoPlayer == null) {
                    videoPlayer = oldVideoPlayer
                    audioPlayer = oldAudioPlayer
                } else {
                    oldVideoPlayer?.releaseAsync()
                    oldAudioPlayer?.releaseAsync()
                }
            }
        } finally {
            compositionBuildJob = null
        }
    }

    // Real-time volume control for AUDIO PLAYER - NO composition rebuild required!
    LaunchedEffect(project.settings.audioVolume, audioPlayer, previewState) {
        // THREAD SAFETY: Ensure player access happens on main thread
        withContext(Dispatchers.Main.immediate) {
            val currentAudioPlayer = audioPlayer ?: return@withContext

            // Don't wait if player is still building - will auto-apply when ready
            if (previewState is PreviewState.Building) return@withContext

            // Don't try to set volume if player is in error state
            if (previewState is PreviewState.Error) return@withContext

            // Only proceed if player is ready (no timeout needed - we check state above)
            if (!playerReadyFlow.value) return@withContext

            // Set audio player volume (0.0 to 1.0)
            // This is instant - no composition rebuild needed!
            currentAudioPlayer.volume = project.settings.audioVolume
        }
    }

    // Control playback based on isPlaying state - sync both players
    LaunchedEffect(isPlaying, videoPlayer, previewState, canPlay) {
        val currentVideoPlayer = videoPlayer ?: return@LaunchedEffect

        // Don't wait if player is still building - will auto-play when ready (if autoPlay is true)
        if (previewState is PreviewState.Building) return@LaunchedEffect

        // Don't try to play if player is in error state
        if (previewState is PreviewState.Error) return@LaunchedEffect

        // Playback not allowed yet (e.g. composing overlay visible) — force paused.
        if (!canPlay) {
            currentVideoPlayer.pause()
            audioPlayer?.pause()
            return@LaunchedEffect
        }

        if (isPlaying) {
            try {
                // Only proceed if player is ready (no timeout - we check state above)
                // Large projects with many images can take a long time to build, so we don't wait with timeout
                if (!playerReadyFlow.value) {
                    android.util.Log.d("VideoPreviewPlayer", "Play requested but player not ready yet")
                    return@LaunchedEffect
                }

                android.util.Log.d("VideoPreviewPlayer", "Starting playback (both players)")

                // Sync audio to video position BEFORE starting playback
                val videoPos = currentVideoPlayer.currentPosition
                syncAudioToVideo(videoPos)

                // Start both players
                currentVideoPlayer.play()
                audioPlayer?.play()
            } catch (e: Exception) {
                android.util.Log.e("VideoPreviewPlayer", "Error starting playback", e)
            }
        } else {
            try {
                android.util.Log.d("VideoPreviewPlayer", "Pausing playback (both players)")
                currentVideoPlayer.pause()
                audioPlayer?.pause() // Sync audio with video
            } catch (e: Exception) {
                android.util.Log.e("VideoPreviewPlayer", "Error pausing playback", e)
            }
        }
    }

    // Auto-start playback the moment playback becomes allowed (e.g. composing overlay hides)
    // and the player is ready. Keyed on canPlay/previewState (not isPlaying) so it only fires
    // on the gate/ready transition and never fights with manual pause from the user.
    LaunchedEffect(canPlay, previewState, videoPlayer) {
        if (!canPlay || !autoPlay) return@LaunchedEffect
        val currentVideoPlayer = videoPlayer ?: return@LaunchedEffect
        if (previewState !is PreviewState.Ready || !playerReadyFlow.value) return@LaunchedEffect

        withContext(Dispatchers.Main.immediate) {
            syncAudioToVideo(currentVideoPlayer.currentPosition)
            currentVideoPlayer.play()
            audioPlayer?.play()
            onPlaybackStateChange(true)
        }
    }

    // Cleanup players and bitmaps on dispose
    DisposableEffect(Unit) {
        onDispose {
            // Release players asynchronously to avoid ANR
            videoPlayer?.releaseAsync()
            audioPlayer?.releaseAsync()
            videoPlayer = null
            audioPlayer = null
            // Recycle transition bitmaps to free memory
            compositionFactory.recycleBitmaps()
        }
    }

    // Cancel all processing when app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var wasPlayingBeforeActivityPause = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Pause only (no release) — covers interstitial ads and AOA overlay
                    wasPlayingBeforeActivityPause = videoPlayer?.isPlaying == true
                    videoPlayer?.pause()
                    audioPlayer?.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Resume if player is still alive (not released by ON_STOP)
                    if (wasPlayingBeforeActivityPause && videoPlayer != null) {
                        videoPlayer?.play()
                        audioPlayer?.play()
                        onPlaybackStateChange(true)
                    }
                    wasPlayingBeforeActivityPause = false
                }
                Lifecycle.Event.ON_STOP -> {
                    // Pause players when app goes to background
                    // Keep players alive for quick resume - no release
                    wasPlayingBeforeActivityPause = false
                    videoPlayer?.pause()
                    audioPlayer?.pause()
                    onPlaybackStateChange(false)

                    // Don't release players or cancel composition
                    // Players stay in memory and can resume immediately
                    // Only rebuild on explicit user action (settings change, retry, etc.)
                }
                Lifecycle.Event.ON_START -> {
                    // Composition builds on initial load only
                    // Rebuilds only on explicit user action (error retry button, settings change)
                    // No automatic rebuild on app resume
                    // Players stay alive and can resume immediately via ON_RESUME
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
            // Always show the video player if available (keeps last frame visible during rebuild)
            videoPlayer?.let { compositionPlayer ->
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
        }
    }
}

private const val POSITION_UPDATE_INTERVAL_MS = 500L  // Reduced from 200ms to minimize UI recomposition
