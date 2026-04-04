package com.videomaker.aimusic.modules.unifiedsearch.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.AppFilterChip
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Neutral_N400
import com.videomaker.aimusic.ui.theme.Neutral_N500
import com.videomaker.aimusic.ui.theme.TextSecondary

@Composable
fun UnifiedSearchTypingOverlay(
    currentText: String,
    suggestions: List<String>,
    persistentRelatedSearches: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit
) {
    val dimens = AppDimens.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = dimens.spaceMd)
    ) {
        item {
            Spacer(Modifier.height(100.dp))
        }

        // Show persistent related keywords from previous search (if available)
        if (persistentRelatedSearches.isNotEmpty()) {
            items(
                items = persistentRelatedSearches,
                key = { "persistent_typing_$it" }
            ) { keyword ->
                RelatedSearchRow(
                    query = currentText,
                    suggestion = keyword,
                    onClick = { onSuggestionClick(keyword) }
                )
            }
        }
        if (suggestions.isNotEmpty()) {
            item(key = "suggestions_header") {
                Text(
                    text = "Result",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.W600,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(horizontal = dimens.spaceLg)
                )
                Spacer(modifier = Modifier.height(dimens.spaceSm))
            }

            items(suggestions, key = { "suggestion_row_$it" }) { suggestion ->
                val annotatedText = remember(suggestion, currentText) {
                    buildAnnotatedString {
                        val startIndex = suggestion.indexOf(currentText, ignoreCase = true)

                        if (suggestion.isNotEmpty() && startIndex >= 0) {
                            val endIndex = startIndex + currentText.length

                            append(suggestion)

                            addStyle(
                                style = SpanStyle(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                start = startIndex,
                                end = endIndex
                            )

                            addStyle(
                                style = SpanStyle(
                                    color = Color.Gray
                                ),
                                start = endIndex,
                                end = suggestion.length
                            )
                        } else {
                            append(suggestion)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionClick(suggestion) }
                        .padding(vertical = 8.dp, horizontal = 20.dp)
                ) {
                    Text(
                        text = annotatedText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_recommend_search),
                        tint = Neutral_N400,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
