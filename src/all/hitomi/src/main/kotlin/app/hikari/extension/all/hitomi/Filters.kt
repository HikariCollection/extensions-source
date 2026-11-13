package app.hikari.extension.all.hitomi

import app.hikari.source.model.Filter
import app.hikari.source.model.FilterList

class TypeFilter : Filter.Select<String>(
    "Type",
    arrayOf(
        "All",
        "Doujinshi",
        "Manga",
        "Artist CG",
        "Game CG",
        "Imageset",
        "Anime",
    ),
)

fun getFilters(): FilterList = FilterList(
    TypeFilter(),
)
