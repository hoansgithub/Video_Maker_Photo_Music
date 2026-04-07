package com.videomaker.aimusic.modules.unifiedsearch.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.ProvideShimmerEffect
import com.videomaker.aimusic.ui.components.ShimmerBox
import com.videomaker.aimusic.ui.components.SongListItemPlaceholder
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.FoundationBlack_100
import com.videomaker.aimusic.ui.theme.FoundationBlack_Gray_100

@Composable
fun UnifiedSearchLoadingContent(
    query: String = "",
    relatedSearches: List<String> = emptyList(),
    onRelatedSearchClick: (String) -> Unit = {}
) {
    val dimens = AppDimens.current

    ProvideShimmerEffect {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = dimens.spaceMd),
            userScrollEnabled = true  // Enable scrolling for related searches
        ) {
            item {
                Spacer(Modifier.height(100.dp))
            }

            // Related searches - same as Results
            if (relatedSearches.isNotEmpty()) {
                items(
                    items = relatedSearches,
                    key = { "related_loading_$it" }
                ) { suggestion ->
                    RelatedSearchRow(
                        query = query,
                        suggestion = suggestion,
                        onClick = { onRelatedSearchClick(suggestion) }
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Color.White,
                        strokeWidth = 4.dp,
                        trackColor = Color(0xFF4A4A4A)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = stringResource(R.string.search_loading_title),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W600,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.search_loading_message),
                        color = FoundationBlack_Gray_100,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
