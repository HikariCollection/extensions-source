package app.hikari.extension.en.flamecomics

import app.hikari.source.model.Page
import app.hikari.source.model.SChapter
import app.hikari.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup

@Serializable
class BuildIdDto(
    val buildId: String,
)

@Serializable
class NextDataDto<T>(
    val pageProps: T,
)

@Serializable
class BrowseDto(
    val series: List<SeriesDto> = emptyList(),
)

@Serializable
class LatestDto(
    val allSeries: List<SeriesDto> = emptyList(),
)

@Serializable
class SeriesPageDto(
    val series: SeriesDto = SeriesDto(),
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterPageDto(
    val chapter: ChapterImagesDto = ChapterImagesDto(),
)

@Serializable
class SeriesDto(
    val title: String = "",
    private val altTitles: List<String>? = null,
    private val description: String? = null,
    private val cover: String = "",
    private val type: String = "",
    private val tags: List<String>? = null,
    private val author: List<String>? = null,
    private val artist: List<String>? = null,
    private val status: String = "",
    @SerialName("series_id") private val seriesId: Int? = null,
    @SerialName("last_edit") private val lastEdit: Long = 0L,
    val popularityRank: Int? = null,
) {
    fun toSManga(): SManga? = seriesId?.let(::toSManga)

    private fun toSManga(id: Int) = SManga.create().apply {
        url = "/series/$id"
        title = this@SeriesDto.title
        thumbnail_url = imagesUrl(id).apply {
            addPathSegment(cover)
            addQueryParameter(lastEdit.toString(), null)
            fragment(THUMBNAIL_FRAGMENT)
        }.build().toString()
    }

    fun toSMangaDetails() = toSManga(seriesId!!).apply {
        val synopsis = this@SeriesDto.description
            ?.let { Jsoup.parseBodyFragment(it).wholeText() }
            .orEmpty()
        val altNames = altTitles.orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        description = buildString {
            append(synopsis)
            if (altNames.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternative Names:")
                altNames.forEach { name -> append("\n- $name") }
            }
        }.takeIf { it.isNotEmpty() }

        genre = (listOf(type) + tags.orEmpty()).distinct().joinToString()
        author = this@SeriesDto.author?.joinToString()
        artist = this@SeriesDto.artist?.joinToString()
        status = when (this@SeriesDto.status.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "dropped" -> SManga.CANCELLED
            "hiatus" -> SManga.ON_HIATUS
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    private val chapter: Double = 0.0,
    private val title: String? = null,
    @SerialName("release_date") private val releaseDate: Long = 0L,
    @SerialName("series_id") private val seriesId: Int = 0,
    private val token: String = "",
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/series/$seriesId/$token"
        chapter_number = chapter.toFloat()
        date_upload = releaseDate * 1000
        name = buildString {
            append("Chapter ${chapter.toString().removeSuffix(".0")}")
            if (!this@ChapterDto.title.isNullOrBlank()) {
                append(" - ${this@ChapterDto.title}")
            }
        }
    }
}

@Serializable
class ChapterImagesDto(
    @SerialName("release_date") private val releaseDate: Long = 0L,
    @SerialName("series_id") private val seriesId: Int = 0,
    private val token: String = "",
    private val images: Map<String, ImageDto> = emptyMap(),
) {
    fun toPages() = images.values.mapIndexed { index, image ->
        Page(
            index,
            imageUrl = imagesUrl(seriesId).apply {
                addPathSegment(token)
                addPathSegment(image.name)
                addQueryParameter(releaseDate.toString(), null)
            }.build().toString(),
        )
    }
}

@Serializable
class ImageDto(
    val name: String = "",
)

private fun imagesUrl(seriesId: Int): HttpUrl.Builder = "https://cdn.flamecomics.xyz/uploads/images/series".toHttpUrl().newBuilder()
    .addPathSegment(seriesId.toString())
