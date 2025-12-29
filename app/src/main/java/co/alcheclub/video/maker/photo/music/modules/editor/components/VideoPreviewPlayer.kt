package co.alcheclub.video.maker.photo.music.modules.editor.components

import android.content.Context
import android.net.Uri
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.PlayerView
import co.alcheclub.video.maker.photo.music.domain.model.Project
import co.alcheclub.video.maker.photo.music.media.composition.CompositionFactory
import co.alcheclub.video.maker.photo.music.media.library.AudioTrackLibrary
import java.io.File

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
    var videoPlayer by remember { mutableStateOf<CompositionPlayer?>(null) }
    var audioPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

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
        android.util.Log.d("VideoPreviewPlayer", "=== Rebuilding composition ===")
        android.util.Log.d("VideoPreviewPlayer", "Key: $compositionKey")
        android.util.Log.d("VideoPreviewPlayer", "Assets: ${project.assets.size}, AudioTrack: ${project.settings.audioTrackId}")
        previewState = PreviewState.Building

        // Store old players to release AFTER new ones are ready
        val oldVideoPlayer = videoPlayer
        val oldAudioPlayer = audioPlayer

        try {
            // Check if project has assets
            if (project.assets.isEmpty()) {
                previewState = PreviewState.Error("No assets to preview")
                oldVideoPlayer?.release()
                oldAudioPlayer?.release()
                videoPlayer = null
                audioPlayer = null
                return@LaunchedEffect
            }

            // Create video-only composition (CompositionPlayer doesn't support audio sequences)
            android.util.Log.d("VideoPreviewPlayer", "Creating video composition...")
            val composition = compositionFactory.createComposition(project, includeAudio = false)
            android.util.Log.d("VideoPreviewPlayer", "Video composition created")

            // Create video player
            android.util.Log.d("VideoPreviewPlayer", "Creating CompositionPlayer...")
            val newVideoPlayer = CompositionPlayer.Builder(context).build()
            newVideoPlayer.setComposition(composition)
            newVideoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            newVideoPlayer.prepare()
            android.util.Log.d("VideoPreviewPlayer", "CompositionPlayer ready")

            // Create separate audio player for background music
            android.util.Log.d("VideoPreviewPlayer", "Creating audio player...")
            val newAudioPlayer = createAudioPlayer(context, project.settings.audioTrackId, project.settings.customAudioUri)
            android.util.Log.d("VideoPreviewPlayer", "Audio player created: ${newAudioPlayer != null}")

            // Add listener for video state changes
            newVideoPlayer.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    onPlaybackStateChange(playing)
                    // Sync audio with video
                    newAudioPlayer?.let { audio ->
                        if (playing) {
                            audio.seekTo(newVideoPlayer.currentPosition)
                            audio.play()
                        } else {
                            audio.pause()
                        }
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    android.util.Log.d("VideoPreviewPlayer", "Playback state: $playbackState")
                    when (playbackState) {
                        Player.STATE_READY -> {
                            android.util.Log.d("VideoPreviewPlayer", "Player STATE_READY, autoPlay=$autoPlay")
                            previewState = PreviewState.Ready
                            if (autoPlay) {
                                newVideoPlayer.play()
                            }
                        }
                        Player.STATE_BUFFERING -> {
                            // Don't show Building state during normal buffering
                            android.util.Log.d("VideoPreviewPlayer", "Player STATE_BUFFERING")
                        }
                        Player.STATE_ENDED -> {
                            android.util.Log.d("VideoPreviewPlayer", "Player STATE_ENDED, resetting for replay")
                            // Reset both players for replay
                            newVideoPlayer.seekTo(0)
                            newAudioPlayer?.seekTo(0)
                            newAudioPlayer?.pause()
                            onPlaybackStateChange(false)
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("VideoPreviewPlayer", "Player error: ${error.message}", error)
                    previewState = PreviewState.Error(error.message ?: "Playback error")
                }
            })

            // Atomically swap players - assign new ones first, then release old
            videoPlayer = newVideoPlayer
            audioPlayer = newAudioPlayer

            // Now release old players
            android.util.Log.d("VideoPreviewPlayer", "Releasing old players...")
            oldVideoPlayer?.release()
            oldAudioPlayer?.release()
            android.util.Log.d("VideoPreviewPlayer", "Player swap complete")

        } catch (e: Exception) {
            android.util.Log.e("VideoPreviewPlayer", "Failed to build preview", e)
            previewState = PreviewState.Error(e.message ?: "Failed to build preview")
            // Keep old players if new ones failed to create
            if (videoPlayer == null) {
                videoPlayer = oldVideoPlayer
                audioPlayer = oldAudioPlayer
            } else {
                oldVideoPlayer?.release()
                oldAudioPlayer?.release()
            }
        }
    }

    // Control playback based on isPlaying
    LaunchedEffect(isPlaying, videoPlayer, audioPlayer) {
        videoPlayer?.let { video ->
            if (isPlaying && video.playbackState == Player.STATE_READY) {
                video.play()
            } else {
                video.pause()
                audioPlayer?.pause()
            }
        }
    }

    // Cleanup players on dispose
    DisposableEffect(Unit) {
        onDispose {
            videoPlayer?.release()
            videoPlayer = null
            audioPlayer?.release()
            audioPlayer = null
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

/**
 * Create audio player for background music preview
 */
private fun createAudioPlayer(
    context: Context,
    audioTrackId: String?,
    customAudioUri: Uri?
): ExoPlayer? {
    android.util.Log.d("VideoPreviewPlayer", "createAudioPlayer: trackId=$audioTrackId, customUri=$customAudioUri")

    val audioUri = getPreviewAudioUri(context, audioTrackId, customAudioUri)
    if (audioUri == null) {
        android.util.Log.w("VideoPreviewPlayer", "No audio URI available")
        return null
    }

    android.util.Log.d("VideoPreviewPlayer", "Creating ExoPlayer with URI: $audioUri")
    return ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(audioUri))
        repeatMode = Player.REPEAT_MODE_OFF
        volume = 1.0f
        prepare()
    }
}

