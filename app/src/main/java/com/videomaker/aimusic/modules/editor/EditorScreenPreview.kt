package com.videomaker.aimusic.modules.editor

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.domain.model.Asset
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.ui.theme.PlayerCardBackground
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

/**
 * Editor Screen Previews
 *
 * Open this file in Android Studio and view the previews to see the new editor design.
 */

// ============================================
// PREVIEW DATA
// ============================================
private val previewProject = Project(
    id = "preview-1",
    name = "Summer Vacation",
    createdAt = System.currentTimeMillis(),
    updatedAt = System.currentTimeMillis(),
    thumbnailUri = null,
    settings = ProjectSettings(
        imageDurationMs = 3000L,
        transitionPercentage = 30,
        effectSetId = "dreamy_vibes",
        overlayFrameId = null,
        musicSongId = 1L,
        musicSongName = "Sample Song",
        musicSongUrl = "https://example.com/song.mp3",
        customAudioUri = null,
        audioVolume = 0.8f,
        aspectRatio = AspectRatio.RATIO_9_16
    ),
    assets = listOf(
        Asset(id = "1", uri = Uri.parse("content://1"), orderIndex = 0),
        Asset(id = "2", uri = Uri.parse("content://2"), orderIndex = 1),
        Asset(id = "3", uri = Uri.parse("content://3"), orderIndex = 2),
        Asset(id = "4", uri = Uri.parse("content://4"), orderIndex = 3),
        Asset(id = "5", uri = Uri.parse("content://5"), orderIndex = 4)
    )
)

// ============================================
// TOP BAR PREVIEW (NEW DESIGN)
// ============================================
@Preview(
    name = "New Top Bar - 1080p",
    showBackground = true,
    widthDp = 400,
    heightDp = 56
)
@Composable
fun EditorTopBarPreview() {
    VideoMakerTheme {
        Surface {
            EditorTopBar(
                selectedQuality = VideoQuality.FHD_1080,
                isProcessing = false,
                canExport = true,
                onBackClick = {},
                onQualityChange = {},
                onDoneClick = {}
            )
        }
    }
}

@Preview(
    name = "New Top Bar - 720p",
    showBackground = true,
    widthDp = 400,
    heightDp = 56
)
@Composable
fun EditorTopBar720pPreview() {
    VideoMakerTheme {
        Surface {
            EditorTopBar(
                selectedQuality = VideoQuality.HD_720,
                isProcessing = false,
                canExport = true,
                onBackClick = {},
                onQualityChange = {},
                onDoneClick = {}
            )
        }
    }
}

// ============================================
// FULL EDITOR LAYOUT MOCKUP
// ============================================
@Preview(
    name = "Editor Layout - Complete",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=1080px,height=2400px,dpi=440"
)
@Composable
fun EditorLayoutPreview() {
    VideoMakerTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            EditorTopBar(
                selectedQuality = VideoQuality.FHD_1080,
                isProcessing = false,
                canExport = true,
                onBackClick = {},
                onQualityChange = {},
                onDoneClick = {}
            )

            // Video Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📹 Video Preview",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Music Section - Professional design
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(PlayerCardBackground, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                // Seeker row - TOP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play/pause button
                    IconButton(
                        onClick = { },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Slider mockup
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "12:02",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.width(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Separator line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Song info row - BOTTOM
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Music icon in box
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎵", fontSize = 20.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Song Name",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Dropdown icon - matches quality selector
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Expand",
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Separator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Settings Tab Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SplashBackground)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Effect button
                Column(
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Effect",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Effect",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }

                // Image Duration button
                Column(
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Image Duration",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Image Duration",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }

                // Ratio button
                Column(
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = "Ratio",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ratio",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

/**
 * Layout Structure:
 *
 * ┌─────────────────────────────────────┐
 * │ [←]     1080p ▼     [Done]          │ ← NEW TOP BAR
 * ├─────────────────────────────────────┤
 * │                                     │
 * │      📹 Video Preview               │ ← Takes remaining space
 * │                                     │
 * ├─────────────────────────────────────┤
 * │ ⏱ 00:05 / 00:15 ————●————          │ ← Seekbar (~60dp)
 * ├─────────────────────────────────────┤
 * │ 🖼 [+] [IMG1] [IMG2] [IMG3] [IMG4]  │ ← Asset Strip (~80dp)
 * ├─────────────────────────────────────┤
 * │ Duration: 00:15 • 5 photos [Export] │ ← Bottom Bar (~80dp)
 * └─────────────────────────────────────┘
 *
 * Key Changes:
 * - Top Bar: Back + Quality Dropdown (1080p/720p) + Done
 * - Removed: Settings button
 * - Quality: User selects export resolution before editing
 * - Done: Navigates to export screen
 */
