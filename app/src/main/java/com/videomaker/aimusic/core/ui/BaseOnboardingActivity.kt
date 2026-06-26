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
import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.R
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
 * Subclasses use [showAlt] to branch between [OnboardingNormalScreen] and
 * [OnboardingAltScreen] in their [Content]. Call [triggerAltSwap] when the
 * normal screen's swap delay completes, and [resetToNormal] when selections
 * are cleared (e.g. deselect-all). Steps with a single placement never
 * flip [showAlt], so no swap occurs.
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
     * Screen-swap state: when `true`, [Content] branches to [OnboardingAltScreen].
     * Lives at Activity level so it persists across recompositions. ViewModel state
     * (user selections) and scroll position are preserved across the swap.
     */
    private val _showAlt = mutableStateOf(false)

    /** Whether the ALT screen is currently showing. Read by [Content] to branch. */
    protected val showAlt: Boolean get() = _showAlt.value

    /** Called by [OnboardingNormalScreen] when swap delay completes. */
    protected fun triggerAltSwap() {
        _showAlt.value = true
    }

    /** Called when all selections are cleared to return to the primary ad. */
    protected fun resetToNormal() {
        _showAlt.value = false
    }

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
                    // Ad swap: Content() branches on showAlt between OnboardingNormalScreen
                    // and OnboardingAltScreen. Compose's structural recomposition disposes
                    // the old branch and creates the new one, giving each NativeAdView a
                    // clean lifecycle. No key() wrapper — preserves scroll position and
                    // other remember state across the swap.
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
        val options = android.app.ActivityOptions.makeCustomAnimation(
            this,
            R.anim.slide_in_right,   // new page enters from right
            R.anim.slide_out_left    // current page exits to left
        )
        startActivity(intent, options.toBundle())
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
        val options = android.app.ActivityOptions.makeCustomAnimation(
            this,
            android.R.anim.slide_in_left,   // previous page enters from left
            android.R.anim.slide_out_right   // current page exits to right
        )
        startActivity(intent, options.toBundle())
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
