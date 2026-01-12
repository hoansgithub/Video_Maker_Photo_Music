package com.videomaker.aimusic.modules.editor.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.AudioTrack
import com.videomaker.aimusic.domain.model.OverlayFrame
import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.domain.model.TransitionSet
import com.videomaker.aimusic.media.library.AudioTrackLibrary
import com.videomaker.aimusic.media.library.FrameLibrary
import com.videomaker.aimusic.media.library.TransitionSetLibrary
import coil.compose.AsyncImage

/**
 * SettingsPanel - Fullscreen settings overlay
 *
 * Uses a "pending changes" pattern:
 * - Setting changes are staged (not applied immediately)
 * - User must tap "Apply" to trigger video reprocessing
 * - This prevents unnecessary reprocessing while browsing options
 *
 * Settings (user can mix and match):
 * - Effect Set (collection of transitions)
 * - Image Duration (2-12 seconds per image)
 * - Transition Duration (percentage)
 * - Overlay Frame (decorative frame)
 * - Background Music (bundled or custom)
 * - Audio Volume (0-100%)
 * - Aspect Ratio (16:9, 9:16, 1:1, 4:3)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel(
    settings: ProjectSettings,
    hasPendingChanges: Boolean,
    onEffectSetChange: (String?) -> Unit,
    onImageDurationChange: (Long) -> Unit,
    onTransitionPercentageChange: (Int) -> Unit,
    onOverlayFrameChange: (String?) -> Unit,
    onAudioTrackChange: (String?) -> Unit,
    onCustomAudioChange: (Uri?) -> Unit,
    onAudioVolumeChange: (Float) -> Unit,
    onAspectRatioChange: (AspectRatio) -> Unit,
    onApplySettings: () -> Unit,
    onDiscardSettings: () -> Unit,
    onClose: () -> Unit,
    onOpenMusicPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasPendingChanges) {
                            onDiscardSettings()
                        }
                        onClose()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                },
                actions = {
                    // Apply button - always visible when there are changes
                    if (hasPendingChanges) {
                        Button(
                            onClick = {
                                onApplySettings()
                                onClose()
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(R.string.settings_apply), fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Effect Set Selector
            EffectSetSelector(
                selectedEffectSetId = settings.effectSetId,
                onEffectSetSelect = onEffectSetChange
            )

            // Image Duration
            ImageDurationSelector(
                selectedDurationMs = settings.imageDurationMs,
                onDurationSelect = onImageDurationChange
            )

            // Transition Duration (Percentage)
            TransitionPercentageSelector(
                selectedPercentage = settings.transitionPercentage,
                imageDurationMs = settings.imageDurationMs,
                onPercentageSelect = onTransitionPercentageChange
            )

            // Overlay Frame
            OverlayFrameSelector(
                selectedFrameId = settings.overlayFrameId,
                onFrameSelect = onOverlayFrameChange
            )

            // Background Music
            AudioSection(
                selectedTrackId = settings.audioTrackId,
                customAudioUri = settings.customAudioUri,
                audioVolume = settings.audioVolume,
                onTrackSelect = onAudioTrackChange,
                onCustomAudioSelect = onCustomAudioChange,
                onVolumeChange = onAudioVolumeChange,
                onOpenMusicPicker = onOpenMusicPicker
            )

            // Aspect Ratio
            AspectRatioSelector(
                selectedRatio = settings.aspectRatio,
                onRatioSelect = onAspectRatioChange
            )

            // Bottom spacing
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Delay before auto-scrolling to let the panel animation complete
 */
private const val AUTO_SCROLL_DELAY_MS = 400L

@Composable
private fun EffectSetSelector(
    selectedEffectSetId: String?,
    onEffectSetSelect: (String?) -> Unit
) {
    // Load effect sets
    var effectSets by remember { mutableStateOf<List<TransitionSet>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Load data on background thread
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            effectSets = TransitionSetLibrary.getAll()
        }
        isLoading = false
    }

    Column {
        Text(
            text = stringResource(R.string.settings_effect_set),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        val sets = effectSets
        if (isLoading || sets == null) {
            // Loading placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        } else {
            EffectSetSelectorContent(
                effectSets = sets,
                selectedEffectSetId = selectedEffectSetId,
                onEffectSetSelect = onEffectSetSelect
            )
        }
    }
}

@Composable
private fun EffectSetSelectorContent(
    effectSets: List<TransitionSet>,
    selectedEffectSetId: String?,
    onEffectSetSelect: (String?) -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to selected item
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(AUTO_SCROLL_DELAY_MS)
        val selectedIndex = effectSets.indexOfFirst { it.id == selectedEffectSetId }
        if (selectedIndex > 0) {
            listState.animateScrollToItem(selectedIndex + 1) // +1 for "None" item
        }
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // None option
        item(key = "none") {
            EffectSetChip(
                effectSet = null,
                isSelected = selectedEffectSetId == null,
                onClick = { onEffectSetSelect(null) }
            )
        }

        // Effect sets
        items(
            items = effectSets,
            key = { it.id }
        ) { effectSet ->
            EffectSetChip(
                effectSet = effectSet,
                isSelected = effectSet.id == selectedEffectSetId,
                onClick = { onEffectSetSelect(effectSet.id) }
            )
        }
    }
}

