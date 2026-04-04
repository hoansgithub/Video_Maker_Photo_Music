package com.videomaker.aimusic.modules.favourite_templates

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.modules.projects.TemplateTabState
import com.videomaker.aimusic.ui.components.StaggeredGrid
import com.videomaker.aimusic.ui.components.TemplateCard
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.TextPrimary

@Composable
fun LikeTemplateEmpty(
    state: TemplateTabState,
    onSeeAllClick: () -> Unit,
    onSearch: () -> Unit,
    onTemplateClick: (String) -> Unit
) {
    val dimens = AppDimens.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        contentPadding = PaddingValues(
            bottom = dimens.space3Xl + dimens.space2Xl
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)
    ) {
        item {
            Column(
                modifier = Modifier
                    .padding(top = 40.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Empty box icon
                Image(
                    painter = painterResource(id = R.drawable.ic_empty_box),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Title
                Text(
                    text = stringResource(R.string.like_template_empty_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Subtitle
                Text(
                    text = stringResource(R.string.projects_empty_subtitle),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Create New Video Button
                Button(
                    onClick = onSearch,
                    modifier = Modifier
                        .height(56.dp)
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(160.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.like_template_search_button),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W500,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.like_template_section_header),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = onSeeAllClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "See all",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        when (state) {
            is TemplateTabState.Idle,
            is TemplateTabState.Loading -> {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is TemplateTabState.Success -> {
                if (state.templates.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.projects_tab_liked_template),
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    item(key = "template_grid", contentType = "grid") {
                        val aspectRatios = remember(state.templates) {
                            state.templates.map { parseTemplateAspectRatio(it.aspectRatio) }
                        }

                        StaggeredGrid(
                            itemCount = state.templates.size,
                            aspectRatios = aspectRatios,
                            columns = 2,
                            spacing = dimens.spaceSm,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)

                        ) { index ->
                            TemplateCard(
                                name = state.templates[index].name,
                                thumbnailPath = state.templates[index].thumbnailPath,
                                aspectRatio = aspectRatios[index],
                                isPremium = state.templates[index].isPremium,
                                useCount = state.templates[index].useCount,
                                onClick = { onTemplateClick(state.templates[index].id) }
                            )
                        }
                    }
                }
            }

            is TemplateTabState.Error -> {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
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