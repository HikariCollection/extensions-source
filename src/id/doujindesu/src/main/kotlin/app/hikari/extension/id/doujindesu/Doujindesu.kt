package app.hikari.extension.id.doujindesu

import app.hikari.extension.core.KeiSource
import app.hikari.extension.core.UriFilter
import app.hikari.extension.core.get
import app.hikari.extension.core.tryParseInstant
import app.hikari.source.model.FilterList
import app.hikari.source.model.MangasPage
import app.hikari.source.model.Page
import app.hikari.source.model.SChapter
import app.hikari.source.model.SManga
import app.hikari.source.model.SMangaUpdate
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

class Doujindesu : KeiSource() {

    override val name: String = "Doujindesu"
    override val lang: String = "id"
    override val baseUrl: String = "https://doujin.desu.xxx"
    override val supportsLatest: Boolean = true

    private val apiUrl: String = "$baseUrl/api"

    private val apiHeaders: Headers
        get() = headersBuilder()
            .add("Accept", "application/json")
            .add("Referer", "$baseUrl/")
            .build()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    // ====================== Popular ======================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val limit = 20
        val offset = (page - 1) * limit
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("sort", "views")
            .build()

        val response = client.get(url, apiHeaders)
        return parseMangaList(response, limit)
    }

    // ====================== Latest ======================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val limit = 20
        val offset = (page - 1) * limit
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("sort", "latest_chapter")
            .build()

        val response = client.get(url, apiHeaders)
        return parseMangaList(response, limit)
    }

    // ====================== Search ======================

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val limit = 20
        val offset = (page - 1) * limit
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("search", query.trim())
        }

        filters.filterIsInstance<UriFilter>().forEach {
            it.addToUri(url)
        }

        val response = client.get(url.build(), apiHeaders)
        return parseMangaList(response, limit)
    }

    private fun parseMangaList(response: Response, limit: Int): MangasPage {
        val totalCount = response.header("x-total-count")?.toIntOrNull()
        val mangaDtos = response.parseEncrypted<List<DoujindesuMangaDto>>()
        val mangas = mangaDtos.map(::mangaFromObject)
        val hasNextPage = if (totalCount != null) {
            mangas.size >= limit
        } else {
            mangas.size == limit
        }
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromObject(dto: DoujindesuMangaDto): SManga = SManga.create().apply {
        title = dto.title.trim()
        thumbnail_url = dto.coverUrl
        url = dto.slug.ifBlank { dto.id }
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
    )

    // ====================== Manga Details & Chapters ======================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val host = url.host
        val validHost = host == "doujin.desu.xxx" ||
            host == "doujindesu.tv" ||
            host == "doujindesu.xxx" ||
            host.endsWith(".desu.xxx")
        if (!validHost) return null

        val segments = url.pathSegments
        val firstSegment = segments.firstOrNull() ?: return null
        if (firstSegment != "manga") return null
        val slug = segments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null

        val manga = SManga.create().apply {
            this.url = slug
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        if (!fetchDetails && !fetchChapters) {
            return@coroutineScope SMangaUpdate(manga, chapters)
        }

        val response = client.get("$apiUrl/manga/${manga.url}", apiHeaders)
        val detailDto = response.parseEncrypted<DoujindesuMangaDto>()

        val updatedManga = if (fetchDetails) {
            manga.apply {
                detailDto.title.takeIf { it.isNotBlank() }?.let { title = it.trim() }
                detailDto.coverUrl?.let { thumbnail_url = it }
                author = detailDto.author?.trim().orEmpty()
                artist = detailDto.artist?.trim().orEmpty()
                status = parseStatus(detailDto.status)
                description = buildString {
                    val synopsis = cleanDescription(detailDto.description)
                    if (synopsis.isNotBlank()) {
                        append(synopsis)
                    }
                    detailDto.altTitles?.takeIf { it.isNotBlank() }?.let { alt ->
                        val cleanAlt = alt.split('|', ',')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .joinToString(", ")
                        if (cleanAlt.isNotEmpty()) {
                            if (isNotEmpty()) append("\n\n")
                            append("Alternative Title: ").append(cleanAlt)
                        }
                    }
                }
                val genres = detailDto.mangaGenres.mapNotNull { it.genres?.name?.trim('\"', ' ') }
                val type = detailDto.type?.replaceFirstChar { it.uppercase() }
                genre = (listOfNotNull(type) + genres).filter { it.isNotBlank() }.distinct().joinToString()
            }
        } else {
            manga
        }

        val updatedChapters = if (fetchChapters) {
            val mangaTitle = updatedManga.title
            detailDto.chapters
                .filter { !it.isHoneypot }
                .map { chapterFromObject(it, mangaTitle) }
        } else {
            chapters
        }

        SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun cleanDescription(rawHtml: String?): String {
        if (rawHtml.isNullOrBlank()) return ""
        var decoded = Parser.unescapeEntities(rawHtml, false)
        while (decoded.contains('&') && decoded.contains(';')) {
            val prev = decoded
            decoded = Parser.unescapeEntities(decoded, false)
            if (decoded == prev) break
        }

        val doc = Jsoup.parseBodyFragment(decoded)
        val pElements = doc.select("p")

        val paragraphs = if (pElements.isNotEmpty()) {
            pElements.mapNotNull { p ->
                val pText = p.text()
                if (pText.contains("Download Batch", ignoreCase = true) ||
                    p.select("a[href*='mirrored.to'], a[href*='mir.cr']").isNotEmpty()
                ) {
                    null
                } else {
                    pText
                        .replace(Regex("^(?i)\\s*(?:sinopsis|synopsis)\\s*:\\s*"), "")
                        .trim()
                        .takeIf { it.isNotBlank() }
                }
            }
        } else {
            doc.select("a[href*='mirrored.to'], a[href*='mir.cr']").remove()
            val text = doc.body().text()
                .replace(Regex("(?i)download\\s*batch.*"), "")
                .replace(Regex("^(?i)\\s*(?:sinopsis|synopsis)\\s*:\\s*"), "")
                .trim()
            if (text.isNotBlank()) listOf(text) else emptyList()
        }

        return paragraphs.joinToString("\n\n")
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun chapterFromObject(obj: DoujindesuChapterDto, mangaTitle: String): SChapter = SChapter.create().apply {
        url = obj.id
        chapter_number = obj.chapterNumber.toFloat()
        date_upload = tryParseInstant(obj.createdAt)
        val numberString = obj.chapterNumber.toString().removeSuffix(".0")
        var rawTitle = obj.title?.trim().orEmpty()

        if (mangaTitle.isNotBlank() && rawTitle.startsWith(mangaTitle, ignoreCase = true)) {
            rawTitle = rawTitle.substring(mangaTitle.length).trim()
            rawTitle = rawTitle.replace(Regex("^[-–—:]+\\s*"), "").trim()
        }

        val chapterRegex = Regex("(?i)^(?:chapter|ch\\.?)\\s*(\\d+(?:\\.\\d+)?)\\s*(.*)$")
        val match = chapterRegex.find(rawTitle)

        name = when {
            match != null -> {
                val extra = match.groupValues[2].trim().replace(Regex("^[-–—:]+\\s*"), "").trim()
                if (extra.isNotEmpty()) {
                    "Chapter $numberString - $extra"
                } else {
                    "Chapter $numberString"
                }
            }
            rawTitle.isEmpty() || rawTitle.equals(mangaTitle, ignoreCase = true) -> {
                "Chapter $numberString"
            }
            else -> {
                "Chapter $numberString - $rawTitle"
            }
        }
    }

    // ====================== Page List ======================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$apiUrl/chapters/${chapter.url}", apiHeaders)
        val detail = response.parseEncrypted<DoujindesuChapterDetailDto>()

        return detail.contentUrls.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .add("Referer", "$baseUrl/")
            .add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build()

        return Request.Builder()
            .url(page.imageUrl!!)
            .headers(imageHeaders)
            .get()
            .build()
    }
}
