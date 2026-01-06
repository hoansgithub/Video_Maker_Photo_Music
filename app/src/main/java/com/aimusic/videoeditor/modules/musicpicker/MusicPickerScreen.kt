package com.aimusic.videoeditor.modules.musicpicker

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimusic.videoeditor.R
import com.aimusic.videoeditor.domain.model.DeviceAudioTrack

/**
 * MusicPickerScreen - Bottom sheet for selecting custom audio from device
 *
 * Features:
 * - Lists all audio files from device storage
 * - Preview button to listen before selecting
 * - Shows track info (title, artist, duration)
 * - Permission handling for audio access
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPickerScreen(
    viewModel: MusicPickerViewModel,
    onTrackSelected: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionGranted by viewModel.permissionGranted.collectAsStateWithLifecycle()
    val previewingTrackId by viewModel.previewingTrackId.collectAsStateWithLifecycle()

    // Bottom sheet state
    var showBottomSheet by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Media player for preview - use remember to survive recomposition
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Cleanup media player on dispose
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) player.stop()
                } catch (e: IllegalStateException) {
                    // Ignore - player in invalid state
                }
                player.release()
            }
            mediaPlayer = null
        }
    }

    // Handle preview state changes - safe MediaPlayer lifecycle management
    LaunchedEffect(previewingTrackId) {
        // Always cleanup existing player first (safe release)
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (e: IllegalStateException) {
                // Ignore - player in invalid state
            }
            player.release()
            mediaPlayer = null
        }

        // Start new playback if track is selected
        previewingTrackId?.let { trackId ->
            val state = uiState
            if (state is MusicPickerUiState.Success) {
                val track = state.tracks.find { it.id == trackId }
                track?.let { audioTrack ->
                    try {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(context, audioTrack.uri)
                            // Use prepareAsync to avoid blocking main thread
                            setOnPreparedListener { start() }
                            setOnCompletionListener { _ ->
                                viewModel.stopPreview()
                            }
                            setOnErrorListener { _, what, extra ->
                                android.util.Log.e("MusicPickerScreen", "MediaPlayer error: what=$what, extra=$extra")
                                viewModel.stopPreview()
                                true // Return true to indicate error was handled
                            }
                            prepareAsync() // Non-blocking!
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MusicPickerScreen", "Failed to initialize preview", e)
                        mediaPlayer?.release()
                        mediaPlayer = null
                        viewModel.stopPreview()
                    }
                }
            }
        }
    }

    // Determine the correct permission based on Android version
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    // Handle navigation events
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is MusicPickerNavigationEvent.NavigateBack -> onDismiss()
                is MusicPickerNavigationEvent.TrackSelected -> {
                    onTrackSelected(event.uri)
                    onDismiss()
                }
            }
            viewModel.onNavigationHandled()
        }
    }

    // Check permission on launch
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.onPermissionGranted()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    // Bottom Sheet
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
                viewModel.navigateBack()
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            MusicPickerContent(
                uiState = uiState,
                previewingTrackId = previewingTrackId,
                onTrackClick = { track -> viewModel.selectTrack(track) },
                onPreviewClick = { track -> viewModel.togglePreview(track.id) },
                onCloseClick = {
                    showBottomSheet = false
                    viewModel.navigateBack()
                },
                onRetryPermission = { permissionLauncher.launch(permission) }
            )
        }
    }
}

@Composable
private fun MusicPickerContent(
    uiState: MusicPickerUiState,
    previewingTrackId: Long?,
    onTrackClick: (DeviceAudioTrack) -> Unit,
    onPreviewClick: (DeviceAudioTrack) -> Unit,
    onCloseClick: () -> Unit,
    onRetryPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCloseClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close)
                )
            }

            Text(
                text = stringResource(R.string.music_picker_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Placeholder for alignment
            Spacer(modifier = Modifier.width(48.dp))
        }

        HorizontalDivider()

        // Content based on state
        when (uiState) {
            is MusicPickerUiState.Initial,
            is MusicPickerUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.music_picker_loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is MusicPickerUiState.Empty -> {
                EmptyContent(modifier = Modifier.weight(1f))
            }

            is MusicPickerUiState.Success -> {
                AudioTrackList(
                    tracks = uiState.tracks,
                    previewingTrackId = previewingTrackId,
                    onTrackClick = onTrackClick,
                    onPreviewClick = onPreviewClick,
                    modifier = Modifier.weight(1f)
                )
            }

            is MusicPickerUiState.Error -> {
                ErrorContent(
                    message = uiState.message,
                    onRetryClick = onRetryPermission,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AudioTrackList(
    tracks: List<DeviceAudioTrack>,
    previewingTrackId: Long?,
    onTrackClick: (DeviceAudioTrack) -> Unit,
    onPreviewClick: (DeviceAudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth()
    ) {
        items(
            items = tracks,
            key = { it.id }
        ) { track ->
            AudioTrackItem(
                track = track,
                isPlaying = track.id == previewingTrackId,
                onTrackClick = { onTrackClick(track) },
                onPreviewClick = { onPreviewClick(track) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        // Bottom padding
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AudioTrackItem(
    track: DeviceAudioTrack,
    isPlaying: Boolean,
    onTrackClick: () -> Unit,
    onPreviewClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTrackClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Music icon / playing indicator
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isPlaying) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (isPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Track info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            track.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = track.formattedDuration,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Preview button
        IconButton(
            onClick = onPreviewClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying)
                    stringResource(R.string.music_picker_stop_preview)
                else
                    stringResource(R.string.music_picker_preview),
                tint = if (isPlaying) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Select button
        IconButton(
            onClick = onTrackClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.music_picker_select),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.music_picker_no_music),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.music_picker_no_music_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.music_picker_permission_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.music_picker_permission_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRetryClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.music_picker_allow_access),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
