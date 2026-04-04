package com.videomaker.aimusic.modules.unifiedsearch.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.ui.components.ProvideShimmerEffect
import com.videomaker.aimusic.ui.components.ShimmerBox
import com.videomaker.aimusic.ui.components.SongListItemPlaceholder
import com.videomaker.aimusic.ui.theme.AppDimens

@Composable
fun UnifiedSearchLoadingContent() {
    val dimens = AppDimens.current

    ProvideShimmerEffect {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = dimens.spaceMd),
            userScrollEnabled = false
        ) {
            item(key = "templates_header") {
                ShimmerBox(
                    modifier = Modifier
                        .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm)
                        .width(120.dp)
                        .height(16.dp),
                    cornerRadius = 8.dp
                )
            }

            item(key = "templates_grid") {
                Column(
                    modifier = Modifier.padding(horizontal = dimens.spaceLg),
                    verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)
                ) {
                    repeat(2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
                        ) {
                            ShimmerBox(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(9f / 16f),
                                cornerRadius = 12.dp
                            )
                            ShimmerBox(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(9f / 16f),
                                cornerRadius = 12.dp
                            )
                        }
                    }
                }
            }

            item(key = "music_header") {
                ShimmerBox(
                    modifier = Modifier
                        .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd)
                        .width(100.dp)
                        .height(16.dp),
                    cornerRadius = 8.dp
                )
            }

            items(4, key = { "song_loading_$it" }) {
                SongListItemPlaceholder()
            }
        }
    }
}
