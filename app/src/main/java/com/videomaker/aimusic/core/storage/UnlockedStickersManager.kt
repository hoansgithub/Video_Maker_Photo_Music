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
 * Manages locally unlocked premium stickers.
 *
 * Premium stickers (is_premium = true) are unlocked after watching a rewarded ad.
 * Unlocked state persists across restarts. Mirrors [UnlockedEffectSetsManager].
 */
class UnlockedStickersManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mutex = Mutex()

    private val _unlockedStickerIds = MutableStateFlow<Set<String>>(loadUnlockedIds())
    val unlockedStickerIds: StateFlow<Set<String>> = _unlockedStickerIds.asStateFlow()

    companion object {
        private const val PREFS_NAME = "unlocked_stickers"
        private const val KEY_UNLOCKED_IDS = "unlocked_ids"
    }

    private fun loadUnlockedIds(): Set<String> =
        prefs.getStringSet(KEY_UNLOCKED_IDS, emptySet()) ?: emptySet()

    fun isUnlocked(stickerId: String): Boolean =
        _unlockedStickerIds.value.contains(stickerId)

    suspend fun unlockSticker(stickerId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentIds = _unlockedStickerIds.value.toMutableSet()
            if (currentIds.add(stickerId)) {
                prefs.edit().putStringSet(KEY_UNLOCKED_IDS, currentIds).apply()
                _unlockedStickerIds.value = currentIds
                android.util.Log.d("UnlockedStickers", "✅ Unlocked sticker: $stickerId")
            }
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            prefs.edit().clear().apply()
            _unlockedStickerIds.value = emptySet()
        }
    }
}
