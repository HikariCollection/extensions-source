package app.hikari.extension.all.webtoons

import app.hikari.extension.core.UriPartFilter
import app.hikari.source.model.Filter

internal class SearchSectionFilter : UriPartFilter(
    "Search Catalog",
    "",
    arrayOf(
        "Originals" to "originals",
        "Canvas" to "canvas",
        "All" to "all",
    ),
)

internal class SortFilter(defaultValue: String = "MANA") : UriPartFilter(
    "Sort By (Browse)",
    "sortOrder",
    arrayOf(
        "Popularity" to "MANA",
        "Likes" to "LIKEIT",
        "Latest Update" to "UPDATE",
    ),
    defaultValue = defaultValue,
)

internal class GenreFilter(genres: Array<Pair<String, String>>) : UriPartFilter(
    "Genre (Browse)",
    "",
    genres,
)

internal fun createFiltersForLocale(langPath: String): List<Filter<*>> = listOf(
    SearchSectionFilter(),
    SortFilter(),
    GenreFilter(genresForLocale(langPath)),
)

private fun genresForLocale(langPath: String): Array<Pair<String, String>> = when (langPath) {
    "id" -> arrayOf(
        "All" to "",
        "Action" to "action",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Fantasy" to "fantasy",
        "Horror" to "horror",
        "Local" to "local",
        "Romance" to "romance",
        "Romantic Fantasy" to "romantic_fantasy",
        "Slice of Life" to "slice_of_life",
        "Thriller" to "thriller",
        "Web Novel" to "web_novel",
    )
    else -> arrayOf(
        "All" to "",
        "Action" to "action",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Fantasy" to "fantasy",
        "Graphic Novel" to "graphic_novel",
        "Heartwarming" to "heartwarming",
        "Historical" to "historical",
        "Horror" to "horror",
        "Mystery" to "mystery",
        "Romance" to "romance",
        "Sci-Fi" to "sf",
        "Slice of Life" to "slice_of_life",
        "Sports" to "sports",
        "Superhero" to "super_hero",
        "Supernatural" to "supernatural",
        "Thriller" to "thriller",
        "Tiptoon" to "tiptoon",
    )
}
