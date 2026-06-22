package com.videomaker.aimusic.modules.editor

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import coil.imageLoader
import coil.request.ImageRequest
import com.videomaker.aimusic.core.ads.RewardedAdController
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.storage.UnlockedStickersManager
import com.videomaker.aimusic.domain.model.Sticker
import com.videomaker.aimusic.domain.model.StickerCategory
import com.videomaker.aimusic.domain.usecase.GetStickerCategoriesUseCase
import com.videomaker.aimusic.domain.usecase.GetStickersByCategoryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// ============================================
// UI STATE
// ============================================

sealed class StickerCategoriesState {
    data object Loading : StickerCategoriesState()

    @Immutable
    data class Success(val categories: List<StickerCategory>) : StickerCategoriesState()

    data class Error(val message: String) : StickerCategoriesState()
}

sealed class StickerListState {
    data object Loading : StickerListState()

    @Immutable
    data class Success(
        val stickers: List<Sticker>,
        val hasMorePages: Boolean,
        val isLoadingMore: Boolean
    ) : StickerListState()

    data class Error(val message: String) : StickerListState()
}

// ============================================
// VIEW MODEL
// ============================================

/**
 * StickerViewModel - drives the sticker picker.
 *
 * Loads categories, then stickers for the selected category. Auto-downloads the
 * first 2 stickers of the first category. Reuses [DownloadState] and the rewarded-ad
 * unlock pattern from [EffectSetViewModel].
 */
