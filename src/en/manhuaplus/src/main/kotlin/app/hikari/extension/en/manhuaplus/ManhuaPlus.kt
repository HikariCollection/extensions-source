package app.hikari.extension.en.manhuaplus

import app.hikari.multisrc.madara.Madara
import app.hikari.source.model.Page
import okhttp3.Request

class ManhuaPlus : Madara(
    name = "Manhua Plus",
    baseUrl = "https://manhuaplus.com",
    lang = "en",
) {
    override val filterNonMangaItems = false
    override val pageListParseSelector = ".read-container img"

    // WordPress.com image hosts reject the app's default UA
    override fun imageRequest(page: Page): Request {
        val request = super.imageRequest(page)
        return if (request.url.host.endsWith(".wordpress.com")) {
            request.newBuilder().header("User-Agent", System.getProperty("http.agent") ?: "Mozilla/5.0").build()
        } else {
            request
        }
    }
}
