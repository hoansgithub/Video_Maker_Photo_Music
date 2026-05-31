package com.videomaker.aimusic.core.playback

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.videomaker.aimusic.domain.repository.SongRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays a looping background song during the onboarding flow.
 *
 * Two-phase startup so playback is instant by the time the Language screen appears:
 * - [preload] is called as early as possible (at splash). It fetches the geo top-1 song
 *   ([SongRepository.getFeaturedSongs] with limit = 1, already region-sorted) and buffers it
 *   silently. ExoPlayer keeps buffering in the background through the post-splash interstitial ad.
 * - [start] is called when onboarding actually begins (LanguageSelectionActivity). If the song is
 *   already buffered it plays immediately; otherwise it kicks off the load and plays when ready.
 *
 * Other behaviour:
 * - [pauseForAd] / [resumeAfterAd] silence the song only while the fullscreen onboarding ad step
 *   is visible.
 * - App background/foreground is handled globally via [ProcessLifecycleOwner] so transitions
 *   *between* onboarding Activities never pause the music — only a real app-background does.
 * - [stop] is called when the user reaches Home (MainActivity); it releases the player and is
 *   terminal — the music will not restart afterwards.
 *
 * This is an app-scoped Koin `single`. All player interaction happens on the main thread.
 */
class OnboardingMusicPlayer(
    private val context: Context,
    private val songRepository: SongRepository,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val engineFactory: () -> OnboardingAudioEngine = { ExoPlayerOnboardingAudioEngine(context) }
) {

    private companion object {
        // Bounded retry so loading spans the splash + post-splash ad window while the backend warms up.
        const val MAX_LOAD_ATTEMPTS = 5
        const val RETRY_DELAY_MS = 1_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)

    private var engine: OnboardingAudioEngine? = null
    private var loadJob: Job? = null

    // State flags (main-thread confined).
    private var released = false
    private var playRequested = false
    private var adPaused = false
    private var appBackgrounded = false

    /**
     * Registers the global app foreground/background observer. Called once from DI setup.
     * Kept out of the constructor so the state machine can be unit-tested without Android.
     */
    fun observeProcessLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = onAppForeground()
            override fun onStop(owner: LifecycleOwner) = onAppBackground()
        })
    }

    /** Silently fetch + buffer the song (e.g. at splash) without starting playback. No-op after [stop]. */
    fun preload() = ensureLoaded()

    /**
     * Request playback (e.g. when the Language screen appears). Idempotent. Buffers first if not
     * already preloaded, then plays as soon as the song is ready. No-op after [stop].
     */
    fun start() {
        if (released) return
        playRequested = true
        ensureLoaded()
        applyPlaybackState()
    }

    /** Pause while the fullscreen onboarding ad step is visible. */
    fun pauseForAd() {
        adPaused = true
        applyPlaybackState()
    }

    /** Resume after leaving the fullscreen onboarding ad step (unless backgrounded/stopped). */
    fun resumeAfterAd() {
        adPaused = false
        applyPlaybackState()
    }

    internal fun onAppBackground() {
        appBackgrounded = true
        applyPlaybackState()
    }

    internal fun onAppForeground() {
        appBackgrounded = false
        applyPlaybackState()
    }

    /** Terminal. Releases the player when the user enters Home. */
    fun stop() {
        released = true
        loadJob?.cancel()
        engine?.release()
        engine = null
    }

    /** Fetches the song (with bounded retry) and prepares the engine. Runs at most once. */
    private fun ensureLoaded() {
        if (released || engine != null || loadJob?.isActive == true) return
        loadJob = scope.launch {
            var url: String? = null
            for (attempt in 0 until MAX_LOAD_ATTEMPTS) {
                if (released) return@launch
                url = runCatching {
                    songRepository.getFeaturedSongs(limit = 1, offset = 0).getOrNull()
                }.getOrNull()?.firstOrNull()?.mp3Url
                if (!url.isNullOrBlank()) break
                delay(RETRY_DELAY_MS)
            }

            val resolved = url
            // Bail silently if there is no playable song or we were stopped while loading.
            if (resolved.isNullOrBlank() || released) return@launch

            engine = engineFactory().also { it.prepare(resolved) }
            applyPlaybackState()
        }
    }

    /** Plays or pauses the (prepared) engine according to the current desired state. */
    private fun applyPlaybackState() {
        val e = engine ?: return
        if (playRequested && !adPaused && !appBackgrounded && !released) e.play() else e.pause()
    }
}

/**
 * Minimal audio seam so [OnboardingMusicPlayer]'s state machine is testable without ExoPlayer.
 */
interface OnboardingAudioEngine {
    /** Buffer [url] looping, but do not start playback yet. */
    fun prepare(url: String)
    fun play()
    fun pause()
    fun release()
}

/** ExoPlayer-backed [OnboardingAudioEngine]. Must be created/used on the main thread. */
private class ExoPlayerOnboardingAudioEngine(context: Context) : OnboardingAudioEngine {

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
        player.playWhenReady = false  // buffer silently until play() is called
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
