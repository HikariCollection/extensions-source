package app.hikari.multisrc.mangataro

import app.hikari.extension.core.*

import android.util.Log
import app.hikari.network.HttpException
import app.hikari.network.POST
import app.hikari.source.model.Filter
import app.hikari.source.model.FilterList
import app.hikari.source.model.MangasPage
import app.hikari.source.model.Page
import app.hikari.source.model.SChapter
import app.hikari.source.model.SManga
import app.hikari.source.model.SMangaUpdate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

abstract class MangaTaro(
    override val name: String = "",
    override val baseUrl: String = "",
    override val lang: String = "",
) : KeiSource() {

    override val supportsLatest: Boolean = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    protected open val apiHeaders: Headers
        get() = headersBuilder()
            .add("Accept", "application/json")
            .add("Referer", "$baseUrl/")
            .build()

    // ========================= Popular =========================
    override suspend fun getPopularManga(page: Int): MangasPage = browseManga(page, "", SortFilter.popular)

    // ========================== Latest =========================
    override suspend fun getLatestUpdates(page: Int): MangasPage = browseManga(page, "", SortFilter.latest)

    // ========================== Search =========================
    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = if (
        query.isNotBlank() &&
        filters.filterIsInstance<SearchWithFilters>().firstOrNull()?.state == false
    ) {
        querySearch(query)
    } else {
        browseManga(page, query, filters)
    }

    private suspend fun querySearch(query: String): MangasPage {
        val body = SearchQueryPayload(
            query = query.trim(),
            limit = 25,
        ).toJsonRequestBody()

        val data = client.post("$baseUrl/auth/search", body, apiHeaders).parseAs<SearchQueryResponse>().results

        val mangas = data.filter { it.type != "Novel" }
            .map {
                SManga.create().apply {
                    url = MangaUrl(it.id.toString(), it.slug).toJsonString()
                    title = it.title.unescapeHtml()
                    thumbnail_url = it.thumbnail
                    description = it.description.unescapeHtml()
                    status = when (it.status) {
                        "Ongoing" -> SManga.ONGOING
                        "Completed" -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                }
            }

        return MangasPage(
            mangas = mangas,
            hasNextPage = false,
        )
    }

    protected suspend fun fetchBrowsePage(page: Int, query: String, filters: FilterList): List<BrowseManga> {
        val body = SearchPayload(
            page = page,
            search = query.trim(),
            years = filters.filterIsInstance<YearFilter>().firstOrNull()
                ?.selected.let(::listOfNotNull),
            genres = filters.filterIsInstance<TagFilter>().firstOrNull()
                ?.checked.orEmpty(),
            types = filters.filterIsInstance<TypeFilter>().firstOrNull()
                ?.selected.let(::listOfNotNull),
            statuses = filters.filterIsInstance<StatusFilter>().firstOrNull()
                ?.selected.let(::listOfNotNull),
            sort = filters.filterIsInstance<SortFilter>().firstOrNull()?.selected ?: "post_desc",
            genreMatchMode = filters.filterIsInstance<TagFilterMatch>().firstOrNull()?.selected ?: "all",
        ).toJsonRequestBody()

        return client.post("$baseUrl/wp-json/manga/v1/load", body, apiHeaders).parseAs()
    }

    private suspend fun browseManga(page: Int, query: String, filters: FilterList): MangasPage {
        val data = fetchBrowsePage(page, query, filters)

        val mangas = data.filter { it.type != "Novel" && it.url.isNotBlank() }
            .map(::browseMangaToSManga)

        return MangasPage(
            mangas = mangas,
            hasNextPage = data.size == 24,
        )
    }

    protected fun browseMangaToSManga(manga: BrowseManga): SManga = SManga.create().apply {
        url = MangaUrl(id = manga.id, slug = manga.url.toSlug()).toJsonString()
        title = manga.title.unescapeHtml()
        thumbnail_url = manga.cover
        description = manga.description.unescapeHtml()
        status = when (manga.status) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val slug = url.toString().toSlug()
        val document = client.get("$baseUrl/manga/$slug", headers).asJsoup()

        val id = document.body().dataset()["manga-id"] ?: return null
        val statusElement = document.select(".capitalize").firstOrNull {
            val text = it.text().lowercase()
            text == "ongoing" || text == "completed"
        }
        val status = when (statusElement?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        if (document.select(".capitalize").any { it.text().contains("Novel") }) {
            throw Exception("Novels are not supported")
        }

        return fetchMangaDetails(id, status)
    }

    // ========================= Filters =========================
    override fun getFilterList(): FilterList = FilterList(
        SearchWithFilters(),
        Filter.Header("If unchecked, all filters will be ignored with search query"),
        Filter.Header("But will give more relevant results"),
        Filter.Separator(),
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        YearFilter(),
        TagFilter(),
        TagFilterMatch(),
    )

    // ========================= Details =========================
    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) fetchMangaDetails(manga.url.parseAs<MangaUrl>().id, manga.status) else manga
        }
        val chapterList = async { if (fetchChapters) getChapterList(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchMangaDetails(id: String, status: Int): SManga {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("wp-json/wp/v2/manga")
            addPathSegment(id)
            addQueryParameter("_embed", null)
        }.build()

        val data = client.get(url, apiHeaders).parseAs<MangaDetails>()

        return SManga.create().apply {
            this.url = MangaUrl(data.id.toString(), data.slug).toJsonString()
            title = data.title.rendered.unescapeHtml()
            description = Jsoup.parseBodyFragment(data.content.rendered).wholeText().unescapeHtml()
            genre = buildSet {
                addAll(data.embedded.getTerms("post_tag"))
                if (listOf("Manhwa", "Manhua", "Manga").none { this.contains(it) }) {
                    add(data.type)
                }
            }.joinToString()
            author = data.embedded.getTerms("manga_author").joinToString()
            this.status = status
            thumbnail_url = data.embedded.featuredMedia.firstOrNull()?.url
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = runCatching { manga.url.parseAs<MangaUrl>().slug }
            .getOrElse { manga.url.removePrefix("/manga/").removePrefix("/").removeSuffix("/") }

        return "$baseUrl/manga/$slug"
    }

    // ======================== Chapters =========================
    private var serverTimeOffset = 0L

    private fun chapterListUrl(dto: MangaUrl, offset: Int): HttpUrl {
        val now = Instant.ofEpochMilli(System.currentTimeMillis() + serverTimeOffset)
        val token = md5("${now.epochSecond}mng_ch_${isoDateFormatter.format(now)}").substring(0, 16)

        return "$baseUrl/auth/manga-chapters".toHttpUrl().newBuilder().apply {
            addQueryParameter("manga_id", dto.id)
            addQueryParameter("offset", offset.toString())
            addQueryParameter("limit", "500")
            addQueryParameter("order", "DESC")
            addQueryParameter("_t", token)
            addQueryParameter("_ts", now.epochSecond.toString())
            dto.group?.let {
                addQueryParameter("group_id", it.toString())
            }
        }.build()
    }

    private suspend fun fetchChapterPage(dto: MangaUrl, offset: Int): ChapterList {
        val response = client.get(chapterListUrl(dto, offset), apiHeaders, ensureSuccess = false)
        if (response.code != 403) {
            if (!response.isSuccessful) {
                response.close()
                throw HttpException(response.code)
            }
            return response.parseAs()
        }

        val serverTime = response.headers.getDate("Date")
        response.close()
        serverTimeOffset = (serverTime ?: throw HttpException(403)).time - System.currentTimeMillis()
        return client.get(chapterListUrl(dto, offset), apiHeaders).parseAs()
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val dto = manga.url.parseAs<MangaUrl>()

        val data = buildList {
            var page = fetchChapterPage(dto, 0)
            addAll(page.chapters)
            while (page.hasMore && page.chapters.isNotEmpty()) {
                page = fetchChapterPage(dto, size)
                addAll(page.chapters)
            }
        }
        countViews(dto.id)

        val placeholders = listOf(null, "", "N/A", "—")
        var hasScanlator = false

        val chapters = data.filter {
            it.language.equals(lang, ignoreCase = true)
        }.map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.url.removeSuffix("/"))
                name = buildString {
                    append("Chapter ")
                    append(it.chapter)
                    it.title?.takeIf { title -> title !in placeholders }?.let { title ->
                        append(": ", title.unescapeHtml())
                    }
                }
                it.groupName?.let { group ->
                    if (group !in placeholders) {
                        scanlator = group
                        hasScanlator = true
                    }
                }
                date_upload = it.date.parseRelativeDate()
            }
        }

        if (hasScanlator) {
            chapters.onEach { it.scanlator = it.scanlator ?: "\u200B" }
        }

        return chapters
    }

    // ========================== Pages ==========================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = if (chapter.url.startsWith("http")) chapter.url else baseUrl + chapter.url
        val chapterId = chapterUrl.removeSuffix("/").toHttpUrl()
            .pathSegments.last()
            .substringAfterLast("-")

        val url = "$baseUrl/auth/chapter-content".toHttpUrl().newBuilder()
            .addQueryParameter("chapter_id", chapterId)
            .build()

        return client.get(url, apiHeaders).parseAs<Pages>().images.mapIndexed { idx, img ->
            Page(idx, imageUrl = img)
        }
    }

    // ========================= Helpers =========================
    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun String.toSlug(): String {
        val fullUrl = if (startsWith("http://") || startsWith("https://")) this else "$baseUrl$this"
        val path = fullUrl.toHttpUrl().pathSegments.filter(String::isNotBlank)

        return if ((path.size == 2 && path[0] == "manga") || (path.size >= 3 && path[0] == "read")) {
            path[1]
        } else if (path.isNotEmpty()) {
            path.last()
        } else {
            throw Exception("Expected manga or read path, got $this")
        }
    }

    private fun String.parseRelativeDate(): Long {
        val calendar = Calendar.getInstance()
        val (amount, unit) = relativeDateRegex.matchEntire(this)?.destructured
            ?: return 0L

        when (unit) {
            "second" -> calendar.add(Calendar.SECOND, -amount.toInt())
            "minute" -> calendar.add(Calendar.MINUTE, -amount.toInt())
            "hour" -> calendar.add(Calendar.HOUR, -amount.toInt())
            "day" -> calendar.add(Calendar.DAY_OF_YEAR, -amount.toInt())
            "week" -> calendar.add(Calendar.WEEK_OF_YEAR, -amount.toInt())
            "month" -> calendar.add(Calendar.MONTH, -amount.toInt())
            "year" -> calendar.add(Calendar.YEAR, -amount.toInt())
        }

        return calendar.timeInMillis
    }

    private fun countViews(postId: String) {
        val payload = mapOf("post_id" to postId).toJsonRequestBody()
        val url = "$baseUrl/wp-json/pviews/v1/increment/"
        val request = POST(url, apiHeaders, payload)

        client.newCall(request)
            .enqueue(
                object : Callback {
                    override fun onResponse(call: okhttp3.Call, response: Response) {
                        try {
                            response.close()
                        } catch (_: Exception) {}
                    }

                    override fun onFailure(call: okhttp3.Call, e: IOException) {
                        Log.e(name, "Failed to count views", e)
                    }
                },
            )
    }

    companion object {
        private val isoDateFormatter = DateTimeFormatter.ofPattern("yyyyMMddHH", Locale.US).withZone(ZoneOffset.UTC)
        private val relativeDateRegex = Regex("""(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago""")
    }
}

