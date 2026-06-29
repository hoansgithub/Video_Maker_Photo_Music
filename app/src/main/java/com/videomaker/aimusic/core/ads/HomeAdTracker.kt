package com.videomaker.aimusic.core.ads

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HomeAdTracker
 *
 * Tracks Home Screen active state, dismiss/collapse state of the Home collapsible ad,
 * and whether the user is coming from another in-app flow.
 * Monitors the application lifecycle to reset states on app reopen.
 */
class HomeAdTracker : DefaultLifecycleObserver {

    private val _isCollapsibleAdDismissed = MutableStateFlow(false)
    val isCollapsibleAdDismissed: StateFlow<Boolean> = _isCollapsibleAdDismissed.asStateFlow()

    private val _isFromOtherFlow = MutableStateFlow(false)
    val isFromOtherFlow: StateFlow<Boolean> = _isFromOtherFlow.asStateFlow()

    @Volatile
    var isHomeScreenActive: Boolean = false

    /**
     * Called when the user navigates away from the Home Screen to another flow.
     */
    fun onNavigateAway() {
        android.util.Log.d("HomeAdTracker", "onNavigateAway() called - setting isFromOtherFlow = true")
        _isFromOtherFlow.value = true
    }

    /**
     * Called when the user explicitly closes/collapses the collapsible ad.
     */
    fun dismissCollapsibleAd() {
        android.util.Log.d("HomeAdTracker", "dismissCollapsibleAd() called - setting isCollapsibleAdDismissed = true")
        _isCollapsibleAdDismissed.value = true
    }

    /**
     * Resets tracking states for a new activity session.
     */
    fun resetForNewSession() {
        android.util.Log.d("HomeAdTracker", "resetForNewSession() called - resetting states")
        _isFromOtherFlow.value = false
        _isCollapsibleAdDismissed.value = false
    }

    override fun onStart(owner: LifecycleOwner) {
        // App was reopened from the background
        android.util.Log.d("HomeAdTracker", "onStart() called - isHomeScreenActive = $isHomeScreenActive")
        if (isHomeScreenActive) {
            // User was on the Home Screen when app was backgrounded and now returned to it.
            // Reset state to show the collapsible ad again.
            _isFromOtherFlow.value = false
            _isCollapsibleAdDismissed.value = false
        }
    }
}
