package app.hikari.extension.core

import app.hikari.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val param: String,
    private val vals: Array<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    vals.map { it.first }.toTypedArray(),
    vals.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val value = vals[state].second
        if (value.isNotBlank()) {
            builder.addQueryParameter(param, value)
        }
    }

    fun toUriPart(): String = vals[state].second
    fun getValue(): String = vals[state].second
}

open class UriSortFilter(
    name: String,
    private val sortParam: String,
    private val orderParam: String,
    private val vals: Array<Pair<String, String>>,
    defaultValue: String? = null,
    private val ascendingValue: String = "asc",
    private val descendingValue: String = "desc",
) : Filter.Sort(
    name,
    vals.map { it.first }.toTypedArray(),
    Selection(
        vals.indexOfFirst { it.second == defaultValue }
            .takeIf { it != -1 } ?: 0,
        ascending = false,
    ),
),
    UriFilter {

    override fun addToUri(builder: HttpUrl.Builder) {
        val state = state ?: return

        val selected = vals[state.index].second
        val order = if (state.ascending) ascendingValue else descendingValue

        builder.addQueryParameter(sortParam, selected)
        builder.addQueryParameter(orderParam, order)
    }
}

open class UriMultiSelectOption(name: String, val value: String) : Filter.CheckBox(name)

open class UriMultiSelectFilter(
    name: String,
    private val param: String,
    vals: Array<Pair<String, String>>,
    private val joinWithComma: Boolean = true,
) : Filter.Group<UriMultiSelectOption>(
    name,
    vals.map { UriMultiSelectOption(it.first, it.second) },
),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val checked = state.filter { it.state }.map { it.value }
        if (checked.isNotEmpty()) {
            if (joinWithComma) {
                builder.addQueryParameter(param, checked.joinToString(","))
            } else {
                checked.forEach { builder.addQueryParameter(param, it) }
            }
        }
    }
}

open class UriMultiTriSelectOption(name: String, val value: String) : Filter.TriState(name)

open class UriMultiTriSelectFilter(
    name: String,
    protected val includeParam: String,
    protected val excludeParam: String,
    vals: Array<Pair<String, String>>,
    protected val joinWithComma: Boolean = true,
) : Filter.Group<UriMultiTriSelectOption>(
    name,
    vals.map { UriMultiTriSelectOption(it.first, it.second) },
),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val included = state.filter { it.isIncluded() }.map { it.value }
        val excluded = state.filter { it.isExcluded() }.map { it.value }

        if (included.isNotEmpty()) {
            if (joinWithComma) {
                builder.addQueryParameter(includeParam, included.joinToString(","))
            } else {
                included.forEach { builder.addQueryParameter(includeParam, it) }
            }
        }
        if (excluded.isNotEmpty()) {
            if (joinWithComma) {
                builder.addQueryParameter(excludeParam, excluded.joinToString(","))
            } else {
                excluded.forEach { builder.addQueryParameter(excludeParam, it) }
            }
        }
    }
}
