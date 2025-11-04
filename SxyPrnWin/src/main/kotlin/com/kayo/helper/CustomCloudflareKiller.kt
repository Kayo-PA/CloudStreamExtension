package com.kayo.helper

import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.AnyThread
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.debugWarning
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.nicehttp.Requests.Companion.await
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.URI

@AnyThread
class CustomCloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        private val ERROR_CODES = listOf(403, 503)
        private val CLOUDFLARE_SERVERS = listOf("cloudflare-nginx", "cloudflare")

        fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";").mapNotNull {
                val split = it.split("=").map { s -> s.trim() }
                val k = split.getOrNull(0).orEmpty()
                val v = split.getOrNull(1).orEmpty()
                if (k.isNotBlank() && v.isNotBlank()) k to v else null
            }.toMap()
        }
    }

    init {
        // Needs to clear cookies between sessions to generate new cookies.
        safe {
            // This can throw an exception on unsupported devices :(
            CookieManager.getInstance().removeAllCookies(null)
        }
    }

    // In-memory map: host -> cookie map
    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    /**
     * Helper: merge several maps into okhttp Headers
     */
    private fun buildHeaders(vararg maps: Map<String, String>): Headers {
        val builder = Headers.Builder()
        // later maps override earlier ones
        val merged = linkedMapOf<String, String>()
        for (m in maps) {
            for ((k, v) in m) {
                if (k.isNotBlank()) merged[k] = v
            }
        }
        for ((k, v) in merged) {
            // Headers.Builder throws if key or value contains '\n' etc; assume values are safe
            builder[k] = v
        }
        return builder.build()
    }

    /**
     * Gets the headers with cookies, webview user agent included!
     * */
    fun getCookieHeaders(url: String): Headers {
        val userAgentHeaders = WebViewResolver.webViewUserAgent?.let {
            mapOf("User-Agent" to it.replace("Mobile ",""))
        } ?: emptyMap()

        val hostCookies = savedCookies[URI(url).host] ?: emptyMap()
        // build a Cookie header string from hostCookies (key=value; ...)
        val cookieHeader = hostCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

        val cookieMap = if (cookieHeader.isNotBlank()) mapOf("Cookie" to cookieHeader) else emptyMap()
        return buildHeaders(userAgentHeaders, cookieMap)
    }

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()

        when (val cookies = savedCookies[request.url.host]) {
            null -> {
                val response = chain.proceed(request)
                if (!(response.header("Server") in CLOUDFLARE_SERVERS && response.code in ERROR_CODES)) {
                    return@runBlocking response
                } else {
                    response.close()
                    bypassCloudflare(request)?.let {
                        Log.d(TAG, "Succeeded bypassing cloudflare: ${request.url}")
                        return@runBlocking it
                    }
                }
            }
            else -> {
                return@runBlocking proceed(request, cookies)
            }
        }

        debugWarning({ true }) { "Failed cloudflare at: ${request.url}" }
        return@runBlocking chain.proceed(request)
    }

    private fun getWebViewCookie(url: String): String? {
        return safe {
            CookieManager.getInstance()?.getCookie(url)
        }
    }

    /**
     * Returns true if the cf cookies were successfully fetched from the CookieManager
     * Also saves the cookies.
     * */
    private fun trySolveWithSavedCookies(request: Request): Boolean {
        // Not sure if this takes expiration into account
        return getWebViewCookie(request.url.toString())?.let { cookie ->
            cookie.contains("cf_clearance").also { solved ->
                if (solved) savedCookies[request.url.host] = parseCookieMap(cookie)
            }
        } ?: false
    }

    private suspend fun proceed(request: Request, cookies: Map<String, String>): Response {
        // Use WebViewResolver.webViewUserAgent if available (your WebViewResolver implementation should set this)
        val userAgentMap = WebViewResolver.webViewUserAgent?.let {
            mapOf("User-Agent" to it.replace("Mobile ",""))
        } ?: emptyMap()

        // Build cookie header string from provided cookies map
        val cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

        // Preserve request's existing headers (as a map) but override/merge with UA and Cookie
        val requestHeadersMap = request.headers.toMultimap().mapValues { it.value.joinToString(", ") }

        val mergedHeaders = buildHeaders(requestHeadersMap, userAgentMap, if (cookieHeader.isNotBlank()) mapOf("Cookie" to cookieHeader) else emptyMap())

        return app.baseClient.newCall(
            request.newBuilder()
                .headers(mergedHeaders)
                .build()
        ).await()
    }

    private suspend fun bypassCloudflare(request: Request): Response? {
        val url = request.url.toString()

        // If no cookies then try to get them (this also checks CookieManager for persisted cookies)
        if (!trySolveWithSavedCookies(request)) {
            Log.d(TAG, "Loading webview to solve cloudflare for ${request.url}")

            // Create resolver: set userAgent = null so resolver will use WebViewResolver.webViewUserAgent or system default.
            val resolver = WebViewResolver(
                Regex(".^"), // never exit based on url
                additionalUrls = listOf(Regex(".")),
                userAgent = null,
                useOkhttp = false,

            )

            // Launch WebView resolving on main thread (resolver implementation should handle threading)
            resolver.resolveUsingWebView(url) {
                trySolveWithSavedCookies(request)
            }
        }

        val cookies = savedCookies[request.url.host] ?: return null
        return proceed(request, cookies)
    }
}
