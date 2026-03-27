package com.videomaker.aimusic.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.widget.model.WidgetType
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ============================================
// UI STATE
// ============================================

data class WidgetData(
    val trendingTemplates: List<VideoTemplate> = emptyList(),
    val trendingSongs: List<MusicSong> = emptyList(),
    val newReleaseTemplates: List<VideoTemplate> = emptyList()
)

sealed class WidgetUiState {
    data object Loading : WidgetUiState()
    data class Success(val data: WidgetData) : WidgetUiState()
    data class Error(val message: String) : WidgetUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class WidgetNavigationEvent {
    data object NavigateBack : WidgetNavigationEvent()
    data class NavigateToTemplatePreviewer(val templateId: String) : WidgetNavigationEvent()
    data object NavigateToSearch : WidgetNavigationEvent()
    data class NavigateToTemplatePreviewerWithSong(val songId: Long) : WidgetNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

class WidgetViewModel(
    private val templateRepository: TemplateRepository,
    private val songRepository: SongRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<WidgetUiState>(WidgetUiState.Loading)
    val uiState: StateFlow<WidgetUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<WidgetNavigationEvent?>(null)
    val navigationEvent: StateFlow<WidgetNavigationEvent?> = _navigationEvent.asStateFlow()

    // Currently selected song — drives MusicPlayerBottomSheet visibility
    private val _selectedSong = MutableStateFlow<MusicSong?>(null)
    val selectedSong: StateFlow<MusicSong?> = _selectedSong.asStateFlow()

    // Pin widget event - StateFlow for one-time event (requires Context in composable)
    private val _pinWidgetEvent = MutableStateFlow<WidgetType?>(null)
    val pinWidgetEvent: StateFlow<WidgetType?> = _pinWidgetEvent.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = WidgetUiState.Loading

            val trendingTemplatesDeferred = async {
                templateRepository.getFeaturedTemplates(limit = 5)
            }
            val trendingSongsDeferred = async {
                songRepository.getFeaturedSongs(limit = 4)
            }
            val newReleaseTemplatesDeferred = async {
                templateRepository.getTemplates(limit = 4, offset = 0)
            }

            val trendingTemplates = trendingTemplatesDeferred.await().getOrElse { emptyList() }
            val trendingSongs = trendingSongsDeferred.await().getOrElse { emptyList() }
            val newReleaseTemplates = newReleaseTemplatesDeferred.await().getOrElse { emptyList() }

            _uiState.value = WidgetUiState.Success(
                WidgetData(
                    trendingTemplates = trendingTemplates,
                    trendingSongs = trendingSongs,
                    newReleaseTemplates = newReleaseTemplates
                )
            )
        }
    }

    fun onAddWidgetClick(widgetType: WidgetType) {
        _pinWidgetEvent.value = widgetType
    }

    fun onPinWidgetHandled() {
        _pinWidgetEvent.value = null
    }

    fun onTemplateClick(template: VideoTemplate) {
        _navigationEvent.value = WidgetNavigationEvent.NavigateToTemplatePreviewer(template.id)
    }

    fun onAddTemplateClick() {
        _navigationEvent.value = WidgetNavigationEvent.NavigateToSearch
    }

    fun onSearchClick() {
        _navigationEvent.value = WidgetNavigationEvent.NavigateToSearch
    }

    // Opens MusicPlayerBottomSheet for the tapped song
    fun onSongPlayClick(song: MusicSong) {
        _selectedSong.value = song
    }

    // Dismisses MusicPlayerBottomSheet
    fun onDismissPlayer() {
        _selectedSong.value = null
    }

    // Called from MusicPlayerBottomSheet "Use to Create" — navigates to TemplatePreviewer
    fun onUseToCreateVideo(song: MusicSong) {
        _selectedSong.value = null
        _navigationEvent.value = WidgetNavigationEvent.NavigateToTemplatePreviewerWithSong(song.id)
    }

    fun onNavigateBack() {
        _navigationEvent.value = WidgetNavigationEvent.NavigateBack
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }
}