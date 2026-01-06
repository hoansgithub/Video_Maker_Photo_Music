package com.aimusic.videoeditor.core.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Manages SharedPreferences for the app
 * Injected as a singleton via ACCDI
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "video_maker_prefs"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }

    /**
     * Check if onboarding has been completed
     */
    fun isOnboardingComplete(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    }

    /**
     * Mark onboarding as complete
     */
    fun setOnboardingComplete(complete: Boolean) {
        prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETE, complete) }
    }

    /**
     * Check if this is the first app launch
     */
    fun isFirstLaunch(): Boolean {
        val isFirst = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        if (isFirst) {
            prefs.edit { putBoolean(KEY_FIRST_LAUNCH, false) }
        }
        return isFirst
    }

    /**
     * Clear all preferences (for testing/logout)
     */
    fun clear() {
        prefs.edit { clear() }
    }
}