class StickerViewModel(
    private val appContext: Context,
    private val getStickerCategoriesUseCase: GetStickerCategoriesUseCase,
    private val getStickersByCategoryUseCase: GetStickersByCategoryUseCase,
    private val unlockedStickersManager: UnlockedStickersManager,
    private val adsLoaderService: AdsLoaderService
) : ViewModel() {

    companion object {
        private const val CATEGORY_PAGE_SIZE = 50
        private const val STICKER_PAGE_SIZE = 30
        private const val AUTO_DOWNLOAD_COUNT = 2
        private const val TAG = "StickerVM"
    }

    private val downloadStatesMutex = Mutex()

    private val _categoriesState = MutableStateFlow<StickerCategoriesState>(StickerCategoriesState.Loading)
    val categoriesState: StateFlow<StickerCategoriesState> = _categoriesState.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _stickerState = MutableStateFlow<StickerListState>(StickerListState.Loading)
    val stickerState: StateFlow<StickerListState> = _stickerState.asStateFlow()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    val unlockedStickerIds = unlockedStickersManager.unlockedStickerIds

    // Rewarded ad (reuses effect-set placement)
    private val rewardedAdController = RewardedAdController(
        placement = AdPlacement.REWARD_UNLOCK_EFFECT_SET,
        viewModelScope = viewModelScope
    )
    val shouldPresentAd: StateFlow<Boolean> = rewardedAdController.shouldPresentAd

    private val _pendingUnlockSticker = MutableStateFlow<Sticker?>(null)
    private var onUnlockSuccessCallback: ((Sticker) -> Unit)? = null

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Per-category sticker cache so re-selecting a tab is instant
    private val stickerCache = mutableMapOf<String, List<Sticker>>()
    private var firstCategoryAutoDownloaded = false

    init {
        loadCategories()
    }

    // ---------- Categories ----------

    private fun loadCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            _categoriesState.value = StickerCategoriesState.Loading
            val result = getStickerCategoriesUseCase(offset = 0, limit = CATEGORY_PAGE_SIZE)
            if (result.isSuccess) {
                val categories = result.getOrDefault(emptyList())
                _categoriesState.value = StickerCategoriesState.Success(categories)
                categories.firstOrNull()?.let { selectCategory(it.id, isInitial = true) }
            } else {
                _categoriesState.value =
                    StickerCategoriesState.Error("Failed to load stickers. Please try again.")
            }
        }
    }

    fun selectCategory(categoryId: String, isInitial: Boolean = false) {
        if (_selectedCategoryId.value == categoryId && _stickerState.value is StickerListState.Success) return
        _selectedCategoryId.value = categoryId

        stickerCache[categoryId]?.let { cached ->
            _stickerState.value = StickerListState.Success(cached, hasMorePages = false, isLoadingMore = false)
            if (isInitial) maybeAutoDownloadFirstCategory(cached)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _stickerState.value = StickerListState.Loading
            val result = getStickersByCategoryUseCase(categoryId, offset = 0, limit = STICKER_PAGE_SIZE)
            // Guard against rapid tab switches: ignore stale responses
            if (_selectedCategoryId.value != categoryId) return@launch
            if (result.isSuccess) {
                val stickers = result.getOrDefault(emptyList())
                stickerCache[categoryId] = stickers
                _stickerState.value = StickerListState.Success(
                    stickers = stickers,
                    hasMorePages = stickers.size >= STICKER_PAGE_SIZE,
                    isLoadingMore = false
                )
                if (isInitial) maybeAutoDownloadFirstCategory(stickers)
            } else {
                _stickerState.value = StickerListState.Error("Failed to load stickers. Please try again.")
            }
        }
    }

    fun loadNextStickers() {
        val categoryId = _selectedCategoryId.value ?: return
        val current = _stickerState.value as? StickerListState.Success ?: return
        if (!current.hasMorePages || current.isLoadingMore) return

        viewModelScope.launch(Dispatchers.IO) {
            _stickerState.value = current.copy(isLoadingMore = true)
            val offset = current.stickers.size
            val result = getStickersByCategoryUseCase(categoryId, offset = offset, limit = STICKER_PAGE_SIZE)
            if (_selectedCategoryId.value != categoryId) return@launch
            if (result.isSuccess) {
                val newItems = result.getOrDefault(emptyList())
                val all = current.stickers + newItems
                stickerCache[categoryId] = all
                _stickerState.value = StickerListState.Success(
                    stickers = all,
                    hasMorePages = newItems.size >= STICKER_PAGE_SIZE,
                    isLoadingMore = false
                )
            } else {
                _stickerState.value = current.copy(isLoadingMore = false, hasMorePages = false)
            }
        }
    }

    // ---------- Download ----------

    private fun maybeAutoDownloadFirstCategory(stickers: List<Sticker>) {
        if (firstCategoryAutoDownloaded) return
        firstCategoryAutoDownloaded = true
        stickers.take(AUTO_DOWNLOAD_COUNT).forEach { startDownload(it) }
    }

    /**
     * "Download" = prefetch the sticker asset into Coil's disk cache so it renders
     * instantly and is available for export. Marks the sticker [DownloadState.Downloaded].
     */
    fun startDownload(sticker: Sticker, onFinished: () -> Unit = {}) {
        val id = sticker.id
        when (_downloadStates.value[id]) {
            is DownloadState.Downloaded -> { onFinished(); return }
            is DownloadState.Downloading -> {
                viewModelScope.launch {
                    while (_downloadStates.value[id] is DownloadState.Downloading) delay(100)
                    if (_downloadStates.value[id] is DownloadState.Downloaded) onFinished()
                }
                return
            }
            else -> {}
        }

        viewModelScope.launch(Dispatchers.IO) {
            updateDownloadState(id, DownloadState.Downloading(0f))
            val request = ImageRequest.Builder(appContext)
                // Download the 512px original so it's cached for placing on the video.
                .data(sticker.fullUrl)
                .build()
            val result = runCatching { appContext.imageLoader.execute(request) }
            val ok = result.getOrNull()?.let { it is coil.request.SuccessResult } ?: false
            if (ok) {
                updateDownloadState(id, DownloadState.Downloaded)
                onFinished()
            } else {
                android.util.Log.w(TAG, "Sticker download failed: $id")
                updateDownloadState(id, DownloadState.NotDownloaded)
            }
        }
    }

    private suspend fun updateDownloadState(id: String, state: DownloadState) {
        downloadStatesMutex.withLock {
            _downloadStates.value = _downloadStates.value.toMutableMap().apply { put(id, state) }
        }
    }

    fun isDownloaded(stickerId: String): Boolean =
        _downloadStates.value[stickerId] is DownloadState.Downloaded

    // ---------- Selection / unlock ----------

    fun isStickerUnlocked(sticker: Sticker): Boolean =
        !sticker.isPremium || unlockedStickersManager.isUnlocked(sticker.id)

    /**
     * Handle a sticker tap. If premium & locked, show a rewarded ad first; otherwise
     * download (if needed) then invoke [onReady] so the editor can add the sticker.
     */
    fun onStickerClick(sticker: Sticker, onReady: (Sticker) -> Unit) {
        if (isStickerUnlocked(sticker)) {
            ensureDownloadedThen(sticker, onReady)
        } else {
            _pendingUnlockSticker.value = sticker
            onUnlockSuccessCallback = { unlocked -> ensureDownloadedThen(unlocked, onReady) }
            requestPendingUnlockAd()
        }
    }

    private fun ensureDownloadedThen(sticker: Sticker, onReady: (Sticker) -> Unit) {
        if (isDownloaded(sticker.id)) {
            onReady(sticker)
        } else {
            startDownload(sticker) { onReady(sticker) }
        }
    }

    private fun requestPendingUnlockAd() {
        val sticker = _pendingUnlockSticker.value ?: return
        rewardedAdController.requestAd(
            onReward = {
                viewModelScope.launch {
                    unlockedStickersManager.unlockSticker(sticker.id)
                    onUnlockSuccessCallback?.invoke(sticker)
                    _pendingUnlockSticker.value = null
                    onUnlockSuccessCallback = null
                }
            },
            checkEnabled = { adsLoaderService.canLoadAd(AdPlacement.REWARD_UNLOCK_EFFECT_SET) }
        )
    }

    fun onRewardEarned() = rewardedAdController.onRewardEarned()

    fun onAdFailed() {
        rewardedAdController.onAdFailed()
        _pendingUnlockSticker.value = null
        onUnlockSuccessCallback = null
    }

    fun onErrorMessageShown() { _errorMessage.value = null }

    fun onRetryCategories() = loadCategories()

    fun onRetryStickers() {
        _selectedCategoryId.value?.let { selectCategory(it) }
    }
}
