package com.videomaker.aimusic.modules.gallery.banner

import androidx.compose.runtime.Immutable
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.VideoTemplate

/**
 * A resolved home-banner ready (or loading) for display.
 *
 * [position] is the 0-based index in the remote-config list, used for tracking. Content
 * ([template] / [song]) is `null` while the get-by-id call is in flight (shimmer state).
 *
 * [isPlaceholder] marks a banner whose content is a local stand-in (random item from the bundled
 * JSON library) shown only while the real remote id resolves. Such banners render the visual but
 * must not be interactive (clicks / inline play), since the underlying item is not the real one.
 *
 * [placeholderImageUrl] is an offline `file:///android_asset` thumbnail/cover from the bundled
 * content. It is shown beneath the real (remote) image so that on a slow / no network connection
 * the banner still displays a local image until the remote thumbnail/cover finishes downloading.
 *
 * [placeholderVideoUrl] (template only) is the bundled offline `.mp4` preview of the local stand-in.
 * On the current page it is played (muted, looping) on top of the static thumbnail so the banner
 * animates like a template card while the remote thumbnail is still loading.
 */
@Immutable
sealed interface HomeBannerUi {
    val position: Int
    val isLoading: Boolean
    val isPlaceholder: Boolean
    val placeholderImageUrl: String?

    data class TemplateBanner(
        override val position: Int,
        val style: BannerTemplate,
        val template: VideoTemplate?,
        override val isPlaceholder: Boolean = false,
        override val placeholderImageUrl: String? = null,
        val placeholderVideoUrl: String? = null,
    ) : HomeBannerUi {
        override val isLoading: Boolean get() = template == null
    }

    data class SongBanner(
        override val position: Int,
        val style: BannerSong,
        val song: MusicSong?,
        override val isPlaceholder: Boolean = false,
        override val placeholderImageUrl: String? = null,
    ) : HomeBannerUi {
        override val isLoading: Boolean get() = song == null
    }
}
