package com.videomaker.aimusic.modules.gallery

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import androidx.compose.runtime.Immutable
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.media.library.BundledContentLibrary
import com.videomaker.aimusic.media.library.MusicSongLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

// ============================================
// UI STATE
// ============================================

sealed class TemplateListState {
    data object Loading : TemplateListState()
    data class Success(
        val templates: List<VideoTemplate>,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false
    ) : TemplateListState()
}

sealed class GalleryUiState {
    data object Loading : GalleryUiState()
    @Immutable
    data class Success(
        val trendingSongs: List<TrendingSong>,
        val topSongs: List<TopSong>,
        val vibeTags: List<VibeTag>,
        val selectedVibeTagId: String?,       // null = "All"
        val templateListState: TemplateListState,
        val featuredTemplates: List<VideoTemplate> = emptyList()
    ) : GalleryUiState()
    data class Error(val message: String) : GalleryUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class GalleryNavigationEvent {
    data class NavigateToSongDetail(val songId: Long) : GalleryNavigationEvent()
    /**
     * Navigate to template detail with optional ad
     * @param shouldShowAd true if ad is ready and should be shown
     */
    data class NavigateToTemplateDetail(
        val templateId: String,
        val sourceLocation: String,
        val shouldShowAd: Boolean = false
    ) : GalleryNavigationEvent()
    data object NavigateToAllTopSongs : GalleryNavigationEvent()
    data class NavigateToAllTemplates(val selectedVibeTagId: String?) : GalleryNavigationEvent()
    data object NavigateToCreate : GalleryNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

class GalleryViewModel(
    private val application: Application,
    private val imageLoader: ImageLoader,
    private val templateRepository: TemplateRepository,
    private val adsLoaderService: co.alcheclub.lib.acccore.ads.loader.AdsLoaderService,
    private val trendingPopupCoordinator: com.videomaker.aimusic.core.popup.TrendingPopupCoordinator,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    /** Called by HomeScreen when the Gallery tab settles into focus. */
    fun onTabFocused() {
        trendingPopupCoordinator.onTabFocused(
            com.videomaker.aimusic.core.popup.TrendingPopupTab.GALLERY
        )
    }

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<GalleryNavigationEvent?>(null)
    val navigationEvent: StateFlow<GalleryNavigationEvent?> = _navigationEvent.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Pagination constants
    private val PAGE_SIZE = 20
    private val MAX_ITEMS = 100

    init {
        loadGalleryData()
    }

    fun refresh() {
        viewModelScope.launch {
            // compareAndSet is atomic — prevents double-refresh race
            if (!_isRefreshing.compareAndSet(false, true)) return@launch
            try {
                loadGalleryData()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun shuffle() {
        android.util.Log.d("GalleryViewModel", "shuffle() triggered")
        val current = _uiState.value as? GalleryUiState.Success ?: run {
            android.util.Log.e("GalleryViewModel", "shuffle failed: uiState is not Success")
            return
        }
        val listState = current.templateListState as? TemplateListState.Success ?: run {
            android.util.Log.e("GalleryViewModel", "shuffle failed: templateListState is not Success")
            return
        }

        // 1) Mark the currently visible templates as "seen" (so they won't be prioritized anymore)
        val visibleIds = listState.templates.take(PAGE_SIZE).map { it.id }.toSet()
        android.util.Log.d("GalleryViewModel", "shuffle: marking as seen ids=$visibleIds")
        preferencesManager.addSeenTemplateIds(visibleIds)

        // 2) Show loading state on the template grid
        _uiState.value = current.copy(
            templateListState = TemplateListState.Loading
        )

        // 3) Fetch a fresh batch from repository, then partition by seen status (new first)
        viewModelScope.launch {
            try {
                val result = if (current.selectedVibeTagId == null) {
                    templateRepository.getTemplates(limit = MAX_ITEMS, offset = 0)
                } else {
                    templateRepository.getTemplatesByVibeTag(
                        tag = current.selectedVibeTagId,
                        limit = MAX_ITEMS,
                        offset = 0
                    )
                }

                val allTemplates = result.getOrElse { emptyList() }
                android.util.Log.d("GalleryViewModel", "shuffle: fetched allTemplates size=${allTemplates.size}")
                val reordered = reorderBySeenStatus(allTemplates)

                val updated = _uiState.value as? GalleryUiState.Success ?: run {
                    android.util.Log.e("GalleryViewModel", "shuffle: updated state is not Success after fetch")
                    return@launch
                }
                _uiState.value = updated.copy(
                    templateListState = TemplateListState.Success(
                        templates = reordered,
                        hasMore = allTemplates.size >= MAX_ITEMS
                    )
                )
                android.util.Log.d("GalleryViewModel", "shuffle: success, set templateListState to Success with templates size=${reordered.size}")
            } catch (e: Exception) {
                android.util.Log.e("GalleryViewModel", "shuffle failed with exception", e)
                // Restore success state on error
                val restored = _uiState.value as? GalleryUiState.Success ?: return@launch
                _uiState.value = restored.copy(
                    templateListState = listState
                )
            }
        }
    }

    private fun loadGalleryData() {
        viewModelScope.launch {
            try {
                val trendingSongs = MusicSongLibrary.getTrending(3).map { it.toTrendingSong() }
                val topSongs = MusicSongLibrary.getTop(12).mapIndexed { index, song ->
                    song.toTopSong(ranking = index + 1, likes = Random.nextInt(10000, 150000))
                }

                // Async children of this launch; they outlive the withTimeoutOrNull below.
                val vibeTagsDeferred = async { templateRepository.getVibeTags().getOrElse { emptyList() } }
                val templatesDeferred = async { templateRepository.getTemplates(limit = MAX_ITEMS, offset = 0).getOrElse { emptyList() } }
                val featuredDeferred = async { templateRepository.getFeaturedTemplates(limit = 6).getOrElse { emptyList() } }

                // Geo-aware path: wait up to 5s for the main grid. If it returns in time,
                // assemble fully with the other two; otherwise fall back to bundled then swap.
                val templatesInitial =
                    withTimeoutOrNull(GEO_TIMEOUT_MS) { templatesDeferred.await() }

                if (templatesInitial != null && templatesInitial.isNotEmpty()) {
                    val vibeTags = vibeTagsDeferred.await()
                    val featuredTemplates = featuredDeferred.await()
                    _uiState.value = buildGallerySuccess(
                        trendingSongs = trendingSongs,
                        topSongs = topSongs,
                        vibeTags = vibeTags,
                        templates = templatesInitial,
                        featuredTemplates = featuredTemplates
                    )
                    preloadCarouselImages(trendingSongs)
                    return@launch
                }

                // Timeout / empty — show bundled defaults immediately…
                _uiState.value = buildGallerySuccess(
                    trendingSongs = trendingSongs,
                    topSongs = topSongs,
                    vibeTags = emptyList(),
                    templates = BundledContentLibrary.getDefaultTemplates(),
                    featuredTemplates = emptyList()
                )
                preloadCarouselImages(trendingSongs)

                // …then keep waiting for the real responses and swap in.
                val realTemplates = templatesDeferred.await()
                val realVibeTags = vibeTagsDeferred.await()
                val realFeatured = featuredDeferred.await()
                if (realTemplates.isNotEmpty()) {
                    val current = _uiState.value as? GalleryUiState.Success ?: return@launch
                    val reordered = reorderBySeenStatus(realTemplates)
                    _uiState.value = current.copy(
                        vibeTags = realVibeTags,
                        templateListState = TemplateListState.Success(
                            templates = reordered,
                            hasMore = realTemplates.size >= MAX_ITEMS && realTemplates.size < MAX_ITEMS
                        ),
                        featuredTemplates = realFeatured
                    )
                }
            } catch (e: Exception) {
                _uiState.value = GalleryUiState.Error(e.message ?: "Failed to load gallery data")
            }
        }
    }

    private fun buildGallerySuccess(
        trendingSongs: List<TrendingSong>,
        topSongs: List<TopSong>,
        vibeTags: List<VibeTag>,
        templates: List<VideoTemplate>,
        featuredTemplates: List<VideoTemplate>
    ) = GalleryUiState.Success(
        trendingSongs = trendingSongs,
        topSongs = topSongs,
        vibeTags = vibeTags,
        selectedVibeTagId = null,
        templateListState = TemplateListState.Success(
            templates = reorderBySeenStatus(templates),
            hasMore = templates.size >= MAX_ITEMS && templates.size < MAX_ITEMS
        ),
        featuredTemplates = featuredTemplates
    )

    fun onVibeTagSelected(tagId: String?) {
        val current = _uiState.value as? GalleryUiState.Success ?: return
        // Don't re-fetch if already selected
        if (current.selectedVibeTagId == tagId) return

        _uiState.value = current.copy(
            selectedVibeTagId = tagId,
            templateListState = TemplateListState.Loading
        )

        viewModelScope.launch {
            val result = if (tagId == null) {
                templateRepository.getTemplates(limit = MAX_ITEMS, offset = 0)
            } else {
                templateRepository.getTemplatesByVibeTag(tag = tagId, limit = MAX_ITEMS, offset = 0)
            }
            val templates = result.getOrElse { emptyList() }
            val updated = _uiState.value as? GalleryUiState.Success ?: return@launch
            if (updated.selectedVibeTagId != tagId) return@launch
            val reordered = reorderBySeenStatus(templates)
            _uiState.value = updated.copy(
                templateListState = TemplateListState.Success(
                    templates = reordered,
                    hasMore = templates.size >= MAX_ITEMS && templates.size < MAX_ITEMS
                )
            )
        }
    }

    fun loadMore() {
        val current = _uiState.value as? GalleryUiState.Success ?: return
        val templateState = current.templateListState as? TemplateListState.Success ?: return

        // Don't load if already loading or no more items
        if (templateState.isLoadingMore || !templateState.hasMore) return

        // Don't exceed max items
        if (templateState.templates.size >= MAX_ITEMS) return

        // Set loading state
        _uiState.value = current.copy(
            templateListState = templateState.copy(isLoadingMore = true)
        )

        viewModelScope.launch {
            val offset = templateState.templates.size
            val result = if (current.selectedVibeTagId == null) {
                templateRepository.getTemplates(limit = PAGE_SIZE, offset = offset)
            } else {
                templateRepository.getTemplatesByVibeTag(
                    tag = current.selectedVibeTagId,
                    limit = PAGE_SIZE,
                    offset = offset
                )
            }

            val newTemplates = result.getOrElse { emptyList() }
            val updated = _uiState.value as? GalleryUiState.Success ?: return@launch
            val updatedTemplateState = updated.templateListState as? TemplateListState.Success ?: return@launch

            val combinedTemplates = (updatedTemplateState.templates + newTemplates).take(MAX_ITEMS)

            _uiState.value = updated.copy(
                templateListState = TemplateListState.Success(
                    templates = combinedTemplates,
                    hasMore = newTemplates.size >= PAGE_SIZE && combinedTemplates.size < MAX_ITEMS,
                    isLoadingMore = false
                )
            )
        }
    }

    private fun preloadCarouselImages(songs: List<TrendingSong>) {
        viewModelScope.launch(Dispatchers.IO) {
            songs.forEach { song ->
                val request = ImageRequest.Builder(application)
                    .data(song.coverUrl)
                    .size(Size(720, 405))
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCacheKey("banner_${song.id}")
                    .diskCacheKey("banner_${song.id}")
                    .build()
                imageLoader.enqueue(request)
            }
        }
    }

    private fun preloadFeaturedThumbnails(templates: List<VideoTemplate>) {
        viewModelScope.launch(Dispatchers.IO) {
            templates.forEach { template ->
                if (template.thumbnailPath.isEmpty()) return@forEach
                val request = ImageRequest.Builder(application)
                    .data(template.thumbnailPath)
                    .size(Size(200, 350))  // Reduced from 720x405 to match TemplateCard size
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCacheKey("featured_${template.id}")
                    .diskCacheKey("featured_${template.id}")
                    .build()
                imageLoader.enqueue(request)
            }
        }
    }

    fun onTrendingSongClick(song: TrendingSong) {
        _navigationEvent.value = GalleryNavigationEvent.NavigateToSongDetail(song.id)
    }

    fun onTopSongClick(song: TopSong) {
        _navigationEvent.value = GalleryNavigationEvent.NavigateToSongDetail(song.id)
    }

    fun onTemplateClick(template: VideoTemplate, sourceLocation: String) {
        // Mark clicked template as seen
        preferencesManager.addSeenTemplateIds(setOf(template.id))

        // Check if template grid tap ad is ready (non-blocking, with frequency cap)
        val isAdReady = adsLoaderService.isInterstitialReady(com.videomaker.aimusic.core.constants.AdPlacement.INTERSTITIAL_TEMPLATE_GRID_TAP)

        android.util.Log.d("GalleryViewModel", "🔔 onTemplateClick - Ad ready: $isAdReady")

        _navigationEvent.value = GalleryNavigationEvent.NavigateToTemplateDetail(
            templateId = template.id,
            sourceLocation = sourceLocation,
            shouldShowAd = isAdReady
        )
    }

    fun onSeeAllTopSongsClick() {
        _navigationEvent.value = GalleryNavigationEvent.NavigateToAllTopSongs
    }

    fun onSeeAllTemplatesClick() {
        val selectedTagId = (_uiState.value as? GalleryUiState.Success)?.selectedVibeTagId
        _navigationEvent.value = GalleryNavigationEvent.NavigateToAllTemplates(selectedTagId)
    }

    fun onCreateClick() {
        val firstFeatured = (_uiState.value as? GalleryUiState.Success)?.featuredTemplates?.firstOrNull()
        _navigationEvent.value = if (firstFeatured != null) {
            GalleryNavigationEvent.NavigateToTemplateDetail(
                templateId = firstFeatured.id,
                sourceLocation = AnalyticsEvent.Value.Location.HOME_BANNER
            )
        } else {
            GalleryNavigationEvent.NavigateToCreate
        }
    }

    /**
     * Preload template grid tap interstitial ad.
     * Called after ad is shown to prepare the next one (Drama app pattern).
     * ACCCore handles duplicate prevention automatically.
     */
    fun preloadTemplateGridAd() {
        viewModelScope.launch {
            android.util.Log.d("GalleryViewModel", "🔄 Reloading template grid tap ad after show...")
            runCatching {
                com.videomaker.aimusic.core.ads.InterstitialAdHelperExt.preloadInterstitial(
                    adsLoaderService = adsLoaderService,
                    placement = com.videomaker.aimusic.core.constants.AdPlacement.INTERSTITIAL_TEMPLATE_GRID_TAP,
                    loadTimeoutMillis = null,
                    showLoadingOverlay = false
                )
            }.onSuccess { success ->
                if (success) {
                    android.util.Log.d("GalleryViewModel", "✅ Template grid tap ad reload SUCCESS")
                }
            }.onFailure { e ->
                android.util.Log.e("GalleryViewModel", "❌ Template grid tap ad reload exception: ${e.message}", e)
            }
        }
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    private fun reorderBySeenStatus(templates: List<VideoTemplate>): List<VideoTemplate> {
        val seenIds = preferencesManager.getSeenTemplateIds()
        val (seen, unseen) = templates.partition { it.id in seenIds }
        android.util.Log.d("GalleryViewModel", "reorderBySeenStatus: templates size=${templates.size}, seen size=${seen.size}, unseen size=${unseen.size}")
        return if (unseen.isEmpty() && templates.isNotEmpty()) {
            android.util.Log.d("GalleryViewModel", "reorderBySeenStatus: all templates have been seen! Shuffling all templates to keep feed fresh.")
            templates.shuffled()
        } else {
            unseen + seen.shuffled()
        }
    }

    private companion object {
        /** Max time we wait for region-aware templates before showing bundled GL defaults. */
        const val GEO_TIMEOUT_MS = 5_000L
    }
}
