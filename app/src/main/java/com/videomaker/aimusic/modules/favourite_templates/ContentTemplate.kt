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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Stable
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.core.constants.AdPlacement

@Stable
private sealed class TemplateGridItem {
    data class TemplateItem(val template: VideoTemplate) : TemplateGridItem()
    data object AdItem : TemplateGridItem()
}

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
        if (state.isEmpty()) return@LazyColumn

        item(key = "template_grid", contentType = "grid") {
            val gridItems = remember(state) {
                buildList {
                    state.forEachIndexed { index, template ->
                        add(TemplateGridItem.TemplateItem(template))
                        if (index == 0) {
                            add(TemplateGridItem.AdItem)
                        }
                    }
                }
            }

            val adAspectRatio = 9f / 16f
            val aspectRatios = remember(gridItems) {
                gridItems.map { item ->
                    when (item) {
                        is TemplateGridItem.TemplateItem -> parseTemplateAspectRatio(item.template.aspectRatio)
                        is TemplateGridItem.AdItem -> adAspectRatio
                    }
                }
            }

            StaggeredGrid(
                itemCount = gridItems.size,
                aspectRatios = aspectRatios,
                columns = 2,
                spacing = dimens.spaceSm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)

            ) { index ->
                when (val item = gridItems[index]) {
                    is TemplateGridItem.TemplateItem -> {
                        TemplateCard(
                            name = item.template.name,
                            thumbnailPath = item.template.thumbnailPath,
                            aspectRatio = aspectRatios[index],
                            isPremium = item.template.isPremium,
                            isShowOption = true,
                            useCount = item.template.useCount,
                            onClickDelete = {
                                onDeleteTemplateClick.invoke(item.template.id)
                            },
                            onClick = { onTemplateClick(item.template.id) }
                        )
                    }
                    is TemplateGridItem.AdItem -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            NativeAdView(
                                placement = AdPlacement.NATIVE_LIBRARY_CREATED_VIDEO,
                                modifier = Modifier.fillMaxSize(),
                                isDebug = BuildConfig.DEBUG
                            )
                        }
                    }
                }
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