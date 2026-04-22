package com.videomaker.aimusic.data.repository

import com.videomaker.aimusic.data.local.database.dao.LikedSongDao
import com.videomaker.aimusic.data.local.database.entity.LikedSongEntity
import com.videomaker.aimusic.domain.model.MusicSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LikedSongRepositoryHookStartTimeTest {

    @Test
    fun likeSong_andObserveLikedSongs_preserveHookStartTime() = runBlocking {
        val dao = FakeLikedSongDao()
        val repository = LikedSongRepositoryImpl(dao)
        val song = MusicSong(
            id = 42L,
            name = "Hooked",
            artist = "DJ Test",
            mp3Url = "https://example.com/song.mp3",
            previewUrl = "https://example.com/song-preview.mp3",
            coverUrl = "https://example.com/song.jpg",
            durationMs = 95_000,
            hookStartTimeMs = 12_345L
        )

        repository.likeSong(song)

        assertEquals(12_345L, dao.requireEntity(song.id).hookStartTimeMs)

        val observedSong = repository.observeLikedSongs().first().single()
        assertEquals(12_345L, observedSong.hookStartTimeMs)
    }

    @Test
    fun observeLikedSongs_defaultsHookStartTimeToZero_forLegacyStoredEntity() = runBlocking {
        val dao = FakeLikedSongDao(
            initialEntities = listOf(
                LikedSongEntity(
                    songId = 99L,
                    name = "Legacy",
                    artist = "Stored Artist",
                    coverUrl = "https://example.com/legacy.jpg",
                    mp3Url = "https://example.com/legacy.mp3",
                    previewUrl = "https://example.com/legacy-preview.mp3",
                    durationMs = 87_000
                )
            )
        )
        val repository = LikedSongRepositoryImpl(dao)

        val observedSong = repository.observeLikedSongs().first().single()

        assertEquals(0L, observedSong.hookStartTimeMs)
    }

    private class FakeLikedSongDao : LikedSongDao {
        constructor(initialEntities: List<LikedSongEntity> = emptyList()) {
            entities = MutableStateFlow(initialEntities)
        }

        private val entities: MutableStateFlow<List<LikedSongEntity>>

        override suspend fun insert(entity: LikedSongEntity) {
            entities.value = entities.value
                .filterNot { it.songId == entity.songId } + entity
        }

        override suspend fun deleteById(songId: Long) {
            entities.value = entities.value.filterNot { it.songId == songId }
        }

        override fun observeAll(limit: Int): Flow<List<LikedSongEntity>> =
            entities.map { it.sortedByDescending(LikedSongEntity::likedAt).take(limit) }

        override fun observeIsLiked(songId: Long): Flow<Boolean> =
            entities.map { list -> list.any { it.songId == songId } }

        override fun observeLikedIds(limit: Int): Flow<List<Long>> =
            entities.map { list ->
                list.sortedByDescending(LikedSongEntity::likedAt)
                    .take(limit)
                    .map(LikedSongEntity::songId)
            }

        fun requireEntity(songId: Long): LikedSongEntity {
            return entities.value.firstOrNull { it.songId == songId }
                ?: error("Missing liked song $songId")
        }
    }
}
