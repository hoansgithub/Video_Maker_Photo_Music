package com.videomaker.aimusic.media.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MusicPreviewManager - Global singleton for music preview state
 *
 * Ensures only one song can be previewing across the entire app.
 * When opening music selector in different screens, the preview state is shared.
 *
 * Usage:
 * - Call startPreview(songId) to start previewing a song
 * - Call stopPreview() to stop current preview
 * - Observe previewingSongId to show playing indicator
 */
object MusicPreviewManager {

    private val _previewingSongId = MutableStateFlow<Long?>(null)
    val previewingSongId: StateFlow<Long?> = _previewingSongId.asStateFlow()

    private val _selectedForConfirmId = MutableStateFlow<Long?>(null)
    val selectedForConfirmId: StateFlow<Long?> = _selectedForConfirmId.asStateFlow()

    private val _isLoadingPreview = MutableStateFlow(false)
    val isLoadingPreview: StateFlow<Boolean> = _isLoadingPreview.asStateFlow()

    /**
     * Start or toggle preview for a song
     */
    fun togglePreview(songId: Long) {
        if (_previewingSongId.value == songId) {
            // Stop preview but keep selection
            _previewingSongId.value = null
            _isLoadingPreview.value = false
        } else {
            // Start new preview
            _isLoadingPreview.value = true
            _previewingSongId.value = songId
            _selectedForConfirmId.value = songId
        }
    }

    /**
     * Called when ExoPlayer is prepared and starts playing
     */
    fun onPreviewPrepared() {
        _isLoadingPreview.value = false
    }

    /**
     * Stop preview without changing selection
     */
    fun stopPreview() {
        _previewingSongId.value = null
        _isLoadingPreview.value = false
    }

    /**
     * Get currently selected song ID for confirmation
     */
    fun getSelectedId(): Long? = _selectedForConfirmId.value

    /**
     * Clear all preview state (called when sheet is dismissed)
     */
    fun clearPreviewState() {
        _previewingSongId.value = null
        _selectedForConfirmId.value = null
        _isLoadingPreview.value = false
    }
}
