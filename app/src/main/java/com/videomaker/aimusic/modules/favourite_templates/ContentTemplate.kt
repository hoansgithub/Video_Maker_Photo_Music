package com.videomaker.aimusic.modules.favourite_templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.ui.components.StaggeredGrid
import com.videomaker.aimusic.ui.components.TemplateCard
import com.videomaker.aimusic.ui.theme.AppDimens

@Composable
fun ContentTemplate(
    state: List<VideoTemplate>,
    onTemplateClick: (String) -> Unit,
    onDeleteTemplateClick: (String) -> Unit,
) {
    val dimens = AppDimens.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        contentPadding = PaddingValues(
            top = dimens.spaceLg,
            bottom = dimens.space3Xl + dimens.space2Xl
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)
    ) {
        item(key = "template_grid", contentType = "grid") {
            val aspectRatios = remember(state) {
                state.map { parseTemplateAspectRatio(it.aspectRatio) }
            }

            StaggeredGrid(
                itemCount = state.size,
                aspectRatios = aspectRatios,
                columns = 2,
                spacing = dimens.spaceSm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)

            ) { index ->
                TemplateCard(
                    name = state[index].name,
                    thumbnailPath = state[index].thumbnailPath,
                    aspectRatio = aspectRatios[index],
                    isPremium = state[index].isPremium,
                    isShowOption = true,
                    useCount = state[index].useCount,
                    onClickDelete = {
                        onDeleteTemplateClick.invoke(state[index].id)
                    },
                    onClick = { onTemplateClick(state[index].id) }
                )
            }
        }
    }
}

private fun parseTemplateAspectRatio(aspectRatio: String): Float {
    return try {
        val parts = aspectRatio.split(":")
        if (parts.size == 2) {
            val width = parts[0].toFloatOrNull() ?: 9f
            val height = parts[1].toFloatOrNull() ?: 16f
            width / height
        } else {
            9f / 16f
        }
    } catch (_: Exception) {
        9f / 16f
    }
}