internal fun String.unescapeHtml(): String {
    var decoded = this
    do {
        val previous = decoded
        decoded = Parser.unescapeEntities(decoded, false)
    } while (decoded != previous)
    return decoded
}

// ========================== DTOs ==========================

class StringifiedListSerializer<T>(elementSerializer: KSerializer<T>) :
    JsonTransformingSerializer<List<T>>(ListSerializer(elementSerializer)) {
    override fun transformSerialize(element: JsonElement): JsonElement = JsonPrimitive(element.toString())
}

@Serializable
class SearchPayload(
    private val page: Int,
    private val search: String,
    @Serializable(with = StringifiedListSerializer::class)
    private val years: List<Int>,
    @Serializable(with = StringifiedListSerializer::class)
    private val genres: List<Int>,
    @Serializable(with = StringifiedListSerializer::class)
    private val types: List<String>,
    @Serializable(with = StringifiedListSerializer::class)
    private val statuses: List<String>,
    private val sort: String,
    private val genreMatchMode: String,
)

@Serializable
class SearchQueryPayload(
    val limit: Int,
    val query: String,
)

@Serializable
class SearchQueryResponse(
    val results: List<Manga> = emptyList(),
) {
    @Serializable
    class Manga(
        val id: Int,
        val slug: String = "",
        val title: String = "",
        val thumbnail: String = "",
        val type: String = "",
        val description: String = "",
        val status: String = "",
    )
}

