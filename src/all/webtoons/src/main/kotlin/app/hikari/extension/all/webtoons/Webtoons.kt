package app.hikari.extension.all.webtoons

import app.hikari.extension.core.KeiSource
import app.hikari.extension.core.asJsoup
import app.hikari.extension.core.get
import app.hikari.extension.core.parseAs
import app.hikari.extension.core.textOrNull
import app.hikari.source.Source
import app.hikari.source.SourceFactory
import app.hikari.source.model.FilterList
import app.hikari.source.model.MangasPage
import app.hikari.source.model.Page
import app.hikari.source.model.SChapter
import app.hikari.source.model.SManga
import app.hikari.source.model.SMangaUpdate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Webtoons : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        WebtoonsSource("en", "en"),
        WebtoonsSource("id", "id"),
    )
}

class WebtoonsSource(
    override val lang: String,
    private val langPath: String,
) : KeiSource() {

    override val name: String = "WEBTOON"
    override val baseUrl: String = "https://www.webtoons.com"
    override val supportsLatest: Boolean = true

    override fun getHomeUrl(): String = "$baseUrl/$langPath"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("Referer", "$baseUrl/")

    override fun getFilterList(): FilterList = FilterList(createFiltersForLocale(langPath))

    override suspend fun getPopularManga(page: Int): MangasPage {
        val doc = client.get("$baseUrl/$langPath/ranking/popular", headers).asJsoup()
        return MangasPage(parseMangaCards(doc), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val doc = client.get("$baseUrl/$langPath/originals", headers).asJsoup()
        return MangasPage(parseMangaCards(doc), false)
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val activeFilters = filters.ifEmpty { getFilterList() }

        if (query.isNotBlank()) {
            val section = activeFilters.filterIsInstance<SearchSectionFilter>().firstOrNull()?.getValue() ?: "originals"
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment(langPath)
                addPathSegment("search")
                if (section != "all") {
                    addPathSegment(section)
                    addQueryParameter("page", page.toString())
                }
                addQueryParameter("keyword", query.trim())
            }.build()

            val doc = client.get(url, headers).asJsoup()
            val hasNextPage = section != "all" &&
                doc.selectFirst("._pagination a.pagination[aria-current=true] ~ a.pagination, ._pagination a.next") != null
            return MangasPage(parseMangaCards(doc), hasNextPage)
        }

        val genre = activeFilters.filterIsInstance<GenreFilter>().firstOrNull()?.getValue().orEmpty()
        val sort = activeFilters.filterIsInstance<SortFilter>().firstOrNull()?.getValue() ?: "MANA"

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(langPath)
            addPathSegment("genres")
            if (genre.isNotEmpty()) {
                addPathSegment(genre)
            }
            addQueryParameter("sortOrder", sort)
        }.build()

        val doc = client.get(url, headers).asJsoup()
        return MangasPage(parseMangaCards(doc), false)
    }

    private fun parseMangaCards(document: Document): List<SManga> {
        val seen = HashSet<String>()
        return document.select("ul.webtoon_list > li > a[href*='title_no=']").mapNotNull { element ->
            parseCard(element)?.takeIf { seen.add(it.url) }
        }
    }

    private fun parseCard(element: Element): SManga? {
        val href = element.attr("abs:href").takeIf { it.isNotBlank() } ?: return null
        val title = element.selectFirst("strong.title")?.textOrNull() ?: return null
        val thumbnail = element.selectFirst(".image_wrap img")?.attr("abs:src")

        return SManga.create().apply {
            setUrlWithoutDomain(href)
            this.title = title
            thumbnail_url = thumbnail
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.host.endsWith("webtoons.com")) return null
        val titleNo = url.queryParameter("title_no") ?: return null
        if (url.pathSegments.firstOrNull() != langPath) return null

        val canonicalUrl = url.newBuilder()
            .host("www.webtoons.com")
            .query("title_no=$titleNo")
            .build()

        val manga = SManga.create().apply { setUrlWithoutDomain(canonicalUrl.toString()) }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val seriesUrl = if (manga.url.startsWith("http")) manga.url else baseUrl + manga.url
        val seriesHttpUrl = seriesUrl.toHttpUrl()
        val titleNo = seriesHttpUrl.queryParameter("title_no")
            ?: throw IllegalStateException("Missing title_no in $seriesUrl")
        val endpointType = if ("/canvas/" in seriesUrl) "canvas" else "webtoon"

        val detailsDeferred = async {
            if (fetchDetails) {
                val doc = client.get(seriesUrl, headers).asJsoup()
                parseDetails(doc).apply { this.url = manga.url }
            } else {
                manga
            }
        }

        val chaptersDeferred = async {
            if (fetchChapters) {
                fetchAllChapters(endpointType, titleNo)
            } else {
                chapters
            }
        }

        SMangaUpdate(
            manga = detailsDeferred.await(),
            chapters = chaptersDeferred.await(),
        )
    }

    private fun parseDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(".info .subj")?.textOrNull() ?: ""
        author = document.selectFirst(".author_area")?.let { area ->
            area.select("button").remove()
            area.text().replace("...", "").trim()
        }
        genre = document.select(".info .genre").joinToString(", ") { it.text().trim() }
        description = document.selectFirst("p.summary")?.textOrNull()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst(".detail_header .thmb img")?.attr("abs:src")

        val dayInfo = document.selectFirst(".day_info")?.text().orEmpty().uppercase()
        status = when {
            "COMPLETED" in dayInfo -> SManga.COMPLETED
            "HIATUS" in dayInfo -> SManga.ON_HIATUS
            dayInfo.isNotBlank() -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    private suspend fun fetchAllChapters(endpointType: String, titleNo: String): List<SChapter> {
        val mobileHeaders = headersBuilder()
            .set("Referer", "https://m.webtoons.com/")
            .build()

        val allChapters = mutableListOf<SChapter>()
        var cursor: Long? = null

        do {
            val apiUrl = "https://m.webtoons.com/api/v1/$endpointType/$titleNo/episodes".toHttpUrl()
                .newBuilder()
                .addQueryParameter("pageSize", "99999")
                .apply {
                    if (cursor != null) {
                        addQueryParameter("cursor", cursor.toString())
                    }
                }
                .build()

            val result = client.get(apiUrl, mobileHeaders)
                .parseAs<EpisodesResponseDto>()
                .result ?: break

            result.episodeList.mapTo(allChapters) { it.toSChapter() }
            cursor = result.nextCursor?.takeIf { it > 0L }
        } while (cursor != null)

        return allChapters.sortedByDescending { it.chapter_number }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get(getChapterUrl(chapter), headers).asJsoup()
        return doc.select("div#_imageList img[data-url], img._images[data-url]")
            .mapIndexed { index, img ->
                Page(index, imageUrl = img.attr("abs:data-url"))
            }
    }
}
