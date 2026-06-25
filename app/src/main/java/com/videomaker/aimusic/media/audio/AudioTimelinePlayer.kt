package com.videomaker.aimusic.media.audio

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.videomaker.aimusic.domain.model.AudioNode
import com.videomaker.aimusic.media.renderer.PlaybackClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * AudioTimelinePlayer - Manages multiple ExoPlayer instances for multi-track audio.
 *
 * Each active AudioNode gets its own ExoPlayer instance. The player starts/stops
 * each node's ExoPlayer based on the PlaybackClock position.
 *
 * For preview playback only. Export uses CompositionFactory with AudioMixer.
 *
 * Fadeout is applied in realtime via volume ramp — no Transformer preprocessing
 * needed for preview. This avoids player rebuilds and audible glitches.
 *
 * Usage:
 * ```kotlin
 * val player = AudioTimelinePlayer(context, playbackClock, scope, audioCache)
 * player.setNodes(audioNodes, hookStartTimeMs, totalDurationMs, fadeoutDurationMs)
 * player.play()  // Starts monitoring clock and activating nodes
 * player.pause()
 * player.release()
 * ```
 */
class AudioTimelinePlayer(
    private val context: Context,
    private val playbackClock: PlaybackClock,
    private val scope: CoroutineScope,
    private val audioCache: AudioPreviewCache? = null
) {

    companion object {
        private const val TAG = "AudioTimelinePlayer"

        // Sync loop checks node activation every 200ms.
        // This is NOT for drift correction — it's for starting/stopping
        // timeline nodes (voiceover, SFX) at their startTimeMs/endTimeMs.
        private const val SYNC_INTERVAL_MS = 200L

        // Only re-seek if drift exceeds 500ms. ExoPlayer's seekTo() causes
        // a brief decoder reset (audible as a glitch), so we only correct
        // major desync (e.g., after app resume or long background).
        // Normal clock jitter between SystemClock and ExoPlayer is <50ms.
        private const val DRIFT_THRESHOLD_MS = 500L

        // After an explicit seek, don't drift-correct for this period.
        // Prevents the sync loop from re-seeking while ExoPlayer is still
        // buffering after the first seek.
        private const val SEEK_COOLDOWN_MS = 1000L
    }

    // Active ExoPlayer instances keyed by AudioNode ID
    private val activePlayers = mutableMapOf<String, ExoPlayer>()

    // Current audio nodes on the timeline
    private var nodes: List<AudioNode> = emptyList()

    // Hook start offset for source (non-preprocessed) audio
    private var hookStartTimeMs: Long = 0L

    // Realtime fadeout parameters (applied via volume ramp in sync loop)
    private var totalDurationMs: Long = 0L
    private var fadeoutDurationMs: Long = 0L

    // Sync monitoring job
    private var syncJob: Job? = null

    // Timestamp of last explicit seek (prevents drift-correction during cooldown)
    private var lastSeekUptimeMs: Long = 0L

    // Previous clock position for detecting video loop wrap-around
    private var prevClockTimeMs: Long = 0L

    /**
     * Set the audio nodes for the timeline.
     * Creates/releases ExoPlayer instances as needed.
     *
     * @param newNodes The audio nodes to manage
     * @param hookStartMs Hook start offset for source audio clipping (beat-sync mode)
     * @param totalDurMs Total video duration for realtime fadeout calculation
     * @param fadeoutDurMs Fadeout ramp duration (volume ramps from 1→0 over this period)
     */
    fun setNodes(
        newNodes: List<AudioNode>,
        hookStartMs: Long = 0L,
        totalDurMs: Long = 0L,
        fadeoutDurMs: Long = 0L
    ) {
        hookStartTimeMs = hookStartMs
        totalDurationMs = totalDurMs
        fadeoutDurationMs = fadeoutDurMs

        // Release players for removed nodes
        val newIds = newNodes.map { it.id }.toSet()
        val removedIds = activePlayers.keys - newIds
        removedIds.forEach { id ->
            Log.d(TAG, "Releasing player for removed node: $id")
            activePlayers.remove(id)?.release()
        }

        // Detect changed nodes (need player rebuild)
        val oldNodes = nodes.associateBy { it.id }
        nodes = newNodes

        newNodes.forEach { node ->
            val oldNode = oldNodes[node.id]
            val existingPlayer = activePlayers[node.id]

            if (existingPlayer == null) {
                // New node — create player
                createPlayerForNode(node)
            } else if (oldNode != null && isNodeSourceChanged(oldNode, node)) {
                // Source changed (different song, trim, etc.) — rebuild player
                Log.d(TAG, "Rebuilding player for changed node: ${node.id}")
                existingPlayer.release()
                activePlayers.remove(node.id)
                createPlayerForNode(node)
            } else {
                // Only volume or processedAudioUri changed — update in-place (no rebuild)
                // processedAudioUri is ignored for preview; fadeout is applied via realtime volume
                existingPlayer.volume = node.volume
            }
        }
    }

    /**
     * Start playback — begins monitoring clock and activating/deactivating nodes.
     */
    fun play() {
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                syncPlayersToTimeline()
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    /**
     * Pause all active players.
     */
    fun pause() {
        syncJob?.cancel()
        syncJob = null

        activePlayers.values.forEach { player ->
            player.pause()
        }
    }

    /**
     * Seek all players to match the given position.
     * Sets a cooldown to prevent the sync loop from re-seeking immediately.
     */
    fun seekTo(positionMs: Long) {
        lastSeekUptimeMs = SystemClock.uptimeMillis()
        prevClockTimeMs = positionMs

        activePlayers.forEach { (id, player) ->
            val node = nodes.find { it.id == id } ?: return@forEach

            if (isPrimaryBgmNode(node)) {
                // Primary BGM: timeline position maps directly to audio position
                player.seekTo(positionMs.coerceAtLeast(0L))
            } else if (node.isActiveAt(positionMs)) {
                val nodePositionMs = positionMs - node.startTimeMs
                player.seekTo(nodePositionMs.coerceAtLeast(0L))
            } else {
                player.pause()
            }
        }
    }

    /**
     * Release all resources.
     */
    fun release() {
        syncJob?.cancel()
        syncJob = null

        activePlayers.values.forEach { it.release() }
        activePlayers.clear()
        nodes = emptyList()
    }

    // ============================================
    // INTERNAL
    // ============================================

    /**
     * Check if a node's playback source changed (requiring player rebuild).
     * Ignores processedAudioUri — preview always streams from songUrl/customAudioUri
     * and applies fadeout via realtime volume. processedAudioUri is for export only.
     */
    private fun isNodeSourceChanged(old: AudioNode, new: AudioNode): Boolean {
        return old.customAudioUri != new.customAudioUri ||
                old.songUrl != new.songUrl ||
                old.trimStartMs != new.trimStartMs ||
                old.trimEndMs != new.trimEndMs ||
                old.songId != new.songId
    }

    /**
     * Check if this is the primary BGM node (first node, always active for full duration).
     */
    private fun isPrimaryBgmNode(node: AudioNode): Boolean {
        return node == nodes.firstOrNull()
    }

    /**
     * Calculate fadeout volume multiplier for the given position.
     * Returns 1.0 outside fadeout range, ramps linearly from 1.0→0.0 during fadeout.
     */
    private fun fadeoutMultiplier(positionMs: Long): Float {
        if (totalDurationMs <= 0 || fadeoutDurationMs <= 0) return 1f
        val fadeoutStartMs = totalDurationMs - fadeoutDurationMs
        if (positionMs < fadeoutStartMs) return 1f
        val progress = ((positionMs - fadeoutStartMs).toFloat() / fadeoutDurationMs).coerceIn(0f, 1f)
        return 1f - progress
    }

    private fun createPlayerForNode(node: AudioNode) {
        try {
            // Resolve URI: customAudioUri > songUrl (processedAudioUri ignored for preview)
            val uri: String? = when {
                node.customAudioUri != null -> node.customAudioUri
                node.songUrl != null -> node.songUrl
                else -> null
            }

            if (uri == null) {
                Log.w(TAG, "No audio URI for node ${node.id}")
                return
            }

            // Create ExoPlayer — use cache for remote URLs to prevent 403 on expired CDN links
            val player = if (audioCache != null) {
                ExoPlayer.Builder(context)
                    .setMediaSourceFactory(
                        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                            audioCache.cacheDataSourceFactory
                        )
                    )
                    .build()
            } else {
                ExoPlayer.Builder(context).build()
            }

            // Build media item with appropriate clipping
            val mediaItem = if (node.trimStartMs > 0 || node.trimEndMs != null) {
                // Has per-node trim range
                val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(node.trimStartMs)
                if (node.trimEndMs != null) {
                    clippingBuilder.setEndPositionMs(node.trimEndMs)
                }
                Log.d(TAG, "Node ${node.id}: trim clip ${node.trimStartMs}-${node.trimEndMs ?: "end"}")
                MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(clippingBuilder.build())
                    .build()
            } else if (hookStartTimeMs > 0 && node == nodes.firstOrNull()) {
                // Primary node without trim: use hookStartTimeMs for beat-sync
                Log.d(TAG, "Node ${node.id}: hook start clip at ${hookStartTimeMs}ms")
                MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(hookStartTimeMs)
                            .build()
                    )
                    .build()
            } else {
                MediaItem.Builder().setUri(uri).build()
            }

            player.setMediaItem(mediaItem)

            // Always loop source audio — may be shorter than video duration
            player.repeatMode = Player.REPEAT_MODE_ALL

            player.volume = node.volume
            player.prepare()
            player.playWhenReady = false

            activePlayers[node.id] = player
            Log.d(TAG, "Created player for node ${node.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create player for node ${node.id}: ${e.message}")
        }
    }

    /**
     * Core sync loop — manages node activation and drift correction.
     *
     * Design: once an ExoPlayer is playing, let it run freely. Both ExoPlayer
     * and PlaybackClock use system uptime internally, so they stay within ~30ms
     * of each other naturally. Only re-seek on major drift (>500ms) with a
     * cooldown to prevent glitchy rapid re-seeking.
     *
     * The loop's primary job is activating/deactivating timeline nodes (SFX,
     * voiceover) at their startTimeMs/endTimeMs boundaries.
     *
     * Fadeout is applied via realtime volume ramp — no preprocessed audio needed.
     */
    private fun syncPlayersToTimeline() {
        if (!playbackClock.isPlaying) return

        val currentTimeMs = playbackClock.currentTimeMs()
        val inCooldown = (SystemClock.uptimeMillis() - lastSeekUptimeMs) < SEEK_COOLDOWN_MS

        // Detect video loop wrap-around: clock jumped from near-end to near-start.
        // Distinguished from user backward seek by checking both ends + cooldown.
        val isLoopWrap = totalDurationMs > 0
                && currentTimeMs < DRIFT_THRESHOLD_MS
                && prevClockTimeMs > totalDurationMs - DRIFT_THRESHOLD_MS
                && !inCooldown
        prevClockTimeMs = currentTimeMs

        for (node in nodes) {
            val player = activePlayers[node.id] ?: continue

            // Skip players that aren't ready yet (still buffering after prepare/seek)
            if (player.playbackState != Player.STATE_READY &&
                player.playbackState != Player.STATE_ENDED
            ) {
                // Player is buffering — just ensure playWhenReady is set
                if (!player.playWhenReady) {
                    player.playWhenReady = true
                }
                continue
            }

            if (isPrimaryBgmNode(node)) {
                // Primary BGM: always active.
                // ExoPlayer loops internally (REPEAT_MODE_ALL) at the clip boundary,
                // which may differ from totalDurationMs. Use player.duration to compute
                // the expected position within the current loop iteration.
                val clipDurationMs = player.duration.takeIf { it > 0 && it != androidx.media3.common.C.TIME_UNSET } ?: 0L

                if (!player.isPlaying) {
                    // Start playing (first time or after STATE_ENDED)
                    val seekPos = if (clipDurationMs > 0) currentTimeMs % clipDurationMs else currentTimeMs
                    player.seekTo(seekPos.coerceAtLeast(0L))
                    lastSeekUptimeMs = SystemClock.uptimeMillis()
                    player.play()
                } else if (isLoopWrap) {
                    // Video looped — re-seek to start immediately (bypass cooldown)
                    Log.d(TAG, "Video loop wrap — re-seeking to 0ms (node ${node.id})")
                    player.seekTo(0L)
                    lastSeekUptimeMs = SystemClock.uptimeMillis()
                } else if (!inCooldown && clipDurationMs > 0) {
                    // Drift-correct: expected position accounts for audio looping
                    val expectedPlayerPos = currentTimeMs % clipDurationMs
                    val drift = kotlin.math.abs(expectedPlayerPos - player.currentPosition)
                    // Ignore drift near the audio loop boundary (wrap-around artifact)
                    if (drift > DRIFT_THRESHOLD_MS && drift < clipDurationMs - DRIFT_THRESHOLD_MS) {
                        Log.d(TAG, "Drift correction: ${drift}ms (node ${node.id})")
                        player.seekTo(expectedPlayerPos)
                        lastSeekUptimeMs = SystemClock.uptimeMillis()
                    }
                }
                // Volume with realtime fadeout (no seek, no glitch)
                player.volume = node.volume * fadeoutMultiplier(currentTimeMs)
            } else if (node.isActiveAt(currentTimeMs)) {
                // Timeline node: should be playing
                val expectedPositionMs = currentTimeMs - node.startTimeMs

                if (!player.isPlaying) {
                    // Activate: seek to correct position and start
                    player.seekTo(expectedPositionMs.coerceAtLeast(0L))
                    lastSeekUptimeMs = SystemClock.uptimeMillis()
                    player.play()
                } else if (!inCooldown) {
                    val drift = kotlin.math.abs(expectedPositionMs - player.currentPosition)
                    if (drift > DRIFT_THRESHOLD_MS) {
                        Log.d(TAG, "Drift correction: ${drift}ms (node ${node.id})")
                        player.seekTo(expectedPositionMs)
                        lastSeekUptimeMs = SystemClock.uptimeMillis()
                    }
                }

                // Volume with fade envelope (no seek, no glitch)
                player.volume = node.volumeAt(currentTimeMs)
            } else {
                // Node outside its active range — deactivate
                if (player.isPlaying) {
                    player.pause()
                }
            }
        }
    }
}
