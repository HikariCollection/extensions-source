package app.hikari.extension.id.shinigami

import app.hikari.extension.core.KeiSource
import app.hikari.extension.core.get
import app.hikari.extension.core.parseAs
import app.hikari.extension.core.tryParseInstant
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
import okhttp3.Response

class Shinigami : KeiSource() {

    override val name: String = "Shinigami"
    override val lang: String = "id"
    override val baseUrl: String = "https://11.shinigami.asia"
    override val id: Long = ID

    override val supportsLatest: Boolean = true

    private val apiUrl = "https://api.shngm.io"

    private val apiHeaders: Headers
        get() = headersBuilder()
            .add("Accept", "application/json")
            .add("DNT", "1")
            .add("Sec-GPC", "1")
            .build()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    // ====================== Popular ======================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/v1/manga/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "30")
            .addQueryParameter("sort", "popularity")
            .build()

        val response = client.get(url, apiHeaders)
        return parseMangaList(response)
    }

    // ====================== Latest ======================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/v1/manga/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "30")
            .addQueryParameter("sort", "latest")
            .build()

        val response = client.get(url, apiHeaders)
        return parseMangaList(response)
    }

    // ====================== Search ======================

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/v1/manga/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "30")

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        filters.filterIsInstance<UriFilter>().forEach {
            it.addToUri(url)
        }

        val response = client.get(url.build(), apiHeaders)
        return parseMangaList(response)
    }

    private fun parseMangaList(response: Response): MangasPage {
        val rootObject = response.parseAs<ShinigamiBrowseDto>()
        val projectList = rootObject.data.map(::popularMangaFromObject)
        val hasNextPage = rootObject.meta.totalPage?.let { rootObject.meta.page < it } ?: false
        return MangasPage(projectList, hasNextPage)
    }

    private fun popularMangaFromObject(obj: ShinigamiBrowseDataDto): SManga = SManga.create().apply {
        title = obj.title.orEmpty()
        thumbnail_url = obj.thumbnail
        url = obj.mangaId.orEmpty()
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        SortOrderFilter(),
        StatusFilter(),
        FormatFilter(),
        TypeFilter(),
        GenreFilter(),
    )

    // ====================== Manga Details & Chapters ======================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != "shinigami.asia" && !url.host.endsWith(".shinigami.asia")) return null
        val firstSegment = url.pathSegments.firstOrNull()
        if (firstSegment != "series") return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null

        val manga = SManga.create().apply {
            this.url = id
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        if (manga.url.startsWith("/series/")) {
            throw Exception("Migrate dari $name ke $name (ekstensi yang sama)")
        }

        val detailsDeferred = if (fetchDetails) {
            async { getMangaDetails(manga) }
        } else {
            null
        }

        val chaptersDeferred = if (fetchChapters) {
            async { getChapterList(manga) }
        } else {
            null
        }

        val updatedManga = detailsDeferred?.await() ?: manga
        val updatedChapters = chaptersDeferred?.await() ?: chapters

        SMangaUpdate(updatedManga, updatedChapters)
    }

    private suspend fun getMangaDetails(manga: SManga): SManga {
        val response = client.get("$apiUrl/v1/manga/detail/${manga.url}", apiHeaders)
        val mangaDetailsResponse = response.parseAs<ShinigamiMangaDetailDto>()
        val mangaDetails = mangaDetailsResponse.data

        return manga.apply {
            mangaDetails.title?.takeIf { it.isNotBlank() }?.let { title = it }
            mangaDetails.thumbnail?.let { thumbnail_url = it }
            author = mangaDetails.taxonomy["Author"]?.joinToString { it.name }.orEmpty()
            artist = mangaDetails.taxonomy["Artist"]?.joinToString { it.name }.orEmpty()
            status = mangaDetails.status.toStatus()
            description = buildString {
                append(mangaDetails.description)
                mangaDetails.altTitle?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append("\n\n")
                    append("Alternative Title: ").append(it)
                }
            }

            val genres = mangaDetails.taxonomy["Genre"]?.joinToString { it.name }.orEmpty()
            val type = mangaDetails.taxonomy["Format"]?.joinToString { it.name }.orEmpty()
            genre = listOf(genres, type).filter { it.isNotBlank() }.joinToString()
        }
    }

    private fun Int.toStatus(): Int = when (this) {
        1 -> SManga.ONGOING
        2 -> SManga.COMPLETED
        3 -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val response = client.get("$apiUrl/v1/chapter/${manga.url}/list?page_size=3000", apiHeaders)
        val result = response.parseAs<ShinigamiChapterListDto>()
        return result.chapterList.map(::chapterFromObject)
    }

    private fun chapterFromObject(obj: ShinigamiChapterListDataDto): SChapter = SChapter.create().apply {
        date_upload = tryParseInstant(obj.date)
        name = "Chapter ${obj.name.toString().removeSuffix(".0")} ${obj.title}".trim()
        chapter_number = obj.name.toFloat()
        url = obj.chapterId
    }

    // ====================== Page List ======================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.url.startsWith("/series/")) {
            throw Exception("Migrate dari $name ke $name (ekstensi yang sama)")
        }

        val response = client.get("$apiUrl/v1/chapter/detail/${chapter.url}", apiHeaders)
        val result = response.parseAs<ShinigamiPageListDto>()

        return result.pageList.chapterPage.pages.mapIndexed { index, imageName ->
            Page(index, imageUrl = "${result.pageList.baseUrl}${result.pageList.chapterPage.path}$imageName")
        }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .add("DNT", "1")
            .add("Sec-GPC", "1")
            .build()

        return Request.Builder()
            .url(page.imageUrl!!)
            .headers(imageHeaders)
            .get()
            .build()
    }

    companion object {
        const val ID = 3411809758861089969L
    }
}

