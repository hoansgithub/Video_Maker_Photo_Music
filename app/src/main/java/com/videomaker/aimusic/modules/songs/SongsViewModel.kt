package com.videomaker.aimusic.modules.songs

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.usecase.ClearSongCacheUseCase
import com.videomaker.aimusic.domain.usecase.GetGenresUseCase
import com.videomaker.aimusic.domain.usecase.GetSuggestedSongsUseCase
import com.videomaker.aimusic.domain.usecase.GetWeeklyRankingSongsUseCase
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.media.library.BundledContentLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// ============================================
// SECTION STATE — per-section loading indicator
// ============================================

@Immutable
sealed class SectionState<out T> {
    @Immutable data object Loading : SectionState<Nothing>()
    @Immutable data class Success<out T>(val data: T) : SectionState<T>()
    @Immutable data class Error(val message: String) : SectionState<Nothing>()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class SongsNavigationEvent {
    /** Navigate to the image picker so the user can build a video with the chosen song. */
    data class NavigateToAssetPickerForSong(val songId: Long) : SongsNavigationEvent()
    /** Navigate to TemplatePreviewer with the selected song so user can browse templates with their music. */
    data class NavigateToTemplatePreviewer(val songId: Long) : SongsNavigationEvent()
    data object NavigateToSuggestedAll : SongsNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

class SongsViewModel(
    private val getSuggestedSongsUseCase: GetSuggestedSongsUseCase,
    private val getWeeklyRankingSongsUseCase: GetWeeklyRankingSongsUseCase,
    private val getGenresUseCase: GetGenresUseCase,
    private val clearSongCacheUseCase: ClearSongCacheUseCase,
    private val songRepository: SongRepository,
    private val trendingPopupCoordinator: com.videomaker.aimusic.core.popup.TrendingPopupCoordinator,
    private val adsLoaderService: AdsLoaderService
) : ViewModel() {

    /** Called by HomeScreen when the Songs tab settles into focus. */
    fun onTabFocused() {
        trendingPopupCoordinator.onTabFocused(
            com.videomaker.aimusic.core.popup.TrendingPopupTab.SONGS
        )
    }

    private val _suggestedState =
        MutableStateFlow<SectionState<List<MusicSong>>>(SectionState.Loading)
    val suggestedState: StateFlow<SectionState<List<MusicSong>>> = _suggestedState.asStateFlow()

    private val _rankingState =
        MutableStateFlow<SectionState<List<MusicSong>>>(SectionState.Loading)
    val rankingState: StateFlow<SectionState<List<MusicSong>>> = _rankingState.asStateFlow()

    private val _stationState =
        MutableStateFlow<SectionState<List<MusicSong>>>(SectionState.Loading)
    val stationState: StateFlow<SectionState<List<MusicSong>>> = _stationState.asStateFlow()

    // Genres for the station tag chips
    private val _genresState =
        MutableStateFlow<SectionState<List<SongGenre>>>(SectionState.Loading)
    val genresState: StateFlow<SectionState<List<SongGenre>>> = _genresState.asStateFlow()

    // Currently active genre filter (null = show all / unfiltered)
    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre: StateFlow<String?> = _selectedGenre.asStateFlow()

    // Station pagination
    private var stationOffset = 0
    private var stationHasMore = true
    private var stationJob: Job? = null
    private val _stationLoadingMore = MutableStateFlow(false)
    val stationLoadingMore: StateFlow<Boolean> = _stationLoadingMore.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _navigationEvent = MutableStateFlow<SongsNavigationEvent?>(null)
    val navigationEvent: StateFlow<SongsNavigationEvent?> = _navigationEvent.asStateFlow()

    // Song currently shown in the player bottom sheet (null = sheet closed)
    private val _selectedSong = MutableStateFlow<MusicSong?>(null)
    val selectedSong: StateFlow<MusicSong?> = _selectedSong.asStateFlow()

    // Playlist + category + genre passed to MusicPlayerBottomSheet so it can navigate
    // next/prev within the source context (e.g., the Suggested list, the Pop station, ...).
    private val _selectedPlaylist = MutableStateFlow<List<MusicSong>>(emptyList())
    val selectedPlaylist: StateFlow<List<MusicSong>> = _selectedPlaylist.asStateFlow()

    private val _selectedCategoryLocation = MutableStateFlow(
        com.videomaker.aimusic.core.analytics.AnalyticsEvent.Value.Location.SONG_PREVIEW
    )
    val selectedCategoryLocation: StateFlow<String> = _selectedCategoryLocation.asStateFlow()

    private val _selectedGenreId = MutableStateFlow<String?>(null)
    val selectedGenreId: StateFlow<String?> = _selectedGenreId.asStateFlow()

    init {
        loadAll()
        // Preload station in-feed native ad so it's ready when user scrolls
        viewModelScope.launch(Dispatchers.IO) {
            adsLoaderService.loadNative(AdPlacement.NATIVE_STATION_INFEED)
        }
    }

    // Each section loads independently — UI shows shimmer per section
    private fun loadAll() {
        loadSuggested()
        loadRanking()
        loadStations()
        viewModelScope.launch(Dispatchers.IO) { loadGenres() }
    }

    fun refresh() {
        viewModelScope.launch {
            // compareAndSet is atomic — prevents double-refresh race when refresh()
            // is called rapidly (e.g. double-tap / pull-to-refresh + button tap)
            if (!_isRefreshing.compareAndSet(false, true)) return@launch
            try {
                clearSongCacheUseCase()  // wipe disk cache before re-fetching
                coroutineScope {
                    launch(Dispatchers.IO) { loadGenres() }
                }
                loadSuggested()
                loadRanking()
                loadStations()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun shuffle() {
        val station = (_stationState.value as? SectionState.Success)?.data
        if (station != null) {
            _stationState.value = SectionState.Success(station.shuffled())
        }
    }

    /** Called when user taps a genre chip. null = "All" (no filter). */
    fun onGenreSelected(genre: String?) {
        if (_selectedGenre.value == genre) return
        _selectedGenre.value = genre
        // Cancel in-flight station load to prevent stale offset corruption
        stationJob?.cancel()
        stationJob = viewModelScope.launch(Dispatchers.IO) { loadStationsInternal() }
    }

    /** Called by UI when user scrolls near the end of the station list. */
    fun loadMoreStations() {
        // Atomic check-and-set prevents duplicate requests on fast scroll
        if (!_stationLoadingMore.compareAndSet(false, true)) return
        if (!stationHasMore) {
            _stationLoadingMore.value = false
            return
        }
        viewModelScope.launch(Dispatchers.IO) { loadMoreStationsInternal() }
    }

    private fun loadSuggested() {
        loadSectionWithGeoFallback(_suggestedState) {
            getSuggestedSongsUseCase(limit = 10)
        }
    }

    private fun loadRanking() {
        loadSectionWithGeoFallback(_rankingState) {
            getWeeklyRankingSongsUseCase(limit = 9)
        }
    }

    private fun loadStations() {
        stationJob?.cancel()
        stationJob = viewModelScope.launch(Dispatchers.IO) { loadStationsInternal() }
    }

    private suspend fun loadStationsInternal() {
        _stationState.value = SectionState.Loading
        stationOffset = 0
        stationHasMore = true
        val genres = stationGenresList()
        songRepository.getSuggestedSongs(preferredGenres = genres, offset = 0, limit = STATION_PAGE_SIZE)
            .onSuccess { songs ->
                stationOffset = songs.size
                stationHasMore = songs.size >= STATION_PAGE_SIZE && stationOffset < STATION_MAX_ITEMS
                _stationState.value = SectionState.Success(songs.ifEmpty { defaultSongs() })
            }
            .onFailure { _stationState.value = SectionState.Success(defaultSongs()) }
    }

    private suspend fun loadMoreStationsInternal() {
        try {
            val genres = stationGenresList()
            songRepository.getSuggestedSongs(preferredGenres = genres, offset = stationOffset, limit = STATION_PAGE_SIZE)
                .onSuccess { newSongs ->
                    stationOffset += newSongs.size
                    stationHasMore = newSongs.size >= STATION_PAGE_SIZE && stationOffset < STATION_MAX_ITEMS
                    val current = (_stationState.value as? SectionState.Success)?.data.orEmpty()
                    val existingIds = current.map { it.id }.toSet()
                    val unique = newSongs.filterNot { it.id in existingIds }
                    _stationState.value = SectionState.Success(current + unique)
                }
        } finally {
            _stationLoadingMore.value = false
        }
    }

    private fun stationGenresList(): List<String> {
        val genre = _selectedGenre.value
        return if (genre != null) listOf(genre) else emptyList()
    }

    /**
     * Geo timeout fallback:
     * 1. Kick off the real (region-aware) request.
     * 2. Wait up to [GEO_TIMEOUT_MS]. If it returns in time, render it directly.
     * 3. On timeout, render the bundled GL defaults so the section never appears empty,
     *    keep awaiting the real result, then swap in once it arrives.
     */
    private fun loadSectionWithGeoFallback(
        state: MutableStateFlow<SectionState<List<MusicSong>>>,
        load: suspend () -> Result<List<MusicSong>>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            state.value = SectionState.Loading
            val deferred = async { load() }
            val initial = withTimeoutOrNull(GEO_TIMEOUT_MS) { deferred.await() }

            if (initial != null) {
                initial
                    .onSuccess {
                        state.value = SectionState.Success(it.ifEmpty { defaultSongs() })
                    }
                    .onFailure {
                        state.value = SectionState.Success(defaultSongs())
                    }
                return@launch
            }

            // Timeout — show bundled defaults now, keep awaiting the real result.
            state.value = SectionState.Success(defaultSongs())
            deferred.await().onSuccess { real ->
                if (real.isNotEmpty()) state.value = SectionState.Success(real)
            }
        }
    }

    private fun defaultSongs(): List<MusicSong> = BundledContentLibrary.getDefaultSongs()

    private suspend fun loadGenres() {
        _genresState.value = SectionState.Loading
        getGenresUseCase()
            .onSuccess { _genresState.value = SectionState.Success(it) }
            // Genre failure is non-critical — fall back to empty (hides the chip row)
            .onFailure { _genresState.value = SectionState.Success(emptyList()) }
    }

    /**
     * Opens the music player bottom sheet for the given song.
     * Captures the source [playlist] + [categoryLocation] + [genreId] so the player
     * can navigate next/prev within that context.
     */
    fun onSongClick(
        song: MusicSong,
        playlist: List<MusicSong>,
        categoryLocation: String,
        genreId: String? = null
    ) {
        _selectedPlaylist.value = playlist
        _selectedCategoryLocation.value = categoryLocation
        _selectedGenreId.value = genreId
        _selectedSong.value = song
    }

    /** Opens the music player bottom sheet by fetching the song by ID (used by widget deep links). */
    fun onSongClickById(songId: Long) {
        if (songId == -1L) return
        viewModelScope.launch(Dispatchers.IO) {
            songRepository.getSongById(songId)
                .onSuccess {
                    _selectedPlaylist.value = emptyList()
                    _selectedCategoryLocation.value =
                        com.videomaker.aimusic.core.analytics.AnalyticsEvent.Value.Location.SONG_PREVIEW
                    _selectedGenreId.value = null
                    _selectedSong.value = it
                }
        }
    }

    /** Dismisses the player bottom sheet without any further action. */
    fun onDismissPlayer() {
        _selectedSong.value = null
    }

    /** Called when user taps "Use to Create Video" — closes sheet and navigates to TemplatePreviewer. */
    fun onUseToCreateVideo(song: MusicSong) {
        _selectedSong.value = null
        _navigationEvent.value = SongsNavigationEvent.NavigateToTemplatePreviewer(song.id)
    }

    fun onSeeMoreSuggestedClick() {
        _navigationEvent.value = SongsNavigationEvent.NavigateToSuggestedAll
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    private companion object {
        const val STATION_PAGE_SIZE = 15
        const val STATION_MAX_ITEMS = 100
        /** Max time we wait for region-aware content before showing bundled GL defaults. */
        const val GEO_TIMEOUT_MS = 5_000L
    }
}
