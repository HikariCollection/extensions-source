package app.hikari.extension.all.hitomi

import app.hikari.extension.core.KeiSource
import app.hikari.extension.core.get
import app.hikari.extension.core.parseAs
import app.hikari.network.await
import app.hikari.source.Source
import app.hikari.source.SourceFactory
import app.hikari.source.model.FilterList
import app.hikari.source.model.MangasPage
import app.hikari.source.model.Page
import app.hikari.source.model.SChapter
import app.hikari.source.model.SManga
import app.hikari.source.model.SMangaUpdate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Hitomi : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        HitomiSource("all", "all"),
        HitomiSource("en", "english"),
        HitomiSource("ja", "japanese"),
        HitomiSource("ko", "korean"),
        HitomiSource("zh", "chinese"),
        HitomiSource("id", "indonesian"),
        HitomiSource("es", "spanish"),
        HitomiSource("ru", "russian"),
        HitomiSource("fr", "french"),
    )
}

class HitomiSource(
    override val lang: String,
    private val hitomiLang: String,
) : KeiSource() {

    override val name: String = "Hitomi.la"
    override val baseUrl: String = "https://hitomi.la"
    override val supportsLatest: Boolean = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(4)

    override fun Headers.Builder.configureHeaders(): Headers.Builder =
        set("Referer", "$baseUrl/")
            .set("Origin", baseUrl)

    override fun getFilterList(): FilterList = getFilters()

    override suspend fun getPopularManga(page: Int): MangasPage =
        getGalleryPageFromNozomi("https://ltn.gold-usergeneratedcontent.net/popular-$hitomiLang.nozomi", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        getGalleryPageFromNozomi("https://ltn.gold-usergeneratedcontent.net/index-$hitomiLang.nozomi", page)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val trimmed = query.trim()
        val typeFilter = filters.filterIsInstance<TypeFilter>().firstOrNull()
        val typeVal = typeFilter?.let { it.values[it.state].lowercase().replace(" ", "") } ?: "all"

        val nozomiUrl = when {
            trimmed.isNotEmpty() -> {
                val cleanQuery = trimmed.replace(" ", "%20")
                when {
                    cleanQuery.startsWith("tag:") -> {
                        val tag = cleanQuery.removePrefix("tag:")
                        "https://ltn.gold-usergeneratedcontent.net/n/tag/$tag-$hitomiLang.nozomi"
                    }
                    cleanQuery.startsWith("artist:") -> {
                        val artist = cleanQuery.removePrefix("artist:")
                        "https://ltn.gold-usergeneratedcontent.net/n/artist/$artist-$hitomiLang.nozomi"
                    }
                    cleanQuery.startsWith("series:") -> {
                        val series = cleanQuery.removePrefix("series:")
                        "https://ltn.gold-usergeneratedcontent.net/n/series/$series-$hitomiLang.nozomi"
                    }
                    cleanQuery.startsWith("group:") -> {
                        val group = cleanQuery.removePrefix("group:")
                        "https://ltn.gold-usergeneratedcontent.net/n/group/$group-$hitomiLang.nozomi"
                    }
                    cleanQuery.startsWith("character:") -> {
                        val character = cleanQuery.removePrefix("character:")
                        "https://ltn.gold-usergeneratedcontent.net/n/character/$character-$hitomiLang.nozomi"
                    }
                    else -> {
                        "https://ltn.gold-usergeneratedcontent.net/n/tag/$cleanQuery-$hitomiLang.nozomi"
                    }
                }
            }
            typeVal != "all" -> {
                "https://ltn.gold-usergeneratedcontent.net/n/type/$typeVal-$hitomiLang.nozomi"
            }
            else -> {
                "https://ltn.gold-usergeneratedcontent.net/index-$hitomiLang.nozomi"
            }
        }

        return getGalleryPageFromNozomi(nozomiUrl, page)
    }

    private suspend fun getGalleryPageFromNozomi(nozomiUrl: String, page: Int): MangasPage {
        val pageSize = 25
        val startByte = (page - 1) * pageSize * 4
        val endByte = startByte + (pageSize * 4) - 1

        val req = Request.Builder()
            .url(nozomiUrl)
            .headers(headers)
            .header("Range", "bytes=$startByte-$endByte")
            .build()

        val galleryIds = try {
            client.newCall(req).await().use { res ->
                if (!res.isSuccessful && res.code != 206) {
                    return MangasPage(emptyList(), false)
                }
                res.body.byteStream().use { NozomiDecoder.decode(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }

        if (galleryIds.isEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val mangas = coroutineScope {
            galleryIds.map { gid ->
                async {
                    fetchMangaSummary(gid)
                }
            }.awaitAll().filterNotNull()
        }

        val hasNextPage = galleryIds.size >= pageSize
        return MangasPage(mangas, hasNextPage)
    }

    private suspend fun fetchMangaSummary(gid: Long): SManga? {
        val galleryUrl = "https://ltn.gold-usergeneratedcontent.net/galleries/$gid.js"
        return try {
            client.get(galleryUrl, headers, ensureSuccess = false).use { res ->
                if (!res.isSuccessful) return null
                val body = res.body.string()
                val jsonPart = body.substringAfter('=', "").trim()
                if (jsonPart.isEmpty()) return null
                val dto = jsonPart.parseAs<HitomiGalleryDto>()
                val thumbUrl = dto.files?.firstOrNull()?.hash?.let { GgDecoder.buildThumbnailUrl(it) }

                SManga.create().apply {
                    url = "/galleries/$gid.html"
                    title = dto.title
                    thumbnail_url = thumbUrl
                    status = SManga.COMPLETED
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val gid = manga.url.substringAfterLast('/').substringBefore(".html")
        val galleryUrl = "https://ltn.gold-usergeneratedcontent.net/galleries/$gid.js"

        val dto = async {
            client.get(galleryUrl, headers).use { res ->
                val jsonPart = res.body.string().substringAfter('=', "").trim()
                jsonPart.parseAs<HitomiGalleryDto>()
            }
        }.await()

        val updatedManga = if (fetchDetails) {
            SManga.create().apply {
                url = manga.url
                title = dto.title
                artist = dto.artists?.mapNotNull { it.artist }?.joinToString(", ")
                author = dto.groups?.mapNotNull { it.group }?.joinToString(", ") ?: artist
                description = buildString {
                    dto.japanese_title?.takeIf { it.isNotBlank() }?.let {
                        append("Japanese Title: ").append(it).append("\n")
                    }
                    dto.type?.takeIf { it.isNotBlank() }?.let {
                        append("Type: ").append(it).append("\n")
                    }
                    dto.language?.takeIf { it.isNotBlank() }?.let {
                        append("Language: ").append(it).append("\n")
                    }
                    dto.parodys?.mapNotNull { it.parody }?.takeIf { it.isNotEmpty() }?.let {
                        append("Parody: ").append(it.joinToString(", ")).append("\n")
                    }
                    dto.characters?.mapNotNull { it.character }?.takeIf { it.isNotEmpty() }?.let {
                        append("Characters: ").append(it.joinToString(", ")).append("\n")
                    }
                }.trim()
                genre = dto.tags?.mapNotNull {
                    val t = it.tag ?: return@mapNotNull null
                    when {
                        it.female == "1" -> "$t ♀"
                        it.male == "1" -> "$t ♂"
                        else -> t
                    }
                }?.joinToString(", ")
                status = SManga.COMPLETED
                thumbnail_url = dto.files?.firstOrNull()?.hash?.let { GgDecoder.buildThumbnailUrl(it) }
            }
        } else {
            manga
        }

        val updatedChapters = if (fetchChapters) {
            val dateUpload = parseDate(dto.date)
            listOf(
                SChapter.create().apply {
                    url = "/reader/$gid.html"
                    name = "Gallery"
                    date_upload = dateUpload
                    chapter_number = 1f
                },
            )
        } else {
            chapters
        }

        SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val gid = chapter.url.substringAfterLast('/').substringBefore(".html")
        val galleryUrl = "https://ltn.gold-usergeneratedcontent.net/galleries/$gid.js"

        val dto = client.get(galleryUrl, headers).use { res ->
            val jsonPart = res.body.string().substringAfter('=', "").trim()
            jsonPart.parseAs<HitomiGalleryDto>()
        }

        val files = dto.files ?: return emptyList()
        GgDecoder.ensureUpdated(client)
        return files.mapIndexed { index, file ->
            val imgUrl = GgDecoder.buildImageUrl(client, file.hash)
            Page(index, "", imgUrl)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val host = url.host
        if (host != "hitomi.la" && host != "www.hitomi.la") return null
        val path = url.encodedPath
        val gidMatch = Regex("""/(?:manga|doujinshi|cg|gamecg|imageset|galleries|reader)/(?:[^/?#]+-)?(\d+)(?:\.html)?""").find(path)
            ?: return null
        val gid = gidMatch.groupValues[1].toLongOrNull() ?: return null

        return fetchMangaSummary(gid) ?: SManga.create().apply {
            this.url = "/galleries/$gid.html"
            this.title = "Hitomi Gallery #$gid"
        }
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            val cleanDate = dateStr.substringBeforeLast('-')
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(cleanDate)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
