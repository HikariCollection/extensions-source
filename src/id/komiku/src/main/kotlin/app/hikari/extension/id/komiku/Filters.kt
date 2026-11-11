package app.hikari.extension.id.komiku

import app.hikari.extension.core.UriMultiSelectFilter
import app.hikari.extension.core.UriPartFilter

class OrderByFilter(defaultVal: String = "modified") : UriPartFilter(
    "Urutkan",
    "orderby",
    arrayOf(
        Pair("Update", "modified"),
        Pair("Judul Baru", "post_date"),
        Pair("Acak", "rand"),
        Pair("Populer", "views"),
    ),
    defaultVal,
)

class StatusFilter : UriPartFilter(
    "Status",
    "status",
    arrayOf(
        Pair("Semua", ""),
        Pair("Ongoing", "ongoing"),
        Pair("Tamat", "end"),
    ),
)

class TypeFilter : UriPartFilter(
    "Tipe",
    "tipe",
    arrayOf(
        Pair("Semua", ""),
        Pair("Manga", "manga"),
        Pair("Manhua", "manhua"),
        Pair("Manhwa", "manhwa"),
    ),
)

class GenreFilter : UriMultiSelectFilter(
    "Genre",
    "genre",
    arrayOf(
        Pair("Action", "action"),
        Pair("Adventure", "adventure"),
        Pair("Comedy", "comedy"),
        Pair("Cooking", "cooking"),
        Pair("Crime", "crime"),
        Pair("Demons", "demons"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("Game", "game"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Gore", "gore"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Isekai", "isekai"),
        Pair("Josei", "josei"),
        Pair("Magic", "magic"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Mecha", "mecha"),
        Pair("Medical", "medical"),
        Pair("Military", "military"),
        Pair("Monsters", "monsters"),
        Pair("Music", "music"),
        Pair("Mystery", "mystery"),
        Pair("One Shot", "one-shot"),
        Pair("Psychological", "psychological"),
        Pair("Reincarnation", "reincarnation"),
        Pair("Romance", "romance"),
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
        Pair("Thriller", "thriller"),
        Pair("Tragedy", "tragedy"),
        Pair("Vampire", "vampire"),
        Pair("Webtoons", "webtoons"),
        Pair("Yuri", "yuri"),
    ),
)
