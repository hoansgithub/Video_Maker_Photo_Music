package com.videomaker.aimusic.modules.editor.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.videomaker.aimusic.domain.model.AudioNode
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.media.audio.AudioTimelinePlayer
import com.videomaker.aimusic.media.renderer.PlaybackClock
import org.koin.compose.koinInject

/**
 * AudioPreviewPlayer - Multi-track audio playback synced to PlaybackClock.
 *
 * Thin Compose wrapper around [AudioTimelinePlayer] which manages one ExoPlayer
 * per AudioNode. Each node is independently activated/deactivated based on its
 * timeline position (`startTimeMs`, `trimStartMs`, `trimEndMs`).
 *
 * Supports:
 * - Multiple overlapping audio nodes (BGM + voiceover + SFX)
 * - Per-node volume and fade envelopes
 * - Source audio with hookStartTimeMs clipping (beat-sync)
 * - Realtime fadeout via volume ramp (no Transformer preprocessing for preview)
 * - Cached remote URLs via AudioPreviewCache (prevents 403 on expired CDN links)
 * - 500ms drift correction loop keeps audio in sync with GL renderer
 *
 * Only rebuilds individual ExoPlayers when a node's audio source changes.
 * Volume and fadeout changes are applied in-place (no rebuild). Effect set /
 * aspect ratio / overlay changes do NOT trigger any audio rebuild.
 */
@Composable
fun AudioPreviewPlayer(
    audioNodes: List<AudioNode>,
    hookStartTimeMs: Long,
    totalDurationMs: Long,
    fadeoutDurationMs: Long,
    isPlaying: Boolean,
    playbackClock: PlaybackClock,
    seekToPosition: Long?,
    scrubToPosition: Long?,
    onSeekComplete: () -> Unit,
    onScrubComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioCache: AudioPreviewCache = koinInject()
    val scope = rememberCoroutineScope()

    // Always-current refs for callbacks (avoids stale captures)
    val currentIsPlaying by rememberUpdatedState(isPlaying)

    // Create AudioTimelinePlayer once, tied to composable lifetime
    val timelinePlayer = remember {
        AudioTimelinePlayer(context, playbackClock, scope, audioCache)
    }

    // Update nodes when audioNodes, hookStartTimeMs, or fadeout params change.
    // AudioTimelinePlayer.setNodes() diffs internally: only rebuilds players
    // whose source changed, updates volume in-place for the rest.
    LaunchedEffect(audioNodes, hookStartTimeMs, totalDurationMs, fadeoutDurationMs) {
        timelinePlayer.setNodes(audioNodes, hookStartTimeMs, totalDurationMs, fadeoutDurationMs)

        // If already playing, kick the sync loop so new nodes start immediately
        if (currentIsPlaying) {
            timelinePlayer.play()
        }
    }

    // Play / pause
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            timelinePlayer.play()
        } else {
            timelinePlayer.pause()
        }
    }

    // Handle seek (user releases slider) — seek all audio players to final position
    LaunchedEffect(seekToPosition) {
        if (seekToPosition != null) {
            timelinePlayer.seekTo(playbackClock.currentTimeMs())
            onSeekComplete()
        }
    }

    // Handle scrub (user drags slider) — only update PlaybackClock position.
    // Audio players are paused during scrub (stopPlayback called on drag start),
    // so we skip expensive ExoPlayer seeks. Players sync on play resume.
    // GL renderer reads PlaybackClock directly each frame, so visual scrub is smooth.
    LaunchedEffect(scrubToPosition) {
        if (scrubToPosition != null) {
            // PlaybackClock already updated by viewModel.scrubTo()
            // No timelinePlayer.seekTo() — avoid churning paused ExoPlayers
            onScrubComplete()
        }
    }

    // Lifecycle pause/resume is handled by EditorViewModel.onScreenPause/onScreenResume
    // which updates isPlaying → flows down to LaunchedEffect(isPlaying) above.

    // Release on dispose
    DisposableEffect(Unit) {
        onDispose {
            timelinePlayer.release()
        }
    }
}
