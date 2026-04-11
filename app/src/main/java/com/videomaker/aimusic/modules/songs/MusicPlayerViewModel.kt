package com.videomaker.aimusic.modules.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.ads.RewardedAdController
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.storage.UnlockedSongsManager
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.LikedSongRepository
import com.videomaker.aimusic.domain.usecase.LikeSongUseCase
import com.videomaker.aimusic.domain.usecase.UnlikeSongUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ============================================
// VIEW MODEL
// ============================================

class MusicPlayerViewModel(
    private val songId: Long,
    private val song: MusicSong,
    private val likeSongUseCase: LikeSongUseCase,
    private val unlikeSongUseCase: UnlikeSongUseCase,
    likedSongRepository: LikedSongRepository,
    private val unlockedSongsManager: UnlockedSongsManager,
    private val adsLoaderService: AdsLoaderService
) : ViewModel() {

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
        adsLoaderService = adsLoaderService,
        viewModelScope = viewModelScope
    )

    // Expose rewarded ad states
    val showWatchAdDialog: StateFlow<Boolean> = rewardedAdController.showWatchAdDialog
    val shouldPresentAd: StateFlow<Boolean> = rewardedAdController.shouldPresentAd

    // Check if song is unlocked
    val isSongUnlocked: StateFlow<Boolean> = unlockedSongsManager.unlockedSongIds
        .map { unlockedIds ->
            !song.isPremium || unlockedIds.contains(song.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Callback for after song is used to create
    private var onSongUnlockedCallback: (() -> Unit)? = null

    fun toggleLike(song: MusicSong) {
        viewModelScope.launch {
            if (isLiked.value) {
                unlikeSongUseCase(songId)
            } else {
                likeSongUseCase(song)
            }
        }
    }

    fun onUseToCreateClick(onProceed: () -> Unit) {
        if (song.isPremium && !unlockedSongsManager.isUnlocked(song.id)) {
            // Song is locked - request ad
            onSongUnlockedCallback = onProceed
            rewardedAdController.requestAd(
                onReward = {
                    viewModelScope.launch {
                        unlockedSongsManager.unlockSong(song.id)
                        onSongUnlockedCallback?.invoke()
                        onSongUnlockedCallback = null
                    }
                },
                onSkip = {
                    // Ad disabled - unlock for free
                    viewModelScope.launch {
                        unlockedSongsManager.unlockSong(song.id)
                        onSongUnlockedCallback?.invoke()
                        onSongUnlockedCallback = null
                    }
                },
                checkEnabled = { adsLoaderService.canLoadAd(AdPlacement.REWARD_UNLOCK_SONG) }
            )
        } else {
            // Song is unlocked or free - proceed
            onProceed()
        }
    }

    fun onWatchAdDialogDismiss() {
        rewardedAdController.onDialogDismiss()
        onSongUnlockedCallback = null
    }

    fun onWatchAdConfirmed() {
        rewardedAdController.onDialogConfirm()
    }

    fun onRewardEarned() {
        rewardedAdController.onRewardEarned()
    }

    fun onAdFailed() {
        rewardedAdController.onAdFailed()
        onSongUnlockedCallback = null
    }
}
