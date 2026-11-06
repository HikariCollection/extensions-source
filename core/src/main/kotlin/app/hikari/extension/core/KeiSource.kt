package app.hikari.extension.core

import app.hikari.source.model.SManga
import app.hikari.source.online.HttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

abstract class KeiSource : HttpSource() {

    override val client: OkHttpClient by lazy {
        network.client.newBuilder()
            .configureClient()
            .build()
    }

    open suspend fun getMangaByUrl(url: HttpUrl): SManga? = null

    protected open fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = this

    protected fun OkHttpClient.Builder.rateLimit(
        permits: Int,
        period: Long = 1,
        unit: TimeUnit = TimeUnit.SECONDS,
    ): OkHttpClient.Builder {
        return addInterceptor(RateLimitInterceptor(permits, period, unit))
    }

    protected open fun Headers.Builder.configureHeaders(): Headers.Builder = this

    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder()
            .set(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
            )
            .set("Referer", "$baseUrl/")
            .configureHeaders()
    }
}

private class RateLimitInterceptor(
    private val permits: Int,
    period: Long,
    unit: TimeUnit,
) : Interceptor {
    private val requestIntervalNanos = unit.toNanos(period) / permits
    private var lastRequestNanos = 0L

    @Synchronized
    override fun intercept(chain: Interceptor.Chain): Response {
        val now = System.nanoTime()
        val elapsed = now - lastRequestNanos
        if (elapsed < requestIntervalNanos) {
            val sleepMs = TimeUnit.NANOSECONDS.toMillis(requestIntervalNanos - elapsed)
            if (sleepMs > 0) {
                Thread.sleep(sleepMs)
            }
        }
        lastRequestNanos = System.nanoTime()
        return chain.proceed(chain.request())
    }
}
