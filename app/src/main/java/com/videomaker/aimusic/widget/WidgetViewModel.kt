package com.videomaker.aimusic.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.widget.model.WidgetType
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

    // Pin widget event - Channel for one-time event (requires Context in composable)
    private val _pinWidgetEvent = Channel<WidgetType>(Channel.BUFFERED)
    val pinWidgetEvent = _pinWidgetEvent.receiveAsFlow()

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
        viewModelScope.launch {
            _pinWidgetEvent.send(widgetType)
        }
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

    fun onSongPlayClick(song: MusicSong) {
        _navigationEvent.value = WidgetNavigationEvent.NavigateToTemplatePreviewerWithSong(song.id)
    }

    fun onNavigateBack() {
        _navigationEvent.value = WidgetNavigationEvent.NavigateBack
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }
}