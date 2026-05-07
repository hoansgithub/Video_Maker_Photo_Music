package com.videomaker.aimusic.core.notification

import com.videomaker.aimusic.domain.model.VideoTemplate

object ViralTemplateResolver {
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
