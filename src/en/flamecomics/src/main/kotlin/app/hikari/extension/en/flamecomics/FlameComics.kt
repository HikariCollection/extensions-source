package app.hikari.extension.en.flamecomics

import app.hikari.extension.core.KeiSource
import app.hikari.extension.core.asJsoup
import app.hikari.extension.core.extractNextJs
import app.hikari.extension.core.get
import app.hikari.extension.core.parseAs
import app.hikari.source.model.FilterList
import app.hikari.source.model.MangasPage
import app.hikari.source.model.Page
import app.hikari.source.model.SChapter
import app.hikari.source.model.SManga
import app.hikari.source.model.SMangaUpdate
import kotlinx.serialization.json.JsonObject
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class FlameComics : KeiSource() {

    override val name: String = "Flame Comics"
    override val lang: String = "en"
    override val baseUrl: String = "https://flamecomics.xyz"
    override val id: Long = 8531542650987673943L
    override val supportsLatest: Boolean = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder =
        addInterceptor(::buildIdOutdatedInterceptor)
            .rateLimit(2, 2.seconds) { it.fragment != THUMBNAIL_FRAGMENT }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val series = fetchBrowseSeries().sortedBy { it.popularityRank ?: Int.MAX_VALUE }
        return MangasPage(series.mapNotNull { it.toSManga() }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val series = client.get(dataUrl { addPathSegment("latest.json") }, xhrHeaders())
            .parseAs<NextDataDto<LatestDto>>().pageProps.allSeries
        return MangasPage(series.mapNotNull { it.toSManga() }, false)
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val normalizedQuery = query.normalizeTitle()
        val series = fetchBrowseSeries().filter { normalizedQuery in it.title.normalizeTitle() }
        return MangasPage(series.mapNotNull { it.toSManga() }, false)
    }

    private suspend fun fetchBrowseSeries(): List<SeriesDto> =
        client.get(dataUrl { addPathSegment("browse.json") }, xhrHeaders())
            .parseAs<NextDataDto<BrowseDto>>().pageProps.series

    private fun String.normalizeTitle() = SPECIAL_CHARS_REGEX.replace(lowercase(), "")

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "series") return null
        val seriesId = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        val manga = SManga.create().apply { this.url = "/series/$seriesId" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val seriesId = getMangaUrl(manga).toHttpUrl().pathSegments.last()
        val seriesPage = client.get(
            dataUrl {
                addPathSegment("series")
                addPathSegment("$seriesId.json")
                addQueryParameter("id", seriesId)
            },
            xhrHeaders(),
        ).parseAs<NextDataDto<SeriesPageDto>>().pageProps

        return SMangaUpdate(
            manga = seriesPage.series.toSMangaDetails(),
            chapters = seriesPage.chapters.map { it.toSChapter() },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (seriesId, token) = getChapterUrl(chapter).toHttpUrl().pathSegments.drop(1)
        return client.get(
            dataUrl {
                addPathSegment("series")
                addPathSegment(seriesId)
                addPathSegment("$token.json")
                addQueryParameter("id", seriesId)
                addQueryParameter("token", token)
            },
            xhrHeaders(),
        ).parseAs<NextDataDto<ChapterPageDto>>().pageProps.chapter.toPages()
    }

    private fun xhrHeaders(): Headers = headersBuilder()
        .set("X-Requested-With", "XMLHttpRequest")
        .build()

    @Volatile
    private var buildId: String? = null

    private suspend fun dataUrl(path: HttpUrl.Builder.() -> Unit): HttpUrl {
        val id = buildId?.takeIf { it.isNotBlank() }
            ?: client.get(baseUrl).asJsoup().extractNextJs<BuildIdDto> { it is JsonObject && "buildId" in it }?.buildId?.takeIf { it.isNotBlank() }?.also { buildId = it }
            ?: throw Exception("Failed to find buildId")

        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("_next")
            .addPathSegment("data")
            .addPathSegment(id)
            .apply(path)
            .build()
    }

    private fun buildIdOutdatedInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url

        if (
            response.code != 404 ||
            url.host != baseUrl.toHttpUrl().host ||
            url.pathSegments.getOrNull(0) != "_next" ||
            url.pathSegments.getOrNull(1) != "data" ||
            url.pathSegments.size < 3
        ) {
            return response
        }
        response.close()

        val homeRequest = request.newBuilder()
            .url(baseUrl)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        val newBuildId = chain.proceed(homeRequest).asJsoup().extractNextJs<BuildIdDto> { it is JsonObject && "buildId" in it }?.buildId?.takeIf { it.isNotBlank() }
            ?: throw IOException("Failed to find buildId")
        buildId = newBuildId

        val newUrl = url.newBuilder().setPathSegment(2, newBuildId).build()
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}

internal const val THUMBNAIL_FRAGMENT = "thumbnail"

private val SPECIAL_CHARS_REGEX = Regex("""[^A-Za-z0-9 ]""")
