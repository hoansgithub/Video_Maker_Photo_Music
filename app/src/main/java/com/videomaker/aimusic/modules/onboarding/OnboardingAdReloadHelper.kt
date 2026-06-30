package com.videomaker.aimusic.modules.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import co.alcheclub.lib.acccore.ads.mediation.AdLoadResult
import com.videomaker.aimusic.core.ads.AdPlacementConfigService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * State holder for ALT ad reload (last-only pattern).
 *
 * After the initial PRIMARY→ALT swap, subsequent user interactions trigger
 * a force-reload of the ALT ad using a single-unit "_last_only" placement
 * (the last waterfall unit, which typically has the highest fill rate).
 *
 * 2-second gap between reloads prevents excessive ad requests.
 */
@Stable
class AdReloadState internal constructor(
    private val sourcePlacement: String,
    private val lastOnlyPlacement: String?,
    private val adsLoaderService: AdsLoaderService,
    private val tag: String,
) {
    /** Fire-and-forget scope: ad loads survive Activity/composable lifecycle. */
    private val adLoadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var reloadKey by mutableIntStateOf(0)
        private set
    var isReloadingAd by mutableStateOf(false)
        private set
    var lastAdImpressionTime by mutableLongStateOf(0L)
        private set

    /** Current placement: source on first load, _last_only after reload. */
    val currentPlacement: String
        get() = if (reloadKey == 0) sourcePlacement
                else lastOnlyPlacement ?: sourcePlacement

    /**
     * Called on user interaction (selection, tap). Triggers a force-reload
     * of the _last_only placement if 2s have elapsed since the last impression
     * and no reload is in progress.
     */
    fun onUserInteraction() {
        val lop = lastOnlyPlacement ?: return
        val now = System.currentTimeMillis()
        if (now - lastAdImpressionTime < 2000L) return
        if (isReloadingAd) return

        isReloadingAd = true
        adLoadScope.launch(Dispatchers.IO) {
            try {
                val result = adsLoaderService.loadNative(lop, forceReload = true)
                withContext(Dispatchers.Main) {
                    if (result is AdLoadResult.Success || result is AdLoadResult.AlreadyLoading) {
                        reloadKey++
                        lastAdImpressionTime = System.currentTimeMillis()
                    }
                    isReloadingAd = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { isReloadingAd = false }
            }
        }
    }

    internal fun updateImpressionTime() {
        lastAdImpressionTime = System.currentTimeMillis()
    }
}

/**
 * Remember an [AdReloadState] for the given ALT placement.
 *
 * Creates a dynamic "_last_only" placement from the source placement's
 * last waterfall unit for fast subsequent reloads.
 */
@Composable
fun rememberAdReloadState(
    sourcePlacement: String,
    tag: String = "AdReload",
    adConfigService: AdPlacementConfigService = koinInject(),
    adsLoaderService: AdsLoaderService = koinInject(),
): AdReloadState {
    val lastOnlyPlacement = remember(sourcePlacement) {
        adConfigService.createLastOnlyPlacement(sourcePlacement)
    }

    val state = remember(sourcePlacement) {
        AdReloadState(
            sourcePlacement = sourcePlacement,
            lastOnlyPlacement = lastOnlyPlacement,
            adsLoaderService = adsLoaderService,
            tag = tag,
        )
    }

    LaunchedEffect(Unit) {
        state.updateImpressionTime()
    }

    return state
}
