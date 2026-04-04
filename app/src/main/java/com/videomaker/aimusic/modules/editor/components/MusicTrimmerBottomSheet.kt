package com.videomaker.aimusic.modules.editor.components

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * MusicSettingsBottomSheet - Combined music trim and volume control
 *
 * Features:
 * - Volume slider with live preview
 * - Two drag handles (start and end) to select middle portion
 * - Auto-play on finger release with looping
 * - Real waveform visualization
 * - Apply button (top right)
 *
 * @param songName Name of the song being trimmed
 * @param songUrl URL of the song audio file
 * @param songDurationMs Total duration of the song in milliseconds
 * @param trimStartMs Current trim start position
 * @param trimEndMs Current trim end position
 * @param currentVolume Current volume level (0.0 to 1.0)
 * @param onTrimChange Callback when trim position changes during drag
 * @param onVolumeChange Callback when volume changes (live updates)
 * @param onApply Callback when user applies the changes
 * @param onDismiss Callback when bottom sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSettingsBottomSheet(
    songName: String,
    songUrl: String,
    songDurationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    currentVolume: Float,
    onTrimChange: (startMs: Long, endMs: Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onDurationReady: (Long) -> Unit = {}, // Called when actual duration is loaded
    onError: (String) -> Unit = {}, // Called when network/loading error occurs
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Create isolated music player (ExoPlayer for music-only preview)
    val musicPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(songUrl))
            volume = currentVolume // Set initial volume
            prepare()
        }
    }

    // Track playback state
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(trimStartMs) }
    var actualDurationMs by remember { mutableLongStateOf(songDurationMs) }
    var isDragging by remember { mutableStateOf(false) }
    var waveformData by remember { mutableStateOf<List<Float>?>(null) }

    // Extract real waveform data
    LaunchedEffect(songUrl) {
        waveformData = withContext(Dispatchers.IO) {
            extractWaveformData(songUrl, sampleCount = 100) // Reduced from 200 for better performance
        }
    }

    // Update actual duration when player is ready
    LaunchedEffect(musicPlayer) {
        // ✅ Wait for player to be ready with timeout (30s for slow networks)
        // Duration detection only reads file metadata, not the full audio
        val duration = withTimeoutOrNull(30_000L) {
            while (musicPlayer.duration == androidx.media3.common.C.TIME_UNSET) {
                delay(100)
            }
            musicPlayer.duration
        }

        if (duration != null && duration > 0) {
            actualDurationMs = duration
            onDurationReady(duration)
        } else {
            android.util.Log.w("MusicTrimmer", "Timeout waiting for music duration (network issue?)")
            onError(context.getString(R.string.error_network_timeout))
            onDismiss()
        }
    }

    // Update player volume when currentVolume changes
    LaunchedEffect(currentVolume) {
        musicPlayer.volume = currentVolume
    }

    // Auto-play when not dragging
    LaunchedEffect(isDragging) {
        if (!isDragging && !isPlaying) {
            // On finger release, start playing with looping
            musicPlayer.seekTo(trimStartMs)
            musicPlayer.play()
        }
    }

    // Playback position polling (updates playhead)
    LaunchedEffect(isPlaying) {
        while (isPlaying && isActive) {
            currentPositionMs = musicPlayer.currentPosition

            // Loop within trim range
            if (currentPositionMs >= trimEndMs) {
                musicPlayer.seekTo(trimStartMs)
                currentPositionMs = trimStartMs
            }

            delay(50) // 20 FPS update rate
        }
    }

    // Player listener for state changes and errors
    DisposableEffect(musicPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("MusicTrimmer", "Player error: ${error.message}", error)
                val errorMsg = when {
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        context.getString(R.string.error_network_message)
                    else ->
                        context.getString(R.string.error_network_message)
                }
                onError(errorMsg)
                onDismiss()
            }
        }
        musicPlayer.addListener(listener)

        onDispose {
            musicPlayer.removeListener(listener)
            musicPlayer.release() // Release player when bottom sheet closes
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SplashBackground,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header with song name and apply button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = songName,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                // Apply button (top right)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable {
                            musicPlayer.pause()
                            onApply()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.editor_apply),
                        tint = SplashBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Volume control section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                // Volume label and percentage
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.editor_volume),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "${(currentVolume * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Volume slider
                Slider(
                    value = currentVolume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = com.videomaker.aimusic.ui.theme.Gray500
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Trim section label
            Text(
                text = stringResource(R.string.editor_music_trim),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Waveform with two draggable handles
            MusicWaveformView(
                songDurationMs = actualDurationMs,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                currentPositionMs = currentPositionMs,
                waveformData = waveformData, // Real waveform data
                onTrimChange = onTrimChange,
                onDragStateChange = { dragging ->
                    isDragging = dragging
                    if (dragging) {
                        // Pause when dragging starts
                        musicPlayer.pause()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Extract waveform amplitude data from audio file
 * Returns list of RMS amplitude values (0.0 to 1.0) for visualization
 */
private fun extractWaveformData(audioPath: String, sampleCount: Int): List<Float> {
    val extractor = MediaExtractor()

    try {
        extractor.setDataSource(audioPath)

        // Find audio track
        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                break
            }
        }

        if (audioTrackIndex == -1) {
            return generatePlaceholderWaveform(sampleCount)
        }

        extractor.selectTrack(audioTrackIndex)

        val buffer = ByteBuffer.allocate(256 * 1024) // 256KB buffer
        val amplitudes = mutableListOf<Float>()

        // Extract samples and calculate RMS amplitude
        while (extractor.readSampleData(buffer, 0) >= 0) {
            buffer.rewind()

            // Calculate RMS for this chunk
            var sum = 0.0
            var count = 0

            while (buffer.hasRemaining() && count < buffer.remaining() / 2) {
                val sample = buffer.short.toFloat() / Short.MAX_VALUE
                sum += sample * sample
                count++
            }

            val rms = if (count > 0) sqrt(sum / count).toFloat() else 0f
            amplitudes.add(rms)

            buffer.clear()
            extractor.advance()
        }

        // Downsample to target sample count
        if (amplitudes.isEmpty()) {
            return generatePlaceholderWaveform(sampleCount)
        }

        val downsampledAmplitudes = mutableListOf<Float>()
        val step = amplitudes.size.toFloat() / sampleCount

        for (i in 0 until sampleCount) {
            // Use max amplitude in chunk for better visual representation
            val startIndex = (i * step).toInt()
            val endIndex = ((i + 1) * step).toInt().coerceAtMost(amplitudes.size)
            val maxInChunk = amplitudes.subList(startIndex, endIndex).maxOrNull() ?: 0f
            downsampledAmplitudes.add(maxInChunk)
        }

        // Normalize to 0.0 - 1.0 range
        val maxAmplitude = downsampledAmplitudes.maxOrNull() ?: 1f
        return if (maxAmplitude > 0f) {
            downsampledAmplitudes.map { (it / maxAmplitude).coerceIn(0.1f, 1.0f) }
        } else {
            generatePlaceholderWaveform(sampleCount)
        }
    } catch (e: Exception) {
        android.util.Log.e("MusicTrimmer", "Failed to extract waveform", e)
        return generatePlaceholderWaveform(sampleCount)
    } finally {
        extractor.release()  // ✅ Always executed - no early returns in try block
    }
}

/**
 * Generate placeholder waveform when extraction fails
 */
private fun generatePlaceholderWaveform(sampleCount: Int): List<Float> {
    return List(sampleCount) { index ->
        val baseWave = kotlin.math.sin(index * 0.1).toFloat() * 0.5f + 0.5f
        val detailWave = kotlin.math.sin(index * 0.5).toFloat() * 0.3f
        val variation = (kotlin.random.Random.nextFloat() - 0.5f) * 0.2f
        ((baseWave + detailWave + variation + 0.3f) * 0.8f).coerceIn(0.1f, 1.0f)
    }
}
