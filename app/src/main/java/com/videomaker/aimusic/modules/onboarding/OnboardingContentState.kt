package com.videomaker.aimusic.modules.onboarding

import androidx.compose.runtime.Immutable

@Immutable
data class OnboardingContentState(
    val page1VideoUrl: String? = null,
    val page1ThumbnailUrl: String? = null,
    val page1LocalFallback: Int? = null,
    val page2ThumbnailUrl: String? = null,
    val nameSong: String? = null,
    val nameArtist: String? = null,
    val page2LocalFallback: Int? = null,
    val page3Thumbnails: List<String> = emptyList(),
    val page3LocalFallbacks: List<Int> = emptyList(),
    val isReady: Boolean = false
)
