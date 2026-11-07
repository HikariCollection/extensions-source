package app.hikari.extension.core

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.Response
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import org.jsoup.nodes.Document
import kotlin.reflect.typeOf

private val NEXT_F_REGEX = Regex("""self\.__next_f\.push\(\s*(\[.*])\s*\)\s*;?\s*$""", RegexOption.DOT_MATCHES_ALL)

private fun <T> extractValueNextJs(
    payload: JsonElement,
    predicate: (JsonElement) -> Boolean,
    deserializer: DeserializationStrategy<T>,
): T? {
    if (payload !is JsonObject && payload !is JsonArray) return null
    if (predicate(payload)) {
        try {
            return jsonInstance.decodeFromJsonElement(deserializer, payload)
        } catch (_: Exception) {
            // Continue searching children if decoding fails
        }
    }
    val children: Iterable<JsonElement> = when (payload) {
        is JsonObject -> payload.values
        is JsonArray -> payload
    }
    for (child in children) {
        val result = extractValueNextJs(child, predicate, deserializer)
        if (result != null) return result
    }
    return null
}

private fun resolveNextJsRefs(
    element: JsonElement,
    chunkCache: Map<String, String>,
    modelCache: Map<String, JsonElement>,
    resolving: Set<String> = emptySet(),
): JsonElement = when (element) {
    is JsonObject -> JsonObject(element.mapValues { resolveNextJsRefs(it.value, chunkCache, modelCache, resolving) })
    is JsonArray -> JsonArray(element.map { resolveNextJsRefs(it, chunkCache, modelCache, resolving) })
    is JsonPrimitive -> {
        if (element.isString && element.content.startsWith("$") && element.content.length >= 2) {
            val str = element.content
            when {
                str == "\$undefined" -> JsonNull
                str == "\$Infinity" || str == "\$-Infinity" || str == "\$NaN" || str == "\$-0" ->
                    JsonPrimitive(str.substring(1))
                str[1] == '$' -> JsonPrimitive(str.substring(1))
                str[1] == 'D' -> JsonPrimitive(str.substring(2))
                str[1] == 'n' -> JsonPrimitive(str.substring(2))
                str[1] == 'Q' -> resolveMapRef(str.substring(2), chunkCache, modelCache, resolving) ?: element
                str[1] == 'W' -> resolveSetRef(str.substring(2), chunkCache, modelCache, resolving) ?: element
                str[1] == 'L' -> resolveModelRef(str.substring(2), chunkCache, modelCache, resolving) ?: element
                str[1] == '@' -> resolveModelRef(str.substring(2), chunkCache, modelCache, resolving) ?: element
                else -> resolveModelRef(str.substring(1), chunkCache, modelCache, resolving) ?: element
            }
        } else {
            element
        }
    }
}

private fun resolveModelRef(
    reference: String,
    chunkCache: Map<String, String>,
    modelCache: Map<String, JsonElement>,
    resolving: Set<String>,
): JsonElement? {
    val segments = reference.split(':')
    val id = segments[0]
    if (segments.size == 1) {
        chunkCache[id]?.let { return JsonPrimitive(it) }
    }
    if (id in resolving) return null
    val guard = resolving + id
    var value: JsonElement = modelCache[id] ?: return null
    for (i in 1 until segments.size) {
        if (value is JsonPrimitive && value.isString && value.content.startsWith("$")) {
            value = resolveNextJsRefs(value, chunkCache, modelCache, guard)
        }
        value = walkRefSegment(value, segments[i]) ?: return null
    }
    return resolveNextJsRefs(value, chunkCache, modelCache, guard)
}

private fun walkRefSegment(value: JsonElement, segment: String): JsonElement? = when (value) {
    is JsonObject -> value[segment]
    is JsonArray ->
        if (value.size >= 4 && (value[0] as? JsonPrimitive)?.takeIf { it.isString }?.content == "$") {
            when (segment) {
                "type" -> value[1]
                "key" -> value[2]
                "props" -> value[3]
                else -> segment.toIntOrNull()?.let { value.getOrNull(it) }
            }
        } else {
            segment.toIntOrNull()?.let { value.getOrNull(it) }
        }
    else -> null
}

private fun resolveMapRef(
    id: String,
    chunkCache: Map<String, String>,
    modelCache: Map<String, JsonElement>,
    resolving: Set<String>,
): JsonElement? {
    if (id in resolving) return null
    val entries = modelCache[id] as? JsonArray ?: return null
    val resolved = resolveNextJsRefs(entries, chunkCache, modelCache, resolving + id) as? JsonArray ?: return null
    val pairs = resolved.mapNotNull { (it as? JsonArray)?.takeIf { p -> p.size == 2 } }
    return JsonObject(
        pairs.associate { (k, v) ->
            val key = (k as? JsonPrimitive)?.content ?: k.toString()
            key to v
        },
    )
}

private fun resolveSetRef(
    id: String,
    chunkCache: Map<String, String>,
    modelCache: Map<String, JsonElement>,
    resolving: Set<String>,
): JsonElement? {
    if (id in resolving) return null
    val values = modelCache[id] as? JsonArray ?: return null
    return resolveNextJsRefs(values, chunkCache, modelCache, resolving + id)
}

private fun Document.extractAppRouterPayloads(
    chunkCache: MutableMap<String, String>,
    modelCache: MutableMap<String, JsonElement>,
): List<JsonElement> {
    val stream = Buffer()
    for (script in select("script:not([src])")) {
        val data = script.data().takeIf { "self.__next_f.push" in it } ?: continue
        try {
            val raw = NEXT_F_REGEX.find(data)?.groupValues?.get(1) ?: continue
            val push = jsonInstance.parseToJsonElement(raw).jsonArray
            val content = push.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: continue
            when (push[0].jsonPrimitive.intOrNull) {
                1 -> stream.writeUtf8(content)
                3 -> stream.write(content.decodeBase64() ?: continue)
            }
        } catch (_: Exception) {
        }
    }

    return extractRscPayloads(stream.readUtf8(), chunkCache, modelCache)
}