@Composable
private fun EffectSetChip(
    effectSet: TransitionSet?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon/indicator
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (effectSet == null) {
                Text(
                    text = stringResource(R.string.settings_off),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            } else if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                // Show effect count
                Text(
                    text = "${effectSet.transitions.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Name
        Text(
            text = effectSet?.name ?: stringResource(R.string.settings_none),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        // Description (only for effect sets)
        if (effectSet != null) {
            Text(
                text = effectSet.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ImageDurationSelector(
    selectedDurationMs: Long,
    onDurationSelect: (Long) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.settings_duration_per_image),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            ProjectSettings.IMAGE_DURATION_OPTIONS.forEach { seconds ->
                val durationMs = seconds * 1000L
                SelectableChip(
                    text = stringResource(R.string.settings_duration_seconds, seconds),
                    isSelected = selectedDurationMs == durationMs,
                    onClick = { onDurationSelect(durationMs) }
                )
            }
        }
    }
}

@Composable
private fun TransitionPercentageSelector(
    selectedPercentage: Int,
    imageDurationMs: Long,
    onPercentageSelect: (Int) -> Unit
) {
    // Calculate actual transition duration for display
    val transitionDurationSec = (imageDurationMs * 2 * selectedPercentage / 100) / 1000f

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_transition_duration),
                style = MaterialTheme.typography.bodyMedium
            )
            // Show actual duration based on current settings
            Text(
                text = stringResource(R.string.settings_transition_duration_value, transitionDurationSec),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            ProjectSettings.TRANSITION_PERCENTAGE_OPTIONS.forEach { percentage ->
                SelectableChip(
                    text = "$percentage%",
                    isSelected = selectedPercentage == percentage,
                    onClick = { onPercentageSelect(percentage) }
                )
            }
        }
    }
}

@Composable
private fun OverlayFrameSelector(
    selectedFrameId: String?,
    onFrameSelect: (String?) -> Unit
) {
    val frames = remember { FrameLibrary.getAll() }

    Column {
        Text(
            text = stringResource(R.string.settings_overlay_frame),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            // None option
            FrameChip(
                frame = null,
                isSelected = selectedFrameId == null,
                onClick = { onFrameSelect(null) }
            )

            // Available frames
            frames.forEach { frame ->
                FrameChip(
                    frame = frame,
                    isSelected = frame.id == selectedFrameId,
                    onClick = { onFrameSelect(frame.id) }
                )
            }
        }
    }
}

@Composable
private fun FrameChip(
    frame: OverlayFrame?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (frame == null) {
            Text(
                text = stringResource(R.string.settings_none),
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            AsyncImage(
                model = "file:///android_asset/${frame.frameUrl}",
                contentDescription = frame.name,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun AudioSection(
    selectedTrackId: String?,
    customAudioUri: Uri?,
    audioVolume: Float,
    onTrackSelect: (String?) -> Unit,
    onCustomAudioSelect: (Uri?) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenMusicPicker: () -> Unit
) {
    val tracks = remember { AudioTrackLibrary.getAll() }
    var volumeValue by remember(audioVolume) { mutableFloatStateOf(audioVolume) }
    val hasAudio = selectedTrackId != null || customAudioUri != null

    Column {
        Text(
            text = stringResource(R.string.settings_background_music),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            // None option
            AudioChip(
                track = null,
                isSelected = selectedTrackId == null && customAudioUri == null,
                onClick = {
                    onTrackSelect(null)
                    onCustomAudioSelect(null)
                }
            )

            // Bundled tracks
            tracks.forEach { track ->
                AudioChip(
                    track = track,
                    isSelected = track.id == selectedTrackId,
                    onClick = {
                        onTrackSelect(track.id)
                        onCustomAudioSelect(null)
                    }
                )
            }

            // Custom audio option
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (customAudioUri != null) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        width = if (customAudioUri != null) 2.dp else 1.dp,
                        color = if (customAudioUri != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onOpenMusicPicker() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (customAudioUri != null) stringResource(R.string.settings_custom) else stringResource(R.string.settings_add_audio),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        // Volume slider (show if audio is selected)
        if (hasAudio) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.settings_volume),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${(volumeValue * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Slider(
                value = volumeValue,
                onValueChange = { volumeValue = it },
                onValueChangeFinished = { onVolumeChange(volumeValue) },
                valueRange = 0f..1f
            )
        }
    }
}

@Composable
private fun AudioChip(
    track: AudioTrack?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (track != null) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = track?.name ?: stringResource(R.string.settings_none),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun AspectRatioSelector(
    selectedRatio: AspectRatio,
    onRatioSelect: (AspectRatio) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.settings_aspect_ratio),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AspectRatio.entries.forEach { ratio ->
                SelectableChip(
                    text = ratio.displayName.split(" ").first(),
                    isSelected = ratio == selectedRatio,
                    onClick = { onRatioSelect(ratio) }
                )
            }
        }
    }
}

@Composable
private fun SelectableChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
