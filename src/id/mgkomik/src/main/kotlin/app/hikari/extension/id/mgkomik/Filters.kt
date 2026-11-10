package app.hikari.extension.id.mgkomik

import app.hikari.extension.core.UriMultiSelectFilter
import app.hikari.extension.core.UriPartFilter
import okhttp3.HttpUrl

class SortFilter :
    UriPartFilter(
        "Urutkan",
        "order_by",
        SORT_OPTIONS,
    ) {
    override fun addToUri(builder: HttpUrl.Builder) {
        val value = getValue()
        if (value.isNotBlank() && value != "relevance") {
            builder.addQueryParameter("order_by", value)
        }
    }

    companion object {
        private val SORT_OPTIONS = arrayOf(
            Pair("Relevance", "relevance"),
            Pair("Latest", "latest"),
            Pair("A-Z", "alphabet"),
            Pair("Most Views", "views"),
            Pair("New", "new"),
        )
    }
}

class StatusFilter :
    UriPartFilter(
        "Status",
        "status",
        STATUS_OPTIONS,
    ) {
    companion object {
        private val STATUS_OPTIONS = arrayOf(
            Pair("Semua", ""),
            Pair("Ongoing", "on-going"),
            Pair("Completed", "end"),
            Pair("Hiatus", "on-hold"),
        )
    }
}

class OperatorFilter :
    UriPartFilter(
        "Cocokan Genre",
        "op",
        OPERATOR_OPTIONS,
    ) {
    companion object {
        private val OPERATOR_OPTIONS = arrayOf(
            Pair("Match ANY", ""),
            Pair("Match ALL", "1"),
        )
    }
}

class TypeFilter :
    UriMultiSelectFilter(
        "Tipe",
        "types[]",
        TYPE_OPTIONS,
        joinWithComma = false,
    ) {
    companion object {
        private val TYPE_OPTIONS = arrayOf(
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
        )
    }
}

class GenreFilter :
    UriMultiSelectFilter(
        "Genre",
        "genres[]",
        GENRE_LIST,
        joinWithComma = false,
    )

private val GENRE_LIST = arrayOf(
    Pair("Action", "action"),
    Pair("Adaptation", "adaptation"),
    Pair("Adult", "adult"),
    Pair("Adventure", "adventure"),
    Pair("Animals", "animals"),
    Pair("Apocalypse", "apocalypse"),
    Pair("Childhood Friends", "childhood-friends"),
    Pair("Comedy", "comedy"),
    Pair("Cooking", "cooking"),
    Pair("Crime", "crime"),
    Pair("Demon", "demon"),
    Pair("Demons", "demons"),
    Pair("Drama", "drama"),
    Pair("Dungeons", "dungeons"),
    Pair("Ecchi", "ecchi"),
    Pair("Fantasy", "fantasy"),
    Pair("Fight", "fight"),
    Pair("Fighting", "fighting"),
    Pair("Full Color", "full-color"),
    Pair("Game", "game"),
    Pair("Gender Bender", "gender-bender"),
    Pair("Gore", "gore"),
    Pair("Harem", "harem"),
    Pair("Historical", "historical"),
    Pair("Horror", "horror"),
    Pair("Isekai", "isekai"),
    Pair("Josei", "josei"),
    Pair("Josei(W)", "joseiw"),
    Pair("Kids", "kids"),
    Pair("Magic", "magic"),
    Pair("Manga", "manga"),
    Pair("Mangatoon", "mangatoon"),
    Pair("Manhua", "manhua"),
    Pair("Manhwa", "manhwa"),
    Pair("Martial Arts", "martial-arts"),
    Pair("Mature", "mature"),
    Pair("Mecha", "mecha"),
    Pair("Medical", "medical"),
    Pair("Military", "military"),
    Pair("Modern", "modern"),
    Pair("Monsters", "monsters"),
    Pair("Murim", "murim"),
    Pair("Music", "music"),
    Pair("Mystery", "mystery"),
    Pair("Office Workers", "office-workers"),
    Pair("One-Shot", "one-shot"),
    Pair("Overpowered", "overpowered"),
    Pair("Police", "police"),
    Pair("Psychological", "psychological"),
    Pair("Regression", "regression"),
    Pair("Reincarnation", "reincarnation"),
    Pair("Revenge", "revenge"),
    Pair("Reverse Harem", "reverse-harem"),
    Pair("Rofan", "rofan"),
    Pair("Romance", "romance"),
    Pair("Royal family", "royal-family"),
    Pair("School", "school"),
    Pair("School Life", "school-life"),
    Pair("Sci-fi", "sci-fi"),
    Pair("Seinen", "seinen"),
    Pair("Shoujo", "shoujo"),
    Pair("Shoujo AI", "shouai"),
    Pair("Shoujo(G)", "shoujog"),
    Pair("Shounen", "shounen"),
    Pair("Slice of Life", "slice-of-life"),
    Pair("Smut", "smut"),
    Pair("Sports", "sports"),
    Pair("Super Power", "super-power"),
    Pair("Superhero", "superhero"),
    Pair("Supernatural", "supernatural"),
    Pair("Survival", "survival"),
    Pair("System", "system"),
    Pair("Thriller", "thriller"),
    Pair("Time Travel", "time-travel"),
    Pair("Tragedy", "tragedy"),
    Pair("Transmigration", "transmigration"),
    Pair("Vampire", "vampire"),
    Pair("Villainess", "villainess"),
    Pair("Violence", "violence"),
    Pair("War", "war"),
    Pair("Webtoon", "webtoon"),
    Pair("Webtoons", "webtoons"),
    Pair("Wuxia", "wuxia"),
    Pair("Yuri", "yuri"),
)

