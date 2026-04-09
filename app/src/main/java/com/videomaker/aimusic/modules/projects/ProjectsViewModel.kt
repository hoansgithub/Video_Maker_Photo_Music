package com.videomaker.aimusic.modules.projects

import android.app.Activity
import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderException
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import co.alcheclub.lib.acccore.ads.state.AdsLoadingState
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.media.export.MediaStoreHelper
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.domain.usecase.DeleteProjectUseCase
import com.videomaker.aimusic.domain.usecase.GetAllProjectsUseCase
import com.videomaker.aimusic.domain.usecase.GetSuggestedSongsUseCase
import com.videomaker.aimusic.domain.usecase.LikeSongUseCase
import com.videomaker.aimusic.domain.usecase.ObserveLikedSongsUseCase
import com.videomaker.aimusic.domain.usecase.ObserveLikedTemplatesUseCase
import com.videomaker.aimusic.domain.usecase.UnlikeSongUseCase
import com.videomaker.aimusic.domain.usecase.UnlikeTemplateUseCase
import com.videomaker.aimusic.ui.components.ProcessToastState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

// ============================================
// UI STATE
// ============================================

sealed class TemplateTabState {
    data object Idle : TemplateTabState()
    data object Loading : TemplateTabState()
    data class Success(val templates: List<VideoTemplate>) : TemplateTabState()
    data class Error(val message: String) : TemplateTabState()
}

sealed class SongTabState {
    data object Idle : SongTabState()
    data object Loading : SongTabState()
    data class Success(val songs: List<MusicSong>) : SongTabState()
    data class Error(val message: String) : SongTabState()
}

