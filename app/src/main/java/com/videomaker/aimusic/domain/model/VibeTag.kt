package com.videomaker.aimusic.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class VibeTag(
    val id: String,
    val displayName: String,
    val emoji: String = ""
)