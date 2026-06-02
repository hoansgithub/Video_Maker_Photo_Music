package com.videomaker.aimusic.modules.gallery.banner

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One entry in the remote-config `home_banner_list`.
 *
 * `id` references the real template/song to fetch by id, `style` (1 or 2) picks the UI variant,
 * and `name` is the title rendered on the banner.
 */
sealed interface HomeBannerConfigItem {
    val name: String
    val style: Int

    data class Template(
        val id: String,
        override val style: Int,
        override val name: String,
    ) : HomeBannerConfigItem

    data class Song(
        val id: Long,
        override val style: Int,
        override val name: String,
    ) : HomeBannerConfigItem
}

/**
 * Parses the `home_banner_list` remote-config JSON array.
 *
 * Follows the same lenient pattern as [com.videomaker.aimusic.core.popup.TrendingPopupConfig]:
 * malformed items are skipped, and a blank / malformed array yields an empty list so the caller
 * falls back to the legacy carousel.
 */
object HomeBannerConfigParser {

    private const val TYPE_TEMPLATE = "BannerTemplate"
    private const val TYPE_SONG = "BannerSong"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): List<HomeBannerConfigItem> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = json.parseToJsonElement(raw) as? JsonArray ?: return emptyList()
            array.mapNotNull { element ->
                runCatching { parseItem(element.jsonObject) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun parseItem(obj: kotlinx.serialization.json.JsonObject): HomeBannerConfigItem? {
        val type = obj["type"]?.jsonPrimitive?.content ?: return null
        val data = obj["data"]?.jsonObject ?: return null
        val name = data["name"]?.jsonPrimitive?.content ?: ""
        // Clamp to the two supported variants; anything else falls back to style 1.
        val style = (data["style"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1).let { if (it == 2) 2 else 1 }
        val idContent = data["id"]?.jsonPrimitive?.content ?: return null

        return when (type) {
            TYPE_TEMPLATE -> HomeBannerConfigItem.Template(id = idContent, style = style, name = name)
            TYPE_SONG -> {
                val songId = idContent.toLongOrNull() ?: return null
                HomeBannerConfigItem.Song(id = songId, style = style, name = name)
            }
            else -> null
        }
    }
}
