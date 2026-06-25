package com.videomaker.aimusic.data.repository

import com.videomaker.aimusic.core.data.local.ApiCacheManager
import com.videomaker.aimusic.core.data.local.RegionProvider
import com.videomaker.aimusic.domain.model.TextFontPreset
import com.videomaker.aimusic.domain.repository.TextRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class TextRepositoryImpl(
    private val supabaseClient: SupabaseClient,
    private val apiCacheManager: ApiCacheManager,
    private val regionProvider: RegionProvider
) : TextRepository {

    companion object {
        private const val TABLE_TEXTS = "texts"
    }

    override suspend fun getFonts(): Result<List<TextFontPreset>> =
        withContext(Dispatchers.IO) {
            val cacheKey = "supabase_texts_fonts"

            // Try to get from cache first
            apiCacheManager.get<List<TextFontPreset>>(cacheKey)?.let {
                return@withContext Result.success(it)
            }

            try {
                val dtos = supabaseClient.from(TABLE_TEXTS)
                    .select {
                        filter {
                            eq("is_active", true)
                        }
                        order("sort_order", Order.ASCENDING)
                    }
                    .decodeList<TextDto>()

                val fonts = dtos.map { it.toDomain() }

                apiCacheManager.put(cacheKey, fonts)
                Result.success(fonts)
            } catch (_: Exception) {
                // Fallback to cache if expired
                apiCacheManager.getStale<List<TextFontPreset>>(cacheKey)?.let {
                    return@withContext Result.success(it)
                }

                Result.success(emptyList())
            }
        }
}

@Serializable
private data class TextDto(
    val id: String,
    val name: String,
    @SerialName("font_url") val fontUrl: String,
    @SerialName("font_path") val fontPath: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("thumbnail_path") val thumbnailPath: String? = null,
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("is_new") val isNew: Boolean = false,
    val geo: List<String> = emptyList()
) {
    fun toDomain(): TextFontPreset {
        return TextFontPreset(
            id = id,
            name = name,
            fontResId = null,
            fontUrl = fontUrl,
            fontPath = fontPath,
            thumbnailUrl = thumbnailUrl,
            thumbnailPath = thumbnailPath,
            isPremium = isPremium,
            isNew = isNew,
            geo = geo
        )
    }
}