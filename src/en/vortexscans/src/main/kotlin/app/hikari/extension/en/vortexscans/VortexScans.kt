package app.hikari.extension.en.vortexscans

import app.hikari.multisrc.iken.Iken

class VortexScans : Iken(
    name = "Vortex Scans",
    baseUrl = "https://vortexscans.org",
    lang = "en",
) {
    override fun getGenreList(): List<Pair<String, String>> = listOf(
        Pair("Action", "1"),
        Pair("Adventure", "18"),
        Pair("Comedy", "7"),
        Pair("Drama", "2"),
        Pair("Fantasy", "8"),
        Pair("Historical", "14"),
        Pair("Horror", "9"),
        Pair("Isekai", "35"),
        Pair("Magic", "27"),
        Pair("Martial Arts", "6"),
        Pair("Mystery", "11"),
        Pair("Psychological", "15"),
        Pair("Reincarnation", "19"),
        Pair("Romance", "16"),
        Pair("Sci-fi", "20"),
        Pair("Seinen", "10"),
        Pair("Shounen", "3"),
        Pair("Slice of Life", "21"),
        Pair("Sports", "4"),
        Pair("Supernatural", "22"),
        Pair("Tragedy", "17"),
    )
}
