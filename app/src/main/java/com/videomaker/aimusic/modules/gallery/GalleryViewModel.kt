package com.videomaker.aimusic.modules.gallery

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import androidx.compose.runtime.Immutable
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.media.library.MusicSongLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

// ============================================
// UI STATE
// ============================================

sealed class TemplateListState {
    data object Loading : TemplateListState()
    data class Success(val templates: List<VideoTemplate>) : TemplateListState()
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
    data class NavigateToTemplateDetail(val templateId: String) : GalleryNavigationEvent()
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
    private val templateRepository: TemplateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<GalleryNavigationEvent?>(null)
    val navigationEvent: StateFlow<GalleryNavigationEvent?> = _navigationEvent.asStateFlow()

    init {
        loadGalleryData()
    }

    private fun loadGalleryData() {
        viewModelScope.launch {
            try {
                val trendingSongs = MusicSongLibrary.getTrending(3).map { it.toTrendingSong() }
                val topSongs = MusicSongLibrary.getTop(12).mapIndexed { index, song ->
                    song.toTopSong(ranking = index + 1, likes = Random.nextInt(10000, 150000))
                }
                val (vibeTags, templates, featuredTemplates) = coroutineScope {
                    val vibeTagsDeferred = async { templateRepository.getVibeTags().getOrElse { emptyList() } }
                    val templatesDeferred = async { templateRepository.getTemplates(limit = 6, offset = 0).getOrElse { emptyList() } }  // Progressive loading: load 6 first
                    val featuredDeferred = async { templateRepository.getFeaturedTemplates(limit = 6).getOrElse { emptyList() } }  // Reduced from 10 to 6 for faster loading
                    Triple(vibeTagsDeferred.await(), templatesDeferred.await(), featuredDeferred.await())
                }

                _uiState.value = GalleryUiState.Success(
                    trendingSongs = trendingSongs,
                    topSongs = topSongs,
                    vibeTags = vibeTags,
                    selectedVibeTagId = null,
                    templateListState = TemplateListState.Success(templates),
                    featuredTemplates = featuredTemplates
                )

                // Only preload carousel images (visible on screen)
                // Don't preload featured thumbnails - let Coil load them as they become visible
                preloadCarouselImages(trendingSongs)
            } catch (e: Exception) {
                _uiState.value = GalleryUiState.Error(e.message ?: "Failed to load gallery data")
            }
        }
    }

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
                templateRepository.getTemplates(limit = 6, offset = 0)  // Progressive loading: load 6 first
            } else {
                templateRepository.getTemplatesByVibeTag(tag = tagId, limit = 6, offset = 0)  // Progressive loading: load 6 first
            }
            val templates = result.getOrElse { emptyList() }
            val updated = _uiState.value as? GalleryUiState.Success ?: return@launch
            _uiState.value = updated.copy(
                templateListState = TemplateListState.Success(templates)
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

    fun onTemplateClick(template: VideoTemplate) {
        _navigationEvent.value = GalleryNavigationEvent.NavigateToTemplateDetail(template.id)
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
            GalleryNavigationEvent.NavigateToTemplateDetail(firstFeatured.id)
        } else {
            GalleryNavigationEvent.NavigateToCreate
        }
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }
}
