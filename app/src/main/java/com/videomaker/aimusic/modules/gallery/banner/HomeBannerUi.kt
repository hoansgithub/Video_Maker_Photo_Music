package com.videomaker.aimusic.modules.gallery.banner

import androidx.compose.runtime.Immutable
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.VideoTemplate

/**
 * A resolved home-banner ready (or loading) for display.
 *
 * [position] is the 0-based index in the remote-config list, used for tracking. Content
 * ([template] / [song]) is `null` while the get-by-id call is in flight (shimmer state).
 */
@Immutable
sealed interface HomeBannerUi {
    val position: Int
    val isLoading: Boolean

    data class TemplateBanner(
        override val position: Int,
        val style: BannerTemplate,
        val template: VideoTemplate?,
    ) : HomeBannerUi {
        override val isLoading: Boolean get() = template == null
    }

    data class SongBanner(
        override val position: Int,
        val style: BannerSong,
        val song: MusicSong?,
    ) : HomeBannerUi {
        override val isLoading: Boolean get() = song == null
    }
}
