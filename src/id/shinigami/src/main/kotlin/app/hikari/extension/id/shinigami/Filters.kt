package app.hikari.extension.id.shinigami

import app.hikari.extension.core.UriFilter
import app.hikari.extension.core.UriMultiSelectFilter
import app.hikari.extension.core.UriMultiTriSelectFilter
import app.hikari.extension.core.UriPartFilter
import okhttp3.HttpUrl

class SortFilter :
    UriPartFilter(
        "Sort",
        "sort",
        arrayOf(
            Pair("Default", ""),
            Pair("Latest", "latest"),
            Pair("Popularity", "popularity"),
            Pair("Rating", "rating"),
        ),
    )

class SortOrderFilter :
    UriPartFilter(
        "Sort Order",
        "sort_order",
        arrayOf(
            Pair("Descending", "desc"),
            Pair("Ascending", "asc"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        "status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
        ),
    )

class FormatFilter :
    UriMultiSelectFilter(
        "Format",
        "format",
        arrayOf(
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
        ),
    )

class TypeFilter :
    UriMultiSelectFilter(
        "Type",
        "type",
        arrayOf(
            Pair("Project", "project"),
            Pair("Mirror", "mirror"),
        ),
    )

class GenreFilter :
    UriMultiTriSelectFilter(
        "Genre",
        "genre_include",
        "genre_exclude",
        GENRES,
    ) {
    override fun addToUri(builder: HttpUrl.Builder) {
        super.addToUri(builder)
        if (state.any { it.isIncluded() }) {
            builder.addQueryParameter("genre_include_mode", "and")
        }
        if (state.any { it.isExcluded() }) {
            builder.addQueryParameter("genre_exclude_mode", "and")
        }
    }
}

private val GENRES = arrayOf(
    Pair("Action", "action"),
    Pair("Adult", "adult"),
    Pair("Adventure", "adventure"),
    Pair("Boys' Love", "boys-love"),
    Pair("Comedy", "comedy"),
    Pair("Crime", "crime"),
    Pair("Demon", "demon"),
    Pair("Drama", "drama"),
    Pair("Ecchi", "ecchi"),
    Pair("Fantasy", "fantasy"),
    Pair("Game", "game"),
    Pair("Girls' Love", "girls-love"),
    Pair("Harem", "harem"),
    Pair("Historical", "historical"),
    Pair("Horror", "horror"),
    Pair("Isekai", "isekai"),
    Pair("Josei", "josei-genre"),
    Pair("Magic", "magic"),
    Pair("Martial Arts", "martial-arts"),
    Pair("Mature", "mature"),
    Pair("Medical", "medical"),
    Pair("Murim", "murim"),
    Pair("Mystery", "mystery"),
    Pair("Philosophical", "philosophical"),
    Pair("Psychological", "psychological"),
    Pair("Romance", "romance"),
    Pair("School Life", "school-life"),
    Pair("Sci-fi", "sci-fi"),
    Pair("Seinen", "seinen"),
    Pair("Shoujo", "shoujo"),
    Pair("Shounen", "shounen"),
    Pair("Slice of Life", "slice-of-life"),
    Pair("Smut", "smut"),
    Pair("Sports", "sports"),
    Pair("Supernatural", "supernatural"),
    Pair("Thriller", "thriller"),
    Pair("Tragedy", "tragedy"),
    Pair("Wuxia", "wuxia"),
)

