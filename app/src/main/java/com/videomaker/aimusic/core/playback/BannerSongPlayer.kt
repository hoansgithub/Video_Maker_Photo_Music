package com.videomaker.aimusic.core.playback

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.videomaker.aimusic.domain.model.MusicSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays a single home-banner song inline.
 *
 * - Tapping the play CTA starts playback and stops the banner auto-swipe.
 * - **Cleared** (must re-tap to play again): swiping to another banner, switching tabs,
 *   navigating to another screen, ad overlay, app background — any focus loss.
 *
 * The single safe gate is [onScreenInactive], called on the gallery's lifecycle ON_PAUSE.
 * This covers ALL edge cases (navigation, ads, background, "create" button, etc.).
 *
 * App-scoped Koin `single`. All player interaction happens on the main thread.
 */
class BannerSongPlayer(
    context: Context,
    private val engineFactory: (Context) -> BannerAudioEngine = { ExoPlayerBannerAudioEngine(it) },
) {
    private val appContext = context.applicationContext
    private companion object { const val TAG = "BannerSongPlayer" }

    private var engine: BannerAudioEngine? = null

    // Desired-state flags (main-thread confined).
    private var released = false
    private var playRequested = false
    private var currentSongId: Long? = null

    /** Song the user has requested to play (drives the CTA icon + auto-swipe pause). null = none. */
    private val _activeSongId = MutableStateFlow<Long?>(null)
    val activeSongId: StateFlow<Long?> = _activeSongId.asStateFlow()

    /** Start (or switch to) inline playback of [song]. Idempotent for the same song. */
    fun play(song: MusicSong) {
        if (released) { Log.d(TAG, "play() aborted: released"); return }
        val url = song.mp3Url
        if (url.isBlank()) { Log.d(TAG, "play() aborted: blank url for ${song.id}"); return }

        if (currentSongId != song.id) {
            Log.d(TAG, "play() new song ${song.id} (was $currentSongId)")
            engine?.release()
            engine = engineFactory(appContext).also { it.prepare(url) }
            currentSongId = song.id
        } else if (engine == null || engine?.hasError == true) {
            Log.d(TAG, "play() recreate engine (null=${engine == null}, error=${engine?.hasError})")
            engine?.release()
            engine = engineFactory(appContext).also { it.prepare(url) }
        } else {
            Log.d(TAG, "play() reuse engine for ${song.id}")
        }
        playRequested = true
        _activeSongId.value = song.id
        applyPlaybackState()
    }

    /** User-initiated pause (tapping the pause CTA). Stops playback and clears the play intent. */
    fun pause() {
        clearPlayIntent()
    }

    /** Convenience toggle used by the banner CTA. */
    fun toggle(song: MusicSong) {
        Log.d(TAG, "toggle() songId=${song.id}, activeSongId=${_activeSongId.value}")
        if (_activeSongId.value == song.id) pause() else play(song)
    }

    /**
     * Called when the pager settles on a banner. If a different banner is now current, the playing
     * song was swiped away → clear (user must re-tap to play again).
     */
    fun onBannerSettled(currentBannerSongId: Long?) {
        val active = _activeSongId.value ?: return
        if (active != currentBannerSongId) {
            Log.d(TAG, "onBannerSettled: clearing (active=$active, settled=$currentBannerSongId)")
            clearPlayIntent()
        }
    }

    /** Gallery left view via a tab switch (always in the foreground) → clear. */
    fun onScreenHidden() {
        if (_activeSongId.value != null) {
            Log.d(TAG, "onScreenHidden: clearing")
            clearPlayIntent()
        }
    }

    /**
     * Safe gate: called on lifecycle ON_PAUSE — covers ALL cases where the gallery
     * loses focus (navigation, ad overlay, app background, "create" button, etc.).
     * Always clears the play intent so the user must re-tap to play.
     */
    fun onScreenInactive() {
        if (_activeSongId.value == null) return
        Log.d(TAG, "onScreenInactive: clearing")
        clearPlayIntent()
    }

    /** Terminal cleanup (e.g. ViewModel/screen disposal). */
    fun release() {
        released = true
        engine?.release()
        engine = null
        currentSongId = null
        _activeSongId.value = null
    }

    private fun clearPlayIntent() {
        Log.d(TAG, "clearPlayIntent (was active=${_activeSongId.value})")
        playRequested = false
        _activeSongId.value = null
        engine?.pause()
    }

    private fun applyPlaybackState() {
        val e = engine ?: return
        val shouldPlay = playRequested && !released
        Log.d(TAG, "applyPlaybackState: play=$shouldPlay (req=$playRequested, rel=$released)")
        if (shouldPlay) e.play() else e.pause()
    }
}

/**
 * Minimal audio seam so [BannerSongPlayer]'s state machine is testable without ExoPlayer.
 */
interface BannerAudioEngine {
    /** True when the engine has encountered a playback error and cannot play until re-prepared. */
    val hasError: Boolean
    /** Buffer [url] looping, but do not start playback yet. */
    fun prepare(url: String)
    fun play()
    fun pause()
    fun release()
}

/** ExoPlayer-backed [BannerAudioEngine]. Must be created/used on the main thread. */
private class ExoPlayerBannerAudioEngine(context: Context) : BannerAudioEngine {

    override var hasError: Boolean = false
        private set

    private val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true
        )
        repeatMode = Player.REPEAT_MODE_ONE
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Banner audio error: ${error.message}")
                hasError = true
            }
        })
    }

    override fun prepare(url: String) {
        hasError = false
        player.setMediaItem(MediaItem.fromUri(url))
        player.playWhenReady = false
        player.prepare()
    }

    override fun play() {
        player.playWhenReady = true
    }

    override fun pause() {
        player.playWhenReady = false
    }

    override fun release() {
        player.release()
    }

    companion object {
        private const val TAG = "BannerSongPlayer"
    }
}
