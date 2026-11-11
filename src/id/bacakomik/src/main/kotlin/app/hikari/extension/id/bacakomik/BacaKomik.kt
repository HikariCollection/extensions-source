package app.hikari.extension.id.bacakomik

import app.hikari.extension.core.KeiSource
import app.hikari.extension.core.UriFilter
import app.hikari.extension.core.asJsoup
import app.hikari.extension.core.get
import app.hikari.extension.core.imgAttr
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
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class BacaKomik : KeiSource() {

    override val name: String = "BacaKomik"
    override val baseUrl: String = "https://bacakomik.my"
    override val lang: String = "id"
    override val supportsLatest: Boolean = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    private val chapterRegex = Regex("""Chapter\s+([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Jakarta")
    }

    private fun pagePath(page: Int) = if (page > 1) "page/$page/" else ""

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/daftar-komik/${pagePath(page)}?order=popular".toHttpUrl()
        val doc = client.get(url).asJsoup()
        return mangaListParse(doc)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/daftar-komik/${pagePath(page)}?order=update".toHttpUrl()
        val doc = client.get(url).asJsoup()
        return mangaListParse(doc)
    }

    // =============================== Search ===============================

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val builtUrl = if (page == 1) "$baseUrl/daftar-komik/" else "$baseUrl/daftar-komik/page/$page/?order="
        val urlBuilder = builtUrl.toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("title", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    if (filter.state.isNotBlank()) {
                        urlBuilder.addQueryParameter("author", filter.state)
                    }
                }
                is YearFilter -> {
                    if (filter.state.isNotBlank()) {
                        urlBuilder.addQueryParameter("yearx", filter.state)
                    }
                }
                is UriFilter -> filter.addToUri(urlBuilder)
                else -> {}
            }
        }

        val doc = client.get(urlBuilder.build()).asJsoup()
        return mangaListParse(doc)
    }

    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select("div.animepost").mapNotNull { element ->
            val link = element.selectFirst("div.animposx > a") ?: return@mapNotNull null
            val rawTitle = element.selectFirst(".animposx .tt h4")?.text()?.ifBlank { link.attr("title") } ?: link.attr("title")
            val cleanTitle = rawTitle.removePrefix("Komik ").trim()
            val thumb = element.selectFirst("div.limit img")?.imgAttr()

            SManga.create().apply {
                title = cleanTitle
                thumbnail_url = thumb
                setUrlWithoutDomain(link.attr("href"))
            }
        }

        val hasNextPage = document.select("a.next.page-numbers").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Deep Link ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "komik") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val targetUrl = "$baseUrl/komik/$slug/".toHttpUrl()
        val manga = SManga.create().apply { setUrlWithoutDomain(targetUrl.encodedPath) }
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
                parseChapters(doc)
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
        title = document.selectFirst("h1.entry-title")?.text()?.removePrefix("Komik ")?.trim().orEmpty()

        val descElement = document.selectFirst("div#sinopsis div.entry-content, .desc")
        val pText = descElement?.select("p")?.text().orEmpty()
        description = pText.substringAfter("bercerita tentang ").ifEmpty { pText }

        thumbnail_url = document.selectFirst(".thumb > img:nth-child(1), .thumb img")?.imgAttr()

        val authorElement = document.selectFirst("div.spe span:has(> b:contains(Pengarang))")
        author = authorElement?.ownText()?.removeEmptyPlaceholder()

        val artistElement = document.selectFirst("div.spe span:has(> b:contains(Ilustrator))")
        artist = artistElement?.ownText()?.removeEmptyPlaceholder() ?: author

        val statusText = document.selectFirst("div.spe span:has(> b:contains(Status))")?.text().orEmpty().lowercase()
        status = when {
            "berjalan" in statusText || "ongoing" in statusText -> SManga.ONGOING
            "tamat" in statusText || "completed" in statusText -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        val genres = document.select("div.genre-info a, div.spe span:has(> b:contains(Tema)) a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .map { g ->
                g.lowercase(Locale.ROOT).replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
                }
            }

        genre = genres.joinToString(", ")
    }

    private fun parseChapters(document: Document): List<SChapter> {
        return document.select("#chapter_list li").mapNotNull { element ->
            val urlElement = element.selectFirst(".lchx a") ?: return@mapNotNull null
            val chapterName = urlElement.text().trim()
            val chapterUrl = urlElement.attr("href")
            val dateText = element.selectFirst(".dt a")?.text()

            SChapter.create().apply {
                name = chapterName
                setUrlWithoutDomain(chapterUrl)
                chapterRegex.find(chapterName)?.let {
                    chapter_number = it.groupValues[1].toFloatOrNull() ?: -1f
                }
                date_upload = dateText?.let { parseChapterDate(it) } ?: 0L
            }
        }
    }

    private fun parseChapterDate(date: String): Long {
        val trimmed = date.trim().lowercase()
        if (trimmed.contains("yang lalu")) {
            val value = trimmed.substringBefore(' ').trim().toIntOrNull() ?: return 0L
            val calendar = Calendar.getInstance()
            when {
                "detik" in trimmed -> calendar.add(Calendar.SECOND, -value)
                "menit" in trimmed -> calendar.add(Calendar.MINUTE, -value)
                "jam" in trimmed -> calendar.add(Calendar.HOUR_OF_DAY, -value)
                "hari" in trimmed -> calendar.add(Calendar.DATE, -value)
                "minggu" in trimmed -> calendar.add(Calendar.DATE, -value * 7)
                "bulan" in trimmed -> calendar.add(Calendar.MONTH, -value)
                "tahun" in trimmed -> calendar.add(Calendar.YEAR, -value)
                else -> return 0L
            }
            return calendar.timeInMillis
        } else {
            return try {
                dateFormat.parse(date)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl).asJsoup()

        return document.select("div:has(>img[alt*=\"Chapter\"]) img")
            .filter { it.parent()?.tagName() != "noscript" }
            .mapIndexedNotNull { i, element ->
                val onerror = element.attr("onError")
                val url = if (onerror.contains("src='")) {
                    onerror.substringAfter("src='").substringBefore("';")
                } else {
                    element.attr("data-lazy-src").ifEmpty { element.attr("src") }
                }
                if (url.isNotBlank()) Page(i, imageUrl = url) else null
            }
    }

    // ============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Diabaikan jika menggunakan pencarian teks"),
        Filter.Separator(),
        AuthorFilter(),
        YearFilter(),
        StatusFilter(),
        TypeFilter(),
        SortByFilter(),
        GenreFilter(),
    )

    // ============================== Helpers ==============================

    private fun String?.removeEmptyPlaceholder(): String? =
        if (this.isNullOrBlank() || this == "-" || this == "N/A" || this == "n/a" || this == "Unknown") null else this.trim()
}
