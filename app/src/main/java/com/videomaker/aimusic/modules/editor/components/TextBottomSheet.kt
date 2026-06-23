package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentHeight
import com.videomaker.aimusic.ui.components.ProvideShimmerEffect
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.ads.RewardedAdPresenter
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.domain.model.TextFontPreset
import com.videomaker.aimusic.domain.model.TextOverlay
import com.videomaker.aimusic.domain.model.mockFontPresets
import com.videomaker.aimusic.modules.editor.TextOverlayViewModel
import com.videomaker.aimusic.ui.theme.EffectUnselectedBg
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.TextOnPrimary
import com.videomaker.aimusic.ui.theme.White16
import org.koin.compose.koinInject
import com.videomaker.aimusic.core.analytics.Analytics

/**
 * TextBottomSheet - Sliding inline sheet for customizing text overlays.
 * Aligns layout structure and swiping behaviors to EffectSetBottomSheet.
 */
@Composable
fun TextBottomSheet(
    viewModel: TextOverlayViewModel,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    focusTrigger: Long = 0L,
    modifier: Modifier = Modifier
) {
    val textOverlays by viewModel.textOverlays.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedTextOverlayId.collectAsStateWithLifecycle()
    val unlockedFontIds by viewModel.unlockedFontIds.collectAsStateWithLifecycle()
    val shouldPresentAd by viewModel.shouldPresentFontAd.collectAsStateWithLifecycle()
    val fontAdError by viewModel.fontAdError.collectAsStateWithLifecycle()
    val fontPresets by viewModel.fontPresets.collectAsStateWithLifecycle()
    val isFontsLoading by viewModel.isFontsLoading.collectAsStateWithLifecycle()
    val downloadedFontIds by viewModel.downloadedFontIds.collectAsStateWithLifecycle()

    val adsLoaderService = koinInject<AdsLoaderService>()
    val pleaseEnterText =
        androidx.compose.ui.res.stringResource(id = R.string.text_overlay_placeholder)

    // Helper: if the selected overlay has no real text (empty or still placeholder),
    // remove it so closing the sheet without typing leaves no stale overlay.
    // Also remove it if the user has not modified any properties (text, color, font, zoom/rotate).
    fun removeIfEmpty() {
        val id = selectedId ?: return
        val overlay = textOverlays.find { it.id == id } ?: return
        val isDefaultColor = overlay.color == 0xFFFFFFFFL
        val defaultFontId = fontPresets.firstOrNull()?.id ?: "neue_haas_regular"
        val isDefaultFont = overlay.fontId == defaultFontId
        val isDefaultScaleRotation = overlay.scale == 1.0f && overlay.rotation == 0.0f

        if (overlay.text.trim().isEmpty() ||
            (overlay.text == pleaseEnterText && isDefaultColor && isDefaultFont && isDefaultScaleRotation)
        ) {
            viewModel.removeTextOverlay(id)
        }
    }

    TextBottomSheetContent(
        textOverlays = textOverlays,
        selectedId = selectedId,
        unlockedFontIds = unlockedFontIds,
        shouldPresentAd = shouldPresentAd,
        fontAdError = fontAdError,
        adsLoaderService = adsLoaderService,
        fontPresets = fontPresets,
        isFontsLoading = isFontsLoading,
        downloadedFontIds = downloadedFontIds,
        onUpdateText = { id, text -> viewModel.updateTextOverlay(id, text = text) },
        onRemoveText = viewModel::removeTextOverlay,
        onUpdateColor = { id, color -> viewModel.updateTextOverlay(id, color = color) },
        onUpdateFont = { id, fontId -> viewModel.updateTextOverlay(id, fontId = fontId) },
        onFontClick = { fontPreset, onUnlockSuccess ->
            viewModel.onFontClick(fontPreset) {
                viewModel.downloadFontIfNeeded(fontPreset)
                onUnlockSuccess()
            }
        },
        onFontRewardEarned = viewModel::onFontRewardEarned,
        onFontAdFailed = viewModel::onFontAdFailed,
        clearFontAdError = viewModel::clearFontAdError,
        onDismiss = {
            // Remove overlay if user dismissed without entering real text
            removeIfEmpty()
            onDismiss()
        },
        onConfirm = {
            // Remove overlay if user confirmed without entering real text
            removeIfEmpty()
            onConfirm()
        },
        focusTrigger = focusTrigger,
        modifier = modifier
    )
}

/**
 * TextBottomSheetContent - Stateless UI content matching the design in screenshot.
 */
