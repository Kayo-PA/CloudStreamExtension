package com.kayo

import android.util.Log
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.VPNStatus
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList

class SxyPrnWin : MainAPI() {
    override var mainUrl = "https://www.sxyprn.net"
    override var name = "SxyPrnWin"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)
    private val cfInterceptor = CloudflareKiller().apply {
        WebViewResolver.webViewUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0"
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
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        var pageStr = ((page - 1) * 30).toString()


        val document = if ("page=" in request.data) {
            app.get(request.data + pageStr, interceptor = cfInterceptor).document
        } else if ("/blog/" in request.data) {
            pageStr = ((page - 1) * 20).toString()
            app.get(
                request.data.replace(".html", "$pageStr.html"),
                interceptor = cfInterceptor,
            ).document
        } else {
            app.get(
                request.data.replace(".html", ".html/$pageStr"),
                interceptor = cfInterceptor,
            ).document
        }
        val home = document.select("a.js-pop").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name, list = home, isHorizontalImages = true
            ), hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.attr("title")
        val href = fixUrl(this.attr("href"))
        var posterUrl = fixUrl(this.select("div.post_el_small_mob_ls img").attr("src"))
        if (posterUrl == "") {
            posterUrl =
                fixUrl(this.select("div.post_el_small_mob_ls img").attr("data-src"))
        }
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val searchParam = if (query == "latest") "NEW" else query
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0"
        )

        // Fetch the current page
        val doc = app.get(
            url = "$mainUrl/${searchParam.replace(" ", "-")}.html?page=${(page - 1) * 30}",
            headers = headers,
            interceptor = cfInterceptor
        ).document

        Log.d("SxyPrnWinSearch", "$doc")
        // Extract all results
        val results = doc.select("a.js-pop").mapNotNull { it.toSearchResult() }

        // Determine if there’s a next page
        val hasNextPage = (doc.select("div#center_control a").size.takeIf { it > 0 } ?: 1) > page
        

        // Return in the new SearchResponseList format
        return newSearchResponseList(results, hasNextPage)
    }




    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, interceptor = cfInterceptor, timeout = 100L).document
        var production = ""
        var starring = ""
        var title1 = ""
        if (document.selectFirst("div.post_text h1 b.sub_cat_s")?.toString() != null) {
            production = "[" + document.selectFirst("div.post_text h1 b.sub_cat_s")?.text()
                ?.replace(Regex("[^A-Za-z0-9 ]"), "") + "] "
        }
        if (document.select("div.post_text h1 a.ps_link.tdn.transition").toString() != "") {
            starring =
                document.select("div.post_text a.ps_link.tdn.transition")
                    .joinToString { it.text().replace(Regex("[^A-Za-z0-9 ]"), "") } + " - "
        }
        if (document.selectFirst("div.post_text h1")?.ownText() != "") {
            title1 = document.selectFirst("div.post_text h1")?.ownText()?.substringBefore(".")
                ?.replace(Regex("[^A-Za-z0-9 ]"), "")?.trim() ?: ""
        }
        
        val title = production + starring + title1
        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")
                ?.attr("content")
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
        val document = app.get(data, interceptor = cfInterceptor).document

        val parsed = AppUtils.parseJson<Map<String, String>>(
            document.select("span.vidsnfo").attr("data-vnfo")
        )
        val pid = parsed.keys.first()
        var url = parsed[pid]!! // non-nullable
        val host = "sxyprn.com"

        val tmp = url.split("/").toMutableList()
        tmp[1] += "8/${boo(generateNumber(tmp[6]), generateNumber(tmp[7]), host)}"
        updateUrl(tmp)

        url = mainUrl + tmp.joinToString("/")
        val newurl = app.get(url, interceptor = cfInterceptor).url

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


