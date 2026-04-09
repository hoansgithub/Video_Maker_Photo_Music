package com.videomaker.aimusic.modules.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.domain.usecase.ObserveLikedSongsUseCase
import com.videomaker.aimusic.domain.usecase.ObserveLikedTemplatesUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ============================================
// UI STATE
// ============================================

data class UninstallData(
    val likedTemplates: List<VideoTemplate> = emptyList(),
    val likedSongs: List<MusicSong> = emptyList()
)

sealed class UninstallUiState {
    data object Loading : UninstallUiState()
    data class Success(val data: UninstallData) : UninstallUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class UninstallNavigationEvent {
    data object NavigateBack : UninstallNavigationEvent()
    data class NavigateToTemplatePreviewer(val templateId: String) : UninstallNavigationEvent()
    data object NavigateToTemplates : UninstallNavigationEvent()
    data object NavigateToAllSongs : UninstallNavigationEvent()
    data class NavigateToTemplatePreviewerWithSong(val songId: Long) : UninstallNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

class UninstallViewModel(
    observeLikedTemplatesUseCase: ObserveLikedTemplatesUseCase,
    observeLikedSongsUseCase: ObserveLikedSongsUseCase,
    private val templateRepository: TemplateRepository,
    private val songRepository: SongRepository,
) : ViewModel() {

    private val _navigationEvent = MutableStateFlow<UninstallNavigationEvent?>(null)
    val navigationEvent: StateFlow<UninstallNavigationEvent?> = _navigationEvent.asStateFlow()

    private val _selectedSong = MutableStateFlow<MusicSong?>(null)
    val selectedSong: StateFlow<MusicSong?> = _selectedSong.asStateFlow()

    // Fallback API data shown when the liked lists are empty
    private val _fallbackTemplates = MutableStateFlow<List<VideoTemplate>>(emptyList())
    private val _fallbackSongs = MutableStateFlow<List<MusicSong>>(emptyList())

    val uiState: StateFlow<UninstallUiState> = combine(
        observeLikedTemplatesUseCase().map { it.take(3) },
        observeLikedSongsUseCase().map { it.take(2) },
        _fallbackTemplates,
        _fallbackSongs
    ) { likedTemplates, likedSongs, fallbackTemplates, fallbackSongs ->
        UninstallUiState.Success(
            UninstallData(
                likedTemplates = likedTemplates.ifEmpty { fallbackTemplates },
                likedSongs = likedSongs.ifEmpty { fallbackSongs }
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UninstallUiState.Loading
    )

    init {
        loadFallbackData()
    }

    private fun loadFallbackData() {
        viewModelScope.launch {
            val templatesDeferred = async { templateRepository.getFeaturedTemplates(limit = 3) }
            val songsDeferred = async { songRepository.getFeaturedSongs(limit = 2) }
            _fallbackTemplates.value = templatesDeferred.await().getOrElse { emptyList() }
            _fallbackSongs.value = songsDeferred.await().getOrElse { emptyList() }
        }
    }

    fun onTemplateClick(template: VideoTemplate) {
        Analytics.trackUninstallContentClick(
            section = AnalyticsEvent.Value.Section.TEMPLATE,
            id = template.id
        )
        Analytics.trackTemplateClick(
            templateId = template.id,
            templateName = template.name,
            location = AnalyticsEvent.Value.Location.UNINSTALL
        )
        _navigationEvent.value = UninstallNavigationEvent.NavigateToTemplatePreviewer(template.id)
    }

    fun onSeeMoreTemplatesClick() {
        Analytics.trackUninstallSeeMore(AnalyticsEvent.Value.Section.TEMPLATE)
        _navigationEvent.value = UninstallNavigationEvent.NavigateToTemplates
    }

    fun onSongClick(song: MusicSong) {
        Analytics.trackUninstallContentClick(
            section = AnalyticsEvent.Value.Section.MUSIC,
            id = song.id.toString()
        )
        Analytics.trackSongClick(
            songId = song.id.toString(),
            songName = song.name,
            location = AnalyticsEvent.Value.Location.UNINSTALL
        )
        _selectedSong.value = song
    }

    fun onDismissPlayer() {
        _selectedSong.value = null
    }

    fun onUseToCreateVideo(song: MusicSong) {
        _selectedSong.value = null
        _navigationEvent.value = UninstallNavigationEvent.NavigateToTemplatePreviewerWithSong(song.id)
    }

    fun onSeeMoreSongsClick() {
        Analytics.trackUninstallSeeMore(AnalyticsEvent.Value.Section.MUSIC)
        _navigationEvent.value = UninstallNavigationEvent.NavigateToAllSongs
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }
}
