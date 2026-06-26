package com.videomaker.aimusic.core.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import com.videomaker.aimusic.core.playback.OnboardingMusicPlayer
import com.videomaker.aimusic.modules.onboarding.OnboardingFlowCoordinator
import com.videomaker.aimusic.modules.onboarding.OnboardingStep
import com.videomaker.aimusic.ui.components.RetentionDialog
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import org.koin.android.ext.android.inject

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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start onboarding music on the first step that opens.
        // preload() was called at splash; start() is idempotent (no-op if already playing).
        musicPlayer.start()

        // Auto 1-step-ahead: preload ads for the step AFTER this one.
        // Ensures ads are ready when navigateToNext is called.
        onboardingStep?.let { coordinator.preloadAdsForNextStep(it) }

        onSetupComplete(savedInstanceState)

        setContent {
            VideoMakerTheme {
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

        return AdSwapState(
            currentPlacement = if (showAlt) alt else primary,
            triggerSwap = { triggered = true },
            hasSwapped = showAlt,
            resetSwap = { _showAlt.value = false },
        )
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
