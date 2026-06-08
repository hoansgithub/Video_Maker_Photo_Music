package com.videomaker.aimusic.modules.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.ads.RewardedAdController
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.playback.MusicPlaybackSessionManager
import com.videomaker.aimusic.core.rating.RatingTriggerManager
import com.videomaker.aimusic.core.storage.UnlockedSongsManager
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.LikedSongRepository
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.usecase.LikeSongUseCase
import com.videomaker.aimusic.domain.usecase.UnlikeSongUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

// ============================================
// VIEW MODEL
// ============================================

class MusicPlayerViewModel(
    private val songId: Long,
    private val song: MusicSong,
    private val initialPlaylist: List<MusicSong>,
    private val categoryLocation: String,
    private val initialGenreId: String?,
    private val likeSongUseCase: LikeSongUseCase,
    private val unlikeSongUseCase: UnlikeSongUseCase,
    likedSongRepository: LikedSongRepository,
    private val unlockedSongsManager: UnlockedSongsManager,
    private val adsLoaderService: AdsLoaderService,
    private val songRepository: SongRepository,
    private val sessionManager: MusicPlaybackSessionManager,
    private val ratingTriggerManager: RatingTriggerManager
) : ViewModel() {

    private companion object {
        const val MAX_HISTORY = 50
        const val MAX_QUEUE = 200
        const val MAX_GENRE_FETCH_RETRIES = 3
        const val NEW_GENRE_FETCH_LIMIT = 20
    }

    // ── Existing state (unchanged behavior) ─────────────────────────

    val isLiked: StateFlow<Boolean> = likedSongRepository
        .observeIsLiked(songId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    // Rewarded ad controller for song unlock
    private val rewardedAdController = RewardedAdController(
        placement = AdPlacement.REWARD_UNLOCK_SONG,
        viewModelScope = viewModelScope
    )

    // Expose rewarded ad state
    val shouldPresentAd: StateFlow<Boolean> = rewardedAdController.shouldPresentAd

    // Check if song is unlocked
    val isSongUnlocked: StateFlow<Boolean> = unlockedSongsManager.unlockedSongIds
        .map { unlockedIds ->
            !song.isPremium || unlockedIds.contains(song.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Callback for after song is used to create
    private var onSongUnlockedCallback: (() -> Unit)? = null

    // ── Playback queue + history ────────────────────────────────────

    private val _currentSong = MutableStateFlow(song)
    val currentSong: StateFlow<MusicSong> = _currentSong.asStateFlow()

    private val _canGoPrev = MutableStateFlow(false)
    val canGoPrev: StateFlow<Boolean> = _canGoPrev.asStateFlow()

    private val history: ArrayDeque<MusicSong> = ArrayDeque()
    private var queue: List<MusicSong> = initialPlaylist.toList()
    private var pivotJob: Job? = null

    // ── Navigation API ──────────────────────────────────────────────

    /**
     * Advance to the next song. Algorithm:
     *  1. First unimpressed song in [queue] (excluding current).
     *  2. Else pivot: fetch songs from a random unused genre, retry up to 3 times.
     *  3. Else loop replay: reset usedGenres, pick from [initialPlaylist].
     *
     * Concurrent calls during an active genre fetch are coalesced via [pivotJob].
     */
    fun onNext() {
        val current = _currentSong.value
        val impressed = sessionManager.impressedSongIds.value

        val queueCandidate = findNextUnimpressedSong(queue, current.id, impressed)
        if (queueCandidate != null) {
            pushHistory(current)
            switchTo(queueCandidate)
            return
        }

        if (pivotJob?.isActive == true) return
        pivotJob = viewModelScope.launch(Dispatchers.IO) {
            val pivoted = fetchAndPivotToNewGenre(retriesLeft = MAX_GENRE_FETCH_RETRIES)
            if (!pivoted) {
                loopFromInitialPlaylist(current)
            }
        }
    }

    /**
     * Step back to the previously-played song. No-op when history is empty.
     */
    fun onPrev() {
        val prev = history.removeLastOrNull() ?: return
        _canGoPrev.value = history.isNotEmpty()
        _currentSong.value = prev
        Analytics.trackSongBack(songId = prev.id.toString())
        Analytics.trackSongImpression(
            songId = prev.id.toString(),
            songName = prev.name,
            location = AnalyticsEvent.Value.Location.SONG_PLAYER,
            screenSessionId = "",
            isPremium = prev.isPremium
        )
        sessionManager.markImpressed(prev.id)
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun pushHistory(songToPush: MusicSong) {
        history.addLast(songToPush)
        while (history.size > MAX_HISTORY) history.removeFirst()
        _canGoPrev.value = true
    }

    private fun switchTo(next: MusicSong) {
        _currentSong.value = next
        sessionManager.markImpressed(next.id)
        Analytics.trackSongNext(songId = next.id.toString())
        ratingTriggerManager.onSongNexted()
        Analytics.trackSongImpression(
            songId = next.id.toString(),
            songName = next.name,
            location = AnalyticsEvent.Value.Location.SONG_PLAYER,
            screenSessionId = "",
            isPremium = next.isPremium
        )
    }

    /**
     * Fetch songs from a random unused genre, append to queue, switch to first unimpressed.
     * Returns true on success, false when no genre yields a switchable song after
     * [retriesLeft] attempts. Runs on Dispatchers.IO; mutates StateFlow on Main.
     */
    private suspend fun fetchAndPivotToNewGenre(retriesLeft: Int): Boolean {
        if (retriesLeft <= 0) return false
        val allGenres = songRepository.getGenres().getOrNull() ?: return false
        val pick = pickRandomAvailableGenre(
            allGenres = allGenres,
            usedGenres = sessionManager.usedGenreIds.value,
            initialGenre = initialGenreId,
            random = Random.Default
        ) ?: return false

        val fetched = songRepository.getSongsByGenre(pick.id, NEW_GENRE_FETCH_LIMIT)
            .getOrNull().orEmpty()
        sessionManager.markGenreUsed(pick.id)
        if (fetched.isEmpty()) {
            return fetchAndPivotToNewGenre(retriesLeft - 1)
        }
        queue = appendCapped(queue, fetched, MAX_QUEUE)
        val current = _currentSong.value
        val impressed = sessionManager.impressedSongIds.value
        val target = fetched.firstOrNull { it.id != current.id && it.id !in impressed }
            ?: return fetchAndPivotToNewGenre(retriesLeft - 1)

        withContext(Dispatchers.Main) {
            pushHistory(current)
            switchTo(target)
        }
        return true
    }

    /**
     * All genres exhausted — reset usedGenres and replay from initial playlist.
     * Intentionally allows repeating already-heard songs (spec behavior).
     */
    private suspend fun loopFromInitialPlaylist(current: MusicSong) {
        sessionManager.resetUsedGenres()
        val replay = pickReplaySong(initialPlaylist, current.id) ?: return
        withContext(Dispatchers.Main) {
            pushHistory(current)
            switchTo(replay)
        }
    }

    // ── Existing API (unchanged) ────────────────────────────────────

    fun toggleLike(song: MusicSong) {
        viewModelScope.launch {
            if (isLiked.value) {
                unlikeSongUseCase(songId)
            } else {
                likeSongUseCase(song)
            }
        }
    }

    fun onUseToCreateClick(onProceed: () -> Unit, onProceedWithAd: () -> Unit) {
        val current = _currentSong.value
        viewModelScope.launch(Dispatchers.IO) {
            songRepository.incrementUseCount(current.id)
        }

        if (current.isPremium && !unlockedSongsManager.isUnlocked(current.id)) {
            onSongUnlockedCallback = onProceed
            rewardedAdController.requestAd(
                onReward = {
                    viewModelScope.launch {
                        unlockedSongsManager.unlockSong(current.id)
                        onSongUnlockedCallback?.invoke()
                        onSongUnlockedCallback = null
                    }
                },
                onSkip = {
                    viewModelScope.launch {
                        unlockedSongsManager.unlockSong(current.id)
                        onSongUnlockedCallback?.invoke()
                        onSongUnlockedCallback = null
                    }
                },
                checkEnabled = { adsLoaderService.canLoadAd(AdPlacement.REWARD_UNLOCK_SONG) }
            )
        } else {
            // Free/unlocked song — show interstitial with loading overlay
            // Ad loads on tap with 10s timeout; proceeds immediately if ad fails
            onProceedWithAd()
        }
    }

    fun onRewardEarned() {
        rewardedAdController.onRewardEarned()
    }

    fun onAdFailed() {
        rewardedAdController.onAdFailed()
        onSongUnlockedCallback = null
    }
}
