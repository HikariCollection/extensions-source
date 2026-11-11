package app.hikari.extension.en.hivetoons

import app.hikari.multisrc.iken.Iken

class HiveToons : Iken(
    name = "Hive Toons",
    baseUrl = "https://hivetoons.org",
    lang = "en",
) {
    override val apiUrl: String = "https://api.hivetoons.org/api"

    override fun getGenreList(): List<Pair<String, String>> = listOf(
        Pair("Action", "5"),
        Pair("Adventure", "13"),
        Pair("Comedy", "6"),
        Pair("Drama", "2"),
        Pair("Fantasy", "3"),
        Pair("Fight", "16"),
        Pair("Gaming", "1818"),
        Pair("Growth", "69"),
        Pair("Historical", "37"),
        Pair("Horror", "34"),
        Pair("Isekai", "23"),
        Pair("Magic", "41"),
        Pair("Martial Arts", "14"),
        Pair("Murim", "33"),
        Pair("Mystery", "9"),
        Pair("Necromancer", "1820"),
        Pair("Overpowered", "39"),
        Pair("Player", "1821"),
        Pair("Psychological", "35"),
        Pair("Reincarnation", "31"),
        Pair("Revenge", "18"),
        Pair("Romance", "25"),
        Pair("Satisfying", "1826"),
        Pair("School Life", "7"),
        Pair("Sci-fi", "22"),
        Pair("Seinen", "15"),
        Pair("Shounen", "4"),
        Pair("Slice of Life", "27"),
        Pair("Supernatural", "8"),
        Pair("Survival", "47"),
        Pair("System", "28"),
        Pair("Thriller", "10"),
    )
}
