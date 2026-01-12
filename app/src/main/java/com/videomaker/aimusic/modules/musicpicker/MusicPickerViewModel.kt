package com.videomaker.aimusic.modules.musicpicker

import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.model.DeviceAudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MusicPickerViewModel - Manages device audio file browsing and preview
 *
 * Features:
 * - Queries device for audio files via MediaStore
 * - Provides audio preview functionality
 * - Handles permission state
 *
 * NOTE: Uses ContentResolver instead of Context to avoid memory leaks.
 * ViewModels outlive Activity/Fragment lifecycles, so holding Context
 * references would prevent garbage collection.
 */
class MusicPickerViewModel(
    private val contentResolver: ContentResolver
) : ViewModel() {

    companion object {
        private const val TAG = "MusicPickerViewModel"
    }

    // ============================================
    // UI STATE
    // ============================================
    private val _uiState = MutableStateFlow<MusicPickerUiState>(MusicPickerUiState.Initial)
    val uiState: StateFlow<MusicPickerUiState> = _uiState.asStateFlow()

    // ============================================
    // PERMISSION STATE
    // ============================================
    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    // ============================================
    // PREVIEW STATE
    // ============================================
    private val _previewingTrackId = MutableStateFlow<Long?>(null)
    val previewingTrackId: StateFlow<Long?> = _previewingTrackId.asStateFlow()

    // ============================================
    // NAVIGATION EVENTS
    // ============================================
    private val _navigationEvent = MutableStateFlow<MusicPickerNavigationEvent?>(null)
    val navigationEvent: StateFlow<MusicPickerNavigationEvent?> = _navigationEvent.asStateFlow()

    /**
     * Called when audio permission is granted
     */
    fun onPermissionGranted() {
        _permissionGranted.value = true
        loadAudioTracks()
    }

    /**
     * Called when audio permission is denied
     */
    fun onPermissionDenied() {
        _permissionGranted.value = false
        _uiState.value = MusicPickerUiState.Error("Permission denied. Please allow access to audio files.")
    }

    /**
     * Load audio tracks from device storage
     */
    private fun loadAudioTracks() {
        viewModelScope.launch {
            _uiState.value = MusicPickerUiState.Loading

            try {
                val tracks = queryAudioTracks()
                if (tracks.isEmpty()) {
                    _uiState.value = MusicPickerUiState.Empty
                } else {
                    _uiState.value = MusicPickerUiState.Success(tracks)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to load audio tracks", e)
                _uiState.value = MusicPickerUiState.Error("Failed to load audio files: ${e.message}")
            }
        }
    }

    /**
     * Query audio tracks from MediaStore
     */
    private suspend fun queryAudioTracks(): List<DeviceAudioTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<DeviceAudioTrack>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )

        // Only get music files (not ringtones, notifications, etc.)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        // Sort by title
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn)?.takeIf { it != "<unknown>" }
                val album = cursor.getString(albumColumn)?.takeIf { it != "<unknown>" }
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)

                // Skip very short audio files (likely notifications/ringtones that slipped through)
                if (duration < 10_000) continue // Skip files shorter than 10 seconds

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                tracks.add(
                    DeviceAudioTrack(
                        id = id,
                        uri = contentUri,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        size = size
                    )
                )
            }
        }

        tracks
    }

    /**
     * Start/stop previewing an audio track
     */
    fun togglePreview(trackId: Long) {
        _previewingTrackId.value = if (_previewingTrackId.value == trackId) null else trackId
    }

    /**
     * Stop any currently playing preview
     */
    fun stopPreview() {
        _previewingTrackId.value = null
    }

    /**
     * Select a track and navigate back
     */
    fun selectTrack(track: DeviceAudioTrack) {
        stopPreview()
        _navigationEvent.value = MusicPickerNavigationEvent.TrackSelected(track.uri)
    }

    /**
     * Navigate back without selection
     */
    fun navigateBack() {
        stopPreview()
        _navigationEvent.value = MusicPickerNavigationEvent.NavigateBack
    }

    /**
     * Clear navigation event after handling
     */
    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    /**
     * Cleanup resources when ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        // Stop any ongoing preview
        _previewingTrackId.value = null
        android.util.Log.d(TAG, "ViewModel cleared")
    }
}

/**
 * UI State for Music Picker
 */
sealed class MusicPickerUiState {
    data object Initial : MusicPickerUiState()
    data object Loading : MusicPickerUiState()
    data object Empty : MusicPickerUiState()
    data class Success(val tracks: List<DeviceAudioTrack>) : MusicPickerUiState()
    data class Error(val message: String) : MusicPickerUiState()
}

/**
 * Navigation Events for Music Picker
 */
sealed class MusicPickerNavigationEvent {
    data object NavigateBack : MusicPickerNavigationEvent()
    data class TrackSelected(val uri: Uri) : MusicPickerNavigationEvent()
}
