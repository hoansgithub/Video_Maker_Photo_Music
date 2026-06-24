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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.videomaker.aimusic.core.playback.OnboardingMusicPlayer
import com.videomaker.aimusic.ui.components.RetentionDialog
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import org.koin.android.ext.android.getKoin

/**
 * Standard base class for all onboarding step Activities.
 *
 * Provides a fixed `onCreate` sequence:
 * 1. [onPreCreate] hook (before `super.onCreate`)
 * 2. `enableEdgeToEdge()`
 * 3. Start onboarding music (opt-out via [musicEnabled])
 * 4. [onSetupComplete] hook (ad preloads, analytics, etc.)
 * 5. `setContent { VideoMakerTheme { Content() } }` with optional RetentionDialog
 *
 * Subclasses override [Content] for their Compose UI, and optionally override
 * [musicEnabled] / [retentionDialogEnabled] to opt out of defaults.
 */
abstract class BaseOnboardingActivity : AppCompatActivity() {

    /** Override to `false` to skip starting [OnboardingMusicPlayer]. Default: `true`. */
    protected open val musicEnabled: Boolean = true

    /**
     * Override to `false` to disable the Activity-level [BackHandler] + [RetentionDialog].
     * Screens that manage their own back handling (e.g. pager-based) should set this to `false`.
     * Default: `true`.
     */
    protected open val retentionDialogEnabled: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        onPreCreate(savedInstanceState)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (musicEnabled) {
            getKoin().getOrNull<OnboardingMusicPlayer>()?.start()
        }

        onSetupComplete(savedInstanceState)

        setContent {
            VideoMakerTheme {
                if (retentionDialogEnabled) {
                    WithRetentionDialog { Content() }
                } else {
                    Content()
                }
            }
        }
    }

    /** The screen's Compose content. Called inside [VideoMakerTheme]. */
    @Composable
    protected abstract fun Content()

    /** Hook called before `super.onCreate`. Override for locale or window flags. */
    protected open fun onPreCreate(savedInstanceState: Bundle?) {}

    /** Hook called after edge-to-edge and music setup, before `setContent`. Override for ad preloads or analytics. */
    protected open fun onSetupComplete(savedInstanceState: Bundle?) {}

    /**
     * Navigate forward to [target], optionally configuring the Intent via [intentExtras],
     * then finish this Activity.
     */
    protected fun navigateForward(
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
