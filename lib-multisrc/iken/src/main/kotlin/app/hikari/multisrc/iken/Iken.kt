package app.hikari.multisrc.iken

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

abstract class Iken(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : KeiSource() {

    override val supportsLatest: Boolean = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        set("Referer", "$baseUrl/")
        set("Origin", baseUrl)
        set("Accept", "application/json, text/plain, */*")
    }

    protected open val apiUrl: String get() = "$baseUrl/api"

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
                    val allChapters = mutableListOf<SChapter>()
                    var skip = 0
                    val take = 500
                    while (true) {
                        val chUrl = "$apiUrl/chapters".toHttpUrl().newBuilder()
                            .addQueryParameter("postId", post.id.toString())
                            .addQueryParameter("skip", skip.toString())
                            .addQueryParameter("take", take.toString())
                            .build()
                        val chResponse = client.get(chUrl).parseAs<ChaptersResponse>()
                        val batch = chResponse.post?.chapters.orEmpty()
                        if (batch.isEmpty()) break
                        allChapters.addAll(batch.map { it.toSChapter(slug) })
                        skip += take
                        if (chResponse.totalChapterCount > 0 && allChapters.size >= chResponse.totalChapterCount) break
                        if (batch.size < take) break
                    }
                    allChapters
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

    protected open fun getGenreList(): List<Pair<String, String>> = emptyList()
}
