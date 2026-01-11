package com.aimusic.videoeditor.domain.model

/**
 * VideoTemplate - A preset combining song + effect set for quick video creation
 *
 * Templates are showcased on the home screen to help users quickly
 * create videos with curated combinations of music and effects.
 *
 * Tags:
 * - trending: Popular/viral templates
 * - new: Recently added templates
 * - seasonal: Holiday/seasonal content (Halloween, Christmas, Summer, etc.)
 */
data class VideoTemplate(
    val id: String,
    val name: String,
    val description: String,
    val thumbnailUrl: String = "",
    val songId: Int,
    val effectSetId: String,
    val aspectRatio: String = "9:16",
    val tags: List<String> = emptyList(),
    val isPremium: Boolean = false,
    val isActive: Boolean = true
)