/**
 * Get audio URI for preview playback
 */
private fun getPreviewAudioUri(context: Context, audioTrackId: String?, customAudioUri: Uri?): Uri? {
    android.util.Log.d("VideoPreviewPlayer", "getPreviewAudioUri: trackId=$audioTrackId")

    // Custom audio takes precedence
    customAudioUri?.let {
        android.util.Log.d("VideoPreviewPlayer", "Using custom audio URI: $it")
        return it
    }

    // Get bundled track
    audioTrackId?.let { trackId ->
        val track = AudioTrackLibrary.getById(trackId)
        if (track == null) {
            android.util.Log.e("VideoPreviewPlayer", "Track not found: $trackId")
            return null
        }

        android.util.Log.d("VideoPreviewPlayer", "Found track: ${track.name}, path: ${track.assetPath}")

        // Copy asset to cache (ExoPlayer can't read asset:// URIs)
        return try {
            val fileName = track.assetPath.substringAfterLast("/")
            val cacheFile = File(context.cacheDir, "audio/$fileName")
            android.util.Log.d("VideoPreviewPlayer", "Cache file: ${cacheFile.absolutePath}, exists: ${cacheFile.exists()}")

            if (!cacheFile.exists()) {
                cacheFile.parentFile?.mkdirs()
                context.assets.open(track.assetPath).use { input ->
                    cacheFile.outputStream().use { output ->
                        val bytes = input.copyTo(output)
                        android.util.Log.d("VideoPreviewPlayer", "Copied $bytes bytes to cache")
                    }
                }
            }

            Uri.fromFile(cacheFile)
        } catch (e: Exception) {
            android.util.Log.e("VideoPreviewPlayer", "Failed to load audio: ${e.message}", e)
            null
        }
    }

    return null
}