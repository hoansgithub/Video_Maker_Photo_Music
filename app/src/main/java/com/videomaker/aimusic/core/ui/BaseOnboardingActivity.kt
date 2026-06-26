package com.videomaker.aimusic.core.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import co.alcheclub.lib.acccore.ads.mediation.AdLoadResult
import com.videomaker.aimusic.core.ads.AdPlacementConfigService
import com.videomaker.aimusic.core.playback.OnboardingMusicPlayer
import com.videomaker.aimusic.modules.onboarding.OnboardingFlowCoordinator
import com.videomaker.aimusic.modules.onboarding.OnboardingStep
import com.videomaker.aimusic.ui.components.RetentionDialog
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

/**
 * Standard base class for all onboarding step Activities.
 *
 * Provides a fixed `onCreate` sequence:
 * 1. [onPreCreate] hook (before `super.onCreate`)
 * 2. `enableEdgeToEdge()`
 * 3. Start onboarding music (preloaded at splash, started on first step, idempotent)
 * 4. [onSetupComplete] hook (ad preloads, analytics, etc.)
 * 5. `setContent { VideoMakerTheme { Content() } }` with optional RetentionDialog
 *
 * Subclasses override [Content] for their Compose UI, and optionally override
 * [retentionDialogEnabled] / [onboardingStep] to opt out of defaults.
 *
 * ## Screen-level ad swap
 *
 * Steps with two ad placements (primary + ALT) use a **screen-swap** pattern
 * for IAB viewability compliance. Instead of swapping individual ad views,
 * the **entire [Content] tree is disposed and recreated** when the swap
 * triggers — giving each NativeAdView a clean lifecycle without controlling
 * ad visibility manually.
 *
 * Call [rememberAdSwapState] in [Content] to opt in. Steps with a single
 * placement get a no-op state (no swap, no screen recreation).
 * Activities with custom swap logic (e.g. dual-delay + button enable) can
 * skip [rememberAdSwapState] and manage their own state.
 *
 * Music is a flow-level concern managed by [OnboardingMusicPlayer] singleton:
 * preloaded at splash (RootViewActivity), started on first onboarding step (here),
 * plays through all steps (idempotent start), stopped in MainActivity.
 */
abstract class BaseOnboardingActivity : AppCompatActivity() {

    companion object {
        internal const val EXTRA_BACK_NAVIGATION = "extra_back_navigation"
    }

    /**
     * Override to `false` to disable the Activity-level [BackHandler] + [RetentionDialog].
     * Screens that manage their own back handling (e.g. pager-based) should set this to `false`.
     * Default: `true`.
     */
    protected open val retentionDialogEnabled: Boolean = true

    /**
     * The onboarding step this Activity represents. Override to enable:
     * - Automatic 1-step-ahead ad preloading in [onCreate]
     * - The [navigateToNextStep] convenience method
     *
     * Leave as `null` for non-step screens (e.g. welcome-back).
     */
    protected open val onboardingStep: OnboardingStep? = null

    /** Central coordinator for step sequencing, ad preloading, and navigation. */
    protected val coordinator: OnboardingFlowCoordinator by inject()

    private val musicPlayer: OnboardingMusicPlayer by inject()

    /**
     * Screen-swap state: when `true`, [Content] is re-keyed so the entire composable
     * tree is disposed and recreated with the ALT ad placement. Lives at Activity level
     * so it survives the `key()` change. ViewModel state (user selections) is unaffected
     * because ViewModels are Activity-scoped.
     */
    private val _showAlt = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        onPreCreate(savedInstanceState)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Start onboarding music on the first step that opens.
        // preload() was called at splash; start() is idempotent (no-op if already playing).
        musicPlayer.start()

        // Auto 1-step-ahead: preload ads for the step AFTER this one.
        // Ensures ads are ready when navigateToNext is called.
        onboardingStep?.let { coordinator.preloadAdsForNextStep(it) }

        onSetupComplete(savedInstanceState)

