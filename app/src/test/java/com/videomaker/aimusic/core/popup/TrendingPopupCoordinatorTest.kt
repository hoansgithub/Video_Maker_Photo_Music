package com.videomaker.aimusic.core.popup

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TrendingPopupCoordinatorTest {

    private val nowMs = 1_700_000_000_000L
    private val todayEpochDay = 19_676L

    private fun template(id: String) = VideoTemplate(
        id = id, name = "T-$id", songId = 1, effectSetId = "e", aspectRatio = "9:16"
    )

    private fun song(id: Long) = MusicSong(
        id = id, name = "S-$id", artist = "A", durationMs = 1000, usageCount = 0
    )

    private class FakeTemplateRepo(val items: List<VideoTemplate>) : TemplateRepository {
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

    private class FakeSongRepo(val items: List<MusicSong>) : SongRepository {
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

    private class InMemoryPrefs : PopupSnapshotStore {
        private val map = mutableMapOf<TrendingPopupTab, TrendingPopupDailySnapshot>()
        private val focusCounts = mutableMapOf<TrendingPopupTab, Int>()
        override fun get(tab: TrendingPopupTab) = map[tab]
        override fun set(tab: TrendingPopupTab, snapshot: TrendingPopupDailySnapshot) {
            map[tab] = snapshot
        }
        override fun getFocusCount(tab: TrendingPopupTab) = focusCounts[tab] ?: 0
        override fun incrementFocusCount(tab: TrendingPopupTab) {
            focusCounts[tab] = (focusCounts[tab] ?: 0) + 1
        }
    }

    private class FakeClock(var currentNowMs: Long, val today: Long) : PopupClock {
        override fun nowMs() = currentNowMs
        override fun todayEpochDay() = today
    }

    /**
     * Build a coordinator using Dispatchers.Unconfined so [TrendingPopupCoordinator.onTabFocused]
     * runs synchronously inside the same test thread — no need for kotlinx-coroutines-test.
     */
    private fun coordinator(
        templates: List<VideoTemplate> = (1..3).map { template(it.toString()) },
        songs: List<MusicSong> = (1L..3L).map { song(it) },
        config: TrendingPopupConfigValues = TrendingPopupConfigValues(300L, 3L),
        prefs: InMemoryPrefs = InMemoryPrefs(),
        clock: FakeClock = FakeClock(nowMs, todayEpochDay)
    ): TrendingPopupCoordinator = TrendingPopupCoordinator(
        templateRepository = FakeTemplateRepo(templates),
        songRepository = FakeSongRepo(songs),
        snapshotStore = prefs,
        config = object : PopupConfigSource {
            override fun read() = config
        },
        clock = clock,
        gate = TrendingPopupGate(),
        selectorRandom = Random(seed = 42L),
        scope = CoroutineScope(Dispatchers.Unconfined)
    )

    @Test
    fun `tab focus emits Showing when eligible`() = runBlocking {
        val coord = coordinator()
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        val state = coord.templatePopup.first()
        assertTrue(state is TrendingPopupState.Showing)
    }

    @Test
    fun `second tab focus does not refire while showing`() = runBlocking {
        val coord = coordinator()
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        val firstPick = (coord.templatePopup.first() as TrendingPopupState.Showing).content
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        val secondPick = (coord.templatePopup.first() as TrendingPopupState.Showing).content
        assertEquals(firstPick.id, secondPick.id)
    }

    @Test
    fun `song popup is blocked while template popup is showing`() = runBlocking {
        val coord = coordinator()
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        coord.onTabFocused(TrendingPopupTab.SONGS)
        coord.onTabFocused(TrendingPopupTab.SONGS)
        assertEquals(TrendingPopupState.Hidden, coord.songPopup.first())
    }

    @Test
    fun `dismiss returns to Hidden`() = runBlocking {
        val coord = coordinator()
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        coord.onTemplatePopupDismissed()
        assertEquals(TrendingPopupState.Hidden, coord.templatePopup.first())
    }

    @Test
    fun `cap reached after dailyCap fires`() = runBlocking {
        val clock = FakeClock(nowMs, todayEpochDay)
        val coord = coordinator(
            templates = (1..5).map { template(it.toString()) },
            config = TrendingPopupConfigValues(intervalMinutes = 60L, dailyCap = 2L),
            clock = clock
        )

        coord.onTabFocused(TrendingPopupTab.GALLERY)
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        coord.onTemplatePopupDismissed()
        clock.currentNowMs += 2 * 60 * 60 * 1000L

        coord.onTabFocused(TrendingPopupTab.GALLERY)
        coord.onTemplatePopupDismissed()
        clock.currentNowMs += 2 * 60 * 60 * 1000L

        coord.onTabFocused(TrendingPopupTab.GALLERY)
        assertEquals(TrendingPopupState.Hidden, coord.templatePopup.first())
    }

    @Test
    fun `cta emits OpenTemplatePreviewer for template`() = runBlocking {
        val coord = coordinator()
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        val pick = (coord.templatePopup.first() as TrendingPopupState.Showing).content

        coord.onTemplatePopupCta(pick)
        val nav = coord.navigationEvent.first()
        check(nav is TrendingPopupNavEvent.OpenTemplatePreviewer)
        assertEquals(pick.id, nav.templateId)
        assertEquals(-1L, nav.overrideSongId)
    }

    @Test
    fun `activeTab reflects the focused tab`() = runBlocking {
        val coord = coordinator()
        assertEquals(null, coord.activeTab.first())

        coord.onTabFocused(TrendingPopupTab.GALLERY)
        assertEquals(TrendingPopupTab.GALLERY, coord.activeTab.first())

        coord.onTabFocused(TrendingPopupTab.SONGS)
        assertEquals(TrendingPopupTab.SONGS, coord.activeTab.first())
    }

    @Test
    fun `leaving popup surface clears activeTab but preserves Showing state`() = runBlocking {
        val coord = coordinator()
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        val pick = (coord.templatePopup.first() as TrendingPopupState.Showing).content

        // User swipes to a non-popup surface (e.g. My Videos): popup must be hidden from view...
        coord.onPopupSurfaceInactive()
        assertEquals(null, coord.activeTab.first())
        // ...but its state is preserved so it reappears on returning to Gallery ("show lại").
        val stillShowing = coord.templatePopup.first()
        assertTrue(stillShowing is TrendingPopupState.Showing)
        assertEquals(pick.id, (stillShowing as TrendingPopupState.Showing).content.id)

        // Returning to Gallery restores the same popup without re-picking.
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        assertEquals(TrendingPopupTab.GALLERY, coord.activeTab.first())
        assertEquals(pick.id, (coord.templatePopup.first() as TrendingPopupState.Showing).content.id)
    }

    @Test
    fun `cta emits OpenSongPlayer for song`() = runBlocking {
        val coord = coordinator()
        coord.onTabFocused(TrendingPopupTab.SONGS)
        coord.onTabFocused(TrendingPopupTab.SONGS)
        val pick = (coord.songPopup.first() as TrendingPopupState.Showing).content

        coord.onSongPopupCta(pick)
        val nav = coord.navigationEvent.first()
        check(nav is TrendingPopupNavEvent.OpenSongPlayer)
        assertEquals(pick.id, nav.songId)
    }

    @Test
    fun `isPopupEligible returns true when eligible and false when capped`() = runBlocking {
        val clock = FakeClock(nowMs, todayEpochDay)
        val coord = coordinator(
            config = TrendingPopupConfigValues(intervalMinutes = 60L, dailyCap = 2L),
            clock = clock
        )

        // Initially NOT eligible because focus count is < 2
        assertFalse(coord.isPopupEligible(TrendingPopupTab.GALLERY))

        // Focus tab once (count = 1)
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        assertFalse(coord.isPopupEligible(TrendingPopupTab.GALLERY))

        // Focus tab twice (count = 2) -> now eligible and popup is shown (shown count = 1)
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        assertFalse(coord.isPopupEligible(TrendingPopupTab.GALLERY)) // Blocked by interval since it was just shown

        coord.onTemplatePopupDismissed()

        // Advance clock past 60 min interval to clear interval block
        clock.currentNowMs += 61 * 60 * 1000L

        // Still eligible because shown count is 1 and cap is 2
        assertTrue(coord.isPopupEligible(TrendingPopupTab.GALLERY))

        // Focus tab third time -> second popup is shown (shown count = 2)
        coord.onTabFocused(TrendingPopupTab.GALLERY)

        // Daily cap of 2 reached, should no longer be eligible today
        assertFalse(coord.isPopupEligible(TrendingPopupTab.GALLERY))
    }

    @Test
    fun `popup is not shown on first tab focus but shown on second focus`() = runBlocking {
        val coord = coordinator()

        // 1st focus
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        assertEquals(TrendingPopupState.Hidden, coord.templatePopup.first())

        // 2nd focus
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        assertTrue(coord.templatePopup.first() is TrendingPopupState.Showing)
    }

    @Test
    fun `trending popup is blocked when rating popup is showing`() = runBlocking {
        val coord = coordinator()
        coord.isRatingShowing = { true }

        // Focus GALLERY twice
        coord.onTabFocused(TrendingPopupTab.GALLERY)
        coord.onTabFocused(TrendingPopupTab.GALLERY)

        assertEquals(TrendingPopupState.Hidden, coord.templatePopup.first())
        assertFalse(coord.isPopupEligible(TrendingPopupTab.GALLERY))
    }
}
