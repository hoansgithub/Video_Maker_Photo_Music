package com.videomaker.aimusic.domain.repository

import com.videomaker.aimusic.domain.model.TextFontPreset

interface TextRepository {
    suspend fun getFonts(): Result<List<TextFontPreset>>
}