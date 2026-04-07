package com.videomaker.aimusic.modules.picker

internal data class AssetPickerGridScrollState(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemOffset: Int = 0
)

internal data class AssetPickerSessionSnapshot(
    val permissionMode: PermissionMode,
    val assets: List<GalleryAsset>,
    val selectedUris: Set<String>,
    val selectedAlbumId: String,
    val gridScrollState: AssetPickerGridScrollState
)

internal object AssetPickerSessionCache {
    var snapshot: AssetPickerSessionSnapshot? = null
}
