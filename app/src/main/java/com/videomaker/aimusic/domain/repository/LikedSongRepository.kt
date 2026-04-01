package com.videomaker.aimusic.domain.repository

import com.videomaker.aimusic.domain.model.MusicSong
import kotlinx.coroutines.flow.Flow

interface LikedSongRepository {
    suspend fun likeSong(song: MusicSong)
    suspend fun unlikeSong(songId: Long)
    fun observeLikedSongs(): Flow<List<MusicSong>>
    fun observeLikedSongIds(): Flow<Set<Long>>
    fun observeIsLiked(songId: Long): Flow<Boolean>
}
