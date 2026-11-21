package app.hikari.extension.id.doujindesu

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class EncryptedPayloadDto(
    @SerialName("_enc_resp_") val encResp: String,
)

@Serializable
class DoujindesuMangaDto(
    val id: String = "",
    val title: String = "",
    val slug: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    val status: String? = null,
    val type: String? = null,
    val rating: Double = 0.0,
    val views: Long = 0L,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    @SerialName("alt_titles") val altTitles: String? = null,
    val chapters: List<DoujindesuChapterDto> = emptyList(),
    @SerialName("manga_genres") val mangaGenres: List<MangaGenreItemDto> = emptyList(),
)

@Serializable
class MangaGenreItemDto(
    val genres: DoujindesuGenreDto? = null,
)

@Serializable
class DoujindesuGenreDto(
    val id: Int = 0,
    val name: String = "",
    val slug: String = "",
)

@Serializable
class DoujindesuChapterDto(
    val id: String = "",
    @SerialName("chapter_number") val chapterNumber: Double = 0.0,
    val title: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("is_honeypot") val isHoneypot: Boolean = false,
)

@Serializable
class DoujindesuChapterDetailDto(
    val id: String = "",
    @SerialName("chapter_number") val chapterNumber: Double = 0.0,
    val title: String? = null,
    @SerialName("content_urls") val contentUrls: List<String> = emptyList(),
)
