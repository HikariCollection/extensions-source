package app.hikari.extension.en.weebcentral

import app.hikari.extension.core.UriFilter
import app.hikari.extension.core.UriMultiSelectFilter
import app.hikari.extension.core.UriMultiTriSelectFilter
import app.hikari.extension.core.UriPartFilter
import app.hikari.extension.core.UriSortFilter
import app.hikari.source.model.Filter
import okhttp3.HttpUrl

class AuthorFilter : Filter.Text("Author"), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        if (state.isNotBlank()) {
            builder.addQueryParameter("author", state.trim())
        }
    }
}

class SortFilter(
    defaultSort: String = "Best Match",
) : UriSortFilter(
    name = "Sort",
    sortParam = "sort",
    orderParam = "order",
    vals = arrayOf(
        "Best Match" to "Best Match",
        "Alphabet" to "Alphabet",
        "Popularity" to "Popularity",
        "Subscribers" to "Subscribers",
        "Recently Added" to "Recently Added",
        "Latest Updates" to "Latest Updates",
    ),
    defaultValue = defaultSort,
    ascendingValue = "Ascending",
    descendingValue = "Descending",
)

class OfficialFilter : UriPartFilter(
    name = "Official Translation",
    param = "official",
    vals = arrayOf(
        "Any" to "Any",
        "True" to "True",
        "False" to "False",
    ),
    defaultValue = "Any",
)

class AnimeFilter : UriPartFilter(
    name = "Anime Adaptation",
    param = "anime",
    vals = arrayOf(
        "Any" to "Any",
        "True" to "True",
        "False" to "False",
    ),
    defaultValue = "Any",
)

class AdultFilter : UriPartFilter(
    name = "Adult Content",
    param = "adult",
    vals = arrayOf(
        "Any" to "Any",
        "True" to "True",
        "False" to "False",
    ),
    defaultValue = "Any",
)

class StatusFilter : UriMultiSelectFilter(
    name = "Series Status",
    param = "included_status",
    vals = arrayOf(
        "Ongoing" to "Ongoing",
        "Complete" to "Complete",
        "Hiatus" to "Hiatus",
        "Canceled" to "Canceled",
    ),
    joinWithComma = false,
)

class TypeFilter : UriMultiSelectFilter(
    name = "Series Type",
    param = "included_type",
    vals = arrayOf(
        "Manga" to "Manga",
        "Manhwa" to "Manhwa",
        "Manhua" to "Manhua",
        "OEL" to "OEL",
    ),
    joinWithComma = false,
)

class TagFilter : UriMultiTriSelectFilter(
    name = "Tags",
    includeParam = "included_tag",
    excludeParam = "excluded_tag",
    vals = TAGS,
    joinWithComma = false,
)

private val TAGS = arrayOf(
    "Action" to "Action",
    "Adult" to "Adult",
    "Adventure" to "Adventure",
    "Comedy" to "Comedy",
    "Doujinshi" to "Doujinshi",
    "Drama" to "Drama",
    "Ecchi" to "Ecchi",
    "Fantasy" to "Fantasy",
    "Gender Bender" to "Gender Bender",
    "Harem" to "Harem",
    "Hentai" to "Hentai",
    "Historical" to "Historical",
    "Horror" to "Horror",
    "Isekai" to "Isekai",
    "Josei" to "Josei",
    "Lolicon" to "Lolicon",
    "Martial Arts" to "Martial Arts",
    "Mature" to "Mature",
    "Mecha" to "Mecha",
    "Mystery" to "Mystery",
    "Psychological" to "Psychological",
    "Romance" to "Romance",
    "School Life" to "School Life",
    "Sci-fi" to "Sci-fi",
    "Seinen" to "Seinen",
    "Shotacon" to "Shotacon",
    "Shoujo" to "Shoujo",
    "Shoujo Ai" to "Shoujo Ai",
    "Shounen" to "Shounen",
    "Shounen Ai" to "Shounen Ai",
    "Slice of Life" to "Slice of Life",
    "Smut" to "Smut",
    "Sports" to "Sports",
    "Supernatural" to "Supernatural",
    "Tragedy" to "Tragedy",
    "Yaoi" to "Yaoi",
    "Yuri" to "Yuri",
    "Other" to "Other",
)
