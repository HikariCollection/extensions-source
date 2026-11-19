package app.hikari.extension.id.westmanga

import app.hikari.extension.core.KeiSource
import app.hikari.extension.core.UriFilter
import app.hikari.extension.core.get
import app.hikari.extension.core.parseAs
import app.hikari.network.HttpException
import app.hikari.source.model.FilterList
import app.hikari.source.model.MangasPage
import app.hikari.source.model.Page
import app.hikari.source.model.SChapter
import app.hikari.source.model.SManga
import app.hikari.source.model.SMangaUpdate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class WestManga : KeiSource() {

    override val name: String = "West Manga"
    override val baseUrl: String = "https://v1.westmanga.my"
    override val lang: String = "id"
    override val supportsLatest: Boolean = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        set("Referer", "$baseUrl/")
        set("Origin", baseUrl)
        set("Accept", "application/json, text/plain, */*")
    }

    private val visitTokenMutex = Mutex()
    private var visitToken: String? = null
    private var visitTokenExpiresAt: Long = 0L

    private suspend fun getVisitToken(forceRefresh: Boolean = false): String? = visitTokenMutex.withLock {
        val now = System.currentTimeMillis()
        if (!forceRefresh && visitToken != null && now < visitTokenExpiresAt - 300_000L) {
            return@withLock visitToken
        }

        val url = "$baseUrl/api/visit-token".toHttpUrl()
        val response = runCatching {
            val headers = headersBuilder().set("X-Requested-With", "XMLHttpRequest").build()
            client.get(url, headers = headers).parseAs<VisitTokenResponse>()
        }.getOrNull()

        if (response?.status == true && !response.token.isNullOrBlank()) {
            visitToken = response.token
            visitTokenExpiresAt = (response.expiresAt?.times(1000L)) ?: (now + 3_600_000L)
            return@withLock visitToken
        }

        if (forceRefresh) {
            visitToken = null
            visitTokenExpiresAt = 0L
        }
        return@withLock visitToken
    }

    private suspend fun apiHeaders(): Headers = headersBuilder().apply {
        set("X-Requested-With", "XMLHttpRequest")
        getVisitToken()?.let { set("X-WM-Visit", it) }
    }.build()

    private suspend inline fun <reified T> apiGet(url: HttpUrl): T {
        return try {
            client.get(url, headers = apiHeaders()).parseAs<T>()
        } catch (e: HttpException) {
            if (e.code == 403 || e.code == 404) {
                getVisitToken(forceRefresh = true)
                client.get(url, headers = apiHeaders()).parseAs<T>()
            } else {
                throw e
            }
        }
    }

    // ============================== Browse & Search ==============================

    override suspend fun getPopularManga(page: Int): MangasPage =
        getSearchManga(page, "", SortFilter.popular)

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        getSearchManga(page, "", SortFilter.latest)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/contents".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "20")
            addQueryParameter("type", "Comic")
            val filterList = filters.ifEmpty { getFilterList() }
            filterList.filterIsInstance<UriFilter>().forEach {
                it.addToUri(this)
            }
        }.build()

        val data = apiGet<PaginatedData<BrowseManga>>(url)
        val entries = data.data.map { it.toSManga(baseUrl) }
        return MangasPage(entries, data.paginator.hasNextPage())
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        CountryFilter(),
        ColorFilter(),
        GenreFilter(),
    )

    // ============================== Manga Details & Chapters ==============================

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val path = "$baseUrl${manga.url}".toHttpUrl().pathSegments
        val slug = when {
            path.size >= 2 -> path[1]
            path.isNotEmpty() -> path[0]
            else -> manga.url.trim('/').substringAfterLast('/')
        }
        val api = "$baseUrl/api/comic/$slug".toHttpUrl()
        val data = apiGet<Data<Manga>>(api).data
        val updatedManga = if (fetchDetails) data.toSManga(baseUrl) else manga
        val updatedChapters = if (fetchChapters) data.chapters.map { it.toSChapter() } else chapters
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = when (url.pathSegments.firstOrNull()) {
            "comic", "manga" -> url.pathSegments.getOrNull(1)
            else -> null
        } ?: return null
        val api = "$baseUrl/api/comic/$slug".toHttpUrl()
        return apiGet<Data<Manga>>(api).data.toSManga(baseUrl)
    }

    // ============================== Chapter Reader ==============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val path = "$baseUrl${chapter.url}".toHttpUrl().pathSegments
        val slug = path.firstOrNull() ?: chapter.url.trim('/')
        val url = "$baseUrl/api/v/$slug".toHttpUrl()
        val data = apiGet<Data<ImageList>>(url).data
        return data.images.mapIndexed { idx, img ->
            val imgUrl = if (img.startsWith("http")) img else "$baseUrl$img"
            Page(idx, imageUrl = imgUrl)
        }
    }
}
