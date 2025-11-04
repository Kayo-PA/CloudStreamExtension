package com.kayo.helper

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Android implementation of WebViewResolver.
 * This resolves Cloudflare or JavaScript-protected pages by using a hidden WebView.
 */
class CustomWebViewResolver(
    private val interceptUrl: Regex,
    private val additionalUrls: List<Regex> = emptyList(),
    private val userAgent: String? = DEFAULT_UA,
    private val useOkhttp: Boolean = true, // placeholder
    private val script: String? = null,
    private val scriptCallback: ((String) -> Unit)? = null,
    private val timeout: Long = DEFAULT_TIMEOUT
) : Interceptor {

    companion object {
        const val DEFAULT_TIMEOUT: Long = 15000L // 15 seconds default
        var webViewUserAgent: String? = null
        private const val TAG = "WebViewResolver"

        const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        // This allows it to be used as a no-op interceptor if needed
        Log.d(TAG, "Intercepted request: ${chain.request().url}")
        return chain.proceed(chain.request())
    }

    /**
     * Resolve a URL in a WebView to bypass JavaScript/Cloudflare and extract requests.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
        method: String = "GET",
        requestCallBack: (Request) -> Boolean = { false },
    ): Pair<Request?, List<Request>> = withContext(Dispatchers.Main) {
        val resultDeferred = CompletableDeferred<Pair<Request?, List<Request>>>()
        val matchedRequests = mutableListOf<Request>()
        var interceptedRequest: Request? = null

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        val webView = WebView(appContext()).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = userAgent ?: webViewUserAgent ?: DEFAULT_UA

            // Save for other classes (CloudflareKiller)
            webViewUserAgent = settings.userAgentString

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().acceptThirdPartyCookies(this)
        }

        val handler = Handler(Looper.getMainLooper())

        val webClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val req = request ?: return null
                val reqUrl = req.url.toString()

                val okhttpReq = Request.Builder().url(reqUrl).apply {
                    req.requestHeaders.forEach { (k, v) -> header(k, v) }
                    header("User-Agent", userAgent ?: DEFAULT_UA)
                }.build()

                when {
                    interceptUrl.containsMatchIn(reqUrl) -> {
                        interceptedRequest = okhttpReq
                        if (!resultDeferred.isCompleted) {
                            resultDeferred.complete(interceptedRequest to matchedRequests)
                        }
                        view?.destroy()
                        return WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
                    }
                    additionalUrls.any { it.containsMatchIn(reqUrl) } -> {
                        matchedRequests.add(okhttpReq)
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                script?.let {
                    view?.evaluateJavascript(it) { result ->
                        scriptCallback?.invoke(result)
                    }
                }
            }
        }

        webView.webViewClient = webClient

        handler.postDelayed({
            if (!resultDeferred.isCompleted) {
                Log.w(TAG, "WebView timeout for $url")
                resultDeferred.complete(interceptedRequest to matchedRequests)
                webView.destroy()
            }
        }, timeout)

        // Load the target page
        val combinedHeaders = headers.toMutableMap()
        if (referer != null) combinedHeaders["Referer"] = referer
        webView.loadUrl(url, combinedHeaders)

        // Await until an intercepted request or timeout occurs
        val result = resultDeferred.await()

        try {
            webView.stopLoading()
            webView.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up WebView: ${e.message}")
        }

        result
    }

    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        method: String = "GET",
        requestCallBack: (Request) -> Boolean = { false },
    ): Pair<Request?, List<Request>> =
        resolveUsingWebView(url, referer, emptyMap(), method, requestCallBack)

    suspend fun resolveUsingWebView(
        request: Request,
        requestCallBack: (Request) -> Boolean = { false }
    ): Pair<Request?, List<Request>> =
        resolveUsingWebView(request.url.toString(), request.header("Referer"))
}

/**
 * Replace this with your actual Application context getter.
 * If you have a custom Application class (e.g., MyApp : Application),
 * store a static instance there and return it here.
 */
fun appContext(): android.content.Context {
    // ❗ Replace this with your actual Application reference
    return try {
        Application().applicationContext
    } catch (e: Exception) {
        throw IllegalStateException("Please replace appContext() with your real Application context provider.")
    }
}