@Serializable
class BrowseManga(
    val id: String = "",
    val url: String = "",
    val title: String = "",
    val cover: String = "",
    val type: String = "",
    val description: String = "",
    val status: String = "",
)

@Serializable
data class MangaUrl(
    val id: String,
    val slug: String,
    val group: Long? = null,
)

@Serializable
class MangaDetails(
    val id: Int,
    val slug: String,
    val title: Rendered = Rendered(""),
    val content: Rendered = Rendered(""),
    val type: String = "manga",
    @SerialName("_embedded")
    val embedded: Embedded = Embedded(),
)

@Serializable
class Embedded(
    @SerialName("wp:featuredmedia")
    val featuredMedia: List<Thumbnail> = emptyList(),
    @SerialName("wp:term")
    private val terms: List<List<Term>> = emptyList(),
) {
    fun getTerms(
        type: String,
    ): List<String> = terms.find { it.firstOrNull()?.taxonomy == type }.orEmpty().map { it.name }
}

@Serializable
class Term(
    val name: String = "",
    val taxonomy: String = "",
)

@Serializable
class Thumbnail(
    @SerialName("source_url")
    val url: String = "",
)

@Serializable
class Rendered(
    val rendered: String = "",
)

@Serializable
class ChapterList(
    val chapters: List<Chapter> = emptyList(),
    @SerialName("has_more")
    val hasMore: Boolean = false,
)

