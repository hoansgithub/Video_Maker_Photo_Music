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
 * Manages locally unlocked effect sets.
 *
 * Effect sets start as locked (is_premium = true in database).
 * When user watches a rewarded ad, the effect set is unlocked locally and stored in SharedPreferences.
 * Unlocked state persists across app restarts and device reboots.
 *
 * Storage: SharedPreferences with Set<String> of unlocked effect set IDs
 */
class UnlockedEffectSetsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Mutex to prevent race conditions on parallel unlock operations
    private val mutex = Mutex()

    // In-memory cache of unlocked effect set IDs for fast checks
    private val _unlockedEffectSetIds = MutableStateFlow<Set<String>>(loadUnlockedIds())
    val unlockedEffectSetIds: StateFlow<Set<String>> = _unlockedEffectSetIds.asStateFlow()

    companion object {
        private const val PREFS_NAME = "unlocked_effect_sets"
        private const val KEY_UNLOCKED_IDS = "unlocked_ids"
    }

    /**
     * Load unlocked effect set IDs from SharedPreferences.
     */
    private fun loadUnlockedIds(): Set<String> {
        return prefs.getStringSet(KEY_UNLOCKED_IDS, emptySet()) ?: emptySet()
    }

    /**
     * Check if an effect set is unlocked locally.
     *
     * @param effectSetId The effect set ID to check
     * @return true if unlocked, false if still locked
     */
    fun isUnlocked(effectSetId: String): Boolean {
        return _unlockedEffectSetIds.value.contains(effectSetId)
    }

    /**
     * Unlock an effect set locally after watching rewarded ad.
     * Persists to SharedPreferences and updates in-memory cache.
     * Thread-safe with mutex to prevent race conditions on parallel unlocks.
     *
     * @param effectSetId The effect set ID to unlock
     */
    suspend fun unlockEffectSet(effectSetId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentIds = _unlockedEffectSetIds.value.toMutableSet()
            if (currentIds.add(effectSetId)) {
                // Save to SharedPreferences
                prefs.edit()
                    .putStringSet(KEY_UNLOCKED_IDS, currentIds)
                    .apply()

                // Update in-memory cache
                _unlockedEffectSetIds.value = currentIds

                android.util.Log.d("UnlockedEffectSets", "✅ Unlocked effect set: $effectSetId")
            }
        }
    }

    /**
     * Clear all unlocked effect sets (for testing or reset).
     * Thread-safe with mutex to prevent race conditions.
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            prefs.edit()
                .clear()
                .apply()
            _unlockedEffectSetIds.value = emptySet()
            android.util.Log.d("UnlockedEffectSets", "🗑️ Cleared all unlocked effect sets")
        }
    }

    /**
     * Get count of unlocked effect sets.
     */
    fun getUnlockedCount(): Int {
        return _unlockedEffectSetIds.value.size
    }
}
