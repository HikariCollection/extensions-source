package app.hikari.multisrc.mangathemesia

import app.hikari.extension.core.*

import android.util.Base64
import app.hikari.network.POST
import app.hikari.source.model.Filter
import app.hikari.source.model.FilterList
import app.hikari.source.model.MangasPage
import app.hikari.source.model.Page
import app.hikari.source.model.SChapter
import app.hikari.source.model.SManga
import app.hikari.source.model.SMangaUpdate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

class SimpleIntl(val lang: String? = "en") {
    private val strings = mapOf(
        "alt_names_heading" to "Alternative Titles:",
        "status_filter_option_all" to "All",
        "status_filter_option_ongoing" to "Ongoing",
        "status_filter_option_completed" to "Completed",
        "status_filter_option_hiatus" to "Hiatus",
        "status_filter_option_dropped" to "Dropped",
        "type_filter_option_all" to "All",
        "type_filter_option_manga" to "Manga",
        "type_filter_option_manhwa" to "Manhwa",
        "type_filter_option_manhua" to "Manhua",
        "type_filter_option_comic" to "Comic",
        "order_by_filter_default" to "Default",
        "order_by_filter_az" to "A-Z",
        "order_by_filter_za" to "Z-A",
        "order_by_filter_latest_update" to "Latest Update",
        "order_by_filter_latest_added" to "Latest Added",
        "order_by_filter_popular" to "Popular",
        "project_filter_all_manga" to "All",
        "project_filter_only_project" to "Project Only",
        "author_filter_title" to "Author",
        "year_filter_title" to "Year",
        "status_filter_title" to "Status",
        "type_filter_title" to "Type",
        "order_by_filter_title" to "Order By",
        "genre_exclusion_warning" to "Tap once for include, twice for exclude",
        "genre_filter_title" to "Genres",
        "project_filter_warning" to "Filter by project status",
        "project_filter_name" to "%s Project",
        "project_filter_title" to "Project",
    )

    operator fun get(key: String): String = strings[key] ?: key

    fun format(key: String, vararg args: Any?): String = java.lang.String.format(get(key), *args)
}

