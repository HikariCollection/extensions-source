package app.hikari.multisrc.madara

import app.hikari.source.model.Filter

open class TextFilter(name: String, val taxonomy: String) : Filter.Text(name)

open class StatusTag(name: String, val slug: String) : Filter.CheckBox(name)

open class StatusFilter(name: String, options: List<Pair<String, String>>) :
    Filter.Group<StatusTag>(name, options.map { StatusTag(it.first, it.second) })

open class SortFilter(name: String, private val options: List<Pair<String, String>>) :
    Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    fun key(): String = options[state].second
}

open class AdultFilter(name: String, options: List<Pair<String, String>>) :
    Filter.Select<String>(name, options.map { it.first }.toTypedArray())

open class GenreConditionFilter(name: String, options: List<Pair<String, String>>) :
    Filter.Select<String>(name, options.map { it.first }.toTypedArray())

open class GenreCheckBox(name: String, val slug: String) : Filter.CheckBox(name)

open class GenreList(name: String, genres: List<GenreRoute>) :
    Filter.Group<GenreCheckBox>(name, genres.map { GenreCheckBox(it.name, it.slug) })

open class SingleGenreFilter(
    name: String,
    allLabel: String,
    private val genres: List<GenreRoute>,
) : Filter.Select<String>(name, arrayOf(allLabel) + genres.map(GenreRoute::name).toTypedArray()) {
    fun route(): GenreRoute? = genres.getOrNull(state - 1)
}
