package app.hikari.extension.id.sektedoujin

import app.hikari.multisrc.mangathemesia.MangaThemesia
import okhttp3.OkHttpClient

class Sektedoujin : MangaThemesia(
    name = "Sektedoujin",
    baseUrl = "https://sektedoujin.cc",
    lang = "id",
) {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder =
        rateLimit(3).addInterceptor(acceptHeaderInterceptor())

    override val typeFilterOptions by lazy {
        arrayOf(
            intl["type_filter_option_all"] to "",
            "Doujinshi" to "doujinshi",
            intl["type_filter_option_manga"] to "manga",
            intl["type_filter_option_manhwa"] to "manhwa",
            intl["type_filter_option_manhua"] to "manhua",
            intl["type_filter_option_comic"] to "comic",
            "Novel" to "novel",
        )
    }
}
