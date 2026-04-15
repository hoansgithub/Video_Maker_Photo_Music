package com.videomaker.aimusic.core.notification

import com.videomaker.aimusic.domain.model.VideoTemplate

object ViralTemplateResolver {
    const val VIRAL_USE_COUNT_MIN = 1_000_000L

    data class DailySnapshot(
        val localDate: String,
        val templateId: String,
        val usageCount: Long
    )

    data class Candidate(
        val template: VideoTemplate,
        val collageSources: List<String>
    )

    fun resolve(
        featuredTemplates: List<VideoTemplate>,
        snapshot: DailySnapshot?,
        currentLocalDate: String
    ): Candidate? {
        val sorted = featuredTemplates
            .filter { it.id.isNotBlank() }
            .sortedByDescending { it.useCount }
        val top = sorted.firstOrNull() ?: return null
        if (top.useCount < VIRAL_USE_COUNT_MIN) return null

        val alreadyShownToday =
            snapshot != null &&
                snapshot.localDate == currentLocalDate &&
                snapshot.templateId == top.id
        if (alreadyShownToday) return null

        val collageSources = sorted
            .take(3)
            .mapNotNull { template ->
                template.thumbnailPath.takeIf { it.isNotBlank() }
                    ?: template.previewImagePath.takeIf { it.isNotBlank() }
            }

        return Candidate(
            template = top,
            collageSources = collageSources
        )
    }
}

