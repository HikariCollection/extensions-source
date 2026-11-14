package app.hikari.extension.en.ezmanga

import app.hikari.extension.core.tryParseInstant
import app.hikari.source.model.SChapter
import app.hikari.source.model.SManga
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
class EzMangaPageDto(
    val data: List<EzMangaSeriesDto> = emptyList(),
    val next: Int? = null,
)

@Serializable
class EzMangaSeriesDto(
    private val slug: String = "",
    private val title: String = "",
    private val cover: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = "/series/$slug"
        title = this@EzMangaSeriesDto.title.trim()
        thumbnail_url = cover?.takeIf { it.isNotBlank() }
    }
}

@Serializable
class EzMangaSeriesDetailDto(
    private val slug: String = "",
    private val title: String = "",
    private val alternativeTitles: String? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val cover: String? = null,
    private val type: String? = null,
    private val status: String? = null,
    private val genres: List<EzMangaGenreDto> = emptyList(),
) {
    fun toSManga(existing: SManga): SManga = existing.apply {
        if (url.isBlank() && slug.isNotBlank()) {
            url = "/series/$slug"
        }
        title.takeIf { this@EzMangaSeriesDetailDto.title.isNotBlank() }?.let {
            this.title = this@EzMangaSeriesDetailDto.title.trim()
        }
        cover?.takeIf { it.isNotBlank() }?.let { thumbnail_url = it }
        author = this@EzMangaSeriesDetailDto.author?.trim().orEmpty()
        artist = this@EzMangaSeriesDetailDto.artist?.trim().orEmpty()
        status = parseStatus(this@EzMangaSeriesDetailDto.status)

        val cleanDescription = this@EzMangaSeriesDetailDto.description
            ?.takeIf { it.isNotBlank() }
            ?.let { Jsoup.parse(it).text().trim() }
            .orEmpty()
        val altTitle = alternativeTitles?.trim()?.takeIf { it.isNotBlank() }
        description = buildString {
            append(cleanDescription)
            if (altTitle != null) {
                if (isNotEmpty()) append("\n\n")
                append("Alternative Title: ").append(altTitle)
            }
        }

        val genreNames = genres.mapNotNull { it.name.trim().takeIf(String::isNotBlank) }
        val seriesType = type?.trim()?.takeIf { it.isNotBlank() }
            ?.lowercase()
            ?.replaceFirstChar { it.uppercase() }
        genre = (listOfNotNull(seriesType) + genreNames).distinct().joinToString()
    }

    private fun parseStatus(raw: String?): Int = when (raw?.uppercase()) {
        "ONGOING" -> SManga.ONGOING
        "COMPLETED" -> SManga.COMPLETED
        "HIATUS", "ON_HIATUS" -> SManga.ON_HIATUS
        "CANCELLED", "DROPPED" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class EzMangaGenreDto(
    val name: String = "",
)

@Serializable
class EzMangaChapterListDto(
    val data: List<EzMangaChapterItemDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
class EzMangaChapterItemDto(
    private val slug: String = "",
    private val number: Double = 0.0,
    private val title: String? = null,
    private val createdAt: String? = null,
    private val requiresPurchase: Boolean = false,
    private val isFree: Boolean = true,
) {
    val isLocked: Boolean
        get() = requiresPurchase || !isFree

    fun toSChapter(seriesSlug: String): SChapter = SChapter.create().apply {
        url = "/series/$seriesSlug/$slug"
        val formattedNumber = number.toString().removeSuffix(".0")
        val cleanTitle = title?.trim()?.takeIf { it.isNotBlank() }
        name = if (cleanTitle != null) {
            "Chapter $formattedNumber - $cleanTitle"
        } else {
            "Chapter $formattedNumber"
        }
        chapter_number = number.toFloat()
        date_upload = tryParseInstant(createdAt)
    }
}

@Serializable
class EzMangaChapterDetailDto(
    val images: List<EzMangaImageDto> = emptyList(),
)

@Serializable
class EzMangaImageDto(
    val url: String = "",
    val order: Int = 0,
)
