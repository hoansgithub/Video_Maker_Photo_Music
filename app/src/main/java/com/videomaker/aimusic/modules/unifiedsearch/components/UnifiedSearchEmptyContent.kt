package com.videomaker.aimusic.modules.unifiedsearch.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.TextSecondary

@Composable
fun UnifiedSearchEmptyContent(
    query: String,
    exploreSuggestions: List<VideoTemplate>,
    isLoadingExplore: Boolean,
    onExploreMore: () -> Unit,
    onTemplateClick: (String) -> Unit
) {
    val dimens = AppDimens.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = dimens.spaceMd)
    ) {
        item(key = "empty_message") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.spaceXxl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(dimens.spaceMd))
                Text(
                    text = stringResource(R.string.unified_search_no_results_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(dimens.spaceSm))
                Text(
                    text = stringResource(R.string.search_no_results_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(dimens.spaceSm))
                Text(
                    text = "\"$query\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(dimens.spaceLg))
                Text(
                    text = if (isLoadingExplore) {
                        stringResource(R.string.unified_search_loading_more)
                    } else {
                        stringResource(R.string.unified_search_explore_more)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = Primary,
                    modifier = Modifier.clickable(enabled = !isLoadingExplore, onClick = onExploreMore)
                )
            }
        }

        if (exploreSuggestions.isNotEmpty()) {
            item(key = "empty_suggestions_header") {
                UnifiedSectionHeader(text = stringResource(R.string.unified_search_explore_suggestions))
            }
            item(key = "empty_suggestions_grid") {
                UnifiedTemplateGrid(
                    templates = exploreSuggestions,
                    onTemplateClick = onTemplateClick,
                    modifier = Modifier.padding(horizontal = dimens.spaceLg)
                )
            }
        }
    }
}
