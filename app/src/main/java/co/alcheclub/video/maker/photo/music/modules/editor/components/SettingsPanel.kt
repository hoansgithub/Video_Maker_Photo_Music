package co.alcheclub.video.maker.photo.music.modules.editor.components

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import co.alcheclub.video.maker.photo.music.R
import co.alcheclub.video.maker.photo.music.domain.model.AspectRatio
import co.alcheclub.video.maker.photo.music.domain.model.AudioTrack
import co.alcheclub.video.maker.photo.music.domain.model.OverlayFrame
import co.alcheclub.video.maker.photo.music.domain.model.ProjectSettings
import co.alcheclub.video.maker.photo.music.domain.model.Transition
import co.alcheclub.video.maker.photo.music.domain.model.TransitionCategory
import co.alcheclub.video.maker.photo.music.media.library.AudioTrackLibrary
import co.alcheclub.video.maker.photo.music.media.library.FrameLibrary
import co.alcheclub.video.maker.photo.music.media.library.TransitionShaderLibrary
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
 * - Transition Effect (individual transition)
 * - Image Duration (2-12 seconds per image)
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
    onTransitionChange: (String?) -> Unit,
    onImageDurationChange: (Long) -> Unit,
    onOverlayFrameChange: (String?) -> Unit,
    onAudioTrackChange: (String?) -> Unit,
    onCustomAudioChange: (Uri?) -> Unit,
    onAudioVolumeChange: (Float) -> Unit,
    onAspectRatioChange: (AspectRatio) -> Unit,
    onApplySettings: () -> Unit,
    onDiscardSettings: () -> Unit,
    onClose: () -> Unit,
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
            // Transition Effect
            TransitionSelector(
                selectedTransitionId = settings.transitionId,
                onTransitionSelect = onTransitionChange
            )

            // Image Duration
            ImageDurationSelector(
                selectedDurationMs = settings.imageDurationMs,
                onDurationSelect = onImageDurationChange
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
                onVolumeChange = onAudioVolumeChange
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

@Composable
private fun TransitionSelector(
    selectedTransitionId: String?,
    onTransitionSelect: (String?) -> Unit
) {
    // Get transitions grouped by category
    val groupedTransitions = remember { TransitionShaderLibrary.getGroupedByCategory() }

    // Get available categories (only those with transitions)
    val availableCategories = remember(groupedTransitions) {
        groupedTransitions.keys.toList().sortedBy { it.ordinal }
    }

    // Find the category of the currently selected transition
    val initialCategory = remember(selectedTransitionId, groupedTransitions) {
        if (selectedTransitionId == null) return@remember null
        groupedTransitions.entries.find { (_, transitions) ->
            transitions.any { it.id == selectedTransitionId }
        }?.key
    }

    // Selected category filter (initialized to the category of selected transition)
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }

    // Filter transitions based on selected category
    val filteredTransitions = remember(selectedCategory, groupedTransitions) {
        val result = mutableListOf<Transition?>(null) // null = "None" option
        if (selectedCategory == null) {
            // Show all transitions
            groupedTransitions.forEach { (_, transitions) ->
                result.addAll(transitions)
            }
        } else {
            // Show only selected category
            groupedTransitions[selectedCategory]?.let { transitions ->
                result.addAll(transitions)
            }
        }
        result.toList()
    }

    // Find index of selected transition for auto-scroll
    val selectedTransitionIndex = remember(selectedTransitionId, filteredTransitions) {
        if (selectedTransitionId == null) 0 // "None" is at index 0
        else filteredTransitions.indexOfFirst { it?.id == selectedTransitionId }.coerceAtLeast(0)
    }

    // Find index of selected category for auto-scroll
    val selectedCategoryIndex = remember(selectedCategory, availableCategories) {
        if (selectedCategory == null) 0 // "All" is at index 0
        else availableCategories.indexOf(selectedCategory) + 1 // +1 because "All" is first
    }

    // LazyRow states for scrolling
    val categoryListState = rememberLazyListState()
    val transitionListState = rememberLazyListState()

    // Auto-scroll to selected category on mount
    LaunchedEffect(Unit) {
        if (selectedCategoryIndex > 0) {
            categoryListState.animateScrollToItem(selectedCategoryIndex)
        }
    }

    // Auto-scroll to selected transition when category changes or on mount
    LaunchedEffect(selectedCategory) {
        if (selectedTransitionIndex > 0) {
            transitionListState.animateScrollToItem(selectedTransitionIndex)
        }
    }

    Column {
        Text(
            text = stringResource(R.string.settings_transition_effect),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category filter chips
        LazyRow(
            state = categoryListState,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // "All" chip
            item {
                CategoryChip(
                    text = stringResource(R.string.settings_all),
                    isSelected = selectedCategory == null,
                    onClick = { selectedCategory = null }
                )
            }

            // Category chips
            items(
                items = availableCategories,
                key = { it.name }
            ) { category ->
                CategoryChip(
                    text = category.displayName,
                    isSelected = selectedCategory == category,
                    onClick = { selectedCategory = category }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Transitions LazyRow
        LazyRow(
            state = transitionListState,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = filteredTransitions,
                key = { it?.id ?: "none" }
            ) { transition ->
                TransitionChip(
                    transition = transition,
                    isSelected = if (transition == null) {
                        selectedTransitionId == null
                    } else {
                        transition.id == selectedTransitionId
                    },
                    onClick = { onTransitionSelect(transition?.id) }
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun TransitionChip(
    transition: Transition?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(72.dp)
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
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon/indicator
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (transition == null) {
                Text(
                    text = stringResource(R.string.settings_off),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            } else if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                // Category initial
                Text(
                    text = transition.category.displayName.first().toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = transition?.name ?: stringResource(R.string.settings_none),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (transition?.isPremium == true) {
            Text(
                text = stringResource(R.string.settings_pro),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold
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
                model = "file:///android_asset/${frame.assetPath}",
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
    onVolumeChange: (Float) -> Unit
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
                    .clickable { /* TODO: Open audio picker */ }
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
