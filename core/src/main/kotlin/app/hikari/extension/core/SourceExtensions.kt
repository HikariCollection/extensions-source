package app.hikari.extension.core

import app.hikari.network.await
import app.hikari.network.awaitSuccess
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val jsonInstance = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    allowSpecialFloatingPointValues = true
    useArrayPolymorphism = true
}

inline fun <reified T> Response.parseAs(json: Json = jsonInstance): T {
    return json.decodeFromString(body.string())
}

inline fun <reified T> String.parseAs(json: Json = jsonInstance): T {
    return json.decodeFromString(this)
}

inline fun <reified T> JsonElement.parseAs(json: Json = jsonInstance): T {
    return json.decodeFromJsonElement<T>(this)
}

inline fun <reified T> T.toJsonElement(json: Json = jsonInstance): JsonElement {
    return json.encodeToJsonElement<T>(this)
}

inline fun <reified T> T.toJsonString(json: Json = jsonInstance): String {
    return json.encodeToString(this)
}

inline fun <reified T> T.toJsonRequestBody(json: Json = jsonInstance): RequestBody {
    return json.encodeToString(this).toRequestBody("application/json; charset=utf-8".toMediaType())
}

suspend fun OkHttpClient.get(
    url: HttpUrl,
    headers: Headers = Headers.headersOf(),
    cacheControl: CacheControl? = null,
    ensureSuccess: Boolean = true,
): Response {
    val request = Request.Builder()
        .url(url)
        .headers(headers)
        .apply { if (cacheControl != null) cacheControl(cacheControl) }
        .build()
    return if (ensureSuccess) newCall(request).awaitSuccess() else newCall(request).await()
}

suspend fun OkHttpClient.get(
    url: String,
    headers: Headers = Headers.headersOf(),
    cacheControl: CacheControl? = null,
    ensureSuccess: Boolean = true,
): Response {
    val request = Request.Builder()
        .url(url)
        .headers(headers)
        .apply { if (cacheControl != null) cacheControl(cacheControl) }
        .build()
    return if (ensureSuccess) newCall(request).awaitSuccess() else newCall(request).await()
}

suspend fun OkHttpClient.post(
    url: HttpUrl,
    body: RequestBody,
    headers: Headers = Headers.headersOf(),
    cacheControl: CacheControl? = null,
    ensureSuccess: Boolean = true,
): Response {
    val request = Request.Builder()
        .url(url)
        .post(body)
        .headers(headers)
        .apply { if (cacheControl != null) cacheControl(cacheControl) }
        .build()
    return if (ensureSuccess) newCall(request).awaitSuccess() else newCall(request).await()
}

suspend fun OkHttpClient.post(
    url: String,
    body: RequestBody,
    headers: Headers = Headers.headersOf(),
    cacheControl: CacheControl? = null,
    ensureSuccess: Boolean = true,
): Response {
    val request = Request.Builder()
        .url(url)
        .post(body)
        .headers(headers)
        .apply { if (cacheControl != null) cacheControl(cacheControl) }
        .build()
    return if (ensureSuccess) newCall(request).awaitSuccess() else newCall(request).await()
}

fun Response.asJsoup(): Document {
    return Jsoup.parse(body.string(), request.url.toString())
}

fun String.asJsoup(baseUri: String = ""): Document {
    return Jsoup.parse(this, baseUri)
}

fun Element.textOrNull(): String? = text().takeIf { it.isNotBlank() }

fun Elements.imgAttr(): String? = firstOrNull()?.imgAttr()

fun Element.imgAttr(): String? {
    return when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }.takeIf { it.isNotBlank() }
}

fun DateTimeFormatter.tryParseDate(
    date: String?,
    zone: ZoneId? = null,
): Long {
    date ?: return 0L
    return runCatching {
        LocalDate.parse(date, this)
            .atStartOfDay(zone ?: this.zone ?: ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)
}

fun DateTimeFormatter.tryParseDateTime(
    date: String?,
    zone: ZoneId? = null,
): Long {
    date ?: return 0L
    return runCatching {
        LocalDateTime.parse(date, this)
            .atZone(zone ?: this.zone ?: ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)
}

fun tryParseInstant(date: String?): Long {
    date ?: return 0L
    return runCatching {
        java.time.Instant.parse(date).toEpochMilli()
    }.getOrElse {
        runCatching {
            java.time.OffsetDateTime.parse(date).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }
}
