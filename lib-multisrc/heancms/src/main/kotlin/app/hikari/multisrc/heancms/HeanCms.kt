package app.hikari.multisrc.heancms

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

abstract class HeanCms(
    override val name: String = "",
    override val baseUrl: String = "",
    override val lang: String = "",
) : KeiSource() {

    override val supportsLatest: Boolean = true

    protected open val apiUrl: String get() = "$baseUrl/api"

    protected open val perPage: Int = 18

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    override fun Headers.Builder.configureHeaders(): Headers.Builder {
        return add("Accept", "application/json, text/plain, */*")
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/query".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", perPage.toString())
            .addQueryParameter("series_type", "Comic")
            .addQueryParameter("adult", "true")
            .addQueryParameter("order", "desc")
            .addQueryParameter("orderBy", "total_views")
            .build()

        val response = client.get(url)
        val dto = response.parseAs<HeanCmsQueryDto>()
        val mangas = dto.data.map { it.toSManga(baseUrl) }
        val hasNext = dto.meta?.nextPageUrl != null
        return MangasPage(mangas, hasNext)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/query".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", perPage.toString())
            .addQueryParameter("series_type", "Comic")
            .addQueryParameter("adult", "true")
            .addQueryParameter("order", "desc")
            .addQueryParameter("orderBy", "latest")
            .build()

        val response = client.get(url)
        val dto = response.parseAs<HeanCmsQueryDto>()
        val mangas = dto.data.map { it.toSManga(baseUrl) }
        val hasNext = dto.meta?.nextPageUrl != null
        return MangasPage(mangas, hasNext)
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val urlBuilder = "$apiUrl/query".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", perPage.toString())
            .addQueryParameter("adult", "true")

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("query_string", query.trim())
        }

        var order = "desc"
        var orderBy = "total_views"
        var status = ""
        var seriesType = "Comic"

        for (filter in filters) {
            when (filter) {
                is SortFilter -> {
                    orderBy = filter.sort
                    order = if (filter.isAscending) "asc" else "desc"
                }
                is StatusFilter -> {
                    status = filter.selected
                }
                is TypeFilter -> {
                    if (filter.selected.isNotBlank()) {
                        seriesType = filter.selected
                    }
                }
                else -> Unit
            }
        }

        urlBuilder.addQueryParameter("order", order)
        urlBuilder.addQueryParameter("orderBy", orderBy)
        if (status.isNotBlank()) {
            urlBuilder.addQueryParameter("status", status)
        }
        urlBuilder.addQueryParameter("series_type", seriesType)

        val response = client.get(urlBuilder.build())
        val dto = response.parseAs<HeanCmsQueryDto>()
        val mangas = dto.data.map { it.toSManga(baseUrl) }
        val hasNext = dto.meta?.nextPageUrl != null
        return MangasPage(mangas, hasNext)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "series") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        val response = client.get("$apiUrl/series/$slug")
        val dto = response.parseAs<HeanCmsSeriesDto>()
        return dto.toSManga(baseUrl)
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url.removePrefix("/series/").trim('/')

        val detailsDeferred = async {
            if (fetchDetails || fetchChapters) {
                client.get("$apiUrl/series/$slug").parseAs<HeanCmsSeriesDto>()
            } else {
                null
            }
        }

        val detailsDto = detailsDeferred.await()
        val updatedManga = if (fetchDetails && detailsDto != null) {
            detailsDto.toSManga(baseUrl)
        } else {
            manga
        }

        val updatedChapters = if (fetchChapters && detailsDto != null) {
            fetchAllChapters(detailsDto.id, slug)
        } else {
            chapters
        }

        SMangaUpdate(updatedManga, updatedChapters)
    }

    private suspend fun fetchAllChapters(seriesId: Int, seriesSlug: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var page = 1
        do {
            val url = "$apiUrl/chapter/query".toHttpUrl().newBuilder()
                .addQueryParameter("series_id", seriesId.toString())
                .addQueryParameter("perPage", "100")
                .addQueryParameter("page", page.toString())
                .build()

            val response = client.get(url)
            val dto = response.parseAs<HeanCmsChapterQueryDto>()
            chapters.addAll(dto.data.map { it.toSChapter(seriesSlug) })
            val hasNext = dto.meta?.nextPageUrl != null && dto.data.isNotEmpty()
            page++
        } while (hasNext)

        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val path = chapter.url.removePrefix("/chapter/").trim('/')
        val response = client.get("$apiUrl/chapter/$path")
        val dto = response.parseAs<HeanCmsChapterDetailsDto>()
        val images = dto.chapter.chapterData?.images.orEmpty()
        return images.mapIndexed { index, imgUrl ->
            Page(index, imageUrl = if (imgUrl.startsWith("http")) imgUrl else "$baseUrl/$imgUrl")
        }
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
    )
}
