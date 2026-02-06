package com.videomaker.aimusic.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object for the `songs` table in Supabase.
 *
 * Table schema:
 * - id: BIGINT PRIMARY KEY
 * - name: TEXT NOT NULL
 * - artist: TEXT NOT NULL
 * - mp3_url: TEXT NOT NULL
 * - preview_url: TEXT
 * - cover_url: TEXT
 * - genres: TEXT[] (array)
 * - duration_ms: INTEGER
 * - is_premium: BOOLEAN DEFAULT FALSE
 * - is_active: BOOLEAN DEFAULT TRUE
 * - sort_order: INTEGER DEFAULT 0
 * - created_at: TIMESTAMPTZ
 * - updated_at: TIMESTAMPTZ
 */
@Serializable
data class SongDto(
    val id: Long,
    val name: String,
    val artist: String,
    @SerialName("mp3_url")
    val mp3Url: String,
    @SerialName("preview_url")
    val previewUrl: String? = null,
    @SerialName("cover_url")
    val coverUrl: String? = null,
    val genres: List<String> = emptyList(),
    @SerialName("duration_ms")
    val durationMs: Int? = null,
    @SerialName("is_premium")
    val isPremium: Boolean = false,
    @SerialName("is_active")
    val isActive: Boolean = true,
    @SerialName("sort_order")
    val sortOrder: Int = 0,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)
