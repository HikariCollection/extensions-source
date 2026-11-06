package app.hikari.multisrc.natsuid

import app.hikari.extension.core.*
import app.hikari.source.model.FilterList
import app.hikari.source.model.MangasPage
import app.hikari.source.model.Page
import app.hikari.source.model.SChapter
import app.hikari.source.model.SManga
import app.hikari.source.model.SMangaUpdate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.Response
import org.jsoup.Jsoup
import java.time.format.DateTimeFormatter
import kotlin.random.Random

abstract class NatsuId(
    override val name: String = "",
    override val baseUrl: String = "",
    override val lang: String = "",
) : KeiSource() {

    override val supportsLatest: Boolean = true

    protected open val dateFormat: DateTimeFormatter? = null

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchManga(page, "", SortFilter.popular)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchManga(page, "", SortFilter.latest)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        val requestUrl = "$baseUrl/wp-json/wp/v2/manga".toHttpUrl().newBuilder()
            .addQueryParameter("slug[]", slug)
            .addQueryParameter("_embed", null)
            .build()

        val response = client.get(requestUrl)
        val manga = response.parseAs<List<Manga>>(::transformJsonResponse).getOrNull(0) ?: return null

        if (manga.isNovel) error("Novels are not supported")

        return manga.toSManga()
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/wp-admin/admin-ajax.php?action=advanced_search"
        val body = MultipartBody.Builder().apply {
            setType(MultipartBody.FORM)
            addFormDataPart("nonce", getNonce())
            filters.firstInstanceOrNull<GenreInclusion>()?.selected.also {
                addFormDataPart("inclusion", it ?: "OR")
            }
            filters.firstInstanceOrNull<GenreExclusion>()?.selected.also {
                addFormDataPart("exclusion", it ?: "OR")
            }
            addFormDataPart("page", page.toString())
            val genres = filters.firstInstanceOrNull<GenreFilter>()
            genres?.included.orEmpty().also {
                addFormDataPart("genre", it.toJsonString())
            }
            genres?.excluded.orEmpty().also {
                addFormDataPart("genre_exclude", it.toJsonString())
            }
            addFormDataPart("author", "[]")
            addFormDataPart("artist", "[]")
            val isProject = filters.firstInstanceOrNull<ProjectFilter>()
            addFormDataPart("project", if (isProject?.state == true) "1" else "0")
            filters.firstInstanceOrNull<TypeFilter>()?.checked.orEmpty().also {
                addFormDataPart("type", it.toJsonString())
            }
            filters.firstInstanceOrNull<StatusFilter>()?.checked.orEmpty().also {
                addFormDataPart("status", it.toJsonString())
            }
            val sort = filters.firstInstanceOrNull<SortFilter>() ?: SortFilter()
            addFormDataPart("order", if (sort.isAscending) "asc" else "desc")
            addFormDataPart("orderby", sort.sort)
            addFormDataPart("query", query.trim())
        }.build()

        return parseSearchManga(client.post(url, body))
    }

    protected open suspend fun parseSearchManga(response: Response): MangasPage {
        val document = Jsoup.parseBodyFragment(response.body.string(), baseUrl)
        val slugs = document.select("div > a[href*=/manga/]:has(> img)").mapNotNull {
            it.absUrl("href").toHttpUrl().pathSegments.getOrNull(1)
        }.distinct().ifEmpty {
            return MangasPage(emptyList(), false)
        }

        val url = "$baseUrl/wp-json/wp/v2/manga".toHttpUrl().newBuilder().apply {
            slugs.forEach { slug ->
                addQueryParameter("slug[]", slug)
            }
            addQueryParameter("per_page", "${slugs.size + 1}")
            addQueryParameter("_embed", null)
        }.build()

        val details = client.get(url)
            .parseAs<List<Manga>>(::transformJsonResponse)
            .filterNot { it.isNovel }
            .associateBy { it.slug }

        val mangas = slugs.mapNotNull { slug ->
            details[slug]?.toSManga()
        }

        val hasNextPage = document.selectFirst("button:has(svg)") != null

        return MangasPage(mangas, hasNextPage)
    }

    private var nonce: String? = null
    private val nonceMutex = Mutex()

    private suspend fun getNonce(): String = nonceMutex.withLock {
        nonce ?: run {
            val response = client.get(
                "$baseUrl/wp-admin/admin-ajax.php?type=search_form&action=get_nonce",
            )

            Jsoup.parseBodyFragment(response.body.string())
                .selectFirst("input[name=search_nonce]")
                ?.attr("value")
                ?.takeIf { it.isNotBlank() }
                ?.also {
                    nonce = it
                } ?: error("Unable to get nonce")
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.slug()}/"

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaId = manga.id() ?: run {
            client.get(getMangaUrl(manga)).asJsoup()
                .selectFirst("#gallery-list")?.attr("hx-get")
                ?.substringAfter("manga_id=")
                ?.substringBefore("&")
                ?: error("Could not find manga ID")
        }

        val mangaDeferred = async { if (fetchDetails) getMangaDetails(mangaId) else manga }
        val chaptersDeferred = async { if (fetchChapters) getChapterList(mangaId) else chapters }
        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    open suspend fun getMangaDetails(mangaId: String): SManga {
        val response = client.get("$baseUrl/wp-json/wp/v2/manga/$mangaId?_embed")
        val manga = response.parseAs<Manga>(::transformJsonResponse)
        return manga.toSManga()
    }

    open fun chapterListUrl(mangaId: String): HttpUrl = "$baseUrl/wp-admin/admin-ajax.php".toHttpUrl().newBuilder()
        .addQueryParameter("manga_id", mangaId)
        .addQueryParameter("page", "${Random.nextInt(99, 9999)}")
        .addQueryParameter("action", "chapter_list")
        .build()

    protected open val chapterListSelector = "div a:has(time)"
    protected open val chapterNameSelector = "span"
    protected open val chapterDateSelector = "time"
    protected open val chapterDateAttribute = "datetime"

    open suspend fun getChapterList(mangaId: String): List<SChapter> {
        val response = client.get(chapterListUrl(mangaId))
        val document = Jsoup.parseBodyFragment(response.body.string(), baseUrl)

        return document.select(chapterListSelector).map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                name = it.selectFirst(chapterNameSelector)!!.ownText()
                date_upload = (
                    it.selectFirst(chapterDateSelector)?.attr(chapterDateAttribute)
                        ?.parseDate() ?: 0L
                )
            }
        }
    }

    protected open val pageListSelector = "section[data-image-data] img"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        return document.select(pageListSelector).mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("src"))
        }
    }

    open suspend fun fetchFilterData(): JsonElement {
        val url = "$baseUrl/wp-json/wp/v2/genre?per_page=100&page=1&orderby=count&order=desc"
        val response = client.get(url)
        return response.parseAs<List<Term>>(::transformJsonResponse).toJsonElement()
    }

    override fun getFilterList(): FilterList = getFilterList(null)

    open fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf(
            SortFilter(),
            TypeFilter(),
            StatusFilter(),
            ProjectFilter(),
        )

        data?.let {
            filters.addAll(
                listOf(
                    GenreFilter(
                        it.parseAs<List<Term>>().map { t -> t.name to t.slug }
                            .sortedBy { p -> p.first },
                    ),
                    GenreInclusion(),
                    GenreExclusion(),
                ),
            )
        }

        return FilterList(filters)
    }

    private fun String.parseDate(): Long = dateFormat?.tryParseDateTime(this) ?: tryParseInstant(this)

    protected open fun transformJsonResponse(responseBody: String): String = responseBody

    private fun SManga.slug(): String = if (url.startsWith("{")) {
        url.parseAs<MangaUrl>().slug
    } else {
        "$baseUrl$url".toHttpUrl().pathSegments.getOrNull(1) ?: url
    }

    private val descriptionIdRegex = Regex("""ID: (\d+)""")

    private fun SManga.id(): String? = memo["id"]?.jsonPrimitive?.contentOrNull ?: when {
        url.startsWith("{") -> url.parseAs<MangaUrl>().id.toString()
        else -> description?.trim()?.let { descriptionIdRegex.find(it)?.groupValues?.get(1) }
    }

    private inline fun <reified T> Response.parseAs(transform: (String) -> String = { it }): T {
        return transform(body.string()).parseAs<T>()
    }
}
