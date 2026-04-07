package com.videomaker.aimusic.modules.unifiedsearch.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Neutral_Black
import com.videomaker.aimusic.ui.theme.Neutral_N100
import com.videomaker.aimusic.ui.theme.Neutral_N600
import com.videomaker.aimusic.ui.theme.Neutral_N800
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SearchFieldBackground
import com.videomaker.aimusic.ui.theme.SearchFieldBorder
import com.videomaker.aimusic.ui.theme.TextTertiary

@Composable
fun UnifiedSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearQuery: () -> Unit,
    onBack: () -> Unit
) {
    val dimens = AppDimens.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box {

        // Optional gradient overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface.copy(0.8f),
                            MaterialTheme.colorScheme.surface.copy(0.3f),
                            Color.Transparent,
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(
                    start = 12.dp,
                    end = dimens.spaceLg,
                    top = dimens.spaceSm,
                    bottom = dimens.spaceMd
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = SearchFieldBackground,
                        shape = RoundedCornerShape(dimens.radiusXl)
                    )
                    .border(
                        width = 1.dp,
                        color = SearchFieldBorder,
                        shape = RoundedCornerShape(dimens.radiusXl)
                    )
                    .padding(horizontal = dimens.spaceMd, vertical = dimens.spaceMd)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {

                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_placeholder),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.W500,
                                color = TextTertiary
                            )
                        }

                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(Primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    onSearch()
                                    keyboardController?.hide()
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }

                    if (query.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(dimens.spaceXs))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = TextTertiary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickableSingle { onClearQuery() }
                        )
                    }

                    Spacer(modifier = Modifier.width(dimens.spaceSm))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .size(24.dp)
                            .clickableSingle(
                                enabled = query.isNotEmpty()
                            ) {
                                onSearch()
                                keyboardController?.hide()
                            }
                    )

                }
            }


            Text(
                text = stringResource(R.string.cancel),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W500,
                color = Neutral_N600,
                modifier = Modifier
                    .clickableSingle(
                        onClick = onBack
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
