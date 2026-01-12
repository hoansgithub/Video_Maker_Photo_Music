package com.aimusic.videoeditor.modules.gallery

import com.aimusic.videoeditor.domain.model.MusicSong

/**
 * Represents a trending song for the gallery banner
 * Converted from MusicSong for UI display
 */
data class TrendingSong(
    val id: Int,
    val name: String,
    val artist: String,
    val coverUrl: String
)

/**
 * Represents a top song with ranking and likes
 * Converted from MusicSong for UI display with ranking
 */
data class TopSong(
    val id: Int,
    val name: String,
    val artist: String,
    val coverUrl: String,
    val likes: Int,
    val ranking: Int
)

// ============================================
// EXTENSION FUNCTIONS
// ============================================

/**
 * Convert MusicSong to TrendingSong for banner display
 */
fun MusicSong.toTrendingSong(): TrendingSong = TrendingSong(
    id = id,
    name = name,
    artist = artist,
    coverUrl = coverUrl
)

/**
 * Convert MusicSong to TopSong with ranking
 */
fun MusicSong.toTopSong(ranking: Int, likes: Int = 0): TopSong = TopSong(
    id = id,
    name = name,
    artist = artist,
    coverUrl = coverUrl,
    likes = likes,
    ranking = ranking
)