@Serializable
class Chapter(
    val url: String,
    val chapter: String,
    val title: String? = null,
    val date: String = "",
    @SerialName("group_name")
    val groupName: String? = null,
    val language: String = "",
)

@Serializable
class Pages(
    val images: List<String> = emptyList(),
)

// ========================= Filters =========================

abstract class SelectFilter<T>(
    name: String,
    private val options: List<Pair<String, T>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

class CheckBoxFilter<T>(name: String, val value: T) : Filter.CheckBox(name)

abstract class CheckBoxGroup<T>(
    name: String,
    options: List<Pair<String, T>>,
) : Filter.Group<CheckBoxFilter<T>>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }
}

class SearchWithFilters : Filter.CheckBox("Apply filters to Text Search", false)

class TypeFilter :
    SelectFilter<String?>(
        name = "Type",
        options = listOf(
            "All" to null,
            "Manga" to "Manga",
            "Manhwa" to "Manhwa",
            "Manhua" to "Manhua",
        ),
    )

class StatusFilter :
    SelectFilter<String?>(
        name = "Status",
        options = listOf(
            "All" to null,
            "Completed" to "Completed",
            "Ongoing" to "Ongoing",
        ),
    )

class YearFilter :
    SelectFilter<Int?>(
        name = "Year",
        options = buildList {
            add("All" to null)
            val current = Calendar.getInstance().get(Calendar.YEAR)
            (current downTo 1949).mapTo(this) { it.toString() to it }
        },
    )

class TagFilter(options: List<Pair<String, Int>> = emptyList()) :
    CheckBoxGroup<Int>(
        name = "Tags",
        options = options,
    )

class TagFilterMatch :
    SelectFilter<String>(
        name = "Tag Match",
        options = listOf(
            "Any" to "any",
            "All" to "all",
        ),
    )

class SortFilter(
    state: Selection = Selection(0, false),
) : Filter.Sort(
    name = "Sort",
    values = sort.map { it.first }.toTypedArray(),
    state = state,
) {
    private val sortDirection
        get() = if (state?.ascending == true) {
            "asc"
        } else {
            "desc"
        }
    val selected get() = "${sort[state?.index ?: 0].second}_$sortDirection"

    companion object {
        val popular = FilterList(
            SortFilter(Selection(3, false)),
            TagFilterMatch(),
        )
        val latest = FilterList(
            SortFilter(Selection(0, false)),
            TagFilterMatch(),
        )
    }
}

private val sort = listOf(
    "Latest Updates" to "post",
    "Release Date" to "release",
    "Title A-Z" to "title",
    "Popular" to "popular",
)
