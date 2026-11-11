package app.hikari.multisrc.heancms

import app.hikari.source.model.Filter

class SortFilter(
    name: String = "Sort",
    values: Array<String> = sortBy.map { it.first }.toTypedArray(),
    state: Selection = Selection(0, false),
) : Filter.Sort(name, values, state) {
    val sort get() = sortBy[state?.index ?: 0].second
    val isAscending get() = state?.ascending ?: false

    companion object {
        val sortBy = listOf(
            Pair("Latest Update", "latest"),
            Pair("Total Views", "total_views"),
            Pair("Rating", "rating"),
            Pair("Created At", "created_at"),
            Pair("Title", "title"),
        )
    }
}

class StatusFilter(
    name: String = "Status",
    private val options: List<Pair<String, String>> = statusOptions,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second

    companion object {
        val statusOptions = listOf(
            Pair("All", ""),
            Pair("Ongoing", "Ongoing"),
            Pair("Completed", "Completed"),
            Pair("Hiatus", "Hiatus"),
            Pair("Cancelled", "Cancelled"),
        )
    }
}

class TypeFilter(
    name: String = "Series Type",
    private val options: List<Pair<String, String>> = typeOptions,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second

    companion object {
        val typeOptions = listOf(
            Pair("All", ""),
            Pair("Comic", "Comic"),
            Pair("Novel", "Novel"),
        )
    }
}
