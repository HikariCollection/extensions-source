package app.hikari.extension.id.komikindo

import app.hikari.extension.core.UriMultiSelectFilter
import app.hikari.extension.core.UriPartFilter

class OrderFilter(defaultVal: String = "") : UriPartFilter(
    "Urutkan",
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
        Pair("Semua", ""),
        Pair("Ongoing", "Ongoing"),
        Pair("Completed", "Completed"),
    ),
)

class TypeFilter : UriPartFilter(
    "Tipe",
    "type",
    arrayOf(
        Pair("Semua", ""),
        Pair("Manga", "Manga"),
        Pair("Manhwa", "Manhwa"),
        Pair("Manhua", "Manhua"),
    ),
)

class FormatFilter : UriPartFilter(
    "Format",
    "format",
    arrayOf(
        Pair("Semua", ""),
        Pair("Hitam Putih", "bw"),
        Pair("Berwarna", "colorized"),
    ),
)

class GenreFilter : UriMultiSelectFilter(
    "Genre",
    "genre[]",
    arrayOf(
        Pair("Action", "action"),
        Pair("Adult", "adult"),
        Pair("Adventure", "adventure"),
        Pair("Arts", "arts"),
        Pair("Boys' Love", "boys-love"),
        Pair("Comedy", "comedy"),
        Pair("Crime", "crime"),
        Pair("Demons", "demons"),
        Pair("Drama", "drama"),
        Pair("Drama Supernatural", "drama-supernatural"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("Game", "game"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Girls' Love", "girls-love"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Isekai", "isekai"),
        Pair("Josei", "josei"),
        Pair("Life", "life"),
        Pair("Magic", "magic"),
        Pair("Magical Girls", "magical-girls"),
        Pair("Martial", "martial"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Mecha", "mecha"),
        Pair("Medical", "medical"),
        Pair("Music", "music"),
        Pair("Mystery", "mystery"),
        Pair("Philosophical", "philosophical"),
        Pair("Psychological", "psychological"),
        Pair("Reincarnation", "reincarnation"),
        Pair("Romance", "romance"),
        Pair("School", "school"),
        Pair("School Life", "school-life"),
        Pair("Sci-Fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shotacon", "shotacon"),
        Pair("Shoujo", "shoujo"),
        Pair("Shoujo Ai", "shoujo-ai"),
        Pair("Shounen", "shounen"),
        Pair("Shounen Ai", "shounen-ai"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Smut", "smut"),
        Pair("Sports", "sports"),
        Pair("Superhero", "superhero"),
        Pair("Supernatural", "supernatural"),
        Pair("Thriller", "thriller"),
        Pair("Tragedy", "tragedy"),
        Pair("Wuxia", "wuxia"),
        Pair("Yaoi", "yaoi"),
        Pair("Yuri", "yuri"),
    ),
    joinWithComma = false,
)

class TemaFilter : UriMultiSelectFilter(
    "Tema",
    "tema[]",
    arrayOf(
        Pair("Aliens", "aliens"),
        Pair("Animals", "animals"),
        Pair("Cooking", "cooking"),
        Pair("Crossdressing", "crossdressing"),
        Pair("Delinquents", "delinquents"),
        Pair("Demons", "demons"),
        Pair("Ecchi", "ecchi"),
        Pair("Genderswap", "genderswap"),
        Pair("Ghosts", "ghosts"),
        Pair("Gore", "gore"),
        Pair("Gyaru", "gyaru"),
        Pair("Harem", "harem"),
        Pair("Incest", "incest"),
        Pair("Loli", "loli"),
        Pair("Mafia", "mafia"),
        Pair("Magic", "magic"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Military", "military"),
        Pair("Monster Girls", "monster-girls"),
        Pair("Monsters", "monsters"),
        Pair("Music", "music"),
        Pair("Ninja", "ninja"),
        Pair("Office Workers", "office-workers"),
        Pair("Police", "police"),
        Pair("Post-Apocalyptic", "post-apocalyptic"),
        Pair("Reincarnation", "reincarnation"),
        Pair("Reverse Harem", "reverse-harem"),
        Pair("Samurai", "samurai"),
        Pair("School Life", "school-life"),
        Pair("Shota", "shota"),
        Pair("Smut", "smut"),
        Pair("Supernatural", "supernatural"),
        Pair("Survival", "survival"),
        Pair("Time Travel", "time-travel"),
        Pair("Traditional Games", "traditional-games"),
        Pair("Vampires", "vampires"),
        Pair("Video Games", "video-games"),
        Pair("Villainess", "villainess"),
        Pair("Virtual Reality", "virtual-reality"),
        Pair("Zombies", "zombies"),
    ),
    joinWithComma = false,
)

class DemografisFilter : UriMultiSelectFilter(
    "Demografis",
    "demografis[]",
    arrayOf(
        Pair("Josei", "josei"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shounen", "shounen"),
    ),
    joinWithComma = false,
)

class KontenFilter : UriMultiSelectFilter(
    "Konten",
    "konten[]",
    arrayOf(
        Pair("Ecchi", "ecchi"),
        Pair("Erotica", "erotica"),
        Pair("Gore", "gore"),
        Pair("Sexual Violence", "sexual-violence"),
        Pair("Smut", "smut"),
    ),
    joinWithComma = false,
)
