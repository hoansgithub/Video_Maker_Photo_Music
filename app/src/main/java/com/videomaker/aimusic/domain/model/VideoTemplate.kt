package com.videomaker.aimusic.domain.model

import androidx.compose.runtime.Immutable

/**
 * VideoTemplate - A preset combining song + effect set for quick video creation
 *
 * Templates are showcased on the home screen to help users quickly
 * create videos with curated combinations of music and effects.
 *
 * vibeTags: from vibe_tags table (e.g. birthday, wedding, travel, party, love, ...)
 */
@Immutable
data class VideoTemplate(
    val id: String,
    val name: String,
    val thumbnailUrl: String = "",
    val songId: Long,
    val effectSetId: String,
    val aspectRatio: String = "9:16",
    val imageDurationMs: Int = 3000,
    val transitionPct: Int = 30,
    val vibeTags: List<String> = emptyList(),
    val isPremium: Boolean = false,
    val isActive: Boolean = true
)