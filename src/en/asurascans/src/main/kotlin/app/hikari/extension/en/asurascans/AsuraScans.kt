package app.hikari.extension.en.asurascans

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import app.hikari.extension.core.KeiSource
import app.hikari.extension.core.UriFilter
import app.hikari.extension.core.asJsoup
import app.hikari.extension.core.get
import app.hikari.extension.core.getPreferences
import app.hikari.extension.core.intOrNull
import app.hikari.extension.core.parseAs
import app.hikari.extension.core.string
import app.hikari.extension.core.toJsonElement
import app.hikari.extension.core.toJsonString
import app.hikari.source.ConfigurableSource
import app.hikari.source.model.FilterList
import app.hikari.source.model.MangasPage
import app.hikari.source.model.Page
import app.hikari.source.model.SChapter
import app.hikari.source.model.SManga
import app.hikari.source.model.SMangaUpdate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.IOException
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

class AsuraScans :
    KeiSource(),
    ConfigurableSource {

    override val name: String = "Asura Scans"
    override val lang: String = "en"
    override val baseUrl: String = "https://asurascans.com"
    override val supportsLatest: Boolean = true

    private val apiUrl = "https://api.asurascans.com/api"

    private val preferences: SharedPreferences = getPreferences {
        if (contains("pref_url_map")) {
            edit().remove("pref_url_map").apply()
        }
        if (contains("pref_base_url_host")) {
            edit().remove("pref_base_url_host").apply()
        }
        if (contains("pref_permanent_manga_url_2_en")) {
            edit().remove("pref_permanent_manga_url_2_en").apply()
        }
        if (contains("pref_slug_map")) {
            edit().remove("pref_slug_map").apply()
        }
        if (contains("pref_dynamic_url")) {
            edit().remove("pref_dynamic_url").apply()
        }
        if (contains("pref_slug_map_2")) {
            edit().remove("pref_slug_map_2").apply()
        }
        if (contains("pref_force_high_quality")) {
            edit().remove("pref_force_high_quality").apply()
        }
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(::scrambledImageInterceptor)
        rateLimit(2, 2.seconds) { !it.encodedPath.contains("/covers/") }
    }

    override suspend fun getPopularManga(page: Int): MangasPage =
        getSearchManga(page, "", FilterList(SortFilter(defaultSort = "popular")))

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        getSearchManga(page, "", FilterList(SortFilter(defaultSort = "latest")))

    // ============================== Search ===============================

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("offset", ((page - 1) * PER_PAGE_LIMIT).toString())
            addQueryParameter("limit", PER_PAGE_LIMIT.toString())
            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }

            filters.filterIsInstance<UriFilter>().forEach {
                it.addToUri(this)
            }
        }.build()

        client.get(url, xhrHeaders()).use { response ->
            val result = response.parseAs<DataDto<List<MangaDto>>>()
            val mangas = result.data.orEmpty().map { it.toSManga(baseUrl) }
            return MangasPage(mangas, result.meta?.hasMore ?: false)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments.getOrNull(0) == "comics") {
            val slug = url.pathSegments.getOrNull(1) ?: return null

            val tmpManga = SManga.create().apply {
                this.url = "/series/$slug"
                memo = buildJsonObject { put("slug", slug) }
            }

            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }

        return null
    }

    // ============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String {
        val randomSlug = manga.memo["slug"]?.string
            ?: run {
                val oldSlugMap = preferences.getString(SLUG_MAP, "{}")?.let {
                    runCatching { it.parseAs<JsonObject>() }.getOrNull()
                } ?: JsonObject(emptyMap())
                val slug = getMangaSlug(manga.url)

                oldSlugMap[slug]?.string ?: slug
            }

        return "$baseUrl/comics/$randomSlug"
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = getMangaUrl(manga)
        var response = client.get(url, ensureSuccess = false)

        // retry without random suffix, it redirects to new random suffix
        if (!response.isSuccessful) {
            response.close()
            response = client.get(url.replace(URL_RANDOM_PART, ""))
        }

        response.use { res ->
            val document = res.asJsoup()

            val updatedManga = document.extractAstroProp<MangaDetailsDto>("title", "description")
                .toSMangaDetails()
            document.extractAstroProp<MangaUrlDto>("publicUrl")
                .apply(updatedManga, baseUrl)
            val randomMangaSlug = updatedManga.memo["slug"]?.string ?: ""

            val hidePremium = preferences.hidePremiumChapters()
            val fetchedChapters = document.extractAstroProp<ChapterListDto>("chapters").chapters
                ?.filterNot { hidePremium && it.isLocked }
                .orEmpty()
                .map { it.toSChapter(randomMangaSlug) }

            return SMangaUpdate(updatedManga, fetchedChapters)
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val randomSlug = chapter.memo["mangaSlug"]?.string
            ?: throw Exception("Refresh Chapter List")
        val number = chapter.url.substringAfter("/chapter/")

        return "$baseUrl/comics/$randomSlug/chapter/$number"
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = getChapterUrl(chapter)

        client.get(url).use { response ->
            val document = response.asJsoup()
            var pages = try {
                document.extractAstroProp<PageListDto>("pages").pages
            } catch (_: Exception) {
                emptyList()
            }

            if (pages.isEmpty()) {
                val accessToken = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
                    .find { it.name == "access_token" }?.value
                    ?: return emptyList()

                val pageToken = document.selectFirst("script:containsData(pageToken)")
                    ?.data()?.let { PAGE_TOKEN_REGEX.find(it)?.groupValues?.get(1) }
                    ?: "asura-reader-2026"

                val mangaSlug = response.request.url.pathSegments.getOrNull(1) ?: ""
                val number = response.request.url.pathSegments.getOrNull(3) ?: ""
                val premiumUrl = "$apiUrl/series/$mangaSlug/chapters/$number"
                val headers = headersBuilder()
                    .set("Authorization", "Bearer $accessToken")
                    .set("X-Page-Token", pageToken)
                    .set("X-Requested-With", "XMLHttpRequest")
                    .build()

                pages = try {
                    client.get(premiumUrl, headers).use { premiumResponse ->
                        premiumResponse.parseAs<PremiumPageListDto>().data.chapter.pages
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            return pages.mapIndexed { index, pageDto ->
                val pageUrl = if (!pageDto.tiles.isNullOrEmpty()) {
                    val data = PageData(
                        pageDto.tiles,
                        pageDto.tileCols ?: 4,
                        pageDto.tileRows ?: 5,
                    )
                    pageDto.url.toHttpUrl().newBuilder()
                        .fragment(data.toJsonString())
                        .toString()
                } else {
                    pageDto.url
                }

                Page(index, imageUrl = pageUrl)
            }
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(): FilterList {
        return FilterList(
            SortFilter(),
            StatusFilter(),
            TypeFilter(),
            MinChaptersFilter(),
        )
    }

    // ============================= Utilities =============================

    private fun xhrHeaders(): Headers = headersBuilder()
        .set("X-Requested-With", "XMLHttpRequest")
        .build()

    private fun getMangaSlug(url: String): String = when {
        url.contains("/series/") -> url.substringAfter("/series/").substringBefore("/")
        url.contains("/comics/") -> url.substringAfter("/comics/").substringBefore("/")
        url.contains("/manga/") -> OLD_FORMAT_MANGA_REGEX.find(url)?.groupValues?.get(2) ?: url.substringAfter("/manga/").substringBefore("/")
        else -> url.trim('/')
    }

    private fun scrambledImageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment
            ?: return response
        if (!fragment.startsWith("{")) return response

        val pageData = fragment.parseAs<PageData>()
        val source = response.use {
            BitmapFactory.decodeStream(it.body.byteStream())
        } ?: throw IOException("Failed to decode image")

        val tileW = source.width / pageData.tileCols
        val tileH = source.height / pageData.tileRows

        val output = Bitmap.createBitmap(
            tileW * pageData.tileCols,
            tileH * pageData.tileRows,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(output)

        for (w in pageData.tiles.indices) {
            val j = pageData.tiles[w]

            val srcCol = w % pageData.tileCols
            val srcRow = w / pageData.tileCols
            val dstCol = j % pageData.tileCols
            val dstRow = j / pageData.tileCols

            val srcRect = Rect(srcCol * tileW, srcRow * tileH, (srcCol + 1) * tileW, (srcRow + 1) * tileH)
            val dstRect = Rect(dstCol * tileW, dstRow * tileH, (dstCol + 1) * tileW, (dstRow + 1) * tileH)

            canvas.drawBitmap(source, srcRect, dstRect, null)
        }

        val buffer = Buffer().apply {
            @Suppress("DEPRECATION")
            output.compress(Bitmap.CompressFormat.WEBP, 100, outputStream())
        }

        source.recycle()
        output.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody("image/webp".toMediaType()))
            .build()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_PREMIUM_CHAPTERS
            title = "Hide premium chapters"
            summary = "Hides the chapters that require a subscription to view"
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    private fun SharedPreferences.hidePremiumChapters(): Boolean = getBoolean(
        PREF_HIDE_PREMIUM_CHAPTERS,
        true,
    )

    private inline fun <reified T> Document.extractAstroProp(vararg key: String): T {
        val selector = key.joinToString(separator = "") { "[props*=$it]" }
        val prop = selectFirst(selector)?.attr("props")
            ?: throw Exception("Unable to find prop with ${key.contentToString()}")
        val json = prop.parseAs<JsonElement>()
        val unwrapped = json.unwrapAstro()

        return unwrapped.parseAs()
    }

    private fun JsonElement.unwrapAstro(): JsonElement = when (this) {
        is JsonArray -> when {
            isEmpty() -> JsonNull
            size == 1 && (this[0] as? JsonPrimitive)?.intOrNull != null ->
                JsonNull
            size == 2 && (this[0] as? JsonPrimitive)?.intOrNull != null ->
                this[1].unwrapAstro()
            else -> JsonArray(map { it.unwrapAstro() })
        }
        is JsonObject -> JsonObject(mapValues { it.value.unwrapAstro() })
        else -> this
    }
}

private const val PER_PAGE_LIMIT = 20
private val OLD_FORMAT_MANGA_REGEX = """^/manga/(\d+-)?([^/]+)/?$""".toRegex()
private const val PREF_HIDE_PREMIUM_CHAPTERS = "pref_hide_premium_chapters"
private const val SLUG_MAP = "slug_map_v2"
private val PAGE_TOKEN_REGEX = Regex("""pageToken\*=\*"([^"]+)"""")
private val URL_RANDOM_PART = Regex("""-[a-z0-9]{8}$""")
