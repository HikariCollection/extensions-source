package app.hikari.extension.id.komiku

import app.hikari.extension.core.KeiSource
import app.hikari.extension.core.UriFilter
import app.hikari.extension.core.asJsoup
import app.hikari.extension.core.get
import app.hikari.extension.core.imgAttr
import app.hikari.extension.core.textOrNull
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
import java.text.SimpleDateFormat
import java.util.Locale

class Komiku : KeiSource() {

    override val name: String = "Komiku"
    override val baseUrl: String = "https://komiku.org"
    private val apiBaseUrl: String = "https://api.komiku.org"
    override val lang: String = "id"
    override val supportsLatest: Boolean = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    // ============================== Browse & Search ==============================

    override suspend fun getPopularManga(page: Int): MangasPage =
        getSearchManga(page, "", FilterList(OrderByFilter("views")))

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        getSearchManga(page, "", FilterList(OrderByFilter("modified")))

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            val urlBuilder = if (page > 1) {
                "$apiBaseUrl/page/$page/".toHttpUrl().newBuilder()
            } else {
                "$apiBaseUrl/".toHttpUrl().newBuilder()
            }
            urlBuilder.addQueryParameter("post_type", "manga")
            urlBuilder.addQueryParameter("s", query)
            urlBuilder.build()
        } else {
            val urlBuilder = if (page > 1) {
                "$apiBaseUrl/manga/page/$page/".toHttpUrl().newBuilder()
            } else {
                "$apiBaseUrl/manga/".toHttpUrl().newBuilder()
            }
            val filterList = filters.ifEmpty { getFilterList() }
            filterList.filterIsInstance<UriFilter>().forEach { it.addToUri(urlBuilder) }
            urlBuilder.build()
        }

        val document = client.get(url).asJsoup()
        return parseMangaList(document)
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangaElements = document.select("div.bge")

        val mangas = mangaElements.mapNotNull { element ->
            val titleEl = element.selectFirst("div.kan a h3") ?: element.selectFirst("h3") ?: return@mapNotNull null
            val linkEl = element.selectFirst("div.kan a") ?: element.selectFirst("div.bgei a") ?: return@mapNotNull null
            val imgEl = element.selectFirst("div.bgei img")

            val rawUrl = linkEl.attr("href")
            val rawCover = imgEl?.imgAttr() ?: ""
            val cleanCover = if (rawCover.isNotBlank()) rawCover.substringBefore("?") else null

            SManga.create().apply {
                title = titleEl.text().trim()
                thumbnail_url = cleanCover
                setUrlWithoutDomain(rawUrl)
            }
        }

        val hasNextPage = mangas.size >= 10
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Deep Link ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val manga = SManga.create().apply { setUrlWithoutDomain(url.encodedPath) }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // ============================== Details & Chapters ==============================

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val doc = if (fetchDetails || fetchChapters) {
            client.get(getMangaUrl(manga)).asJsoup()
        } else {
            null
        }

        val mangaDeferred = async {
            if (fetchDetails && doc != null) {
                parseMangaDetails(doc, manga.url)
            } else {
                manga
            }
        }

        val chaptersDeferred = async {
            if (fetchChapters && doc != null) {
                parseChapterList(doc)
            } else {
                chapters
            }
        }

        SMangaUpdate(
            manga = mangaDeferred.await(),
            chapters = chaptersDeferred.await(),
        )
    }

    private fun parseMangaDetails(document: Document, mangaUrl: String): SManga = SManga.create().apply {
        setUrlWithoutDomain(mangaUrl)

        val titleEl = document.selectFirst("h1[itemprop=name]")
            ?: document.selectFirst("table.inftable tr:contains(Judul:) td:nth-child(2)")
        title = titleEl?.text()?.trim().orEmpty()

        val coverEl = document.selectFirst("div.ims img[itemprop=image]")
            ?: document.selectFirst("img[itemprop=image]")
            ?: document.selectFirst("div.ims img")
        thumbnail_url = coverEl?.imgAttr()?.let {
            if (it.isNotBlank()) it.substringBefore("?") else null
        }

        val synopsisEl = document.selectFirst("p.desc[itemprop=description]")
            ?: document.selectFirst("p[itemprop=description]")
            ?: document.selectFirst("div#informasi p")
        description = synopsisEl?.textOrNull()

        author = document.selectFirst("table.inftable tr:contains(Author:) td:nth-child(2)")?.textOrNull()
        artist = author

        val statusText = document.selectFirst("table.inftable tr:contains(Status:) td:nth-child(2)")?.text().orEmpty()
        status = when {
            statusText.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
            statusText.contains("End", ignoreCase = true) || statusText.contains("Tamat", ignoreCase = true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        val genreElements = document.select("ul.genre li.genre a, table.inftable tr:contains(Genre:) td:nth-child(2) a")
        val genres = genreElements.map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        genre = genres.joinToString(", ")
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val rows = document.select("table#Daftar_Chapter tr:has(td.judulseries), table#daftar_chapter tr:has(td.judulseries), tr:has(td.judulseries)")
        return rows.mapNotNull { row ->
            val linkEl = row.selectFirst("td.judulseries a") ?: return@mapNotNull null
            val nameEl = linkEl.selectFirst("span[itemprop=name]") ?: linkEl
            val dateEl = row.selectFirst("td.tanggalseries")

            val rawUrl = linkEl.attr("href")
            val dateUpload = dateEl?.text()?.trim()?.let { dateStr ->
                try {
                    dateFormat.parse(dateStr)?.time ?: 0L
                } catch (_: Exception) {
                    0L
                }
            } ?: 0L

            SChapter.create().apply {
                name = nameEl.text().trim()
                setUrlWithoutDomain(rawUrl)
                date_upload = dateUpload
            }
        }
    }

    // ============================== Reader Pages ==============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val imgElements = document.select("div#Baca_Komik img")

        return imgElements.mapNotNull { it.imgAttr() }
            .filter { src ->
                src.isNotBlank() && !src.contains("promosi") && !src.contains("promooktober") && !src.endsWith("Loading.gif")
            }
            .mapIndexed { index, imageUrl ->
                val finalUrl = if (imageUrl.startsWith("http")) imageUrl else "$baseUrl$imageUrl"
                Page(index, imageUrl = finalUrl)
            }
    }

    // ============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        OrderByFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
    )
}
