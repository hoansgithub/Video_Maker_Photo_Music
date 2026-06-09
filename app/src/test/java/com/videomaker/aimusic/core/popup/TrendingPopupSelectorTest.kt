package com.videomaker.aimusic.core.popup

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TrendingPopupSelectorTest {

    private fun template(id: String, songId: Long = 1) = VideoTemplate(
        id = id, name = "T-$id", songId = songId, effectSetId = "e", aspectRatio = "9:16"
    )

    private fun song(id: Long) = MusicSong(
        id = id, name = "S-$id", artist = "A", durationMs = 1000, usageCount = 0
    )

    private class FakeTemplateRepo(
        private val items: List<VideoTemplate>
    ) : TemplateRepository {
        override suspend fun getTemplateById(id: String) = Result.success<VideoTemplate?>(null)
        override suspend fun getTemplates(limit: Int, offset: Int) =
            Result.success(items.drop(offset).take(limit))
        override suspend fun getTemplatesByVibeTag(tag: String, limit: Int, offset: Int) = Result.success(emptyList<VideoTemplate>())
        override suspend fun getVibeTags() = Result.success(emptyList<VibeTag>())
        override suspend fun getFeaturedTemplates(limit: Int) = Result.success(emptyList<VideoTemplate>())
        override suspend fun searchTemplates(query: String) = Result.success(emptyList<VideoTemplate>())
        override suspend fun searchTemplates(query: String, limit: Int, offset: Int) = Result.success(emptyList<VideoTemplate>())
        override suspend fun incrementUseCount(templateId: String) = Result.success(Unit)
        override suspend fun clearCache() {}
    }

    private class FakeSongRepo(
        private val items: List<MusicSong>
    ) : SongRepository {
        override suspend fun getFeaturedSongs(limit: Int, offset: Int) =
            Result.success(items.drop(offset).take(limit))
        override suspend fun getSongById(id: Long) = Result.success(items.first { it.id == id })
        override suspend fun searchSongs(query: String) = Result.success(emptyList<MusicSong>())
        override suspend fun getGenres() = Result.success(emptyList<SongGenre>())
        override suspend fun getSongsByGenre(genre: String, limit: Int, offset: Int) = Result.success(emptyList<MusicSong>())
        override suspend fun getSongsPaged(offset: Int, limit: Int) = Result.success(emptyList<MusicSong>())
        override suspend fun getSuggestedSongs(preferredGenres: List<String>, offset: Int, limit: Int) = Result.success(emptyList<MusicSong>())
        override suspend fun getRandomSongs(limit: Int) = Result.success(emptyList<MusicSong>())
        override suspend fun incrementUseCount(songId: Long) = Result.success(Unit)
        override suspend fun clearCache() {}
    }

    @Test
    fun `picks from top K when none excluded`() = runBlocking {
        val templates = (1..10).map { template(it.toString()) }
        val selector = TrendingPopupSelector(
            templateRepository = FakeTemplateRepo(templates),
            songRepository = FakeSongRepo(emptyList()),
            random = Random(seed = 42L)
        )
        val pick = selector.pickTemplate(excludeIds = emptySet())
        assertNotNull(pick)
        assertTrue(templates.map { it.id }.contains(pick!!.id))
    }

    @Test
    fun `excludes already-shown ids`() = runBlocking {
        val templates = (1..3).map { template(it.toString()) }
        val selector = TrendingPopupSelector(
            templateRepository = FakeTemplateRepo(templates),
            songRepository = FakeSongRepo(emptyList()),
            random = Random(seed = 42L)
        )
        val pick = selector.pickTemplate(excludeIds = setOf("1", "2"))
        assertEquals("3", pick?.id)
    }

    @Test
    fun `returns null when all top K already shown`() = runBlocking {
        val templates = (1..3).map { template(it.toString()) }
        val selector = TrendingPopupSelector(
            templateRepository = FakeTemplateRepo(templates),
            songRepository = FakeSongRepo(emptyList()),
            random = Random(seed = 42L)
        )
        val pick = selector.pickTemplate(excludeIds = setOf("1", "2", "3"))
        assertNull(pick)
    }

    @Test
    fun `returns null on empty repo result`() = runBlocking {
        val selector = TrendingPopupSelector(
            templateRepository = FakeTemplateRepo(emptyList()),
            songRepository = FakeSongRepo(emptyList()),
            random = Random(seed = 42L)
        )
        val pick = selector.pickTemplate(excludeIds = emptySet())
        assertNull(pick)
    }

    @Test
    fun `excludes templates with non-positive songId so popup never applies empty music`() = runBlocking {
        // Only template "3" has a valid song; "1" (0) and "2" (-1) would yield no music downstream.
        val templates = listOf(
            template("1", songId = 0),
            template("2", songId = -1),
            template("3", songId = 7)
        )
        val selector = TrendingPopupSelector(
            templateRepository = FakeTemplateRepo(templates),
            songRepository = FakeSongRepo(emptyList()),
            random = Random(seed = 42L)
        )
        val pick = selector.pickTemplate(excludeIds = emptySet())
        assertEquals("3", pick?.id)
    }

    @Test
    fun `returns null when every top K template has non-positive songId`() = runBlocking {
        val templates = listOf(template("1", songId = 0), template("2", songId = 0))
        val selector = TrendingPopupSelector(
            templateRepository = FakeTemplateRepo(templates),
            songRepository = FakeSongRepo(emptyList()),
            random = Random(seed = 42L)
        )
        val pick = selector.pickTemplate(excludeIds = emptySet())
        assertNull(pick)
    }

    @Test
    fun `picks song from featured songs`() = runBlocking {
        val songs = (1L..5L).map { song(it) }
        val selector = TrendingPopupSelector(
            templateRepository = FakeTemplateRepo(emptyList()),
            songRepository = FakeSongRepo(songs),
            random = Random(seed = 42L)
        )
        val pick = selector.pickSong(excludeIds = emptySet())
        assertNotNull(pick)
        assertTrue(songs.map { it.id }.contains(pick!!.id))
    }

    @Test
    fun `excludes already-shown song ids`() = runBlocking {
        val songs = (1L..3L).map { song(it) }
        val selector = TrendingPopupSelector(
            templateRepository = FakeTemplateRepo(emptyList()),
            songRepository = FakeSongRepo(songs),
            random = Random(seed = 42L)
        )
        val pick = selector.pickSong(excludeIds = setOf("1", "2"))
        assertEquals(3L, pick?.id)
    }
}
