package app.hikari.multisrc.heancms

import app.hikari.source.model.SChapter
import app.hikari.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Serializable
class HeanCmsQueryDto(
    val meta: HeanCmsMetaDto? = null,
    val data: List<HeanCmsSeriesDto> = emptyList(),
)

@Serializable
class HeanCmsMetaDto(
    val total: Int = 0,
    @SerialName("per_page") val perPage: Int = 0,
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("last_page") val lastPage: Int = 1,
    @SerialName("next_page_url") val nextPageUrl: String? = null,
)

@Serializable
class HeanCmsSeriesDto(
    val id: Int,
    val title: String,
    @SerialName("series_slug") val seriesSlug: String,
    val thumbnail: String? = null,
    val description: String? = null,
    @SerialName("series_type") val seriesType: String? = null,
    val status: String? = null,
    val author: String? = null,
    val studio: String? = null,
    @SerialName("alternative_names") val alternativeNames: String? = null,
    val tags: List<HeanCmsTagDto> = emptyList(),
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        url = "/series/$seriesSlug"
        title = this@HeanCmsSeriesDto.title.trim()
        thumbnail_url = thumbnail?.let { if (it.startsWith("http")) it else "$baseUrl/$it" }
        author = this@HeanCmsSeriesDto.author?.trim()
        artist = this@HeanCmsSeriesDto.studio?.trim()
        genre = tags.map { it.name.trim() }.filter { it.isNotBlank() }.distinct().joinToString()
        description = buildString {
            this@HeanCmsSeriesDto.description?.let { desc ->
                append(Jsoup.parseBodyFragment(desc).wholeText().trim())
            }
            alternativeNames?.let { alt ->
                val cleanedAlt = alt.trim()
                if (cleanedAlt.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Alternative: ").append(cleanedAlt)
                }
            }
        }.ifEmpty { null }
        status = when (this@HeanCmsSeriesDto.status?.lowercase()?.trim()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus", "on hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class HeanCmsTagDto(
    val id: Int? = null,
    val name: String,
)

@Serializable
class HeanCmsChapterQueryDto(
    val meta: HeanCmsMetaDto? = null,
    val data: List<HeanCmsChapterItemDto> = emptyList(),
)

@Serializable
class HeanCmsChapterItemDto(
    val id: Int,
    @SerialName("chapter_name") val chapterName: String,
    @SerialName("chapter_title") val chapterTitle: String? = null,
    @SerialName("chapter_slug") val chapterSlug: String,
    val price: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toSChapter(seriesSlug: String): SChapter = SChapter.create().apply {
        url = "/chapter/$seriesSlug/$chapterSlug"
        name = buildString {
            if (price > 0) append("🔒 ")
            append(chapterName.trim())
            chapterTitle?.let { title ->
                val trimmed = title.trim()
                if (trimmed.isNotBlank()) append(" - ").append(trimmed)
            }
        }
        date_upload = parseDate(createdAt)
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            OffsetDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}

@Serializable
class HeanCmsChapterDetailsDto(
    val chapter: HeanCmsChapterPayloadDto,
)

@Serializable
class HeanCmsChapterPayloadDto(
    val id: Int? = null,
    @SerialName("chapter_name") val chapterName: String? = null,
    @SerialName("chapter_data") val chapterData: HeanCmsChapterDataDto? = null,
    val price: Int = 0,
)

@Serializable
class HeanCmsChapterDataDto(
    val images: List<String> = emptyList(),
)
