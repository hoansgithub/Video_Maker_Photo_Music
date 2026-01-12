package com.videomaker.aimusic.modules.gallery

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.media.library.MusicSongLibrary
import com.videomaker.aimusic.media.library.VideoTemplateLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

// ============================================
// UI STATE
// ============================================

sealed class GalleryUiState {
    data object Loading : GalleryUiState()
    data class Success(
        val trendingSongs: List<TrendingSong>,
        val topSongs: List<TopSong>,
        val trendingTemplates: List<VideoTemplate>,
        val popularTemplates: List<VideoTemplate>
    ) : GalleryUiState()
    data class Error(val message: String) : GalleryUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class GalleryNavigationEvent {
    data class NavigateToSongDetail(val songId: Int) : GalleryNavigationEvent()
    data class NavigateToTemplateDetail(val templateId: String) : GalleryNavigationEvent()
    data object NavigateToAllTopSongs : GalleryNavigationEvent()
    data object NavigateToAllTrendingTemplates : GalleryNavigationEvent()
    data object NavigateToAllPopularTemplates : GalleryNavigationEvent()
    data object NavigateToCreate : GalleryNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

/**
 * GalleryViewModel with optimized image preloading
 *
 * @param application Application instance for Coil ImageRequest context
 *                    Note: Application context is safe in ViewModels (lives for app lifetime)
 * @param imageLoader Coil ImageLoader for image preloading (injected via ACCDI)
 */
class GalleryViewModel(
    private val application: Application,
    private val imageLoader: ImageLoader
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
                // Load songs from library
                val trendingSongs = MusicSongLibrary.getTrending(3)
                    .map { it.toTrendingSong() }

                val topSongs = MusicSongLibrary.getTop(12)
                    .mapIndexed { index, song ->
                        song.toTopSong(
                            ranking = index + 1,
                            likes = Random.nextInt(10000, 150000)
                        )
                    }

                // Load templates from library
                val trendingTemplates = VideoTemplateLibrary.getTrending().take(6)
                val popularTemplates = VideoTemplateLibrary.getFree().take(6)

                _uiState.value = GalleryUiState.Success(
                    trendingSongs = trendingSongs,
                    topSongs = topSongs,
                    trendingTemplates = trendingTemplates,
                    popularTemplates = popularTemplates
                )

                // Preload carousel images AFTER UI is shown (non-blocking)
                preloadCarouselImages(trendingSongs)

            } catch (e: Exception) {
                _uiState.value = GalleryUiState.Error(e.message ?: "Failed to load gallery data")
            }
        }
    }

    /**
     * Preload carousel images for smooth auto-sliding transitions
     * Runs on IO dispatcher to avoid blocking main thread
     */
    private fun preloadCarouselImages(songs: List<TrendingSong>) {
        viewModelScope.launch(Dispatchers.IO) {
            songs.forEach { song ->
                val request = ImageRequest.Builder(application)
                    .data(song.coverUrl)
                    .size(Size(720, 405)) // Match banner display size
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCacheKey("banner_${song.id}")
                    .diskCacheKey("banner_${song.id}")
                    .build()

                // Enqueue low-priority preload (doesn't block UI)
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

    fun onSeeAllTrendingTemplatesClick() {
        _navigationEvent.value = GalleryNavigationEvent.NavigateToAllTrendingTemplates
    }

    fun onSeeAllPopularTemplatesClick() {
        _navigationEvent.value = GalleryNavigationEvent.NavigateToAllPopularTemplates
    }

    fun onCreateClick() {
        _navigationEvent.value = GalleryNavigationEvent.NavigateToCreate
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }
}
