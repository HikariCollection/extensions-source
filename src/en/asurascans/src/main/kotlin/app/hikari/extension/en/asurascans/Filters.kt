package app.hikari.extension.en.asurascans

import app.hikari.extension.core.UriFilter
import app.hikari.extension.core.UriMultiSelectFilter
import app.hikari.extension.core.UriPartFilter
import app.hikari.extension.core.UriSortFilter
import app.hikari.source.model.Filter
import okhttp3.HttpUrl

class SortFilter(defaultSort: String? = null) :
    UriSortFilter(
        name = "Sort By",
        sortParam = "sort",
        orderParam = "order",
        vals = arrayOf(
            "Latest Update" to "latest",
            "Popular" to "popular",
            "Rating" to "rating",
            "A-Z" to "title",
            "Newest" to "update",
        ),
        defaultValue = defaultSort,
        ascendingValue = "asc",
        descendingValue = "desc",
    )

class StatusFilter :
    UriPartFilter(
        name = "Status",
        param = "status",
        vals = arrayOf(
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
            "Dropped" to "dropped",
            "Axed" to "axed",
        ),
    )

class TypeFilter :
    UriPartFilter(
        name = "Type",
        param = "type",
        vals = arrayOf(
            "All" to "",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Mangatoon" to "manga",
        ),
    )

class GenreFilter(genres: List<GenreDto>) :
    UriMultiSelectFilter(
        name = "Genres",
        param = "genres",
        vals = genres.map { it.name to it.slug }.toTypedArray(),
    )

class CreatorFilter(
    private val authors: List<String>,
    private val artists: List<String>,
) : Filter.Select<String>("Creators", (listOf("") + artists + authors).toTypedArray()),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val (param, selected) = when {
            state == 0 -> null to null
            state >= 1 && state <= artists.size -> "artist" to artists[state - 1]
            else -> "author" to authors[state - 1 - artists.size]
        }

        if (param != null) {
            builder.addQueryParameter(param, selected)
        }
    }
}

class MinChaptersFilter :
    Filter.Text("Min Chapters"),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        if (state.isNotBlank()) {
            builder.addQueryParameter("min_chapters", state)
        }
    }
}

