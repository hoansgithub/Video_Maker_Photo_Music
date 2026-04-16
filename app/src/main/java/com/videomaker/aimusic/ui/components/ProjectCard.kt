package com.videomaker.aimusic.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.Asset
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Gray200
import com.videomaker.aimusic.ui.theme.TemplateBadgeBackground
import com.videomaker.aimusic.ui.theme.TextMuted
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import com.videomaker.aimusic.ui.theme.White12
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Project card component for displaying created videos in grid layout.
 * Features:
 * - Thumbnail with aspect ratio matching original video
 * - Duration badge at top right
 * - Play icon at center
 * - Project name, creation date below thumbnail
 * - Asset info and horizontal dots menu at bottom right
 */
@Composable
fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onOptionClick: () -> Unit = {},
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    isHintHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(true) }
    val pulseTransition = rememberInfiniteTransition(label = "project_hint_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "project_hint_alpha"
    )

    AnimatedVisibility(
        visible = isVisible,
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickableSingle(onClick = onClick)
        ) {
            // Thumbnail with aspect ratio (rounded corners)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(project.settings.aspectRatio.ratio)
                    .clip(RoundedCornerShape(dimens.radiusMd))
                    .then(
                        if (isHintHighlighted) {
                            Modifier.border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                shape = RoundedCornerShape(dimens.radiusMd)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .background(Color.Black.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (project.thumbnailUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(project.thumbnailUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = project.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (project.assets.isNotEmpty()) {
                    // Use first asset as thumbnail
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(project.assets.first().uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = project.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                // Duration badge at top-right (styled like heart count badge)
                if (project.assets.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(dimens.spaceXs)
                            .background(
                                color = TemplateBadgeBackground,
                                shape = RoundedCornerShape(999.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = White12,
                                shape = RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = project.formattedDuration,
                            fontSize = 10.sp,
                            color = Gray200,
                            maxLines = 1
                        )
                    }
                }

                // Play icon at center
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .padding(8.dp),
                    tint = Color.White
                )
            }

            // Date and menu row only
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.spaceSm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Creation date/time
                Text(
                    text = formatProjectDate(project.createdAt),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextMuted,
                    modifier = Modifier.weight(1f)
                )

                // Menu button with horizontal dots in circle
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .clickableSingle {
                            onOptionClick()
                            showMenu = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_more_menu),
                        contentDescription = stringResource(R.string.projects_menu),
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(14.dp)
                    )

                        // Dropdown menu
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(stringResource(R.string.projects_download))
                                        AdBadge(
                                            style = AdBadgeStyle.Small(
                                                textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                            )
                                        )
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    onDownload()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.projects_share)) },
                                onClick = {
                                    showMenu = false
                                    onShare()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.projects_delete)) },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirmation = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                    }
                }
            }
        }

        // Delete confirmation dialog
        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text(stringResource(R.string.projects_delete_confirmation_title)) },
                text = { Text(stringResource(R.string.projects_delete_confirmation_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmation = false
                            isVisible = false
                            // Execute deletion immediately - AnimatedVisibility handles exit animation
                            onDelete()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.projects_delete_confirm),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text(stringResource(R.string.projects_delete_cancel))
                    }
                }
            )
        }
    }
}

/**
 * Date formatter for project creation date (thread-safe)
 */
private val projectDateFormatter = DateTimeFormatter
    .ofPattern("MMM dd, yyyy")
    .withZone(ZoneId.systemDefault())

/**
 * Format project creation timestamp to readable date string
 */
private fun formatProjectDate(timestamp: Long): String =
    projectDateFormatter.format(Instant.ofEpochMilli(timestamp))

// ============================================
// PREVIEW
// ============================================

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun ProjectCardPreview() {
    VideoMakerTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            ProjectCard(
                project = Project(
                    id = "preview-1",
                    name = "My Awesome Video",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    thumbnailUri = null,
                    settings = ProjectSettings(
                        imageDurationMs = 3000L,
                        aspectRatio = AspectRatio.RATIO_9_16
                    ),
                    assets = listOf(
                        Asset(
                            id = "1",
                            uri = Uri.parse("content://media/1"),
                            orderIndex = 0
                        ),
                        Asset(
                            id = "2",
                            uri = Uri.parse("content://media/2"),
                            orderIndex = 1
                        ),
                        Asset(
                            id = "3",
                            uri = Uri.parse("content://media/3"),
                            orderIndex = 2
                        )
                    )
                ),
                onClick = {},
                onOptionClick = {},
                onDelete = {},
                onDownload = {},
                onShare = {}
            )
        }
    }
}
