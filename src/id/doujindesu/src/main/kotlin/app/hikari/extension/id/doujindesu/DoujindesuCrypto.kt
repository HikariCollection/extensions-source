package app.hikari.extension.id.doujindesu

import app.hikari.extension.core.jsonInstance
import kotlinx.serialization.json.Json
import okhttp3.Response
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private const val SALT = "doujindesu-scrapers-cannot-read-this-super-secret-salt-2026-v2"
private const val ONE_HOUR_MS = 3_600_000L

fun generateKey(t: Long): String {
    val n = "${SALT}_$t"
    var r = 0
    for (i in 0 until n.length) {
        r = (r shl 5) - r + n[i].code
    }
    val absR = if (r == Int.MIN_VALUE) {
        Int.MAX_VALUE.toLong() + 1L
    } else {
        kotlin.math.abs(r).toLong()
    }
    var d = if (absR == 0L) 123456789L else absR

    val sb = StringBuilder(32)
    for (i in 0 until 32) {
        d = (d * 1664525L + 1013904223L) % 4294967296L
        val ch = (33L + (d % 93L)).toInt().toChar()
        sb.append(ch)
    }
    return sb.toString()
}

fun decryptRo(hex: String, key: String): String {
    val hexLen = hex.length
    val byteCount = hexLen / 2
    val r = IntArray(byteCount)
    var rIdx = 0
    var m = 0
    while (m < hexLen - 1) {
        val hexPair = hex.substring(m, m + 2)
        r[rIdx++] = hexPair.toInt(16)
        m += 2
    }

    val keyLen = key.length
    val out = StringBuilder(byteCount)
    var l = 42
    for (i in 0 until byteCount) {
        val nVal = r[i]
        val g = key[i % keyLen].code
        val v = (nVal xor g xor (i * 13) xor l) and 0xFF
        out.append(v.toChar())
        l = (l + nVal) % 256
    }
    return out.toString()
}

fun decryptToString(encHex: String): String {
    val now = System.currentTimeMillis()
    val hour = now / ONE_HOUR_MS
    val candidateHours = longArrayOf(hour, hour - 1L, hour + 1L)

    var lastException: Exception? = null
    for (h in candidateHours) {
        try {
            val key = generateKey(h)
            val raw = decryptRo(encHex, key)
            return URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            lastException = e
        }
    }
    throw lastException ?: RuntimeException("Failed to decrypt Doujindesu payload")
}

inline fun <reified T> Response.parseEncrypted(json: Json = jsonInstance): T {
    val bodyString = body.string()
    val encPayload = json.decodeFromString<EncryptedPayloadDto>(bodyString)
    val decryptedJson = decryptToString(encPayload.encResp)
    return json.decodeFromString(decryptedJson)
}
