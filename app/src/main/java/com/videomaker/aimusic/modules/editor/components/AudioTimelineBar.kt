package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.domain.model.AudioNode

/**
 * AudioTimelineBar - Horizontal timeline showing audio clip blocks.
 *
 * Each AudioNode is rendered as a colored block positioned according
 * to its startTimeMs on the timeline. Overlapping nodes stack vertically.
 *
 * @param audioNodes List of audio nodes on the timeline
 * @param totalDurationMs Total timeline duration
 * @param currentPositionMs Current playback position (for playhead indicator)
 * @param selectedNodeId Currently selected node ID (highlighted)
 * @param onNodeClick Callback when a node is clicked
 * @param pixelsPerMs Scale factor for timeline width
 */
@Composable
fun AudioTimelineBar(
    audioNodes: List<AudioNode>,
    totalDurationMs: Long,
    currentPositionMs: Long,
    selectedNodeId: String?,
    onNodeClick: (AudioNode) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 40.dp,
    pixelsPerMs: Float = 0.1f // 1 second = 100px
) {
    val scrollState = rememberScrollState()
    val timelineWidthDp = remember(totalDurationMs, pixelsPerMs) {
        (totalDurationMs * pixelsPerMs).dp
    }

    val nodeColors = remember {
        listOf(
            Color(0xFF4CAF50), // Green
            Color(0xFF2196F3), // Blue
            Color(0xFFFF9800), // Orange
            Color(0xFF9C27B0), // Purple
            Color(0xFFE91E63)  // Pink
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            // Timeline background
            Box(
                modifier = Modifier
                    .width(timelineWidthDp)
                    .height(trackHeight * 3) // Support up to 3 stacked tracks
                    .background(Color(0xFF1A1A1A))
            ) {
                // Audio node blocks
                audioNodes.forEachIndexed { index, node ->
                    val nodeDurationMs = node.trimmedDurationMs ?: (totalDurationMs - node.startTimeMs)
                    val startOffsetDp = (node.startTimeMs * pixelsPerMs).dp
                    val nodeWidthDp = (nodeDurationMs * pixelsPerMs).dp
                    val isSelected = node.id == selectedNodeId
                    val trackIndex = index.coerceAtMost(2) // Max 3 tracks
                    val color = nodeColors[index % nodeColors.size]

                    Box(
                        modifier = Modifier
                            .offset(x = startOffsetDp, y = trackHeight * trackIndex)
                            .width(nodeWidthDp)
                            .height(trackHeight - 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSelected) color else color.copy(alpha = 0.7f)
                            )
                            .clickable { onNodeClick(node) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = node.songName ?: "Audio ${index + 1}",
                            color = Color.White,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Playhead indicator
                val playheadOffsetDp = (currentPositionMs * pixelsPerMs).dp
                Canvas(
                    modifier = Modifier
                        .offset(x = playheadOffsetDp)
                        .width(2.dp)
                        .height(trackHeight * 3)
                ) {
                    drawLine(
                        color = Color.Red,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 2f
                    )
                }
            }
        }

        // Time markers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = formatTimeMs(currentPositionMs),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatTimeMs(totalDurationMs),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
