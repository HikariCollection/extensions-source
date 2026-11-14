package app.hikari.extension.en.ezmanga

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import app.hikari.extension.core.KeiSource
import app.hikari.extension.core.UriFilter
import app.hikari.extension.core.get
import app.hikari.extension.core.getPreferences
import app.hikari.extension.core.parseAs
import app.hikari.source.ConfigurableSource
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

class EzManga :
    KeiSource(),
    ConfigurableSource {

    override val name: String = "EzManga"
    override val lang: String = "en"
    override val baseUrl: String = "https://ezmanga.org"
    override val supportsLatest: Boolean = true

    private val apiBase = "https://vapi.ezmanga.org/api"
    private val preferences: SharedPreferences = getPreferences()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    override fun Headers.Builder.configureHeaders(): Headers.Builder {
        return set("Accept", "application/json, text/plain, */*")
            .set("Origin", baseUrl)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        return getSearchManga(page, "", FilterList(SortFilter("popular")))
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        return getSearchManga(page, "", FilterList(SortFilter("latest")))
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$apiBase/v1/series/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.trim())
                .addQueryParameter("page", page.toString())
                .addQueryParameter("perPage", PER_PAGE.toString())
                .build()
        } else {
            "$apiBase/v1/series".toHttpUrl().newBuilder().apply {
                addQueryParameter("page", page.toString())
                addQueryParameter("perPage", PER_PAGE.toString())
                filters.filterIsInstance<UriFilter>().forEach { it.addToUri(this) }
            }.build()
        }

        val dto = client.get(url).use { it.parseAs<EzMangaPageDto>() }
        return MangasPage(
            mangas = dto.data.map { it.toSManga() },
            hasNextPage = dto.next != null,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val baseHost = baseUrl.toHttpUrl().host
        if (url.host != baseHost && url.host != "www.$baseHost") return null
        if (url.pathSegments.getOrNull(0) != "series") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null

        val manga = SManga.create().apply {
            this.url = "/series/$slug"
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val seriesSlug = extractSeriesSlug(manga.url)

        val detailsDeferred = if (fetchDetails) {
            async { fetchMangaDetails(manga, seriesSlug) }
        } else {
            null
        }
        val chaptersDeferred = if (fetchChapters) {
            async { fetchChapterList(seriesSlug) }
        } else {
            null
        }

        SMangaUpdate(
            manga = detailsDeferred?.await() ?: manga,
            chapters = chaptersDeferred?.await() ?: chapters,
        )
    }

    private suspend fun fetchMangaDetails(manga: SManga, seriesSlug: String): SManga {
        val detail = client.get("$apiBase/v1/series/$seriesSlug").use {
            it.parseAs<EzMangaSeriesDetailDto>()
        }
        return detail.toSManga(manga)
    }

    private suspend fun fetchChapterList(seriesSlug: String): List<SChapter> {
        val hidePaid = preferences.hidePaidChapters()
        val chapters = mutableListOf<SChapter>()
        var cursor: String? = null
        val seenCursors = mutableSetOf<String>()

        do {
            val url = "$apiBase/v2/series/$seriesSlug/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("limit", CHAPTER_PAGE_LIMIT.toString())
                .apply {
                    if (cursor != null) {
                        addQueryParameter("cursor", cursor)
                    }
                }
                .build()

            val response = client.get(url).use { it.parseAs<EzMangaChapterListDto>() }
            response.data
                .filterNot { hidePaid && it.isLocked }
                .mapTo(chapters) { it.toSChapter(seriesSlug) }

            val next = response.nextCursor?.takeIf { it.isNotBlank() }
            cursor = if (response.hasMore && next != null && seenCursors.add(next)) next else null
        } while (cursor != null)

        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = chapter.url.trim('/').split('/')
        require(segments.size >= 3 && segments[0] == "series") {
            "Unexpected chapter URL: ${chapter.url}"
        }
        val seriesSlug = segments[1]
        val chapterSlug = segments[2]

        val detail = client.get("$apiBase/v1/series/$seriesSlug/chapters/$chapterSlug").use {
            it.parseAs<EzMangaChapterDetailDto>()
        }

        return detail.images
            .asSequence()
            .filter { it.url.isNotBlank() }
            .sortedBy { it.order }
            .mapIndexed { index, image -> Page(index, imageUrl = image.url) }
            .toList()
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_PAID_CHAPTERS
            title = "Hide paid chapters"
            summary = "Hides early-access chapters that require coins to unlock"
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    private fun SharedPreferences.hidePaidChapters(): Boolean {
        return getBoolean(PREF_HIDE_PAID_CHAPTERS, true)
    }

    private fun extractSeriesSlug(url: String): String {
        val slug = url.removePrefix("/series/").substringBefore('/').trim()
        require(slug.isNotBlank()) { "Invalid series URL: $url" }
        return slug
    }
}

private const val PER_PAGE = 20
private const val CHAPTER_PAGE_LIMIT = 100
private const val PREF_HIDE_PAID_CHAPTERS = "pref_hide_paid_chapters"
