package com.videomaker.aimusic.media.audio

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
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
 * Usage:
 * ```kotlin
 * val player = AudioTimelinePlayer(context, playbackClock, scope)
 * player.setNodes(audioNodes)
 * player.play()  // Starts monitoring clock and activating nodes
 * player.pause()
 * player.release()
 * ```
 */
class AudioTimelinePlayer(
    private val context: Context,
    private val playbackClock: PlaybackClock,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "AudioTimelinePlayer"
        private const val SYNC_INTERVAL_MS = 50L // Check sync every 50ms
        private const val SYNC_DRIFT_THRESHOLD_MS = 100L // Re-sync if drift exceeds this
    }

    // Active ExoPlayer instances keyed by AudioNode ID
    private val activePlayers = mutableMapOf<String, ExoPlayer>()

    // Current audio nodes on the timeline
    private var nodes: List<AudioNode> = emptyList()

    // Sync monitoring job
    private var syncJob: Job? = null

    /**
     * Set the audio nodes for the timeline.
     * Creates/releases ExoPlayer instances as needed.
     */
    fun setNodes(newNodes: List<AudioNode>) {
        // Release players for removed nodes
        val newIds = newNodes.map { it.id }.toSet()
        val removedIds = activePlayers.keys - newIds
        removedIds.forEach { id ->
            activePlayers.remove(id)?.release()
        }

        nodes = newNodes

        // Pre-create players for all nodes (they start paused)
        newNodes.forEach { node ->
            if (!activePlayers.containsKey(node.id)) {
                createPlayerForNode(node)
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
     * Seek all players to match the clock position.
     */
    fun seekTo(positionMs: Long) {
        activePlayers.forEach { (id, player) ->
            val node = nodes.find { it.id == id } ?: return@forEach

            if (node.isActiveAt(positionMs)) {
                val nodePositionMs = positionMs - node.startTimeMs + node.trimStartMs
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

    private fun createPlayerForNode(node: AudioNode) {
        try {
            val player = ExoPlayer.Builder(context).build()

            // Determine media URI
            val uri = node.customAudioUri ?: node.songUrl
            if (uri == null) {
                Log.w(TAG, "No audio URI for node ${node.id}")
                player.release()
                return
            }

            // Build media item with clipping for trim
            val mediaItemBuilder = MediaItem.Builder().setUri(uri)
            val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(node.trimStartMs)

            if (node.trimEndMs != null) {
                clippingBuilder.setEndPositionMs(node.trimEndMs)
            }

            mediaItemBuilder.setClippingConfiguration(clippingBuilder.build())

            player.setMediaItem(mediaItemBuilder.build())
            player.volume = node.volume
            player.prepare()
            // Don't auto-play — sync loop will start it at the right time
            player.playWhenReady = false

            activePlayers[node.id] = player
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create player for node ${node.id}: ${e.message}")
        }
    }

    /**
     * Core sync loop — checks each node's state against the clock.
     * Starts nodes that should be playing, stops nodes that shouldn't.
     */
    private fun syncPlayersToTimeline() {
        if (!playbackClock.isPlaying) return

        val currentTimeMs = playbackClock.currentTimeMs()

        for (node in nodes) {
            val player = activePlayers[node.id] ?: continue

            if (node.isActiveAt(currentTimeMs)) {
                // Node should be playing
                val expectedPositionMs = currentTimeMs - node.startTimeMs

                if (!player.isPlaying) {
                    // Start the player at the correct position
                    player.seekTo(expectedPositionMs.coerceAtLeast(0L))
                    player.play()
                } else {
                    // Check drift
                    val actualPositionMs = player.currentPosition
                    val drift = kotlin.math.abs(expectedPositionMs - actualPositionMs)
                    if (drift > SYNC_DRIFT_THRESHOLD_MS) {
                        player.seekTo(expectedPositionMs)
                    }
                }

                // Update volume with fade envelope
                player.volume = node.volumeAt(currentTimeMs)
            } else {
                // Node should not be playing
                if (player.isPlaying) {
                    player.pause()
                }
            }
        }
    }
}
