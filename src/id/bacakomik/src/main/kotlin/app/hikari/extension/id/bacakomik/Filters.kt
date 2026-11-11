package app.hikari.extension.id.bacakomik

import app.hikari.extension.core.UriMultiSelectFilter
import app.hikari.extension.core.UriPartFilter
import app.hikari.source.model.Filter

class AuthorFilter : Filter.Text("Author")

class YearFilter : Filter.Text("Year")

class TypeFilter : UriPartFilter(
    "Type",
    "type",
    arrayOf(
        Pair("Default", ""),
        Pair("Manga", "Manga"),
        Pair("Manhwa", "Manhwa"),
        Pair("Manhua", "Manhua"),
        Pair("Comic", "Comic"),
    ),
)

class SortByFilter(defaultVal: String = "") : UriPartFilter(
    "Sort By",
    "order",
    arrayOf(
        Pair("Default", ""),
        Pair("A-Z", "title"),
        Pair("Z-A", "titlereverse"),
        Pair("Latest Update", "update"),
        Pair("Latest Added", "latest"),
        Pair("Popular", "popular"),
    ),
    defaultVal,
)

class StatusFilter : UriPartFilter(
    "Status",
    "status",
    arrayOf(
        Pair("All", ""),
        Pair("Ongoing", "ongoing"),
        Pair("Completed", "completed"),
        Pair("Hiatus", "hiatus"),
    ),
)

class GenreFilter : UriMultiSelectFilter(
    "Genre",
    "genre[]",
    arrayOf(
        Pair("2026", "2026"),
        Pair("Actio", "actio"),
        Pair("Action", "action"),
        Pair("Action Adventure", "action-adventure"),
        Pair("Action Adventure Comedy Drama Fantasy", "action-adventure-comedy-drama-fantasy"),
        Pair("Adult", "adult"),
        Pair("Adventure", "adventure"),
        Pair("Comedy", "comedy"),
        Pair("Crime", "crime"),
        Pair("Demons", "demons"),
        Pair("Doujinshi", "doujinshi"),
        Pair("Drama", "drama"),
        Pair("Drama Romance", "drama-romance"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("Fantasy Comedy", "fantasy-comedy"),
        Pair("Game", "game"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Gore", "gore"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Isekai", "isekai"),
        Pair("Josei", "josei"),
        Pair("Magic", "magic"),
        Pair("Manga", "manga"),
        Pair("Manhua", "manhua"),
        Pair("Manhwa", "manhwa"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Mecha", "mecha"),
        Pair("Military", "military"),
        Pair("Music", "music"),
        Pair("Mystery", "mystery"),
        Pair("One Shot", "one-shot"),
        Pair("Psychological", "psychological"),
        Pair("Reincarnation", "reincarnation"),
        Pair("Romance", "romance"),
        Pair("School", "school"),
        Pair("School Life", "school-life"),
        Pair("Sci-fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shoujo Ai", "shoujo-ai"),
        Pair("Shounen", "shounen"),
        Pair("Shounen Ai", "shounen-ai"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Smut", "smut"),
        Pair("Sports", "sports"),
        Pair("Super Power", "super-power"),
        Pair("Supernatural", "supernatural"),
        Pair("Survival", "survival"),
        Pair("Thriller", "thriller"),
        Pair("Time Travel", "time-travel"),
        Pair("Tragedy", "tragedy"),
        Pair("Vampire", "vampire"),
        Pair("Webtoon", "webtoon"),
        Pair("Webtoons", "webtoons"),
        Pair("Yaoi", "yaoi"),
        Pair("Yuri", "yuri"),
    ),
)
