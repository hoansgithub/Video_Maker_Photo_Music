package com.videomaker.aimusic.core.playback

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.videomaker.aimusic.domain.model.MusicSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays a single home-banner song inline, with the nuanced pause/resume rules required by the
 * banner feature:
 *
 * - Tapping the play CTA starts playback and stops the banner auto-swipe.
 * - **Cleared** (must re-tap to play again): swiping to another banner, switching tabs, or
 *   navigating to another screen and coming back.
 * - **Suspended then resumed automatically**: app sent to background then reopened, or a popup/AD
 *   shown over the app while playing and then dismissed.
 *
 * The distinction between "app background" (resume) and "navigate to another screen" (don't resume)
 * is made with a debounce-free started-activity counter ([ActivityLifecycleCallbacks]) instead of
 * `ProcessLifecycleOwner`, so the navigation-clear can reliably tell the two apart.
 *
 * App-scoped Koin `single`. All player interaction happens on the main thread.
 */
class BannerSongPlayer(
    context: Context,
    private val engineFactory: (Context) -> BannerAudioEngine = { ExoPlayerBannerAudioEngine(it) },
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var engine: BannerAudioEngine? = null

    // Desired-state flags (main-thread confined).
    private var released = false
    private var playRequested = false
    private var adPaused = false
    private var appBackgrounded = false
    private var currentSongId: Long? = null

    /** Count of started activities — `> 0` means the app is in the foreground. No debounce. */
    private var startedActivities = 0
    private val isAppForeground: Boolean get() = startedActivities > 0

    /** Song the user has requested to play (drives the CTA icon + auto-swipe pause). null = none. */
    private val _activeSongId = MutableStateFlow<Long?>(null)
    val activeSongId: StateFlow<Long?> = _activeSongId.asStateFlow()

    /** Registers the started-activity counter. Called once from DI setup. */
    fun registerLifecycle() {
        (appContext as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    startedActivities++
                    if (startedActivities == 1) onAppForeground()
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities = (startedActivities - 1).coerceAtLeast(0)
                    if (startedActivities == 0) onAppBackground()
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    /** Start (or switch to) inline playback of [song]. Idempotent for the same song. */
    fun play(song: MusicSong) {
        if (released) return
        val url = song.mp3Url
        if (url.isBlank()) return

        if (currentSongId != song.id) {
            // New song: tear down the old engine and prepare a fresh one.
            engine?.release()
            engine = engineFactory(appContext).also { it.prepare(url) }
            currentSongId = song.id
        } else if (engine == null) {
            engine = engineFactory(appContext).also { it.prepare(url) }
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
        if (_activeSongId.value == song.id) pause() else play(song)
    }

    /**
     * Called when the pager settles on a banner. If a different banner is now current, the playing
     * song was swiped away → clear (user must re-tap to play again).
     */
    fun onBannerSettled(currentBannerSongId: Long?) {
        val active = _activeSongId.value ?: return
        if (active != currentBannerSongId) clearPlayIntent()
    }

    /** Gallery left view via a tab switch (always in the foreground) → clear. */
    fun onScreenHidden() {
        if (_activeSongId.value != null) clearPlayIntent()
    }

    /**
     * Gallery lifecycle stopped. This fires both on app-background and on navigating to another
     * screen. We defer to the next frame so the started-activity counter settles, then clear only
     * if the app is still in the foreground (i.e. it was a navigation, not a background) and no
     * popup/AD overlay is active.
     */
    fun onScreenStopped() {
        if (_activeSongId.value == null) return
        mainHandler.post {
            if (_activeSongId.value != null && isAppForeground && !adPaused) {
                clearPlayIntent()
            }
        }
    }

    /** A popup/AD is covering the app while playing — suspend (keep the play intent, resume later). */
    fun pauseForOverlay() {
        adPaused = true
        applyPlaybackState()
    }

    /** Popup/AD dismissed — resume if still requested. */
    fun resumeFromOverlay() {
        adPaused = false
        applyPlaybackState()
    }

    /** Terminal cleanup (e.g. ViewModel/screen disposal). */
    fun release() {
        released = true
        engine?.release()
        engine = null
        currentSongId = null
        _activeSongId.value = null
    }

    private fun onAppBackground() {
        appBackgrounded = true
        applyPlaybackState()
    }

    private fun onAppForeground() {
        appBackgrounded = false
        applyPlaybackState()
    }

    private fun clearPlayIntent() {
        playRequested = false
        _activeSongId.value = null
        engine?.pause()
    }

    private fun applyPlaybackState() {
        val e = engine ?: return
        if (playRequested && !adPaused && !appBackgrounded && !released) e.play() else e.pause()
    }
}

/**
 * Minimal audio seam so [BannerSongPlayer]'s state machine is testable without ExoPlayer.
 */
interface BannerAudioEngine {
    /** Buffer [url] looping, but do not start playback yet. */
    fun prepare(url: String)
    fun play()
    fun pause()
    fun release()
}

/** ExoPlayer-backed [BannerAudioEngine]. Must be created/used on the main thread. */
private class ExoPlayerBannerAudioEngine(context: Context) : BannerAudioEngine {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true
        )
        repeatMode = Player.REPEAT_MODE_ONE
    }

    override fun prepare(url: String) {
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
}
