package app.hikari.extension.all.hitomi

import app.hikari.extension.core.get
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.util.regex.Pattern

object GgDecoder {
    private const val GG_URL = "https://ltn.gold-usergeneratedcontent.net/gg.js"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L

    private val CASE_REGEX = Pattern.compile("case\\s+(\\d+):(?:\\s*o\\s*=\\s*(\\d+))?")
    private val IF_REGEX = Pattern.compile("if\\s*\\(g\\s*===?\\s*(\\d+)\\)[\\s{]*o\\s*=\\s*(\\d+)")
    private val DEFAULT_REGEX = Pattern.compile("(?:var\\s|default:)\\s*o\\s*=\\s*(\\d+)")
    private val B_REGEX = Pattern.compile("b:\\s*[\"'](.+)[\"']")

    private val mutex = Mutex()

    @Volatile
    private var lastFetchTime = 0L

    @Volatile
    private var bValue = "1794549601"

    @Volatile
    private var defaultO = 0

    @Volatile
    private var casesMap: Map<Int, Int> = emptyMap()

    suspend fun ensureUpdated(client: OkHttpClient) {
        val now = System.currentTimeMillis()
        if (casesMap.isNotEmpty() && (now - lastFetchTime) < CACHE_TTL_MS) {
            return
        }

        mutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (casesMap.isNotEmpty() && (lockedNow - lastFetchTime) < CACHE_TTL_MS) {
                return
            }

            try {
                val headers = Headers.headersOf("Referer", "https://hitomi.la/")
                val body = client.get(GG_URL, headers).use { it.body.string() }

                val map = HashMap<Int, Int>()
                val pendingKeys = mutableListOf<Int>()
                val caseMatcher = CASE_REGEX.matcher(body)
                while (caseMatcher.find()) {
                    val key = caseMatcher.group(1)?.toIntOrNull() ?: continue
                    val value = caseMatcher.group(2)?.toIntOrNull()
                    pendingKeys.add(key)
                    if (value != null) {
                        for (k in pendingKeys) {
                            map[k] = value
                        }
                        pendingKeys.clear()
                    }
                }

                val ifMatcher = IF_REGEX.matcher(body)
                while (ifMatcher.find()) {
                    val key = ifMatcher.group(1)?.toIntOrNull() ?: continue
                    val value = ifMatcher.group(2)?.toIntOrNull() ?: continue
                    map[key] = value
                }

                val defMatcher = DEFAULT_REGEX.matcher(body)
                if (defMatcher.find()) {
                    defaultO = defMatcher.group(1)?.toIntOrNull() ?: 0
                }

                val bMatcher = B_REGEX.matcher(body)
                if (bMatcher.find()) {
                    bValue = bMatcher.group(1)?.trim('/') ?: "1794549601"
                }

                casesMap = map
                lastFetchTime = lockedNow
            } catch (_: Exception) {
                // Keep existing values on transient network failure
            }
        }
    }

    suspend fun buildImageUrl(client: OkHttpClient, hash: String): String {
        ensureUpdated(client)
        val inum = try {
            val hex = "${hash.takeLast(1)}${hash.substring(hash.length - 3, hash.length - 1)}"
            hex.toInt(16)
        } catch (_: Exception) {
            0
        }
        val o = casesMap[inum] ?: defaultO
        val subdomain = "w${o + 1}"
        val ext = "webp"
        return "https://$subdomain.gold-usergeneratedcontent.net/$bValue/$inum/$hash.$ext"
    }

    fun buildThumbnailUrl(hash: String): String {
        val sub1 = hash.takeLast(1)
        val sub2 = hash.substring(hash.length - 3, hash.length - 1)
        return "https://tn.gold-usergeneratedcontent.net/webpbigtn/$sub1/$sub2/$hash.webp"
    }
}
