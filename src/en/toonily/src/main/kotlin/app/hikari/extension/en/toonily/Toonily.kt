package app.hikari.extension.en.toonily

import app.hikari.multisrc.madara.Madara
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale

class Toonily : Madara(
    name = "Toonily",
    baseUrl = "https://toonily.com",
    lang = "en",
) {
    override val mangaSubString = "serie"
    override val genreDirectory = "genre"
    override val mangaDetailsSelectorDescription = "div.summary__content"
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div"
    override val chapterDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yy", Locale.ENGLISH)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("Cookie", "toonily-mature=1")
}
