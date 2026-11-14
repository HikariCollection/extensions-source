package app.hikari.extension.en.ezmanga

import app.hikari.extension.core.UriMultiSelectFilter
import app.hikari.extension.core.UriPartFilter

class SortFilter(defaultValue: String = "popular") : UriPartFilter(
    name = "Sort",
    param = "sort",
    vals = arrayOf(
        "Popular" to "popular",
        "Latest" to "latest",
    ),
    defaultValue = defaultValue,
)

class StatusFilter : UriPartFilter(
    name = "Status",
    param = "status",
    vals = arrayOf(
        "All" to "",
        "Ongoing" to "ONGOING",
        "Completed" to "COMPLETED",
        "Hiatus" to "HIATUS",
        "Cancelled" to "CANCELLED",
    ),
)

class TypeFilter : UriPartFilter(
    name = "Type",
    param = "type",
    vals = arrayOf(
        "All" to "",
        "Manhwa" to "MANHWA",
        "Manga" to "MANGA",
        "Manhua" to "MANHUA",
    ),
)

class GenreFilter : UriMultiSelectFilter(
    name = "Genres",
    param = "genres",
    vals = arrayOf(
        "Action" to "action",
        "Adventure" to "adventure",
        "All Ages" to "all-ages",
        "Another World" to "another-world",
        "Comedy" to "comedy",
        "Crossdressing" to "crossdressing",
        "Dr" to "dr",
        "Drama" to "drama",
        "Elf" to "elf",
        "Emale Cartoon" to "emale-cartoon",
        "Fantasy" to "fantasy",
        "Full Color" to "full-color",
        "Game World" to "game-world",
        "Gender Bender" to "gender-bender",
        "Gore" to "gore",
        "Historical" to "historical",
        "Horror" to "horror",
        "Isekai" to "isekai",
        "Josei" to "josei",
        "Love Affair" to "love-affair",
        "Magic" to "magic",
        "Manhwa" to "manhwa",
        "Martial Arts" to "martial-arts",
        "Modern" to "modern",
        "Mystery" to "mystery",
        "Psychological" to "psychological",
        "Pure" to "pure",
        "Regression" to "regression",
        "Revenge" to "revenge",
        "Romance" to "romance",
        "Royalty" to "royalty",
        "School Life" to "school-life",
        "Science Fiction" to "science-fiction",
        "Shoujo" to "shoujo",
        "Slice of Life" to "slice-of-life",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Thriller" to "thriller",
        "Webtoon" to "webtoon",
        "White Cat" to "white-cat",
    ),
)