abstract class MangaThemesia(
    override val name: String = "",
    override val baseUrl: String = "",
    override val lang: String = "",
) : KeiSource() {

    override val supportsLatest: Boolean = true

    open val mangaUrlDirectory: String = "/manga"

    open val datePattern = "MMMM d, yyyy"
    open val dateFormat by lazy {
        DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern(datePattern).toFormatter(Locale.forLanguageTag(lang))
    }

    protected val intl by lazy { SimpleIntl(runCatching { lang }.getOrNull() ?: "en") }

    open val projectPageString = "/project"

    override suspend fun getPopularManga(page: Int) = getSearchManga(page, "", popularFilter)

    override suspend fun getLatestUpdates(page: Int) = getSearchManga(page, "", latestFilter)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        return getMangaDetails(
            SManga.create().apply { this.url = url.encodedPath },
        ).takeIf { it.title.isNotEmpty() }
    }

    open fun searchMangaUrl(page: Int, query: String) = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegment(mangaUrlDirectory.drop(1))
        if (query.isNotEmpty()) addQueryParameter("title", query)
        addQueryParameter("page", page.toString())
    }

    open fun searchMangaUrl(page: Int, query: String, filters: FilterList) = searchMangaUrl(page, query).apply {
        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> {
                    addQueryParameter("status", filter.selectedValue())
                }
                is TypeFilter -> {
                    addQueryParameter("type", filter.selectedValue())
                }
                is OrderByFilter -> {
                    addQueryParameter("order", filter.selectedValue())
                }
                is GenreListFilter -> {
                    filter.state
                        .filter { it.state != Filter.TriState.STATE_IGNORE }
                        .forEach {
                            val value = if (it.state == Filter.TriState.STATE_EXCLUDE) "-${it.value}" else it.value
                            addQueryParameter("genre[]", value)
                        }
                }
                is AuthorFilter -> filter.state.takeIf { it.isNotEmpty() }?.let { addQueryParameter("author", it) }
                is YearFilter -> filter.state.takeIf { it.isNotEmpty() }?.let { addQueryParameter("yearx", it) }
                is ProjectFilter -> {
                    if (filter.selectedValue() == "project-filter-on") {
                        setPathSegment(0, projectPageString.substring(1))
                    }
                }
                else -> {}
            }
        }
        addPathSegment("")
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val url = searchMangaUrl(page, query, filters)
        return searchMangaParse(client.get(url.build()).asJsoup())
    }

    open fun searchMangaParse(document: Document): MangasPage {
        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        val hasNextPage = searchMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    protected open fun searchMangaSelector() = ".utao .uta .imgu, .listupd .bs .bsx, .listo .bs .bsx"

    protected open fun searchMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select("img").imgAttr()
        title = element.select("a").attr("title")
        setUrlWithoutDomain(element.select("a").attr("href"))
    }

    protected open fun searchMangaNextPageSelector(): String? = "div.pagination .next, div.hpage .r"

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        val postId = doc.postId()
        sendView(postId)
        return SMangaUpdate(
            mangaDetailsParse(doc).apply {
                if (postId != null) memo = buildJsonObject { put("postId", postId) }
            },
            chapterListParse(doc),
        )
    }

    private fun selector(selector: String, contains: List<String>): String =
        contains.joinToString(", ") { selector.replace("%s", it) }

    open val seriesDetailsSelector = "div.bigcontent, div.animefull, div.main-info, div.postbody"
    open val seriesTitleSelector = ".entry-title, .ts-breadcrumb li:last-child span"
    open val seriesArtistSelector = selector(
        ".infotable tr:contains(%s) td:last-child, .tsinfo .imptdt:contains(%s) i, .fmed b:contains(%s)+span, span:contains(%s)",
        listOf(
            "artist",
            "Artiste",
            "Artista",
            "Ø§Ù„Ø±Ø³Ø§Ù…",
            "Ø§Ù„Ù†Ø§Ø´Ø±",
            "Ä°llÃ¼stratÃ¶r",
            "Ã‡izer",
            "SanatÃ§Ä±",
            "à¸™à¸±à¸à¸§à¸²à¸”",
        ),
    )
    open val seriesAuthorSelector = selector(
        ".infotable tr:contains(%s) td:last-child, .tsinfo .imptdt:contains(%s) i, .fmed b:contains(%s)+span, span:contains(%s)",
        listOf(
            "Author",
            "Auteur",
            "autor",
            "Ø§Ù„Ù…Ø¤Ù„Ù",
            "Mangaka",
            "seniman",
            "Pengarang",
            "Yazar",
            "à¸œà¸¹à¹‰à¹à¸•à¹ˆà¸‡",
            "à¸™à¸±à¸à¹€à¸‚à¸µà¸¢à¸™",
        ),
    )
    open val seriesDescriptionSelector = ".desc, .entry-content[itemprop=description]"
    open val seriesAltNameSelector = ".alternative, .wd-full:contains(alt) span, .alter, .seriestualt, " +
        selector(".infotable tr:contains(%s) td:last-child", listOf("Alternative", "Alternatif", "Ø§Ù„Ø£Ø³Ù…Ø§Ø¡ Ø§Ù„Ø«Ø§Ù†ÙˆÙŠØ©"))
    open val seriesGenreSelector = "div.gnr a, .mgen a, .seriestugenre a, " +
        selector("span:contains(%s)", listOf("genre", "Ø§Ù„ØªØµÙ†ÙŠÙ"))
    open val seriesTypeSelector = selector(
        ".infotable tr:contains(%s) td:last-child, .tsinfo .imptdt:contains(%s) i, .tsinfo .imptdt:contains(%s) a, .fmed b:contains(%s)+span, span:contains(%s) a",
        listOf("type", "à¸›à¸£à¸°à¹€à¸ à¸—", "Ø§Ù„Ù†ÙˆØ¹", "tipe", "TÃ¼rÃ¼"),
    ) + ", a[href*=type\\=]"
    open val seriesStatusSelector = selector(
        ".infotable tr:contains(%s) td:last-child, .tsinfo .imptdt:contains(%s) i, .fmed b:contains(%s)+span span:contains(%s)",
        listOf(
            "status",
            "Statut",
            "Durum",
            "é€£è¼‰çŠ¶æ³",
            "Estado",
            "Ø§Ù„Ø­Ø§Ù„Ø©",
            "Ø­Ø§Ù„Ø© Ø§Ù„Ø¹Ù…Ù„",
            "à¸ªà¸–à¸²à¸™à¸°",
            "stato",
            "StatÃ¼sÃ¼",
        ),
    )
    open val seriesThumbnailSelector = ".infomanga > div[itemprop=image] img, .thumb img"
    open val altNamePrefix by lazy { "${intl["alt_names_heading"]} " }

    open suspend fun getMangaDetails(manga: SManga) = mangaDetailsParse(client.get(getMangaUrl(manga)).asJsoup())

    protected open fun mangaDetailsParse(document: Document) = SManga.create().apply {
        setUrlWithoutDomain(document.location())
        document.selectFirst(seriesDetailsSelector)?.let { seriesDetails ->
            title = seriesDetails.selectFirst(seriesTitleSelector)!!.text()
            artist = seriesDetails.selectFirst(seriesArtistSelector)?.ownText().removeEmptyPlaceholder()
            author = seriesDetails.selectFirst(seriesAuthorSelector)?.ownText().removeEmptyPlaceholder()
            description = seriesDetails.selectFirst(seriesDescriptionSelector)?.textOrNull()
            val altName = seriesDetails.selectFirst(seriesAltNameSelector)?.ownText()
            if (!altName.isNullOrBlank()) {
                val names = altName.split(ALT_NAME_SEPARATOR).joinToString("\n") { "- ${it.trim()}" }
                description = description?.let { "$it\n\n" }.orEmpty() + "$altNamePrefix\n$names".trim()
            }
            val genres = seriesDetails.select(seriesGenreSelector).map { it.text() }.toMutableList()
            seriesDetails.selectFirst(seriesTypeSelector)?.ownText().takeIf {
                it.isNullOrBlank().not()
            }?.let { genres.add(it) }
            genre = genres.map { genre ->
                genre.lowercase(Locale.forLanguageTag(lang)).replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.forLanguageTag(lang)) else char.toString()
                }
            }.joinToString { it.trim() }

            status = seriesDetails.selectFirst(seriesStatusSelector)?.text().parseStatus()
            thumbnail_url = seriesDetails.select(seriesThumbnailSelector).imgAttr()
        }
    }

    protected fun String?.removeEmptyPlaceholder(): String? =
        if (this.isNullOrBlank() || this == "-" || this == "N/A" || this == "n/a" || this == "Unknown") null else this

    open fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        listOf(
            "Ù…Ø³ØªÙ…Ø±Ø©", "en curso", "ongoing", "on going", "new season", "mass released", "ativo",
            "en cours", "en cours de publication", "Ä‘ang tiáº¿n hÃ nh", "em lanÃ§amento", "Ð¾Ð½Ð³Ð¾Ñ–Ð½Ð³",
            "publishing", "devam ediyor", "em andamento", "in corso", "gÃ¼ncel", "berjalan",
            "Ð¿Ñ€Ð¾Ð´Ð¾Ð»Ð¶Ð°ÐµÑ‚ÑÑ", "updating", "lanÃ§ando", "in arrivo", "emision", "en emision",
            "Ù…Ø³ØªÙ…Ø±", "curso", "en marcha", "publicandose", "publicando", "è¿žè½½ä¸­", "devam etmekte", "é€£è¼‰ä¸­",
        ).any { this.contains(it, ignoreCase = true) } -> SManga.ONGOING
        listOf(
            "completed", "completo", "complÃ©tÃ©", "fini", "achevÃ©", "terminÃ©", "tamamlandÄ±",
            "Ä‘Ã£ hoÃ n thÃ nh", "hoÃ n thÃ nh", "Ù…ÙƒØªÙ…Ù„Ø©", "Ð·Ð°Ð²ÐµÑ€ÑˆÐµÐ½Ð¾", "finished", "finalizad",
            "completata", "one-shot", "bitti", "tamat", "completado", "concluÃ­do", "å®Œçµ",
            "concluido", "å·²å®Œç»“", "bitmiÅŸ",
        ).any { this.contains(it, ignoreCase = true) } -> SManga.COMPLETED
        listOf(
            "canceled",
            "cancelled",
            "cancelado",
            "cancellato",
            "cancelados",
            "dropped",
            "discontinued",
            "abandonnÃ©",
        ).any { this.contains(it, ignoreCase = true) } -> SManga.CANCELLED
        listOf(
            "hiatus",
            "on hold",
            "season end",
            "pausado",
            "en espera",
            "en pause",
            "en attente",
            "hiato",
        ).any { this.contains(it, ignoreCase = true) } -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    protected open fun chapterListSelector() =
        "div.bxcl li, div.cl li, #chapterlist li, ul li:has(div.chbox):has(div.eph-num)"

    open fun chapterListParse(document: Document): List<SChapter> {
        val chapters = document.select(chapterListSelector()).map { chapterFromElement(it) }
        if (chapters.isNotEmpty() && chapters.first().date_upload == 0L) {
            val date = document
                .select(
                    ".listinfo time[itemprop=dateModified], .fmed:contains(update) time, span:contains(update) time",
                )
                .attr("datetime")
            if (date.isNotEmpty()) chapters.first().date_upload = parseUpdatedOnDate(date)
        }
        return chapters
    }

    private fun parseUpdatedOnDate(date: String) = DateTimeFormatter.ofPattern("yyyy-MM-dd").tryParseDate(date)

    protected open fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElements = element.select("a")
        setUrlWithoutDomain(urlElements.attr("href"))
        name = element.select(".lch a, .chapternum").text().ifBlank { urlElements.first()!!.text() }
        date_upload = element.selectFirst(".chapterdate")?.text().parseChapterDate()
    }

    protected open fun String?.parseChapterDate() = dateFormat.tryParseDate(this).takeIf { it != 0L }
        ?: DateTimeFormatter.ofPattern(datePattern, Locale.US).tryParseDate(this)

    open val pageSelector = "div#readerarea img"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get(getChapterUrl(chapter)).asJsoup()
        sendView(doc.postId())
        return pageListParse(doc)
    }

    protected open fun pageListParse(document: Document): List<Page> {
        val htmlPages = document.select(pageSelector)
            .mapNotNull { it.imgAttr() }
            .filter { it.isNotEmpty() }
            .mapIndexed { i, img -> Page(i, imageUrl = img) }

        if (htmlPages.isNotEmpty()) {
            return htmlPages
        }

        val script = document.selectFirst("script[src^=data:text/javascript;base64,dHNfcmVhZGVyLnJ1bih7]")
        val docString = script?.attr("src")
            ?.substringAfter("base64,")
            ?.let { Base64.decode(it, Base64.DEFAULT).decodeToString() }
            ?: document.toString()

        val imageListJson = JSON_IMAGE_LIST_REGEX.find(docString)?.destructured?.toList()?.get(0).orEmpty()
        val imageList = runCatching {
            imageListJson.parseAs<List<String>>()
        }.getOrElse { emptyList() }

        return imageList.mapIndexed { i, url ->
            Page(i, imageUrl = url.absolute())
        }
    }

    protected open val sendViewCount: Boolean = true

    protected open fun sendView(postId: String?) {
        if (!sendViewCount || postId.isNullOrEmpty()) return
        val formBody = FormBody.Builder()
            .add("action", "dynamic_view_ajax")
            .add("post_id", postId)
            .build()

        val request = POST("$baseUrl/wp-admin/admin-ajax.php", headers, formBody)

        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) = Unit
                override fun onResponse(call: Call, response: Response) = response.close()
            },
        )
    }

    open fun Document.postId(): String? = select("script").firstNotNullOfOrNull { script ->
        (MANGA_PAGE_ID_REGEX.find(script.data()) ?: CHAPTER_PAGE_ID_REGEX.find(script.data()))
            ?.groupValues?.get(1)
    }

    protected class AuthorFilter(name: String) : Filter.Text(name)

    protected class YearFilter(name: String) : Filter.Text(name)

    open class SelectFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
        defaultValue: String? = null,
    ) : Filter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
        vals.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
    ) {
        fun selectedValue() = vals[state].second
    }

    protected class StatusFilter(name: String, options: Array<Pair<String, String>>) : SelectFilter(name, options)

    protected open val statusOptions by lazy {
        arrayOf(
            Pair(intl["status_filter_option_all"], ""),
            Pair(intl["status_filter_option_ongoing"], "ongoing"),
            Pair(intl["status_filter_option_completed"], "completed"),
            Pair(intl["status_filter_option_hiatus"], "hiatus"),
            Pair(intl["status_filter_option_dropped"], "dropped"),
        )
    }

    protected class TypeFilter(name: String, options: Array<Pair<String, String>>) : SelectFilter(name, options)

    protected open val typeFilterOptions by lazy {
        arrayOf(
            Pair(intl["type_filter_option_all"], ""),
            Pair(intl["type_filter_option_manga"], "Manga"),
            Pair(intl["type_filter_option_manhwa"], "Manhwa"),
            Pair(intl["type_filter_option_manhua"], "Manhua"),
            Pair(intl["type_filter_option_comic"], "Comic"),
        )
    }

    protected class OrderByFilter(
        name: String,
        options: Array<Pair<String, String>>,
        defaultOrder: String? = null,
    ) : SelectFilter(name, options, defaultOrder)

    protected open val orderByFilterOptions by lazy {
        arrayOf(
            Pair(intl["order_by_filter_default"], ""),
            Pair(intl["order_by_filter_az"], "title"),
            Pair(intl["order_by_filter_za"], "titlereverse"),
            Pair(intl["order_by_filter_latest_update"], "update"),
            Pair(intl["order_by_filter_latest_added"], "latest"),
            Pair(intl["order_by_filter_popular"], "popular"),
        )
    }

    protected open val popularFilter by lazy { FilterList(OrderByFilter("", orderByFilterOptions, "popular")) }
    protected open val latestFilter by lazy { FilterList(OrderByFilter("", orderByFilterOptions, "update")) }

    protected class ProjectFilter(name: String, options: Array<Pair<String, String>>) : SelectFilter(name, options)

    protected open val projectFilterOptions by lazy {
        arrayOf(
            Pair(intl["project_filter_all_manga"], ""),
            Pair(intl["project_filter_only_project"], "project-filter-on"),
        )
    }

    @Serializable
    protected class GenreData(
        val name: String,
        val value: String,
        val state: Int = Filter.TriState.STATE_IGNORE,
    )

    protected class Genre(
        name: String,
        val value: String,
        state: Int,
    ) : Filter.TriState(name, state)

    protected class GenreListFilter(name: String, genres: List<Genre>) : Filter.Group<Genre>(name, genres)

    open val hasProjectPage = false

    open suspend fun fetchFilterData() = parseGenres(
        client.get("$baseUrl/$mangaUrlDirectory").asJsoup(),
    )?.toJsonElement()

    override fun getFilterList(): FilterList = getFilterList(null)

    open fun getFilterList(data: JsonElement?): FilterList {
        val genrelist = data?.parseAs<List<GenreData>>()?.map { Genre(it.name, it.value, it.state) }.orEmpty()

        val filters = mutableListOf<Filter<*>>(
            Filter.Separator(),
            AuthorFilter(intl["author_filter_title"]),
            YearFilter(intl["year_filter_title"]),
            StatusFilter(intl["status_filter_title"], statusOptions),
            TypeFilter(intl["type_filter_title"], typeFilterOptions),
            OrderByFilter(intl["order_by_filter_title"], orderByFilterOptions),
        )

        if (genrelist.isNotEmpty()) {
            filters.addAll(
                listOf(
                    Filter.Header(intl["genre_exclusion_warning"]),
                    GenreListFilter(intl["genre_filter_title"], genrelist),
                ),
            )
        }

        if (hasProjectPage) {
            filters.addAll(
                mutableListOf<Filter<*>>(
                    Filter.Separator(),
                    Filter.Header(intl["project_filter_warning"]),
                    Filter.Header(intl.format("project_filter_name", name)),
                    ProjectFilter(intl["project_filter_title"], projectFilterOptions),
                ),
            )
        }
        return FilterList(filters)
    }

    open fun acceptHeaderInterceptor() = Interceptor { chain ->
        val request = chain.request()
        if (IMAGE_EXTENSION_REGEX.containsMatchIn(request.url.encodedPath)) {
            chain.proceed(
                request.newBuilder()
                    .header("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
                    .header("Sec-Fetch-Dest", "image")
                    .header("Sec-Fetch-Mode", "no-cors")
                    .header("Sec-Fetch-Site", "same-site")
                    .build(),
            )
        } else {
            chain.proceed(request)
        }
    }

    protected open fun parseGenres(document: Document): List<GenreData>? =
        document.selectFirst("ul.genrez")?.select("li")?.map { li ->
            GenreData(
                li.selectFirst("label")!!.text(),
                li.selectFirst("input[type=checkbox]")!!.attr("value"),
            )
        }

    private fun String.absolute() = if (startsWith("/")) baseUrl + this else this

    companion object {
        private val MANGA_PAGE_ID_REGEX =
            """(?:post_id["']?\s*:\s*|ts_dynamic_ajax_view\D*|tsUpdateView\D*)(\d+)""".toRegex()
        private val CHAPTER_PAGE_ID_REGEX = "chapter_id\\s*=\\s*(\\d+);".toRegex()
        val JSON_IMAGE_LIST_REGEX = """["']?(?:images|imageUrls)["']?\s*[:=]\s*(\[.*?])""".toRegex()
        private val ALT_NAME_SEPARATOR = Regex("""[|/â€¢,;]""")
        private val IMAGE_EXTENSION_REGEX = Regex("""\.(?:webp|jpe?g|png|gif)""", RegexOption.IGNORE_CASE)
    }
}