        setContent {
            VideoMakerTheme {
                WithSwipeBack {
                    // Screen-level swap: key on _showAlt so the entire Content() tree is
                    // disposed and recreated when the ad swap triggers. Each NativeAdView
                    // gets a clean lifecycle — no need to control ad visibility manually.
                    // Steps without ALT placements never flip _showAlt, so no swap occurs.
                    key(_showAlt.value) {
                        if (retentionDialogEnabled) {
                            WithRetentionDialog { Content() }
                        } else {
                            Content()
                        }
                    }
                }
            }
        }
    }

    /** The screen's Compose content. Called inside [VideoMakerTheme]. */
    @Composable
    protected abstract fun Content()

    /** Hook called before `super.onCreate`. Override for locale or window flags. */
    protected open fun onPreCreate(savedInstanceState: Bundle?) {}

    /** Hook called after edge-to-edge and music setup, before `setContent`. Override for analytics or extra setup. */
    protected open fun onSetupComplete(savedInstanceState: Bundle?) {}

    /**
     * Navigate to the next enabled onboarding step via the [coordinator].
     * Requires [onboardingStep] to be overridden; no-op otherwise.
     */
    protected fun navigateToNextStep() {
        val step = onboardingStep ?: return
        coordinator.navigateToNext(this, step)
    }

    /**
     * Navigate forward to [target], optionally configuring the Intent via [intentExtras],
     * then finish this Activity.
     */
    internal fun navigateForward(
        target: Class<out Activity>,
        intentExtras: (Intent.() -> Unit)? = null
    ) {
        val intent = Intent(this, target)
        intentExtras?.invoke(intent)
        startActivity(intent)
        finish()
    }

    /**
     * Navigate backward to [target] with slide-from-left animation,
     * so the user perceives going back to the previous page.
     * Passes [EXTRA_BACK_NAVIGATION] so the target Activity can
     * force-reload its ad for a fresh impression.
     */
    internal fun navigateBackward(
        target: Class<out Activity>,
        intentExtras: (Intent.() -> Unit)? = null
    ) {
        val intent = Intent(this, target)
        intent.putExtra(EXTRA_BACK_NAVIGATION, true)
        intentExtras?.invoke(intent)
        startActivity(intent)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
        finish()
    }

    /**
     * Navigate to the previous enabled onboarding step via the [coordinator].
     * Requires [onboardingStep] to be overridden; no-op otherwise.
     * Prevents double-navigation via [isNavigatingBack] flag.
     */
    private var isNavigatingBack = false

    protected fun navigateToPreviousStep() {
        if (isNavigatingBack) return
        val step = onboardingStep ?: return
        isNavigatingBack = true
        coordinator.navigateToPrevious(this, step)
    }

    /** Apply a cross-fade transition to the current Activity navigation. */
    protected fun applyFadeTransition() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    // ── Screen-level dual-ad swap (IAB viewability) ─────────────────────

    /**
     * State holder for the screen-swap ad pattern.
     *
     * When [triggerSwap] is called, the base class flips [_showAlt] after a
     * delay, which re-keys [Content] — the entire composable tree is disposed
     * and recreated. The new tree calls [rememberAdSwapState] again, which
     * returns the ALT placement. NativeAdView is created fresh with the ALT
     * unit — no ad-visibility toggling needed.
     *
     * Steps with a single placement get a no-op state: [triggerSwap] does
     * nothing, [currentPlacement] always returns the primary.
     *
     * @property currentPlacement The placement to pass to NativeAdView.
     * @property triggerSwap      Call on user interaction (selection, tap).
     *                            No-op if only one placement or already swapped.
     * @property hasSwapped       `true` once the ALT screen is active.
     */
    @Stable
    class AdSwapState internal constructor(
        val currentPlacement: String,
        val triggerSwap: () -> Unit,
        val hasSwapped: Boolean,
        val resetSwap: () -> Unit = {},
        val reloadKey: Int = 0,
        val onUserInteraction: (CoroutineScope) -> Unit = {},
        val delayedButtonEnabled: Boolean = true,
    )

    /**
     * Standard screen-swap: shows primary ad → user interacts → [swapDelayMs]
     * delay → entire [Content] tree recreated with ALT ad.
     *
     * Reads placements from the coordinator for [onboardingStep]:
     * - 0 placements → empty state (no ad)
     * - 1 placement → no-op state (shows primary, [triggerSwap] does nothing)
     * - 2 placements → screen-swap state (primary → delay → ALT)
     *
     * Activities with custom swap logic (e.g. dual-delay with button enable,
     * reset on deselect) should skip this and manage their own state.
     */
    @Composable
    protected fun rememberAdSwapState(swapDelayMs: Long = 500L): AdSwapState {
        val step = onboardingStep
        val placements = if (step != null) coordinator.adPlacements(step) else emptyList()
        val primary = placements.firstOrNull() ?: return AdSwapState("", {}, false)
        val alt = placements.getOrNull(1)
            ?: return AdSwapState(primary, {}, false)

        val showAlt = _showAlt.value

        var triggered by remember { mutableStateOf(false) }

        LaunchedEffect(triggered) {
            if (triggered && !_showAlt.value) {
                delay(swapDelayMs)
                _showAlt.value = true // Re-keys Content() → full screen swap
            }
        }

        // --- IAB viewability: button disabled during swap + 500ms after ALT shows ---
        var delayedAfterSwap by remember { mutableStateOf(false) }
        LaunchedEffect(showAlt) {
            if (showAlt) {
                delay(swapDelayMs)
                delayedAfterSwap = true
            }
        }
        // Before interaction: enabled. Swap triggered: disabled. After swap + 500ms: enabled.
        val delayedButtonEnabled = if (showAlt) delayedAfterSwap else !triggered

        val adPlacementConfigService: AdPlacementConfigService = koinInject()
        val adsLoaderService: AdsLoaderService = koinInject()

        // --- Reload state for ALT screen (last-only pattern) ---

        val lastOnlyPlacement = remember(alt) {
            adPlacementConfigService.createLastOnlyPlacement(alt)
        }
        var reloadKey by remember { mutableIntStateOf(0) }
        var lastImpressionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var isReloading by remember { mutableStateOf(false) }

        // Track impression time after successful reload
        LaunchedEffect(reloadKey) {
            if (reloadKey > 0) lastImpressionTime = System.currentTimeMillis()
        }

        // --- Force-reload primary ad on back navigation for a fresh impression ---
        // Coordinator already destroyed cached ads before navigating here.
        // Force-reload + reloadKey++ recreates NativeAdView with the fresh ad.
        val isBackNavigation = remember {
            intent?.getBooleanExtra(EXTRA_BACK_NAVIGATION, false) == true
        }
        LaunchedEffect(isBackNavigation) {
            if (isBackNavigation) {
                withContext(Dispatchers.IO) {
                    try {
                        val result = adsLoaderService.loadNative(primary, forceReload = true)
                        withContext(Dispatchers.Main) {
                            if (result is AdLoadResult.Success) {
                                reloadKey++
                            }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) {}
                }
            }
        }

        // Determine current placement: primary → alt (full waterfall) → alt_last_only
        val currentPlacement = when {
            !showAlt -> primary
            reloadKey > 0 && lastOnlyPlacement != null -> lastOnlyPlacement
            else -> alt
        }

        return AdSwapState(
            currentPlacement = currentPlacement,
            triggerSwap = { triggered = true },
            hasSwapped = showAlt,
            resetSwap = { _showAlt.value = false },
            reloadKey = reloadKey,
            delayedButtonEnabled = delayedButtonEnabled,
            onUserInteraction = { scope ->
                if (!_showAlt.value) {
                    // Before swap: trigger initial swap
                    triggered = true
                    return@AdSwapState
                }
                // After swap: reload with _last_only
                val lop = lastOnlyPlacement ?: return@AdSwapState
                val now = System.currentTimeMillis()
                if (now - lastImpressionTime < 2000L) return@AdSwapState
                if (isReloading) return@AdSwapState

                isReloading = true
                scope.launch(Dispatchers.IO) {
                    try {
                        val result = adsLoaderService.loadNative(lop, forceReload = true)
                        withContext(Dispatchers.Main) {
                            if (result is AdLoadResult.Success || result is AdLoadResult.AlreadyLoading) {
                                reloadKey++
                            }
                            isReloading = false
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) { isReloading = false }
                    }
                }
            },
        )
    }

    /**
     * Wraps content with a swipe-right gesture that navigates to the previous step.
     * Only active when there is a previous step to navigate to.
     * Threshold: 100dp rightward drag.
     */
    @Composable
    private fun WithSwipeBack(content: @Composable () -> Unit) {
        val step = onboardingStep
        val canSwipeBack = step != null && coordinator.previousStep(step) != null

        if (!canSwipeBack) {
            content()
            return
        }

        val density = LocalDensity.current
        val swipeThresholdPx = remember { with(density) { 100.dp.toPx() } }
        var totalDragDistance by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(step) {
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragDistance = 0f },
                        onDragEnd = {
                            if (totalDragDistance > swipeThresholdPx) {
                                navigateToPreviousStep()
                            }
                            totalDragDistance = 0f
                        },
                        onDragCancel = { totalDragDistance = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            if (dragAmount > 0) {
                                totalDragDistance += dragAmount
                                change.consume()
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }

    @Composable
    private fun WithRetentionDialog(content: @Composable () -> Unit) {
        var showExitDialog by remember { mutableStateOf(false) }
        BackHandler { showExitDialog = true }
        content()
        if (showExitDialog) {
            RetentionDialog(
                onClose = { finish() },
                onStay = { showExitDialog = false }
            )
        }
    }
}
