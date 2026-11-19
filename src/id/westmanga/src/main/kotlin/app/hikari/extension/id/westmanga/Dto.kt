package app.hikari.extension.id.westmanga

import app.hikari.source.model.SChapter
import app.hikari.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
class Data<T>(
    val data: T,
)

@Serializable
class PaginatedData<T>(
    val data: List<T>,
    val paginator: Paginator,
)

@Serializable
class Paginator(
    @SerialName("current_page") private val current: Int,
    @SerialName("last_page") private val last: Int,
) {
    fun hasNextPage(): Boolean = current < last
}

@Serializable
class BrowseManga(
    private val title: String,
    private val slug: String,
    private val cover: String? = null,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        url = "/manga/$slug/"
        title = this@BrowseManga.title
        thumbnail_url = this@BrowseManga.cover?.let {
            if (it.startsWith("http")) it else "$baseUrl$it"
        }
    }
}

@Serializable
class Manga(
    private val title: String,
    private val slug: String,
    @SerialName("alternative_name") private val alternativeName: String? = null,
    @SerialName("sinopsis") private val synopsis: String? = null,
    private val cover: String? = null,
    private val author: String? = null,
    @SerialName("country_id") private val country: String? = null,
    private val status: String? = null,
    private val color: Boolean? = null,
    private val genres: List<Genre> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        url = "/manga/$slug/"
        title = this@Manga.title
        thumbnail_url = this@Manga.cover?.let {
            if (it.startsWith("http")) it else "$baseUrl$it"
        }
        author = this@Manga.author
        status = when (this@Manga.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        genre = buildList {
            when (this@Manga.country) {
                "JP" -> add("Manga")
                "CN" -> add("Manhua")
                "KR" -> add("Manhwa")
            }
            if (this@Manga.color == true) {
                add("Colored")
            }
            this@Manga.genres.forEach { add(it.name) }
        }.joinToString()

        description = buildString {
            this@Manga.synopsis?.let {
                append(Jsoup.parseBodyFragment(it, baseUrl).text())
            }
            this@Manga.alternativeName?.let {
                if (isNotEmpty()) append("\n\n")
                append("Alternative Name: ")
                append(it.trim())
            }
        }
    }
}

@Serializable
class Genre(
    val name: String,
)

@Serializable
class Chapter(
    private val slug: String,
    private val number: String,
    @SerialName("updated_at") private val updatedAt: Time,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = "/$slug/"
        name = "Chapter $number"
        date_upload = updatedAt.time * 1000
    }
}

@Serializable
class Time(
    val time: Long,
)

@Serializable
class ImageList(
    val images: List<String>,
)

@Serializable
class VisitTokenResponse(
    val status: Boolean = false,
    val token: String? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
)
