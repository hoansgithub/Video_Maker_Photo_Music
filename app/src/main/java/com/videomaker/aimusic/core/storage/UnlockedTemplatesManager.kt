package com.videomaker.aimusic.core.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages locally unlocked templates.
 *
 * Templates start as locked (is_premium = true in database).
 * When user watches a rewarded ad, the template is unlocked locally and stored in SharedPreferences.
 * Unlocked state persists across app restarts and device reboots.
 *
 * Storage: SharedPreferences with Set<String> of unlocked template IDs
 */
class UnlockedTemplatesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Mutex to prevent race conditions on parallel unlock operations
    private val mutex = Mutex()

    // In-memory cache of unlocked template IDs for fast checks
    private val _unlockedTemplateIds = MutableStateFlow<Set<String>>(loadUnlockedIds())
    val unlockedTemplateIds: StateFlow<Set<String>> = _unlockedTemplateIds.asStateFlow()

    companion object {
        private const val PREFS_NAME = "unlocked_templates"
        private const val KEY_UNLOCKED_IDS = "unlocked_ids"
    }

    /**
     * Load unlocked template IDs from SharedPreferences.
     */
    private fun loadUnlockedIds(): Set<String> {
        return prefs.getStringSet(KEY_UNLOCKED_IDS, emptySet()) ?: emptySet()
    }

    /**
     * Check if a template is unlocked locally.
     *
     * @param templateId The template ID to check
     * @return true if unlocked, false if still locked
     */
    fun isUnlocked(templateId: String): Boolean {
        return _unlockedTemplateIds.value.contains(templateId)
    }

    /**
     * Unlock a template locally after watching rewarded ad.
     * Persists to SharedPreferences and updates in-memory cache.
     * Thread-safe with mutex to prevent race conditions on parallel unlocks.
     *
     * @param templateId The template ID to unlock
     */
    suspend fun unlockTemplate(templateId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentIds = _unlockedTemplateIds.value.toMutableSet()
            if (currentIds.add(templateId)) {
                // Save to SharedPreferences
                prefs.edit()
                    .putStringSet(KEY_UNLOCKED_IDS, currentIds)
                    .apply()

                // Update in-memory cache
                _unlockedTemplateIds.value = currentIds

                android.util.Log.d("UnlockedTemplates", "✅ Unlocked template: $templateId")
            }
        }
    }

    /**
     * Clear all unlocked templates (for testing or reset).
     * Thread-safe with mutex to prevent race conditions.
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            prefs.edit()
                .clear()
                .apply()
            _unlockedTemplateIds.value = emptySet()
            android.util.Log.d("UnlockedTemplates", "🗑️ Cleared all unlocked templates")
        }
    }

    /**
     * Get count of unlocked templates.
     */
    fun getUnlockedCount(): Int {
        return _unlockedTemplateIds.value.size
    }
}
