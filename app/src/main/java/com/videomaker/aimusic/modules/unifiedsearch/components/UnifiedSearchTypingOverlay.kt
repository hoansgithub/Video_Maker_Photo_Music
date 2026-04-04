package com.videomaker.aimusic.modules.unifiedsearch.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.AppFilterChip
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.TextSecondary

@Composable
fun UnifiedSearchTypingOverlay(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit
) {
    val dimens = AppDimens.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = dimens.spaceMd)
    ) {
        item(key = "suggestions_header") {
            Text(
                text = stringResource(R.string.search_suggestions),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = dimens.spaceLg)
            )
            Spacer(modifier = Modifier.height(dimens.spaceSm))
        }

        if (suggestions.isEmpty()) {
            item(key = "suggestions_empty") {
                Text(
                    text = stringResource(R.string.unified_search_keep_typing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = dimens.spaceLg)
                )
            }
        } else {
            item(key = "suggestions_chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = dimens.spaceLg),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
                ) {
                    items(suggestions, key = { it }) { suggestion ->
                        AppFilterChip(
                            text = suggestion,
                            onClick = { onSuggestionClick(suggestion) }
                        )
                    }
                }
            }

            items(suggestions, key = { "suggestion_row_$it" }) { suggestion ->
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .clickable { onSuggestionClick(suggestion) }
                        .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd)
                )
            }
        }
    }
}
