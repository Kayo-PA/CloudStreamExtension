package com.kayo

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URI

class SxyPrnWin : MainAPI() {
    override var mainUrl = "https://www.sxyprn.net"
    override var name = "SxyPrnWin"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)

    // Cloudflare interceptor – single instance for the session
    private val cfInterceptor by lazy { CloudflareKiller() }

    // Keep track of hosts that are already cleared
    private val cfPrimedHosts = mutableSetOf<String>()

    // Your desired User-Agent string
    private val customUA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0"

    // Helper to make sure Cloudflare clearance cookies are available
    private suspend fun ensureCfPrimed(url: String) {
        val host = URI(url).host ?: return
        if (cfPrimedHosts.contains(host)) return
        try {
            Log.d("SxyPrnWin", "Priming Cloudflare for $host")
            // Trigger CloudflareKiller once to solve challenge and save cookies
            app.get(url, interceptor = cfInterceptor, timeout = 20000L)
            cfPrimedHosts.add(host)
        } catch (e: Exception) {
            Log.w("SxyPrnWin", "Priming failed for $host: ${e.message}")
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/new.html?page=" to "New Videos",
        "$mainUrl/new.html?sm=trending&page=" to "Trending",
        "$mainUrl/new.html?sm=views&page=" to "Most Viewed",
        "$mainUrl/popular/top-viewed.html?p=day" to "Popular - Day",
        "$mainUrl/popular/top-viewed.html" to "Popular - Week",
        "$mainUrl/popular/top-viewed.html?p=month" to "Popular - Month",
        "$mainUrl/popular/top-viewed.html?p=all" to "Popular - All Time"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        ensureCfPrimed(mainUrl)

        val pageStr = ((page - 1) * 30).toString()
        val headers = mapOf("User-Agent" to customUA)

        val document = when {
            "page=" in request.data -> app.get(request.data + pageStr, headers = headers).document
            "/blog/" in request.data -> {
                val newPage = ((page - 1) * 20).toString()
                app.get(request.data.replace(".html", "$newPage.html"), headers = headers).document
            }
            else -> app.get(
                request.data.replace(".html", ".html/$pageStr"),
                headers = headers
            ).document
        }
        val home = document.select("a.js-pop").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = attr("title")
        val href = fixUrl(attr("href"))
        var posterUrl = fixUrl(select("div.post_el_small_mob_ls img").attr("src"))
        if (posterUrl.isBlank()) {
            posterUrl = fixUrl(select("div.post_el_small_mob_ls img").attr("data-src"))
        }
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        ensureCfPrimed(mainUrl)

        val searchParam = if (query == "latest") "NEW" else query
        val headers = mapOf("User-Agent" to customUA)
        val url = "$mainUrl/${searchParam.replace(" ", "-")}.html?page=${(page - 1) * 30}"

        val doc = try {
            app.get(url, headers = headers).document
        } catch (_: Exception) {
            // If blocked again, re-prime and retry once
            ensureCfPrimed(mainUrl)
            app.get(url, headers = headers).document
        }
        Log.d("SxyprnWin",doc.toString())
        val results = doc.select("a.js-pop").mapNotNull { it.toSearchResult() }
        val hasNextPage = (doc.select("div#center_control a").size.takeIf { it > 0 } ?: 1) > page
        return newSearchResponseList(results, hasNextPage)
    }

    override suspend fun load(url: String): LoadResponse {
        ensureCfPrimed(mainUrl)
        val headers = mapOf("User-Agent" to customUA)

        val document = app.get(url, headers = headers, timeout = 15000L).document
        var production = ""
        var starring = ""
        var title1 = ""

        document.selectFirst("div.post_text h1 b.sub_cat_s")?.let {
            production = "[${it.text().replace(Regex("[^A-Za-z0-9 ]"), "")}] "
        }

        if (document.select("div.post_text h1 a.ps_link.tdn.transition").isNotEmpty()) {
            starring = document.select("div.post_text a.ps_link.tdn.transition")
                .joinToString { it.text().replace(Regex("[^A-Za-z0-9 ]"), "") } + " - "
        }

        document.selectFirst("div.post_text h1")?.ownText()?.let {
            title1 = it.substringBefore(".")
                .replace(Regex("[^A-Za-z0-9 ]"), "")
                .trim()
        }

        val title = production + starring + title1
        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")?.attr("content")
        )

        val recommendations = document.select("div.main_content div div.post_el_small").mapNotNull {
            it.toSearchResult()
        }
        val description = document.select("div.post_text h1").text()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.recommendations = recommendations
        }
    }

    private fun updateUrl(arg: MutableList<String>) {
        arg[5] = (arg[5].toInt() - (generateNumber(arg[6]) + generateNumber(arg[7]))).toString()
    }

    private fun generateNumber(arg: String): Int {
        val digitsOnly = arg.replace(Regex("\\D"), "")
        return digitsOnly.sumOf { it.digitToInt() }
    }

    private fun boo(ss: Int, es: Int, host: String): String {
        val plain = "$ss-$host-$es"
        val b64 =
            android.util.Base64.encodeToString(plain.toByteArray(), android.util.Base64.NO_WRAP)
        return b64.replace("+", "-").replace("/", "_").replace("=", ".")
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureCfPrimed(mainUrl)
        val headers = mapOf("User-Agent" to customUA)

        val document = app.get(data, headers = headers).document
        val parsed = AppUtils.parseJson<Map<String, String>>(
            document.select("span.vidsnfo").attr("data-vnfo")
        )

        val pid = parsed.keys.first()
        var url = parsed[pid]!!
        val host = "sxyprn.com"

        val tmp = url.split("/").toMutableList()
        tmp[1] += "8/${boo(generateNumber(tmp[6]), generateNumber(tmp[7]), host)}"
        updateUrl(tmp)

        url = mainUrl + tmp.joinToString("/")
        val newurl = app.get(url, headers = headers).url

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = newurl,
                type = ExtractorLinkType.VIDEO
            ) {
                this.referer = ""
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }
}
