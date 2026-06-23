package com.videomaker.aimusic.modules.editor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.ads.RewardedAdController
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.data.local.RegionProvider
import com.videomaker.aimusic.domain.model.StickerPlacement
import com.videomaker.aimusic.domain.model.TextFontPreset
import com.videomaker.aimusic.domain.model.TextOverlay
import com.videomaker.aimusic.domain.repository.TextRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class TextOverlayViewModel(
    private val context: Context,
    private val textRepository: TextRepository,
    private val regionProvider: RegionProvider,
    private val adsLoaderService: AdsLoaderService
) : ViewModel() {

    private val _textOverlays = MutableStateFlow<List<TextOverlay>>(emptyList())
    val textOverlays: StateFlow<List<TextOverlay>> = _textOverlays.asStateFlow()

    private val _selectedTextOverlayId = MutableStateFlow<String?>(null)
    val selectedTextOverlayId: StateFlow<String?> = _selectedTextOverlayId.asStateFlow()

    private val _unlockedFontIds = MutableStateFlow<Set<String>>(emptySet())
    val unlockedFontIds: StateFlow<Set<String>> = _unlockedFontIds.asStateFlow()

    private val _fontPresets = MutableStateFlow<List<TextFontPreset>>(emptyList())
    val fontPresets: StateFlow<List<TextFontPreset>> = _fontPresets.asStateFlow()

    private val _isFontsLoading = MutableStateFlow(false)
    val isFontsLoading: StateFlow<Boolean> = _isFontsLoading.asStateFlow()

    // Keep track of fonts currently downloading to avoid duplicate requests
    private val downloadingFonts = ConcurrentHashMap<String, Job>()

    // State flow of downloaded font IDs to trigger composition updates when a download completes
    private val _downloadedFontIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedFontIds: StateFlow<Set<String>> = _downloadedFontIds.asStateFlow()

    // Rewarded ad controller for premium fonts (reusing EFFECT_SET placement)
    private val fontAdController = RewardedAdController(
        placement = AdPlacement.REWARD_UNLOCK_EFFECT_SET,
        viewModelScope = viewModelScope
    )
    val shouldPresentFontAd: StateFlow<Boolean> = fontAdController.shouldPresentAd

    private val _fontAdError = MutableStateFlow<String?>(null)
    val fontAdError: StateFlow<String?> = _fontAdError.asStateFlow()

    private var onFontUnlockCallback: (() -> Unit)? = null
    private var pendingUnlockFontId: String? = null

    var isInitialized = false
        private set

    init {
        fetchFonts()
    }

    fun initialize(initialOverlays: List<TextOverlay>) {
        if (isInitialized) return
        _textOverlays.value = initialOverlays
        isInitialized = true
    }

    fun addTextOverlay(text: String = "Enter Text", stickers: List<StickerPlacement>) {
        val defaultFontId = _fontPresets.value.firstOrNull()?.id ?: "neue_haas_regular"
        // Shared stacking counter across text + stickers so overlays interleave by add order.
        val nextZ = com.videomaker.aimusic.modules.editor.overlay
            .combinedMaxZIndex(_textOverlays.value, stickers) + 1
        val newOverlay = TextOverlay(text = text, fontId = defaultFontId, zIndex = nextZ)
        _textOverlays.update { it + newOverlay }
        _selectedTextOverlayId.value = newOverlay.id
    }

    fun updateTextOverlay(
        id: String,
        text: String? = null,
        color: Long? = null,
        fontId: String? = null,
        xPercentage: Float? = null,
        yPercentage: Float? = null,
        scale: Float? = null,
        rotation: Float? = null
    ) {
        _textOverlays.update { list ->
            list.map { overlay ->
                if (overlay.id == id) {
                    overlay.copy(
                        text = text ?: overlay.text,
                        color = color ?: overlay.color,
                        fontId = fontId ?: overlay.fontId,
                        xPercentage = xPercentage ?: overlay.xPercentage,
                        yPercentage = yPercentage ?: overlay.yPercentage,
                        scale = scale ?: overlay.scale,
                        rotation = rotation ?: overlay.rotation
                    )
                } else {
                    overlay
                }
            }
        }
    }

    fun removeTextOverlay(id: String) {
        _textOverlays.update { list ->
            list.filter { it.id != id }
        }
        if (_selectedTextOverlayId.value == id) {
            _selectedTextOverlayId.value = null
        }
    }

    fun setSelectedTextOverlayId(id: String?) {
        _selectedTextOverlayId.value = id
    }

    fun isFontUnlocked(fontPreset: TextFontPreset): Boolean {
        if (!fontPreset.isPremium) return true
        return _unlockedFontIds.value.contains(fontPreset.id)
    }

    fun onFontClick(fontPreset: TextFontPreset, onUnlockSuccess: () -> Unit) {
        if (isFontUnlocked(fontPreset)) {
            onUnlockSuccess()
        } else {
            pendingUnlockFontId = fontPreset.id
            onFontUnlockCallback = onUnlockSuccess
            fontAdController.requestAd(
                onReward = {
                    val fontId = pendingUnlockFontId
                    if (fontId != null) {
                        _unlockedFontIds.update { it + fontId }
                        onFontUnlockCallback?.invoke()
                    }
                    cleanupFontAdState()
                },
                onSkip = {
                    val fontId = pendingUnlockFontId
                    if (fontId != null) {
                        _unlockedFontIds.update { it + fontId }
                        onFontUnlockCallback?.invoke()
                    }
                    cleanupFontAdState()
                },
                checkEnabled = { adsLoaderService.canLoadAd(AdPlacement.REWARD_UNLOCK_EFFECT_SET) }
            )
        }
    }

    fun onFontRewardEarned() {
        fontAdController.onRewardEarned()
    }

    fun onFontAdFailed() {
        _fontAdError.value =
            context.getString(com.videomaker.aimusic.R.string.text_overlay_font_ad_error)
        fontAdController.onAdFailed()
        cleanupFontAdState()
    }

    fun clearFontAdError() {
        _fontAdError.value = null
    }

    private fun cleanupFontAdState() {
        pendingUnlockFontId = null
        onFontUnlockCallback = null
    }

    private fun fetchFonts() {
        viewModelScope.launch {
            _isFontsLoading.value = true
            val result = textRepository.getFonts()
            result.fold(
                onSuccess = { fonts ->
                    // Sort fonts: Local GEO -> GL -> others
                    val currentGeo = regionProvider.getRegionCode()
                    val sortedFonts = fonts.sortedWith(compareBy { font ->
                        when {
                            font.fontResId != null -> 0
                            font.geo.any { it.equals(currentGeo, ignoreCase = true) } -> 1
                            font.geo.any {
                                it.equals("GL", ignoreCase = true) ||
                                        it.equals("Global", ignoreCase = true) ||
                                        it.startsWith("Global", ignoreCase = true)
                            } -> 2

                            else -> 3
                        }
                    })
                    _fontPresets.value = sortedFonts

                    // Pre-populate already cached fonts
                    val downloaded = sortedFonts.filter { font ->
                        if (font.fontResId != null) true
                        else {
                            val file = font.getFontFile(context)
                            file != null && file.exists()
                        }
                    }.map { it.id }.toSet()
                    _downloadedFontIds.update { it + downloaded }
                },
                onFailure = { e ->
                    android.util.Log.e(
                        "TextOverlayViewModel",
                        "Failed to fetch fonts: ${e.message}",
                        e
                    )
                }
            )
            _isFontsLoading.value = false
        }
    }

    fun downloadFontIfNeeded(fontPreset: TextFontPreset) {
        val fontId = fontPreset.id
        // Already local resource
        if (fontPreset.fontResId != null) return
        // No remote URL details
        val url = fontPreset.fontUrl ?: return
        val path = fontPreset.fontPath ?: return
        val fullUrl = url + path

        val fontFile = fontPreset.getFontFile(context) ?: return
        if (fontFile.exists()) {
            if (!_downloadedFontIds.value.contains(fontId)) {
                _downloadedFontIds.update { it + fontId }
            }
            return
        }

        if (downloadingFonts.containsKey(fontId)) return

        val downloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d(
                    "TextOverlayViewModel",
                    "Downloading font: ${fontPreset.name} from $fullUrl"
                )
                val bytes = java.net.URL(fullUrl).readBytes()
                if (bytes.isNotEmpty()) {
                    fontFile.parentFile?.mkdirs()
                    fontFile.writeBytes(bytes)
                    android.util.Log.d(
                        "TextOverlayViewModel",
                        "Font downloaded and saved: ${fontFile.absolutePath}"
                    )
                    _downloadedFontIds.update { it + fontId }
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "TextOverlayViewModel",
                    "Failed to download font ${fontPreset.name}: ${e.message}",
                    e
                )
            } finally {
                downloadingFonts.remove(fontId)
            }
        }
        downloadingFonts[fontId] = downloadJob
    }
}