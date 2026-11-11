package app.hikari.multisrc.iken

import app.hikari.source.model.Filter
import okhttp3.HttpUrl

typealias Options = List<Pair<String, String>>

interface UrlPartFilter {
    fun addUrlParameter(url: HttpUrl.Builder)
}

abstract class SelectFilter(
    name: String,
    private val urlParameter: String,
    private val options: Options,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
),
    UrlPartFilter {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) {
            url.addQueryParameter(urlParameter, value)
        }
    }
}

class CheckBoxFilter(
    name: String,
    val value: String,
) : Filter.CheckBox(name)

abstract class CheckBoxGroup(
    name: String,
    private val urlParameter: String,
    options: Options,
) : Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
),
    UrlPartFilter {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        val checked = state.filter { it.state }.map { it.value }
        if (checked.isNotEmpty()) {
            url.addQueryParameter(urlParameter, checked.joinToString(","))
        }
    }
}

class StatusFilter : SelectFilter(
    "Status",
    "seriesStatus",
    listOf(
        Pair("All", ""),
        Pair("Ongoing", "ONGOING"),
        Pair("Completed", "COMPLETED"),
        Pair("Hiatus", "HIATUS"),
        Pair("Cancelled", "CANCELLED"),
    ),
)

class TypeFilter : SelectFilter(
    "Type",
    "seriesType",
    listOf(
        Pair("All", ""),
        Pair("Comic", "COMIC"),
        Pair("Manhwa", "MANHWA"),
        Pair("Manhua", "MANHUA"),
        Pair("Manga", "MANGA"),
    ),
)

class SortFilter : SelectFilter(
    "Sort By",
    "orderBy",
    listOf(
        Pair("Views", "views"),
        Pair("Latest Update", "updatedAt"),
        Pair("Latest Added", "createdAt"),
        Pair("Rating", "rating"),
        Pair("Title", "postTitle"),
    ),
    "views",
)

class GenreFilter(genres: Options) : CheckBoxGroup("Genres", "genres", genres)
