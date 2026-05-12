package com.videomaker.aimusic.modules.templatepreviewer.components

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.media.audio.HookStartTimePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Helper - Release player on main thread to avoid crash
 */
private fun ExoPlayer.releaseAsync() {
    val playerToRelease = this
    ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.Main) {
        runCatching {
            android.util.Log.d("UserSongBackgroundPlayer", "Releasing player on main thread...")
            playerToRelease.release()
            android.util.Log.d("UserSongBackgroundPlayer", "Player released successfully")
        }.onFailure { e ->
            android.util.Log.e("UserSongBackgroundPlayer", "Failed to release player", e)
        }
    }
}

/**
 * UserSongBackgroundPlayer - Background audio player for user's selected song in TemplatePreviewer
 *
 * Features:
 * - Plays user's selected song while browsing templates
 * - Starts from hook position (using HookStartTimePolicy)
 * - Loops continuously
 * - Minimal buffer for instant playback
 * - Async release to avoid ANR
 * - Automatically pauses/resumes with Activity lifecycle (ads, background)
 *
 * @param song The user's selected song to play
 * @param autoPlay Whether to start playback automatically (default: true)
 * @param loop Whether to loop the song (default: true)
 * @param startFromHook Whether to start from hook position (default: true)
 * @param volume Playback volume (default: 1.0)
 * @param onPlaybackStateChanged Callback for playback state changes
 */
@OptIn(UnstableApi::class)
@Composable
fun UserSongBackgroundPlayer(
    song: MusicSong,
    cacheDataSourceFactory: CacheDataSource.Factory,
    autoPlay: Boolean = true,
    loop: Boolean = true,
    startFromHook: Boolean = true,
    volume: Float = 1.0f,
    onPlaybackStateChanged: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current

    val player = remember(song.id) {
        // Configure instant playback with minimal buffer
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 500,
                /* maxBufferMs */ 3000,
                /* bufferForPlaybackMs */ 300,
                /* bufferForPlaybackAfterRebufferMs */ 500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Configure audio attributes to handle audio focus properly
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)  // Handle audio focus
            .build()
            .apply {
                repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                this.volume = volume
            }
    }

    val listener = remember(song.id) {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                onPlaybackStateChanged?.invoke(playbackState)

                when (playbackState) {
                    Player.STATE_READY -> {
                        // Auto-play here when player is ready and autoPlay is enabled
                        // This is the first time we can safely call play()
                        if (autoPlay && !player.isPlaying) {
                            android.util.Log.d("UserSongBackgroundPlayer", "Player STATE_READY - Starting auto-play")
                            player.play()
                        } else {
                            android.util.Log.d("UserSongBackgroundPlayer", "Player STATE_READY - Skipping auto-play (autoPlay=$autoPlay, isPlaying=${player.isPlaying})")
                        }
                    }
                    Player.STATE_ENDED -> {
                        // Handled by repeat mode
                    }
                }
            }
        }
    }

    // Apply volume reactively. The volume set inside remember{} only runs once at
    // player creation, so changes (e.g., mute on AD show) wouldn't take effect.
    LaunchedEffect(player, volume) {
        player.volume = volume
    }

    // Track whether music was playing before pause (for ad interruption)
    var wasPlayingBeforePause by remember { mutableStateOf(false) }

    android.util.Log.d("UserSongBackgroundPlayer", "Setting up ProcessLifecycleOwner observer for song: ${song.id}")

    // Use ProcessLifecycleOwner to detect app background/foreground (ads, home button, etc.)
    // When app goes to background → ON_STOP → music pause
    // When app returns to foreground → ON_START → music resume
    DisposableEffect(song.id) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // App went to background (ad showed, home button pressed, etc.)
                    val isActuallyPlaying = player.playbackState == Player.STATE_READY && player.isPlaying
                    wasPlayingBeforePause = autoPlay && isActuallyPlaying

                    android.util.Log.d("UserSongBackgroundPlayer", "App ON_STOP - Pausing music (autoPlay=$autoPlay, wasPlaying=$wasPlayingBeforePause, isPlaying=${player.isPlaying}, playbackState=${player.playbackState})")

                    if (player.isPlaying) {
                        player.pause()
                        android.util.Log.d("UserSongBackgroundPlayer", "App ON_STOP - Music paused successfully")
                    }
                }
                Lifecycle.Event.ON_START -> {
                    // App returned to foreground (ad closed, app resumed, etc.)
                    android.util.Log.d("UserSongBackgroundPlayer", "App ON_START - Checking if should resume (autoPlay=$autoPlay, wasPlaying=$wasPlayingBeforePause, isPlaying=${player.isPlaying}, playbackState=${player.playbackState})")

                    // Wait a bit to ensure ad is fully dismissed
                    ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.Main.immediate) {
                        delay(300)

                        android.util.Log.d("UserSongBackgroundPlayer", "App ON_START - After delay (autoPlay=$autoPlay, wasPlaying=$wasPlayingBeforePause, isPlaying=${player.isPlaying})")

                        if (autoPlay && wasPlayingBeforePause && !player.isPlaying) {
                            player.play()
                            android.util.Log.d("UserSongBackgroundPlayer", "App ON_START - Music resumed")
                        } else {
                            android.util.Log.d("UserSongBackgroundPlayer", "App ON_START - Skipping resume (autoPlay=$autoPlay, wasPlaying=$wasPlayingBeforePause)")
                        }
                    }
                }
                else -> Unit
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        onDispose {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(song.id) {
        val url = song.mp3Url.ifEmpty { song.previewUrl }
        if (url.isNotEmpty()) {
            player.addListener(listener)
            runCatching {
                val mediaItem = MediaItem.fromUri(url)
                player.setMediaItem(mediaItem)
                player.prepare()

                // Seek to hook position if requested
                if (startFromHook) {
                    val hookStartMs = HookStartTimePolicy.resolve(
                        hookStartTimeMs = song.hookStartTimeMs,
                        durationMs = song.durationMs?.toLong()
                    )
                    if (hookStartMs > 0L) {
                        player.seekTo(hookStartMs)
                    }
                }
            }.onFailure { e ->
                android.util.Log.e("UserSongBackgroundPlayer", "Failed to load song: ${song.name}", e)
            }
        }

        onDispose {
            player.removeListener(listener)
            player.stop()
            player.releaseAsync()
        }
    }
}