sealed class ProjectsUiState {
    data object Loading : ProjectsUiState()
    data object Empty : ProjectsUiState()
    data class Success(val projects: List<Project>) : ProjectsUiState()
    data class Error(val message: String) : ProjectsUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class ProjectsNavigationEvent {
    data object NavigateBack : ProjectsNavigationEvent()
    data class NavigateToEditor(val projectId: String) : ProjectsNavigationEvent()
    data class NavigateToTemplateDetail(val templateId: String) : ProjectsNavigationEvent()
    data object NavigateToSongSearch : ProjectsNavigationEvent()
    data object NavigateToAllSongs : ProjectsNavigationEvent()
    data object NavigateToTemplateSearch : ProjectsNavigationEvent()
    data object NavigateToAllTemplates : ProjectsNavigationEvent()
    data class NavigateToAssetPickerForSong(val songId: Long) : ProjectsNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

class ProjectsViewModel(
    private val getAllProjectsUseCase: GetAllProjectsUseCase,
    private val deleteProjectUseCase: DeleteProjectUseCase,
    private val templateRepository: TemplateRepository,
    private val getSuggestedSongsUseCase: GetSuggestedSongsUseCase,
    private val observeLikedSongsUseCase: ObserveLikedSongsUseCase,
    private val observeLikedTemplatesUseCase: ObserveLikedTemplatesUseCase,
    private val likeSongUseCase: LikeSongUseCase,
    private val unlikeSongUseCase: UnlikeSongUseCase,
    private val unlikeTemplateUseCase: UnlikeTemplateUseCase,
    private val adsLoaderService: AdsLoaderService
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProjectsUiState>(ProjectsUiState.Loading)
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<ProjectsNavigationEvent?>(null)
    val navigationEvent: StateFlow<ProjectsNavigationEvent?> = _navigationEvent.asStateFlow()

    private val _templateState = MutableStateFlow<TemplateTabState>(TemplateTabState.Idle)
    val templateState: StateFlow<TemplateTabState> = _templateState.asStateFlow()

    private val _songState = MutableStateFlow<SongTabState>(SongTabState.Idle)
    val songState: StateFlow<SongTabState> = _songState.asStateFlow()

    private val _selectedSong = MutableStateFlow<MusicSong?>(null)
    val selectedSong: StateFlow<MusicSong?> = _selectedSong.asStateFlow()

    private val _toastState = MutableStateFlow<ProcessToastState?>(null)
    val toastState: StateFlow<ProcessToastState?> = _toastState.asStateFlow()

    // Rewarded ad state for download
    private val _showWatchAdDialog = MutableStateFlow(false)
    val showWatchAdDialog: StateFlow<Boolean> = _showWatchAdDialog.asStateFlow()

    private val _pendingDownloadProject = MutableStateFlow<Project?>(null)
    val pendingDownloadProject: StateFlow<Project?> = _pendingDownloadProject.asStateFlow()

    val templateStateLocal: StateFlow<List<VideoTemplate>> = observeLikedTemplatesUseCase()
        .catch { e -> emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val songStateLocal: StateFlow<List<MusicSong>> = observeLikedSongsUseCase()
        .catch { e -> emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // Liked song IDs for heart icon state in the player bottom sheet
    val likedSongIds: StateFlow<Set<Long>> = observeLikedSongsUseCase.ids()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )
    private var isObserving = false

    /**
     * Start observing projects - call this when the My Videos tab becomes visible
     */
    fun startObservingProjects() {
        if (isObserving) return
        isObserving = true
        observeProjects()
    }

    private fun observeProjects() {
        viewModelScope.launch {
            getAllProjectsUseCase.observe()
                .catch { e ->
                    _uiState.value = ProjectsUiState.Error(e.message ?: "Failed to load projects")
                }
                .collect { projects ->
                    _uiState.value = if (projects.isEmpty()) {
                        ProjectsUiState.Empty
                    } else {
                        // Sort by creation time - latest first (descending order)
                        val sortedProjects = projects.sortedByDescending { it.createdAt }
                        ProjectsUiState.Success(sortedProjects)
                    }
                }
        }
    }

    fun onTabSelected(index: Int) {
        when (index) {
            1 -> loadTemplates()
            2 -> loadSongs()
        }
    }

    private fun loadTemplates() {
        if (_templateState.value is TemplateTabState.Success) return
        viewModelScope.launch {
            _templateState.value = TemplateTabState.Loading
            templateRepository.getTemplates(limit = 20, offset = 0)
                .onSuccess { _templateState.value = TemplateTabState.Success(it) }
                .onFailure { _templateState.value = TemplateTabState.Error(it.message ?: "Failed to load templates") }
        }
    }

    private fun loadSongs() {
        if (_songState.value is SongTabState.Success) return
        viewModelScope.launch {
            _songState.value = SongTabState.Loading
            getSuggestedSongsUseCase(limit = 20)
                .onSuccess { _songState.value = SongTabState.Success(it) }
                .onFailure { _songState.value = SongTabState.Error(it.message ?: "Failed to load songs") }
        }
    }

    fun onProjectClick(project: Project) {
        _navigationEvent.value = ProjectsNavigationEvent.NavigateToEditor(project.id)
    }

    fun onTemplateClick(template: VideoTemplate) {
        _navigationEvent.value = ProjectsNavigationEvent.NavigateToTemplateDetail(template.id)
    }

    fun onDeleteProject(project: Project) {
        viewModelScope.launch {
            deleteProjectUseCase(project.id)
        }
    }

    fun onNavigateBack() {
        _navigationEvent.value = ProjectsNavigationEvent.NavigateBack
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    fun onLikeSong(song: MusicSong) {
        viewModelScope.launch {
            likeSongUseCase(song)
        }
    }

    fun onUnlikeSong(song: MusicSong) {
        viewModelScope.launch {
            unlikeSongUseCase(song.id)
        }
    }

    fun onUnlikeTemplate(templateId: String) {
        viewModelScope.launch {
            unlikeTemplateUseCase(templateId)
        }
    }

    fun onSongClick(song: MusicSong) {
        _selectedSong.value = song
    }

    fun onDismissPlayer() {
        _selectedSong.value = null
    }

    fun onUseToCreateVideo(song: MusicSong) {
        _selectedSong.value = null
        _navigationEvent.value = ProjectsNavigationEvent.NavigateToAssetPickerForSong(song.id)
    }

    fun onSongSearch() {
        _navigationEvent.value = ProjectsNavigationEvent.NavigateToSongSearch
    }

    fun onSeeAllSongs() {
        _navigationEvent.value = ProjectsNavigationEvent.NavigateToAllSongs
    }

    fun onTemplateSearch() {
        _navigationEvent.value = ProjectsNavigationEvent.NavigateToTemplateSearch
    }

    fun onSeeAllTemplates() {
        _navigationEvent.value = ProjectsNavigationEvent.NavigateToAllTemplates
    }

    /**
     * Download project video to gallery (shows rewarded ad first)
     */
    fun onDownloadProject(project: Project, context: Context) {
        // Check if ad is enabled for this placement
        if (!adsLoaderService.canLoadAd(AdPlacement.REWARD_DOWNLOAD_VIDEO)) {
            // Ad disabled - proceed directly with download
            android.util.Log.d("ProjectsViewModel", "⏭️ Ad disabled for download - proceeding without ad")
            performDownload(project, context)
            return
        }

        _pendingDownloadProject.value = project
        _showWatchAdDialog.value = true
    }

    /**
     * Dismiss watch ad dialog
     */
    fun onWatchAdDialogDismiss() {
        _showWatchAdDialog.value = false
        _pendingDownloadProject.value = null
    }

    /**
     * User confirmed watching ad
     * Dismisses dialog - UI should then call showRewardedAd(activity)
     */
    fun onWatchAdConfirmed() {
        _showWatchAdDialog.value = false
    }

    /**
     * Show rewarded ad inline (drama app pattern)
     * Called from UI after user confirms watching ad
     *
     * @param activity Activity context for showing the ad
     * @param context Application context for download
     */
    fun showRewardedAd(activity: Activity, context: Context) {
        viewModelScope.launch {
            try {
                // Load reward ad if not already cached (show loading indicator while loading)
                if (!adsLoaderService.isRewardedAdReady(AdPlacement.REWARD_DOWNLOAD_VIDEO)) {
                    android.util.Log.d("ProjectsViewModel", "⏳ Loading rewarded ad...")

                    // Show loading indicator (like drama app)
                    AdsLoadingState.show("Loading ad...")

                    // Load with 60 second timeout
                    withTimeout(60_000) {
                        adsLoaderService.loadRewarded(AdPlacement.REWARD_DOWNLOAD_VIDEO)
                    }

                    // Hide loading indicator before presenting ad
                    AdsLoadingState.hide()
                }

                // Present reward ad and wait for result (blocking call)
                val result = adsLoaderService.presentRewarded(
                    placement = AdPlacement.REWARD_DOWNLOAD_VIDEO,
                    activity = activity
                )

                // Check if user earned the reward (watched full ad)
                if (result.earnedReward) {
                    android.util.Log.d("ProjectsViewModel", "✅ User earned reward - proceeding with download")
                    // Execute pending download
                    _pendingDownloadProject.value?.let { project ->
                        performDownload(project, context)
                    }
                } else {
                    android.util.Log.d("ProjectsViewModel", "❌ User did not earn reward (closed ad early)")
                    // User closed ad without watching - clear pending request
                    _pendingDownloadProject.value = null
                }

            } catch (e: TimeoutCancellationException) {
                android.util.Log.w("ProjectsViewModel", "Ad load timeout - allowing direct download")
                // Timeout loading ad - allow download anyway (per user requirement)
                AdsLoadingState.hide()
                _pendingDownloadProject.value?.let { project ->
                    performDownload(project, context)
                }

            } catch (e: AdsLoaderException.NoAdToShow) {
                android.util.Log.w("ProjectsViewModel", "No ad available - allowing direct download")
                // If no ad available, allow download anyway (per user requirement)
                AdsLoadingState.hide()
                _toastState.value = ProcessToastState.Error("Ad not available right now")
                _pendingDownloadProject.value?.let { project ->
                    performDownload(project, context)
                }

            } catch (e: Exception) {
                android.util.Log.e("ProjectsViewModel", "Failed to show rewarded ad: ${e.message}")
                // On error, allow download anyway (per user requirement)
                AdsLoadingState.hide()
                _toastState.value = ProcessToastState.Error("Ad not available right now")
                _pendingDownloadProject.value?.let { project ->
                    performDownload(project, context)
                }

            } finally {
                // Ensure loading indicator is always hidden
                AdsLoadingState.hide()
            }
        }
    }

    /**
     * Ad failed to load or user dismissed without watching
     */
    fun onAdFailed() {
        _pendingDownloadProject.value = null
    }

    /**
     * Perform actual download (called after ad is watched successfully)
     */
    fun performDownload(project: Project, context: Context) {
        viewModelScope.launch {
            _toastState.value = ProcessToastState.Loading("Downloading...")

            val videoFile = findProjectVideoFile(context, project.id)
            if (videoFile == null) {
                _toastState.value = ProcessToastState.Error("Video file not found")
                _pendingDownloadProject.value = null
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                MediaStoreHelper.saveVideoToGallery(
                    context = context.applicationContext,
                    videoFile = videoFile,
                    displayName = MediaStoreHelper.generateDisplayName(project.id)
                )
            }

            _toastState.value = when (result) {
                is MediaStoreHelper.SaveResult.Success -> {
                    ProcessToastState.Success("Downloaded to gallery")
                }
                is MediaStoreHelper.SaveResult.Error -> {
                    ProcessToastState.Error(result.message)
                }
            }

            _pendingDownloadProject.value = null
        }
    }

    /**
     * Get share video file for project
     * Returns the video file if found, null otherwise
     */
    fun getShareVideoFile(context: Context, projectId: String): File? {
        return findProjectVideoFile(context, projectId)
    }

    /**
     * Show toast for share operation
     */
    fun onShareStarted() {
        _toastState.value = ProcessToastState.Loading("Preparing to share...")
    }

    /**
     * Show toast for share success
     */
    fun onShareCompleted() {
        _toastState.value = ProcessToastState.Success("Shared")
    }

    /**
     * Show toast for share error
     */
    fun onShareError(message: String) {
        _toastState.value = ProcessToastState.Error(message)
    }

    /**
     * Dismiss toast
     */
    fun onToastDismissed() {
        _toastState.value = null
    }

    /**
     * Find the most recent exported video file for a project
     */
    private fun findProjectVideoFile(context: Context, projectId: String): File? {
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir

        // Find all files matching the project ID pattern
        val matchingFiles = moviesDir.listFiles { file ->
            file.name.startsWith("video_${projectId}_") && file.extension == "mp4"
        }

        // Return the most recently modified file
        return matchingFiles?.maxByOrNull { it.lastModified() }
    }
}