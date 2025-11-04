package com.kayo.helper

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.*
import com.lagradost.api.Log
import com.lagradost.api.getContext
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.debugException
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.Coroutines.mainWork
import com.lagradost.cloudstream3.utils.Coroutines.threadSafeListOf
import com.lagradost.nicehttp.requestCreator
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.URI

/**
 * Android-only WebView-based resolver.
 * Mimics JVM class behavior with proper WebView logic.
 */
class CustomWebViewResolver(
    private val interceptUrl: Regex,
    private val additionalUrls: List<Regex> = emptyList(),
    private val userAgent: String? = DEFAULT_USER_AGENT,
    private val useOkhttp: Boolean = true,
    private val script: String? = null,
    private val scriptCallback: ((String) -> Unit)? = null,
    private val timeout: Long = DEFAULT_TIMEOUT
) : Interceptor {

    companion object {
        private const val TAG = "WebViewResolver"
        const val DEFAULT_TIMEOUT = 60_000L
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"

        var webViewUserAgent: String? = null

        fun getWebViewUserAgent(): String? {
            return webViewUserAgent ?: (getContext() as? Context)?.let { ctx ->
                runBlocking {
                    mainWork {
                        WebView(ctx).settings.userAgentString.also {
                            webViewUserAgent = it
                        }
                    }
                }
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return runBlocking {
            val fixedRequest = resolveUsingWebView(request).first
            chain.proceed(fixedRequest ?: request)
        }
    }

    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        method: String = "GET",
        requestCallBack: (Request) -> Boolean = { false },
    ): Pair<Request?, List<Request>> =
        resolveUsingWebView(url, referer, emptyMap(), method, requestCallBack)

    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
        method: String = "GET",
        requestCallBack: (Request) -> Boolean = { false },
    ): Pair<Request?, List<Request>> {
        return try {
            resolveUsingWebView(
                requestCreator(method, url, referer = referer, headers = headers),
                requestCallBack
            )
        } catch (e: IllegalArgumentException) {
            logError(e)
            debugException { "ILLEGAL URL IN resolveUsingWebView!" }
            null to emptyList()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolveUsingWebView(
        request: Request,
        requestCallBack: (Request) -> Boolean = { false }
    ): Pair<Request?, List<Request>> {
        val url = request.url.toString()
        val headers = request.headers
        Log.i(TAG, "Initial WebView request: $url")

        var webView: WebView? = null
        var shouldExit = false
        var fixedRequest: Request? = null
        val extraRequests = threadSafeListOf<Request>()

        fun destroyWebView() {
            main {
                webView?.stopLoading()
                webView?.destroy()
                webView = null
                shouldExit = true
                Log.i(TAG, "Destroyed WebView")
            }
        }

        main {
            try {
                WebView.setWebContentsDebuggingEnabled(true)
                webView = WebView(getContext() as Context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    if (userAgent != null) settings.userAgentString = userAgent
                }

                webView?.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? = runBlocking {
                        val reqUrl = request.url.toString()

                        if (script != null) {
                            Handler(Looper.getMainLooper()).post {
                                view.evaluateJavascript(script) {
                                    scriptCallback?.invoke(it)
                                }
                            }
                        }

                        if (interceptUrl.containsMatchIn(reqUrl)) {
                            fixedRequest = request.toRequest()?.apply { requestCallBack(this) }
                            destroyWebView()
                            return@runBlocking null
                        }

                        if (additionalUrls.any { it.containsMatchIn(reqUrl) }) {
                            request.toRequest()?.also {
                                if (requestCallBack(it)) destroyWebView()
                            }?.let(extraRequests::add)
                        }

                        val blacklist = listOf(
                            ".jpg", ".png", ".webp", ".jpeg", ".gif",
                            ".css", ".woff", ".woff2", ".ttf", ".ts"
                        )

                        return@runBlocking try {
                            when {
                                blacklist.any { URI(reqUrl).path.contains(it) } ||
                                        reqUrl.endsWith("/favicon.ico") ->
                                    WebResourceResponse("image/png", null, null)

                                reqUrl.contains("recaptcha") || reqUrl.contains("/cdn-cgi/") ->
                                    super.shouldInterceptRequest(view, request)

                                useOkhttp && request.method == "GET" ->
                                    app.get(reqUrl, headers = request.requestHeaders)
                                        .okhttpResponse.toWebResourceResponse()

                                useOkhttp && request.method == "POST" ->
                                    app.post(reqUrl, headers = request.requestHeaders)
                                        .okhttpResponse.toWebResourceResponse()

                                else -> super.shouldInterceptRequest(view, request)
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }

                    @SuppressLint("WebViewClientOnReceivedSslError")
                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        handler?.proceed()
                    }
                }

                webView?.loadUrl(url, headers.toMap())
            } catch (e: Exception) {
                logError(e)
            }
        }

        val delayStep = 100L
        var elapsed = 0L
        while (elapsed < timeout && !shouldExit) {
            if (fixedRequest != null) return fixedRequest to extraRequests
            delay(delayStep)
            elapsed += delayStep
        }

        Log.i(TAG, "WebViewResolver timeout after ${timeout / 1000}s")
        destroyWebView()
        return fixedRequest to extraRequests
    }
}

fun WebResourceRequest.toRequest(): Request? = safe {
    val url = this.url.toString()
    requestCreator(this.method, url, this.requestHeaders)
}

fun Response.toWebResourceResponse(): WebResourceResponse {
    val contentTypeValue = this.header("Content-Type")
    val typeRegex = Regex("""(.*);(?:.*charset=(.*)(?:|;)|)""")

    return if (contentTypeValue != null) {
        val found = typeRegex.find(contentTypeValue)
        val contentType = found?.groupValues?.getOrNull(1)?.ifBlank { null } ?: contentTypeValue
        val charset = found?.groupValues?.getOrNull(2)?.ifBlank { null }
        WebResourceResponse(contentType, charset, this.body.byteStream())
    } else {
        WebResourceResponse("application/octet-stream", null, this.body.byteStream())
    }
}
