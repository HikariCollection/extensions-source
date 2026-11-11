package app.hikari.extension.en.vortexscans

import app.hikari.extension.core.KeiSource
import app.hikari.extension.core.get
import app.hikari.extension.core.parseAs
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

class VortexScans : KeiSource() {

    override val name: String = "Vortex Scans"
    override val baseUrl: String = "https://vortexscans.org"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        set("Referer", "$baseUrl/")
        set("Origin", baseUrl)
        set("Accept", "application/json, text/plain, */*")
    }

    private val apiUrl: String = "https://vortexscans.org/api"

    // ============================== Browse & Search ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/query".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", "18")
            .addQueryParameter("orderBy", "views")
            .addQueryParameter("orderRule", "desc")
            .build()

        val response = client.get(url).parseAs<SearchResponse>()
        val mangas = response.posts.filterNot { it.isNovel }.map { it.toSManga() }
        val hasNextPage = page * 18 < response.totalCount
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/query".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", "18")
            .addQueryParameter("orderBy", "updatedAt")
            .addQueryParameter("orderRule", "desc")
            .build()

        val response = client.get(url).parseAs<SearchResponse>()
        val mangas = response.posts.filterNot { it.isNovel }.map { it.toSManga() }
        val hasNextPage = page * 18 < response.totalCount
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val urlBuilder = "$apiUrl/query".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", "18")

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("searchTerm", query.trim())
        }

        filters.forEach { filter ->
            if (filter is UrlPartFilter) {
                filter.addUrlParameter(urlBuilder)
            }
        }

        val response = client.get(urlBuilder.build()).parseAs<SearchResponse>()
        val mangas = response.posts.filterNot { it.isNovel }.map { it.toSManga() }
        val hasNextPage = page * 18 < response.totalCount
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Deep Link ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "series") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val mangaUrl = "/series/$slug"
        val manga = SManga.create().apply { setUrlWithoutDomain(mangaUrl) }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // ============================== Details & Chapters ==============================

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url.removeSuffix("/").substringAfterLast("/")

        val detailsDeferred = async {
            if (fetchDetails || fetchChapters) {
                val detailUrl = "$apiUrl/post".toHttpUrl().newBuilder()
                    .addQueryParameter("postSlug", slug)
                    .build()
                client.get(detailUrl).parseAs<MangaDetailResponse>().post
            } else {
                null
            }
        }

        val chaptersDeferred = async {
            if (fetchChapters) {
                val post = detailsDeferred.await()
                if (post != null) {
                    val chUrl = "$apiUrl/chapters".toHttpUrl().newBuilder()
                        .addQueryParameter("postId", post.id.toString())
                        .addQueryParameter("skip", "0")
                        .addQueryParameter("take", "500")
                        .build()
                    val chResponse = client.get(chUrl).parseAs<ChaptersResponse>()
                    chResponse.post?.chapters?.map { it.toSChapter(slug) } ?: emptyList()
                } else {
                    chapters
                }
            } else {
                chapters
            }
        }

        val post = detailsDeferred.await()
        val updatedManga = if (fetchDetails && post != null) {
            post.toSManga()
        } else {
            manga
        }

        SMangaUpdate(
            manga = updatedManga,
            chapters = chaptersDeferred.await(),
        )
    }

    // ============================== Reader Pages ==============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast("#").toIntOrNull()
            ?: error("Missing chapter ID in URL: ${chapter.url}")

        val url = "$apiUrl/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("chapterId", chapterId.toString())
            .build()

        val response = client.get(url).parseAs<ChapterDetailResponse>()
        return response.chapter.images
            .sortedBy { it.order ?: 0 }
            .mapIndexed { index, image ->
                Page(index, imageUrl = image.url)
            }
    }

    // ============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(getGenreList()),
    )

    private fun getGenreList(): List<Pair<String, String>> = listOf(
        Pair("Action", "1"),
        Pair("Adventure", "18"),
        Pair("Comedy", "7"),
        Pair("Drama", "2"),
        Pair("Fantasy", "8"),
        Pair("Historical", "14"),
        Pair("Horror", "9"),
        Pair("Isekai", "35"),
        Pair("Magic", "27"),
        Pair("Martial Arts", "6"),
        Pair("Mystery", "11"),
        Pair("Psychological", "15"),
        Pair("Reincarnation", "19"),
        Pair("Romance", "16"),
        Pair("Sci-fi", "20"),
        Pair("Seinen", "10"),
        Pair("Shounen", "3"),
        Pair("Slice of Life", "21"),
        Pair("Sports", "4"),
        Pair("Supernatural", "22"),
        Pair("Tragedy", "17"),
    )
}
