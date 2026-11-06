package app.hikari.extension.id.kiryuu

import app.hikari.multisrc.natsuid.NatsuId
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

class Kiryuu : NatsuId(
    name = "Kiryuu",
    baseUrl = "https://v7.kiryuu.to",
    lang = "id",
) {
    override val id: Long = 3639673976007021338L

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(4)

    override fun chapterListUrl(mangaId: String): HttpUrl = super.chapterListUrl(mangaId).newBuilder()
        .setQueryParameter("page", "1")
        .build()
}
