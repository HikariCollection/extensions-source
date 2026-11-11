package app.hikari.multisrc.iken

import app.hikari.source.model.SChapter
import app.hikari.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import java.time.Instant

@Serializable
class SearchResponse(
    val posts: List<MangaDto> = emptyList(),
    val totalCount: Int = 0,
)

@Serializable
class MangaDetailResponse(
    val post: MangaDto,
)

@Serializable
class ChaptersResponse(
    val post: ChapterPostDto? = null,
    val totalChapterCount: Int = 0,
)

@Serializable
class ChapterPostDto(
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDetailResponse(
    val chapter: ChapterDetailDto,
)

@Serializable
class ChapterDetailDto(
    val images: List<ImageDto> = emptyList(),
)

@Serializable
class ImageDto(
    val url: String,
    val order: Int? = null,
)

@Serializable
class GenreDto(
    val id: Int,
    val name: String,
)

@Serializable
class MangaDto(
    val id: Int,
    val slug: String,
    private val postTitle: String,
    private val postContent: String? = null,
    private val content: String? = null,
    @SerialName("isNovel")
    private val rawIsNovel: Boolean = false,
    private val featuredImage: String? = null,
    private val alternativeTitles: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val seriesType: String? = null,
    private val seriesStatus: String? = null,
    private val genres: List<GenreDto> = emptyList(),
) {
    val isNovel: Boolean
        get() = rawIsNovel || seriesType.equals("novel", ignoreCase = true)

    fun toSManga(): SManga = SManga.create().apply {
        url = "/series/$slug"
        title = postTitle
        thumbnail_url = featuredImage
        this.author = this@MangaDto.author?.takeUnless { it.isBlank() }
        this.artist = this@MangaDto.artist?.takeUnless { it.isBlank() } ?: this.author
        description = getDescription(postContent ?: content)
        genre = buildList {
            when (seriesType?.uppercase()) {
                "MANGA" -> add("Manga")
                "MANHUA" -> add("Manhua")
                "MANHWA" -> add("Manhwa")
                else -> {}
            }
            genres.forEach { add(it.name) }
        }.distinct().joinToString()
        status = getStatus()
    }

    private fun getDescription(content: String?) = buildString {
        content?.takeUnless { it.isBlank() }?.let { desc ->
            append(Jsoup.parse(desc.replace("\n", "<br>")).text())
        }
        alternativeTitles?.takeUnless { it.isBlank() }?.let { alt ->
            if (isNotEmpty()) append("\n\n")
            append("Alternative Titles: ").append(alt)
        }
    }.trim()

    private fun getStatus() = when (seriesStatus?.uppercase()) {
        "ONGOING", "COMING_SOON", "MASS_RELEASED" -> SManga.ONGOING
        "COMPLETED" -> SManga.COMPLETED
        "CANCELLED", "DROPPED" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class ChapterDto(
    val id: Int,
    val slug: String,
    val number: JsonPrimitive,
    val title: String? = null,
    val createdAt: String,
    val isLocked: Boolean? = false,
    val isPermanentlyLocked: Boolean? = false,
    val price: Int? = 0,
) {
    fun toSChapter(seriesSlug: String): SChapter = SChapter.create().apply {
        val prefix = if (isLocked == true || isPermanentlyLocked == true || (price ?: 0) > 0) "🔒 " else ""
        val suffix = if (!title.isNullOrBlank()) " - $title" else ""
        name = "${prefix}Chapter $number$suffix"
        url = "/series/$seriesSlug/$slug#$id"
        date_upload = try {
            Instant.parse(createdAt).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}