@Composable
fun TextBottomSheetContent(
    textOverlays: List<TextOverlay>,
    selectedId: String?,
    unlockedFontIds: Set<String>,
    shouldPresentAd: Boolean,
    fontAdError: String?,
    adsLoaderService: AdsLoaderService?,
    fontPresets: List<TextFontPreset>,
    isFontsLoading: Boolean,
    downloadedFontIds: Set<String>,
    onUpdateText: (String, String) -> Unit,
    onRemoveText: (String) -> Unit,
    onUpdateColor: (String, Long) -> Unit,
    onUpdateFont: (String, String) -> Unit,
    onFontClick: (TextFontPreset, () -> Unit) -> Unit,
    onFontRewardEarned: () -> Unit,
    onFontAdFailed: () -> Unit,
    clearFontAdError: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    focusTrigger: Long = 0L,
    modifier: Modifier = Modifier
) {
    val selectedOverlay = remember(textOverlays, selectedId) {
        textOverlays.find { it.id == selectedId }
    }

    val focusManager = LocalFocusManager.current
    // Must match the string used in EditorScreen.addTextOverlay(defaultText)
    val pleaseEnterText = stringResource(id = R.string.text_overlay_placeholder)

    // Reusing rewarded ad controller for unlocking fonts
    if (adsLoaderService != null) {
        RewardedAdPresenter(
            shouldPresent = shouldPresentAd,
            placement = AdPlacement.REWARD_UNLOCK_EFFECT_SET,
            adsLoaderService = adsLoaderService,
            onRewardEarned = onFontRewardEarned,
            onAdFailed = onFontAdFailed
        )
    }

    LaunchedEffect(fontAdError) {
        if (fontAdError != null) {
            clearFontAdError()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(SplashBackground)
    ) {
        val sheetHeight = maxHeight

        val isKeyboardOpen = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isKeyboardOpen) Modifier.wrapContentHeight() else Modifier.fillMaxSize()
                )
                .padding(horizontal = 16.dp)
        ) {
            if (selectedOverlay != null) {
                // Text input row
                var typedText by remember(selectedOverlay.id) {
                    // Start with empty string when text equals the placeholder so the
                    // placeholder shows as a hint, not as actual entered text
                    val initialText =
                        if (selectedOverlay.text == pleaseEnterText) "" else selectedOverlay.text
                    mutableStateOf(
                        TextFieldValue(
                            text = initialText,
                            selection = TextRange(initialText.length)
                        )
                    )
                }
                val focusRequester = remember { FocusRequester() }
                var isTextFieldFocused by remember { mutableStateOf(false) }

                LaunchedEffect(selectedOverlay.id, focusTrigger) {
                    if (focusTrigger > 0L || selectedOverlay != null) {
                        focusRequester.requestFocus()
                    }
                }

                val density = LocalDensity.current
                val keyboardHeight = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

                val translationYPx = remember(keyboardHeight, sheetHeight, isKeyboardOpen) {
                    if (isKeyboardOpen) {
                        0f
                    } else {
                        val requiredShift = keyboardHeight - sheetHeight + 8.dp
                        if (requiredShift > 0.dp) {
                            with(density) { -requiredShift.toPx() }
                        } else {
                            0f
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            this.translationY = translationYPx
                        }
                        .background(SplashBackground)
                        .padding(bottom = 12.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = typedText,
                        onValueChange = {
                            typedText = it
                            if (it.text.trim().isEmpty()) {
                                onUpdateText(selectedOverlay.id, pleaseEnterText)
                            } else {
                                onUpdateText(selectedOverlay.id, it.text)
                            }
                        },
                        placeholder = {
                            Text(
                                stringResource(R.string.text_overlay_placeholder),
                                color = TextSecondary
                            )
                        },
                        maxLines = 1,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = EffectUnselectedBg,
                            unfocusedContainerColor = EffectUnselectedBg,
                            focusedBorderColor = White16,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .height(50.dp)
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                isTextFieldFocused = focusState.isFocused
                                if (focusState.isFocused) {
                                    typedText =
                                        typedText.copy(selection = TextRange(typedText.text.length))
                                }
                                // Do NOT fill field with pleaseEnterText on focus loss —
                                // empty field stays empty so the placeholder remains a hint
                            },
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(Modifier.width(12.dp))

                    if (isTextFieldFocused) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.text_overlay_desc_clear_focus),
                            tint = Primary,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable {
                                    // Always just clear focus — if text is empty the sheet stays
                                    // open and user can continue typing or close manually
                                    focusManager.clearFocus()
                                }
                                .padding(6.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Primary)
                                .clickable {
                                    onConfirm()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.text_overlay_desc_confirm),
                                tint = TextOnPrimary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }

                if (!isKeyboardOpen) {
                    Spacer(Modifier.height(14.dp))

                    // Color picker section
                    Text(
                        text = stringResource(R.string.text_overlay_color_header),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )

                    Spacer(Modifier.height(8.dp))

                    val neutralColors = listOf(
                        0xFFFFFFFFL, // White
                        0xFF2979FFL, // Blue
                        0xFFFF80ABL, // Pink
                        0xFF404040L, // Charcoal
                        0xFF00E676L, // Green
                        0xFFFFD600L, // Yellow
                        0xFFAB47BCL, // Purple
                        0xFFE8D5B7L, // Beige
                        0xFF1A1A1AL, // Near Black
                        0xFF29B6F6L, // Light Blue
                        0xFFFF1744L, // Red
                        0xFFC6FF00L, // Lime
                        0xFFD4A57AL, // Tan
                        0xFF5C6BC0L, // Indigo
                        0xFFFF9100L, // Orange
                        0xFFE8E8E8L, // Light Gray
                        0xFF69F0AEL, // Mint
                        0xFFFF4081L, // Hot Pink
                        0xFFB0B0B0L, // Gray
                        0xFFCE93D8L, // Lavender
                        0xFFFFE57FL, // Gold
                        0xFF00E5FFL, // Cyan
                        0xFF787878L, // Dark Gray
                        0xFFE67E22L, // Amber
                        0xFFFF6D00L, // Deep Orange
                        0xFFF5F0E8L, // Cream
                        0xFF1DE9B6L, // Teal
                        0xFFFF5252L, // Coral Red
                        0xFF000000L, // Black
                        0xFFFFF176L, // Light Yellow
                    )

                    val colorGroups = listOf(neutralColors)

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        colorGroups.forEach { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                group.forEach { colorValue ->
                                    val color = Color(colorValue)
                                    val isColorSelected = selectedOverlay.color == colorValue

                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isColorSelected) 2.5.dp else 1.dp,
                                                color = if (isColorSelected) {
                                                    if (color == Color.White || color == Color(
                                                            0xFFF5F0E8L
                                                        ) || color == Color(0xFFE8E8E8L)
                                                    ) Color.Black else Color.White
                                                } else {
                                                    Color.White.copy(alpha = 0.2f)
                                                },
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                val currentFont = fontPresets.find { it.id == selectedOverlay.fontId }
                                                val isPremium = currentFont?.isPremium ?: false
                                                val fontType = if (isPremium) "ads" else "free"
                                                val colorName = getColorName(colorValue)
                                                Analytics.trackTextColorClick(type = fontType, colorName = colorName)
                                                onUpdateColor(selectedOverlay.id, colorValue)
                                            }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Font selection section
                    Text(
                        text = stringResource(R.string.text_overlay_font_header),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )

                    Spacer(Modifier.height(8.dp))

                    if (isFontsLoading) {
                        ProvideShimmerEffect {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                items(6) {
                                    ShimmerPlaceholder(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp),
                                        cornerRadius = 12.dp
                                    )
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(
                                items = fontPresets,
                                key = { it.id }
                            ) { fontPreset ->
                                val isFirstFont = fontPresets.firstOrNull()?.id == fontPreset.id
                                val isFontSelected = selectedOverlay.fontId == fontPreset.id ||
                                        (selectedOverlay.fontId == "system_default" && fontPreset.id == (fontPresets.firstOrNull()?.id ?: "neue_haas_regular"))
                                val isLocked = !isFirstFont &&
                                    fontPreset.isPremium && !unlockedFontIds.contains(fontPreset.id)
                                val showDownloadLabel = !fontPreset.isNew &&
                                        !isLocked &&
                                        fontPreset.fontResId == null &&
                                        !downloadedFontIds.contains(fontPreset.id)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isFontSelected) Color.Transparent else EffectUnselectedBg)
                                        .border(
                                            width = if (isFontSelected) 2.dp else 1.dp,
                                            color = if (isFontSelected) Color.White else EffectUnselectedBg,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            val fontType = if (fontPreset.isPremium && !isFirstFont) "ads" else "free"
                                            if (showDownloadLabel) {
                                                Analytics.trackTextFontDownload(type = fontType, fontName = fontPreset.name)
                                            } else {
                                                Analytics.trackTextFontClick(type = fontType, fontName = fontPreset.name)
                                            }
                                            onFontClick(fontPreset) {
                                                onUpdateFont(selectedOverlay.id, fontPreset.id)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val fullThumbnailUrl = remember(fontPreset) {
                                        if (fontPreset.thumbnailUrl != null && fontPreset.thumbnailPath != null) {
                                            fontPreset.thumbnailUrl + fontPreset.thumbnailPath
                                        } else null
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        if (fullThumbnailUrl != null) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(32.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                AsyncImage(
                                                    model = fullThumbnailUrl,
                                                    contentDescription = fontPreset.name,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxHeight()
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = fontPreset.name,
                                                fontFamily = fontPreset.fontFamily,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.W500,
                                                color = if (isFontSelected) Color.White else TextPrimary,
                                                textAlign = TextAlign.Start,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        if (isFontSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .background(Primary, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = TextOnPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        } else if (showDownloadLabel) {
                                            Image(
                                                painter = painterResource(id = R.drawable.ic_download),
                                                contentDescription = "Download",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Badges
                                    if (fontPreset.isNew) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_new_item),
                                            contentDescription = stringResource(R.string.text_overlay_desc_new_item),
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(top = 4.dp, start = 4.dp)
                                                .size(18.dp)
                                        )
                                    }

                                    if (isLocked) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_ads),
                                            contentDescription = stringResource(R.string.text_overlay_desc_ad_required),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(bottom = 4.dp, end = 4.dp)
                                                .size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                            .size(32.dp)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = stringResource(R.string.close),
                            tint = TextPrimary,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.text_overlay_no_selection),
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Preview(
    name = "Text Bottom Sheet - Selected State",
    showBackground = true,
    backgroundColor = 0xFF1A1A1A
)
@Composable
private fun TextBottomSheetSelectedPreview() {
    val mockOverlays = listOf(
        TextOverlay(
            id = "1",
            text = "Hello Video Maker",
            color = 0xFFFFD600L,
            fontId = "2",
            xPercentage = 0.5f,
            yPercentage = 0.5f,
            scale = 1f,
            rotation = 0f
        )
    )
    TextBottomSheetContent(
        textOverlays = mockOverlays,
        selectedId = "1",
        unlockedFontIds = setOf("1", "2"),
        shouldPresentAd = false,
        fontAdError = null,
        adsLoaderService = null,
        fontPresets = mockFontPresets,
        isFontsLoading = false,
        downloadedFontIds = emptySet(),
        onUpdateText = { _, _ -> },
        onRemoveText = { _ -> },
        onUpdateColor = { _, _ -> },
        onUpdateFont = { _, _ -> },
        onFontClick = { _, cb -> cb() },
        onFontRewardEarned = {},
        onFontAdFailed = {},
        clearFontAdError = {},
        onDismiss = {},
        onConfirm = {}
    )
}

@Preview(
    name = "Text Bottom Sheet - Empty State",
    showBackground = true,
    backgroundColor = 0xFF1A1A1A
)
@Composable
private fun TextBottomSheetEmptyPreview() {
    TextBottomSheetContent(
        textOverlays = emptyList(),
        selectedId = null,
        unlockedFontIds = emptySet(),
        shouldPresentAd = false,
        fontAdError = null,
        adsLoaderService = null,
        fontPresets = mockFontPresets,
        isFontsLoading = false,
        downloadedFontIds = emptySet(),
        onUpdateText = { _, _ -> },
        onRemoveText = { _ -> },
        onUpdateColor = { _, _ -> },
        onUpdateFont = { _, _ -> },
        onFontClick = { _, _ -> },
        onFontRewardEarned = {},
        onFontAdFailed = {},
        clearFontAdError = {},
        onDismiss = {},
        onConfirm = {}
    )
}

private fun getColorName(colorValue: Long): String {
    return when (colorValue) {
        0xFFFFFFFFL -> "white"
        0xFF2979FFL -> "blue"
        0xFFFF80ABL -> "pink"
        0xFF404040L -> "charcoal"
        0xFF00E676L -> "green"
        0xFFFFD600L -> "yellow"
        0xFFAB47BCL -> "purple"
        0xFFE8D5B7L -> "beige"
        0xFF1A1A1AL -> "near_black"
        0xFF29B6F6L -> "light_blue"
        0xFFFF1744L -> "red"
        0xFFC6FF00L -> "lime"
        0xFFD4A57AL -> "tan"
        0xFF5C6BC0L -> "indigo"
        0xFFFF9100L -> "orange"
        0xFFE8E8E8L -> "light_gray"
        0xFF69F0AEL -> "mint"
        0xFFFF4081L -> "hot_pink"
        0xFFB0B0B0L -> "gray"
        0xFFCE93D8L -> "lavender"
        0xFFFFE57FL -> "gold"
        0xFF00E5FFL -> "cyan"
        0xFF787878L -> "dark_gray"
        0xFFE67E22L -> "amber"
        0xFFFF6D00L -> "deep_orange"
        0xFFF5F0E8L -> "cream"
        0xFF1DE9B6L -> "teal"
        0xFFFF5252L -> "coral_red"
        0xFF000000L -> "black"
        0xFFFFF176L -> "light_yellow"
        else -> String.format("#%08X", colorValue)
    }
}
