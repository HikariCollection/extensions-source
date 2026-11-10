package app.hikari.extension.en.weebcentral

import app.hikari.extension.core.KeiSource
import app.hikari.extension.core.UriFilter
import app.hikari.extension.core.asJsoup
import app.hikari.extension.core.get
import app.hikari.extension.core.textOrNull
import app.hikari.source.model.Filter
import app.hikari.source.model.FilterList
import app.hikari.source.model.MangasPage
import app.hikari.source.model.Page
import app.hikari.source.model.SChapter
import app.hikari.source.model.SManga
import app.hikari.source.model.SMangaUpdate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.Instant

class WeebCentral : KeiSource() {

    override val name: String = "Weeb Central"
    override val baseUrl: String = "https://weebcentral.com"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    override suspend fun getPopularManga(page: Int): MangasPage =
        getSearchManga(page, "", FilterList(SortFilter("Popularity")))

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        getSearchManga(page, "", FilterList(SortFilter("Latest Updates")))

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search/data".toHttpUrl().newBuilder().apply {
            addQueryParameter("limit", "32")
            addQueryParameter("offset", ((page - 1) * 32).toString())
            addQueryParameter("display_mode", "Full Display")
            if (query.isNotBlank()) {
                addQueryParameter("text", query.trim())
            }
            val activeFilters = filters.ifEmpty { getFilterList() }
            activeFilters.filterIsInstance<UriFilter>().forEach {
                it.addToUri(this)
            }
        }.build()

        val doc = client.get(url, headers).asJsoup()
        return searchMangaParse(doc)
    }

    private fun searchMangaParse(document: Document): MangasPage {
        val mangas = document.select("body > article").mapNotNull { article ->
            val link = article.selectFirst("a[href*='/series/']") ?: return@mapNotNull null
            val href = link.attr("href")
            val title = article.selectFirst("section:last-child a.link-hover")?.text()
                ?: article.selectFirst("img")?.attr("alt")?.removeSuffix(" cover")?.trim()
                ?: return@mapNotNull null

            val thumbnail = article.selectFirst("picture source[type=image/webp]")?.attr("srcset")
                ?: article.selectFirst("picture img")?.attr("abs:src")

            SManga.create().apply {
                setUrlWithoutDomain(href)
                this.title = title
                this.thumbnail_url = thumbnail
            }
        }

        val hasNextPage = document.selectFirst("button[hx-get^='/search/data']") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "series") return null
        val seriesId = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply { this.url = "/series/$seriesId" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val seriesUrl = if (manga.url.startsWith("http")) manga.url else baseUrl + manga.url
        val seriesId = seriesUrl.toHttpUrl().pathSegments.getOrNull(1)
            ?: manga.url.removePrefix("/series/").substringBefore("/")

        val detailsDeferred = async {
            if (fetchDetails) {
                val doc = client.get(seriesUrl, headers).asJsoup()
                mangaDetailsParse(doc).apply {
                    this.url = manga.url
                }
            } else {
                manga
            }
        }

        val chaptersDeferred = async {
            if (fetchChapters) {
                val chapterListUrl = "$baseUrl/series/$seriesId/full-chapter-list"
                val doc = client.get(chapterListUrl, headers).asJsoup()
                chapterListParse(doc)
            } else {
                chapters
            }
        }

        SMangaUpdate(detailsDeferred.await(), chaptersDeferred.await())
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")?.text().orEmpty()
        thumbnail_url = document.selectFirst("section picture source[type=image/webp]")?.attr("srcset")
            ?: document.selectFirst("section picture img")?.attr("abs:src")

        author = document.select("li:has(> strong:contains(Author)) a").joinToString { it.text().trim() }

        val genres = document.select("li:has(> strong:contains(Tags)) a").map { it.text().trim() }.toMutableList()
        document.selectFirst("li:has(> strong:contains(Type)) a")?.text()?.takeIf { it.isNotBlank() }?.let {
            genres.add(it.trim())
        }
        genre = genres.joinToString()

        status = when (document.selectFirst("li:has(> strong:contains(Status)) a")?.text()?.trim()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "complete" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "canceled", "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        val desc = document.selectFirst("li:has(> strong:contains(Description)) p")?.textOrNull()
        val related = document.select("li:has(> strong:contains(Related Series)) li").map { it.text().trim() }
        description = buildString {
            if (!desc.isNullOrBlank()) append(desc)
            if (related.isNotEmpty()) {
                if (isNotEmpty()) append("\n\nRelated Series:\n")
                related.forEach { append("- ").append(it).append("\n") }
            }
        }.trim().takeIf { it.isNotBlank() }

        initialized = true
    }

    private fun chapterListParse(document: Document): List<SChapter> {
        return document.select("div.flex.items-center:has(> a[href*=/chapters/])").mapNotNull { div ->
            val a = div.selectFirst("a[href*=/chapters/]") ?: return@mapNotNull null
            val href = a.attr("href")
            val chapterName = a.selectFirst("span.grow > span:first-child")?.text()?.trim()
                ?: a.text().trim()

            val uploadDate = a.selectFirst("time[datetime]")?.attr("datetime")?.let { dt ->
                runCatching { Instant.parse(dt).toEpochMilli() }.getOrDefault(0L)
            } ?: 0L

            SChapter.create().apply {
                setUrlWithoutDomain(href)
                name = chapterName
                date_upload = uploadDate
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = if (chapter.url.startsWith("http")) chapter.url else baseUrl + chapter.url
        val chapterId = chapterUrl.toHttpUrl().pathSegments.lastOrNull { it.isNotBlank() }
            ?: chapter.url.removePrefix("/chapters/").substringBefore("/")

        val imagesUrl = "$baseUrl/chapters/$chapterId/images?is_prev=False&current_page=1&reading_style=long_strip"
        val doc = client.get(imagesUrl, headers).asJsoup()
        val images = doc.select("section#chapter-images img")
        return images.mapIndexed { idx, img ->
            Page(idx, imageUrl = img.attr("abs:src"))
        }
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        AuthorFilter(),
        OfficialFilter(),
        AnimeFilter(),
        AdultFilter(),
        Filter.Separator(),
        StatusFilter(),
        TypeFilter(),
        TagFilter(),
    )
}
