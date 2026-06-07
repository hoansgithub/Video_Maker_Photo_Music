package com.videomaker.aimusic.modules.genretemplate

import androidx.compose.runtime.Composable
import com.videomaker.aimusic.R

@Composable
fun MediaPrivacyScreen(selectedId: String, onSelect: (String) -> Unit) {
    ChoiceScreen(
        titleRes = R.string.media_privacy_title,
        items = MEDIA_PRIVACY_ITEMS,
        selectedId = selectedId,
        onSelect = onSelect,
    )
}
