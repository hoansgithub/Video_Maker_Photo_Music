package com.videomaker.aimusic.data.repository

import com.videomaker.aimusic.data.local.database.dao.LikedSongDao
import com.videomaker.aimusic.data.local.database.entity.LikedSongEntity
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.LikedSongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LikedSongRepositoryImpl(
    private val dao: LikedSongDao
) : LikedSongRepository {

    override suspend fun likeSong(song: MusicSong) {
        dao.insert(song.toEntity())
    }

    override suspend fun unlikeSong(songId: Long) {
        dao.deleteById(songId)
    }

    override fun observeLikedSongs(): Flow<List<MusicSong>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeLikedSongIds(): Flow<Set<Long>> =
        dao.observeLikedIds().map { it.toSet() }

    override fun observeIsLiked(songId: Long): Flow<Boolean> =
        dao.observeIsLiked(songId)

    private fun MusicSong.toEntity() = LikedSongEntity(
        songId = id,
        name = name,
        artist = artist,
        coverUrl = coverUrl,
        mp3Url = mp3Url,
        previewUrl = previewUrl,
        durationMs = durationMs ?: 0
    )

    private fun LikedSongEntity.toModel() = MusicSong(
        id = songId,
        name = name,
        artist = artist,
        coverUrl = coverUrl,
        mp3Url = mp3Url,
        previewUrl = previewUrl,
        durationMs = durationMs
    )
}
