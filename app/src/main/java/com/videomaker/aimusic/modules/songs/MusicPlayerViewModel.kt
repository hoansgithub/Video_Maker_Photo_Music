package com.videomaker.aimusic.modules.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.LikedSongRepository
import com.videomaker.aimusic.domain.usecase.LikeSongUseCase
import com.videomaker.aimusic.domain.usecase.UnlikeSongUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ============================================
// VIEW MODEL
// ============================================

class MusicPlayerViewModel(
    private val songId: Long,
    private val likeSongUseCase: LikeSongUseCase,
    private val unlikeSongUseCase: UnlikeSongUseCase,
    likedSongRepository: LikedSongRepository
) : ViewModel() {

    val isLiked: StateFlow<Boolean> = likedSongRepository
        .observeIsLiked(songId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun toggleLike(song: MusicSong) {
        viewModelScope.launch {
            if (isLiked.value) {
                android.util.Log.d("MusicPlayer", "Unliking song: ${song.name} (id=$songId)")
                unlikeSongUseCase(songId)
                android.util.Log.d("MusicPlayer", "Song unliked successfully")
            } else {
                android.util.Log.d("MusicPlayer", "Liking song: ${song.name} (id=$songId)")
                likeSongUseCase(song)
                android.util.Log.d("MusicPlayer", "Song liked successfully")
            }
        }
    }
}
