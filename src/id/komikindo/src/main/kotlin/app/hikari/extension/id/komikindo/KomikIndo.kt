package app.hikari.extension.id.komikindo

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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.util.Calendar
import java.util.Locale

class KomikIndo : KeiSource() {

    override val name: String = "Komik Indo"
    override val baseUrl: String = "https://komikindo.ch"
    override val lang: String = "id"
    override val supportsLatest: Boolean = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    // ============================== Browse & Search ==============================

    override suspend fun getPopularManga(page: Int): MangasPage =
        getSearchManga(page, "", FilterList(OrderFilter("popular")))

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        getSearchManga(page, "", FilterList(OrderFilter("update")))

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (page > 1) {
            "$baseUrl/daftar-manga/page/$page/".toHttpUrl().newBuilder()
        } else {
            "$baseUrl/daftar-manga/".toHttpUrl().newBuilder()
        }

        if (query.isNotBlank()) {
            url.addQueryParameter("title", query)
        }

        val filterList = filters.ifEmpty { getFilterList() }
        filterList.filterIsInstance<UriFilter>().forEach { it.addToUri(url) }

        val doc = client.get(url.build()).asJsoup()
        return parseMangaList(doc)
    }

    private fun parseMangaList(document: Document): MangasPage {
        val elements = document.select("div.animepost")
        val mangas = elements.mapNotNull { element ->
            val link = element.selectFirst("div.animposx > a") ?: return@mapNotNull null
            val rawTitle = element.selectFirst("div.tt h3")?.text()?.ifBlank { link.attr("title") } ?: link.attr("title")
            val cleanTitle = rawTitle.removePrefix("Komik ").trim()
            val thumb = element.selectFirst("div.limit img")?.imgAttr()

            SManga.create().apply {
                title = cleanTitle
                thumbnail_url = thumb
                setUrlWithoutDomain(link.attr("href"))
            }
        }

        val hasNextPage = document.selectFirst("div.pagination a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Deep Link ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "komik") return null
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
        title = document.selectFirst("h1.entry-title")?.text()?.removePrefix("Komik")?.trim().orEmpty()
        thumbnail_url = document.selectFirst("div.thumb img")?.imgAttr()

        val synopsis = document.selectFirst("div#sinopsis div.entry-content")?.textOrNull()
        val altName = document.selectFirst("div.spe span:has(> b:contains(Judul Alternatif))")?.ownText()?.removeEmptyPlaceholder()
        description = when {
            altName != null && synopsis != null -> "$synopsis\n\nJudul Alternatif: $altName"
            altName != null -> "Judul Alternatif: $altName"
            else -> synopsis
        }

        val statusText = document.selectFirst("div.spe span:has(> b:contains(Status))")?.text().orEmpty().lowercase()
        status = when {
            "berjalan" in statusText || "ongoing" in statusText -> SManga.ONGOING
            "tamat" in statusText || "completed" in statusText -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        author = document.selectFirst("div.spe span:has(> b:contains(Pengarang))")?.ownText()?.removeEmptyPlaceholder()
        artist = document.selectFirst("div.spe span:has(> b:contains(Ilustrator))")?.ownText()?.removeEmptyPlaceholder()

        val genres = document.select(
            "div.genre-info a, div.spe span:has(> b:contains(Tema)) a, div.spe span:has(> b:contains(Grafis)) a, div.spe span:has(> b:contains(Jenis Komik)) a",
        ).map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { g ->
                g.lowercase(Locale.ROOT).replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
                }
            }

        genre = genres.joinToString(", ")
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        return document.select("div#chapter_list ul li").mapNotNull { element ->
            val link = element.selectFirst("span.lchx a") ?: return@mapNotNull null
            val chapterName = link.text().trim()
            val chapterUrl = link.attr("href")
            val dateText = element.selectFirst("span.dt a")?.text()

            SChapter.create().apply {
                name = chapterName
                date_upload = dateText.parseChapterDate()
                setUrlWithoutDomain(chapterUrl)
            }
        }
    }

    // ============================== Reader Pages ==============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get(getChapterUrl(chapter)).asJsoup()
        val imgElements = doc.select("div#chimg-auh img").ifEmpty {
            doc.select("div.chapter-image img")
        }

        return imgElements.mapNotNull { it.imgAttr() }
            .filter { url ->
                url.isNotBlank() && !url.contains("blogger.googleusercontent.com")
            }
            .mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
    }

    // ============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        OrderFilter(),
        StatusFilter(),
        TypeFilter(),
        FormatFilter(),
        Filter.Separator(),
        GenreFilter(),
        TemaFilter(),
        DemografisFilter(),
        KontenFilter(),
    )

    // ============================== Helpers ==============================

    private fun String?.removeEmptyPlaceholder(): String? =
        if (this.isNullOrBlank() || this == "-" || this == "N/A" || this == "n/a" || this == "Unknown") null else this.trim()

    private fun String?.parseChapterDate(): Long {
        this ?: return 0L
        val trimmed = this.trim().lowercase()
        val number = trimmed.substringBefore(" ").toIntOrNull() ?: 1
        val now = Calendar.getInstance()
        when {
            "detik" in trimmed -> now.add(Calendar.SECOND, -number)
            "menit" in trimmed -> now.add(Calendar.MINUTE, -number)
            "jam" in trimmed -> now.add(Calendar.HOUR_OF_DAY, -number)
            "hari" in trimmed -> now.add(Calendar.DAY_OF_MONTH, -number)
            "minggu" in trimmed -> now.add(Calendar.WEEK_OF_YEAR, -number)
            "bulan" in trimmed -> now.add(Calendar.MONTH, -number)
            "tahun" in trimmed -> now.add(Calendar.YEAR, -number)
            else -> return 0L
        }
        return now.timeInMillis
    }
}
