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
 * When used as Interceptor additionalUrls cannot be returned, use WebViewResolver(...).resolveUsingWebView(...)
 * @param interceptUrl will stop the WebView when reaching this url.
 * @param additionalUrls this will make resolveUsingWebView also return all other requests matching the list of Regex.
 * @param userAgent if null then will use the default user agent
 * @param useOkhttp will try to use the okhttp client as much as possible, but this might cause some requests to fail. Disable for cloudflare.
 * @param script pass custom js to execute
 * @param scriptCallback will be called with the result from custom js
 * @param timeout close webview after timeout
 */
class CustomWebViewResolver(
    val interceptUrl: Regex,
    val additionalUrls: List<Regex> = emptyList(),
    val userAgent: String? = null,
    val useOkhttp: Boolean = true,
    val script: String? = null,
    val scriptCallback: ((String) -> Unit)? = null,
    val timeout: Long = DEFAULT_TIMEOUT
) : Interceptor {

    companion object {
        var webViewUserAgent1: String? = null
        const val DEFAULT_TIMEOUT = 60_000L
        private const val TAG = "WebViewResolver"

        fun getwebViewUserAgent1(): String? {
            return webViewUserAgent1 ?: (getContext() as? Context)?.let { ctx ->
                runBlocking {
                    mainWork {
                        WebView(ctx).settings.userAgentString.also { userAgent ->
                            webViewUserAgent1 = userAgent
                        }
                    }
                }
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return runBlocking {
            val fixedRequest = resolveUsingWebView(request) { false }.first
            return@runBlocking chain.proceed(fixedRequest ?: request)
        }
    }

    suspend fun resolveUsingWebView(
        url: String,
        referer: String?,
        method: String,
        requestCallBack: (Request) -> Boolean,
    ): Pair<Request?, List<Request>> =
        resolveUsingWebView(url, referer, emptyMap(), method, requestCallBack)

    suspend fun resolveUsingWebView(
        url: String,
        referer: String?,
        headers: Map<String, String>,
        method: String,
        requestCallBack: (Request) -> Boolean,
    ): Pair<Request?, List<Request>> {
        return try {
            resolveUsingWebView(
                requestCreator(method, url, referer = referer, headers = headers),
                requestCallBack
            )
        } catch (e: java.lang.IllegalArgumentException) {
            logError(e)
            debugException { "ILLEGAL URL IN resolveUsingWebView!" }
            return null to emptyList()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolveUsingWebView(
        request: Request,
        requestCallBack: (Request) -> Boolean
    ): Pair<Request?, List<Request>> {
        val url = request.url.toString()
        val headers = request.headers
        Log.i(TAG, "Initial web-view request: $url")
        var webView: WebView? = null
        var shouldExit = false

        fun destroyWebView() {
            main {
                webView?.stopLoading()
                webView?.destroy()
                webView = null
                shouldExit = true
                Log.i(TAG, "Destroyed webview")
            }
        }

        var fixedRequest: Request? = null
        val extraRequestList = threadSafeListOf<Request>()

        main {
            WebView.setWebContentsDebuggingEnabled(true)
            try {
                webView = WebView(
                    (getContext() as? Context)
                        ?: throw RuntimeException("No base context in WebViewResolver")
                ).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    webViewUserAgent1 = settings.userAgentString
                    if (userAgent != null) {
                        settings.userAgentString = userAgent
                    }
                }

                webView?.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? = runBlocking {
                        val webViewUrl = request.url.toString()
                        Log.i(TAG, "Loading WebView URL: $webViewUrl")

                        if (script != null) {
                            val handler = Handler(Looper.getMainLooper())
                            handler.post {
                                view.evaluateJavascript(script)
                                { scriptCallback?.invoke(it) }
                            }
                        }

                        if (interceptUrl.containsMatchIn(webViewUrl)) {
                            fixedRequest = request.toRequest()?.also {
                                requestCallBack(it)
                            }
                            Log.i(TAG, "Web-view request finished: $webViewUrl")
                            destroyWebView()
                            return@runBlocking null
                        }

                        if (additionalUrls.any { it.containsMatchIn(webViewUrl) }) {
                            request.toRequest()?.also {
                                if (requestCallBack(it)) destroyWebView()
                            }?.let(extraRequestList::add)
                        }

                        val blacklistedFiles = listOf(
                            ".jpg", ".png", ".webp", ".mpg", ".mpeg", ".jpeg", ".webm", ".mp4",
                            ".mp3", ".gifv", ".flv", ".asf", ".mov", ".mng", ".mkv", ".ogg",
                            ".avi", ".wav", ".woff2", ".woff", ".ttf", ".css", ".vtt", ".srt",
                            ".ts", ".gif", "wss://"
                        )

                        return@runBlocking try {
                            when {
                                blacklistedFiles.any { URI(webViewUrl).path.contains(it) } ||
                                        webViewUrl.endsWith("/favicon.ico") ->
                                    WebResourceResponse("image/png", null, null)

                                webViewUrl.contains("recaptcha") ||
                                        webViewUrl.contains("/cdn-cgi/") ->
                                    super.shouldInterceptRequest(view, request)

                                useOkhttp && request.method == "GET" ->
                                    app.get(
                                        webViewUrl,
                                        headers = request.requestHeaders
                                    ).okhttpResponse.toWebResourceResponse()

                                useOkhttp && request.method == "POST" ->
                                    app.post(
                                        webViewUrl,
                                        headers = request.requestHeaders
                                    ).okhttpResponse.toWebResourceResponse()

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

        var loop = 0
        val totalTime = timeout
        val delayTime = 100L

        while (loop < totalTime / delayTime && !shouldExit) {
            if (fixedRequest != null) return fixedRequest to extraRequestList
            delay(delayTime)
            loop += 1
        }

        Log.i(TAG, "Web-view timeout after ${totalTime / 1000}s")
        destroyWebView()
        return fixedRequest to extraRequestList
    }
}

fun WebResourceRequest.toRequest(): Request? {
    val webViewUrl = this.url.toString()

    return safe {
        requestCreator(
            this.method,
            webViewUrl,
            this.requestHeaders,
        )
    }
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