private fun Document.extractPagesRouterPayloads(): List<JsonElement> {
    val data = selectFirst("script#__NEXT_DATA__")?.data() ?: return emptyList()
    return try {
        val root = jsonInstance.parseToJsonElement(data)
        val pageProps = root.jsonObject["props"]?.jsonObject?.get("pageProps")
        listOfNotNull(pageProps, root)
    } catch (_: Exception) {
        emptyList()
    }
}

private fun extractRscPayloads(
    body: String,
    chunkCache: MutableMap<String, String>,
    modelCache: MutableMap<String, JsonElement>,
): List<JsonElement> {
    val results = mutableListOf<JsonElement>()
    var pos = 0

    while (pos < body.length) {
        val colonIdx = body.indexOf(':', pos)
        if (colonIdx == -1) break

        val id = body.substring(pos, colonIdx)
        if (id.isEmpty() || !id.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            pos++
            continue
        }

        pos = colonIdx + 1
        if (pos >= body.length) break

        if (body[pos] == 'T') {
            pos++
            val commaIdx = body.indexOf(',', pos)
            if (commaIdx == -1) break
            val byteLen = body.substring(pos, commaIdx).toIntOrNull(16) ?: break
            pos = commaIdx + 1
            var bytes = 0
            val start = pos
            while (pos < body.length && bytes < byteLen) {
                when {
                    body[pos].code < 0x80 -> bytes += 1
                    body[pos].code < 0x800 -> bytes += 2
                    Character.isHighSurrogate(body[pos]) -> {
                        bytes += 4
                        pos++
                    }
                    else -> bytes += 3
                }
                pos++
            }

            val chunkContent = body.substring(start, pos)
            chunkCache[id] = chunkContent

            try {
                results.add(jsonInstance.parseToJsonElement(chunkContent))
            } catch (_: Exception) {
            }
        } else {
            val (element, end) = parseJsonAt(body, pos)
            if (element != null) {
                results.add(element)
                modelCache[id] = element
            }
            pos = end
        }
    }

    return results
}

private fun parseJsonAt(body: String, start: Int): Pair<JsonElement?, Int> {
    if (start >= body.length) return Pair(null, start)

    var depth = 0
    var inString = false
    var escape = false
    var i = start

    while (i < body.length) {
        val c = body[i++]
        if (escape) {
            escape = false
            continue
        }
        if (c == '\\' && inString) {
            escape = true
            continue
        }
        if (c == '"') {
            inString = !inString
            continue
        }
        if (inString) continue
        when (c) {
            '{', '[' -> depth++
            '}', ']' -> if (--depth == 0) {
                return try {
                    Pair(jsonInstance.parseToJsonElement(body.substring(start, i)), i)
                } catch (_: Exception) {
                    Pair(null, i)
                }
            }
        }
        if (depth == 0 && c.isWhitespace()) {
            return try {
                Pair(jsonInstance.parseToJsonElement(body.substring(start, i - 1)), i)
            } catch (_: Exception) {
                Pair(null, i)
            }
        }
    }
    return Pair(null, i)
}

fun <T> Document.extractNextJs(
    predicate: (JsonElement) -> Boolean,
    deserializer: DeserializationStrategy<T>,
): T? {
    val chunkCache = mutableMapOf<String, String>()
    val modelCache = mutableMapOf<String, JsonElement>()
    val payloads = extractAppRouterPayloads(chunkCache, modelCache).ifEmpty { extractPagesRouterPayloads() }

    for (payload in payloads) {
        val resolvedPayload = resolveNextJsRefs(payload, chunkCache, modelCache)
        val result = extractValueNextJs(resolvedPayload, predicate, deserializer)
        if (result != null) return result
    }
    return null
}

inline fun <reified T> Document.extractNextJs(
    noinline predicate: (JsonElement) -> Boolean = { true },
): T? = extractNextJs(predicate, serializer<T>())

fun <T> String.extractNextJsRsc(
    predicate: (JsonElement) -> Boolean = { true },
    deserializer: DeserializationStrategy<T>,
): T? {
    val chunkCache = mutableMapOf<String, String>()
    val modelCache = mutableMapOf<String, JsonElement>()
    for (payload in extractRscPayloads(this, chunkCache, modelCache)) {
        val resolvedPayload = resolveNextJsRefs(payload, chunkCache, modelCache)
        val result = extractValueNextJs(resolvedPayload, predicate, deserializer)
        if (result != null) return result
    }
    return null
}

inline fun <reified T> String.extractNextJsRsc(
    noinline predicate: (JsonElement) -> Boolean = { true },
): T? = extractNextJsRsc(predicate, serializer<T>())

fun <T> Response.extractNextJs(
    predicate: (JsonElement) -> Boolean = { true },
    deserializer: DeserializationStrategy<T>,
): T? {
    val contentType = header("Content-Type") ?: ""
    return use {
        when {
            "text/x-component" in contentType -> body.string().extractNextJsRsc(predicate, deserializer)
            "text/html" in contentType -> asJsoup().extractNextJs(predicate, deserializer)
            else -> body.string().extractNextJsRsc(predicate, deserializer)
        }
    }
}

inline fun <reified T> Response.extractNextJs(
    noinline predicate: (JsonElement) -> Boolean = { true },
): T? = extractNextJs(predicate, serializer<T>())
