package com.CXXX

import android.annotation.TargetApi
import android.os.Build
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.utils.loadExtractor

class SxyPrn : MainAPI() {
    override var mainUrl = "https://sxyprn.com"
    override var name = "Sxyprn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)

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
            app.get(request.data + pageStr).document
        } else if ("/blog/" in request.data) {
            pageStr = ((page - 1) * 20).toString()
            app.get(request.data.replace(".html", "$pageStr.html")).document
        } else {
            app.get(request.data.replace(".html", ".html/$pageStr")).document
        }
        val home = document.select("div.main_content div.post_el_small").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name, list = home, isHorizontalImages = true
            ), hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.post_text")?.text() ?: return null
        val href = fixUrl(this.selectFirst("a.js-pop")!!.attr("href"))
        var posterUrl = fixUrl(this.select("div.vid_container div.post_vid_thumb img").attr("src"))
        if (posterUrl == "") {
            posterUrl =
                fixUrl(this.select("div.vid_container div.post_vid_thumb img").attr("data-src"))
        }
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    override suspend fun search(query: String): List<SearchResponse> {
        val cookieRegex = Regex("cf_clearance=([^;\\s]+)", RegexOption.IGNORE_CASE)
        val cookieMatch = cookieRegex.find(query)
        val cfClearance = cookieMatch?.value?.trim()

        // Remove the cookie from the query so only the actual search term remains
        val cleanedQuery = query.replace(cookieRegex, "").trim()

        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 0 until 15) {
            val searchParam = if (cleanedQuery == "latest") "NEW" else cleanedQuery

            val headers = mutableMapOf<String, String>()
            headers["User-Agent"] = "Mozilla/5.0 (Android 13; Mobile; rv:139.0) Gecko/139.0 Firefox/139.0"
            if (cfClearance != null) {
                headers["Cookie"] = cfClearance
            }

            val doc = app.get(
                "$mainUrl/${searchParam.replace(" ", "-")}.html?page=${i * 30}",
                headers = headers
            ).document

            val results = doc.select("div.main_content div.post_el_small").mapNotNull {
                it.toSearchResult()
            }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }



    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("div.post_text")?.text()?.trim().toString()
        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")
                ?.attr("content")
        )

        val recommendations = document.select("div.main_content div div.post_el_small").mapNotNull {
            it.toSearchResult()
        }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.recommendations = recommendations
        }
    }

    private fun updateUrl(arg: MutableList<String>): MutableList<String> {
        arg[5] =
            (Integer.parseInt(arg[5]) - (generateNumber(arg[6]) + generateNumber(arg[7]))).toString()
        return arg
    }

    private fun generateNumber(arg: String): Int {
        val str = arg.replace(Regex("\\D"), "")
        var sut = 0
        for (element in str) {
            sut += Integer.parseInt(element.toString(), 10)
        }
        return sut
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val div = document.select(".post_text").first() ?: return false
        val a = div.select(".extlink")
        a.map {
            loadExtractor(it.attr("href"), null, subtitleCallback, callback)
        }
        return true
    }
}


