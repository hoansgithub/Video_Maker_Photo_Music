package co.alcheclub.video.maker.photo.music.modules.editor.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.alcheclub.video.maker.photo.music.domain.model.AspectRatio
import co.alcheclub.video.maker.photo.music.domain.model.AudioTrack
import co.alcheclub.video.maker.photo.music.domain.model.OverlayFrame
import co.alcheclub.video.maker.photo.music.domain.model.ProjectSettings
import co.alcheclub.video.maker.photo.music.domain.model.TransitionSet
import co.alcheclub.video.maker.photo.music.media.library.AudioTrackLibrary
import co.alcheclub.video.maker.photo.music.media.library.OverlayFrameLibrary
import co.alcheclub.video.maker.photo.music.media.library.TransitionSetLibrary

/**
 * SettingsPanel - Expandable panel for project settings
 *
 * Settings (user can mix and match):
 * - Transition Set (collection of 20+ transitions)
 * - Transition Duration (2-12 seconds)
 * - Overlay Frame (decorative frame)
 * - Background Music (bundled or custom)
 * - Audio Volume (0-100%)
 * - Aspect Ratio (16:9, 9:16, 1:1, 4:3)
 */
@Composable
fun SettingsPanel(
    settings: ProjectSettings,
    onTransitionSetChange: (String) -> Unit,
    onTransitionDurationChange: (Long) -> Unit,
    onOverlayFrameChange: (String?) -> Unit,
    onAudioTrackChange: (String?) -> Unit,
    onCustomAudioChange: (Uri?) -> Unit,
    onAudioVolumeChange: (Float) -> Unit,
    onAspectRatioChange: (AspectRatio) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Transition Set
        TransitionSetSelector(
            selectedSetId = settings.transitionSetId,
            onSetSelect = onTransitionSetChange
        )

        // Transition Duration
        TransitionDurationSelector(
            selectedDurationMs = settings.transitionDurationMs,
            onDurationSelect = onTransitionDurationChange
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
    }
}

@Composable
private fun TransitionSetSelector(
    selectedSetId: String,
    onSetSelect: (String) -> Unit
) {
    val sets = remember { TransitionSetLibrary.getAll() }

    Column {
        Text(
            text = "Transition Set",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            sets.forEach { set ->
                TransitionSetChip(
                    set = set,
                    isSelected = set.id == selectedSetId,
                    onClick = { onSetSelect(set.id) }
                )
            }
        }
    }
}

@Composable
private fun TransitionSetChip(
    set: TransitionSet,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
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
        // Thumbnail placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = set.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (set.isPremium) {
            Text(
                text = "PRO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TransitionDurationSelector(
    selectedDurationMs: Long,
    onDurationSelect: (Long) -> Unit
) {
    Column {
        Text(
            text = "Duration per Image",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            ProjectSettings.DURATION_OPTIONS.forEach { seconds ->
                val durationMs = seconds * 1000L
                SelectableChip(
                    text = "${seconds}s",
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
    val frames = remember { OverlayFrameLibrary.getAll() }

    Column {
        Text(
            text = "Overlay Frame",
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
                text = "None",
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            Image(
                painter = painterResource(id = frame.drawableRes),
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
            text = "Background Music",
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
                    text = if (customAudioUri != null) "Custom" else "+ Add",
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
                    text = "Volume",
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
            text = track?.name ?: "None",
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
            text = "Aspect Ratio",
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
