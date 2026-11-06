package app.hikari.multisrc.natsuid

import app.hikari.extension.core.toJsonString
import app.hikari.source.model.SManga
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

@Serializable
class Term(
    val name: String,
    val slug: String,
    val taxonomy: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class Manga(
    val id: Int,
    val slug: String,
    val title: Rendered,
    val content: Rendered,
    @JsonNames("metadata")
    val meta: MangaMeta? = null,
    @SerialName("_embedded")
    val embedded: Embedded,
    @SerialName("yoast_head_json")
    val yoast: Yoast? = null,
) {
    val isNovel get() = embedded.getTerms("type").contains("Novel")

    fun toSManga(): SManga = SManga.create().apply {
        url = MangaUrl(id, slug).toJsonString()
        title = Parser.unescapeEntities(this@Manga.title.rendered, false)
        val mainDescription = Jsoup.parseBodyFragment(content.rendered).wholeText().trim()
        val altTitles = meta?.meta?.alternativeTitle
            ?.split(",")
            ?.map { Parser.unescapeEntities(it.trim(), false) }
            ?.filter { it.isNotBlank() && !it.equals(title, ignoreCase = true) }
            ?.distinct()
            .orEmpty()

        description = buildString {
            append(mainDescription)
            if (altTitles.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternative Names:\n")
                append(altTitles.joinToString("\n") { "- $it" })
            }
        }.trim()

        thumbnail_url = embedded.featuredMedia?.firstOrNull()?.sourceUrl
            ?: yoast?.ogImage?.firstOrNull()?.url
        author = embedded.getTerms("series-author").joinToString()
        artist = embedded.getTerms("artist").joinToString()
        genre = buildSet {
            addAll(embedded.getTerms("genre"))
            addAll(embedded.getTerms("type"))
        }.joinToString()
        status = with(embedded.getTerms("status")) {
            when {
                contains("Ongoing") -> SManga.ONGOING
                contains("Completed") -> SManga.COMPLETED
                contains("Cancelled") -> SManga.CANCELLED
                contains("On Hiatus") -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
        memo = buildJsonObject {
            put("id", id)
        }
        initialized = true
    }
}

@Serializable
class MangaMeta(
    val meta: InnerMeta? = null,
)

@Serializable
class InnerMeta(
    @SerialName("alternative_title")
    val alternativeTitle: String? = null,
)

@Serializable
class Embedded(
    @SerialName("wp:featuredmedia")
    val featuredMedia: List<FeaturedMedia>? = null,
    @SerialName("wp:term")
    val terms: List<List<Term>> = emptyList(),
) {
    fun getTerms(type: String): List<String> = terms.find { it.getOrNull(0)?.taxonomy == type }?.map { it.name } ?: emptyList()
}

@Serializable
class FeaturedMedia(
    @SerialName("source_url")
    val sourceUrl: String? = null,
)

@Serializable
class Yoast(
    @SerialName("og_image")
    val ogImage: List<OgImage>? = null,
)

@Serializable
class OgImage(
    val url: String,
)

@Serializable
class Rendered(
    val rendered: String,
)

@Serializable
class MangaUrl(
    val id: Int,
    val slug: String,
)
