package com.videomaker.aimusic.data.mapper

import android.net.Uri
import com.videomaker.aimusic.data.local.database.entity.ProjectEntity
import com.videomaker.aimusic.data.local.database.entity.ProjectWithAssets
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.ProjectSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectMapperTemplateIdTest {

    @Test
    fun `toDomain maps templateId from entity`() {
        val entity = ProjectEntity(
            id = "project-1",
            name = "Project",
            createdAt = 100L,
            updatedAt = 200L,
            thumbnailUri = null,
            templateId = "template-123"
        )

        val domain = ProjectMapper.toDomain(ProjectWithAssets(project = entity, assets = emptyList()))

        assertEquals("template-123", domain.settings.templateId)
    }

    @Test
    fun `toEntity maps templateId from settings and keeps null when absent`() {
        val withTemplate = Project(
            id = "project-1",
            name = "Project",
            createdAt = 100L,
            updatedAt = 200L,
            thumbnailUri = Uri.parse("content://thumb/1"),
            settings = ProjectSettings(templateId = "template-abc"),
            assets = emptyList()
        )
        val withoutTemplate = withTemplate.copy(
            id = "project-2",
            settings = withTemplate.settings.copy(templateId = null)
        )

        val withTemplateEntity = ProjectMapper.toEntity(withTemplate)
        val withoutTemplateEntity = ProjectMapper.toEntity(withoutTemplate)

        assertEquals("template-abc", withTemplateEntity.templateId)
        assertNull(withoutTemplateEntity.templateId)
    }

    @Test
    fun `toDomain normalizes blank templateId to null`() {
        val entity = ProjectEntity(
            id = "project-blank",
            name = "Project",
            createdAt = 100L,
            updatedAt = 200L,
            thumbnailUri = null,
            templateId = ""
        )

        val domain = ProjectMapper.toDomain(ProjectWithAssets(project = entity, assets = emptyList()))

        assertNull(domain.settings.templateId)
    }

    @Test
    fun `toEntity normalizes blank templateId to null`() {
        val project = Project(
            id = "project-blank",
            name = "Project",
            createdAt = 100L,
            updatedAt = 200L,
            thumbnailUri = Uri.parse("content://thumb/1"),
            settings = ProjectSettings(templateId = ""),
            assets = emptyList()
        )

        val entity = ProjectMapper.toEntity(project)

        assertNull(entity.templateId)
    }
}
