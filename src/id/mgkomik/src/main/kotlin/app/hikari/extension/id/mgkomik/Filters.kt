package app.hikari.extension.id.mgkomik

import app.hikari.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Urutkan",
        SORT_OPTIONS.map { it.first }.toTypedArray(),
    ) {
    fun getValue() = SORT_OPTIONS[state].second

    companion object {
        private val SORT_OPTIONS = listOf(
            Pair("Relevance", "relevance"),
            Pair("Latest", "latest"),
            Pair("A-Z", "alphabet"),
            Pair("Most Views", "views"),
            Pair("New", "new"),
        )
    }
}

class StatusFilter :
    Filter.Select<String>(
        "Status",
        STATUS_OPTIONS.map { it.first }.toTypedArray(),
    ) {
    fun getValue() = STATUS_OPTIONS[state].second

    companion object {
        private val STATUS_OPTIONS = listOf(
            Pair("Semua", ""),
            Pair("Ongoing", "on-going"),
            Pair("Completed", "end"),
            Pair("Hiatus", "on-hold"),
        )
    }
}

class OperatorFilter :
    Filter.Select<String>(
        "Cocokan Genre",
        OPERATOR_OPTIONS.map { it.first }.toTypedArray(),
    ) {
    fun getValue() = OPERATOR_OPTIONS[state].second

    companion object {
        private val OPERATOR_OPTIONS = listOf(
            Pair("Match ANY", ""),
            Pair("Match ALL", "1"),
        )
    }
}

class TypeFilter :
    Filter.Group<TypeCheckBox>(
        "Tipe",
        listOf(
            TypeCheckBox("Manga", "manga"),
            TypeCheckBox("Manhua", "manhua"),
            TypeCheckBox("Manhwa", "manhwa"),
        ),
    )

class TypeCheckBox(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter :
    Filter.Group<GenreCheckBox>(
        "Genre",
        GENRE_LIST.map { GenreCheckBox(it.first, it.second) },
    )

class GenreCheckBox(name: String, val value: String) : Filter.CheckBox(name)

private val GENRE_LIST = listOf(
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
    Pair("Magic", "magic"),
    Pair("Martial Arts", "martial-arts"),
    Pair("Mature", "mature"),
    Pair("Mecha", "mecha"),
    Pair("Military", "military"),
    Pair("Modern", "modern"),
    Pair("Monsters", "monsters"),
    Pair("Murim", "murim"),
    Pair("Mystery", "mystery"),
    Pair("Necromancer", "necromancer"),
    Pair("Overpowered", "overpowered"),
    Pair("Post-Apocalyptic", "post-apocalyptic"),
    Pair("Psychological", "psychological"),
    Pair("Rebirth", "rebirth"),
    Pair("Regression", "regression"),
    Pair("Reincarnation", "reincarnation"),
    Pair("Return", "return"),
    Pair("Returner", "returner"),
    Pair("Revenge", "revenge"),
    Pair("Romance", "romance"),
    Pair("School", "school"),
    Pair("School Life", "school-life"),
    Pair("Sci-Fi", "sci-fi"),
    Pair("Seinen", "seinen"),
    Pair("Shoujo", "shoujo"),
    Pair("Shounen", "shounen"),
    Pair("Slice of Life", "slice-of-life"),
    Pair("Supernatural", "supernatural"),
    Pair("Survival", "survival"),
    Pair("System", "system"),
    Pair("Thriller", "thriller"),
    Pair("Time Travel", "time-travel"),
    Pair("Tower", "tower"),
    Pair("Tragedy", "tragedy"),
    Pair("Urban", "urban"),
    Pair("Vampire", "vampire"),
    Pair("Villain", "villain"),
    Pair("Video Games", "video-games"),
    Pair("Virtual Reality", "virtual-reality"),
    Pair("Webtoon", "webtoon"),
    Pair("Wuxia", "wuxia"),
    Pair("Zombies", "zombies"),
)

