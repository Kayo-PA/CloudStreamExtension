package com.CXXX

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class SxyPrn : MainAPI() {
    override var mainUrl = "https://sxyprn.com"
    override var name = "Sxyprn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)

    private val cookieHeader = mapOf(
        "Cookie" to "cf_clearance=zBNuTtzboYKhXk4GlV9Fx5QEAUjAnWxkpfCrYABd83g-1750492283-1.2.1.1-Nx.GDie0e1yvycBSEdGu1F05PlR48JCDAjZB_x6SKRFIcE3jLmW0CG6ac3MNkvpuRTywxj56WSVHFEfWbfWjhwHr.D1PHnzKBYubHJ6pw309VWZ4U5YShBE9g7s.9srIcEq4sT1o3VWqsATB.I6U16HWYEeA4mXRXU742erCLVXfzEmm58dvL.UYoCQ1SJrhvatsBBH8G59d2iUgT1Ks0zHuHmpHZs1.bd8uHcSqwbKdy.Z6Ch4WQOLIVJmTFCp.76uvEi0fml5owUl0wmY6IjgUh8v5o.IgUQWCZF9iGB2r8a7Mln4vH2GEMh2v4BxWyJ.yIHXBvL9BeKOmgag2ZXi.Yzf.md9pvEMgETAQjI.RVdWQcDbHTilp1OBryztd; PHPSESSID=kefqs5aidh5ndo9k60gabhb41a;",
        "User-Agent" to USER_AGENT
    )

    override val mainPage = mainPageOf(
        "$mainUrl/new.html?page=" to "New Videos",
        "$mainUrl/new.html?sm=trending&page=" to "Trending",
        "$mainUrl/new.html?sm=views&page=" to "Most Viewed",
        "$mainUrl/popular/top-viewed.html?p=day" to "Popular - Day",
        "$mainUrl/popular/top-viewed.html" to "Popular - Week",
        "$mainUrl/popular/top-viewed.html?p=month" to "Popular - Month",
        "$mainUrl/popular/top-viewed.html?p=all" to "Popular - All Time"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var pageStr = ((page - 1) * 30).toString()

        val document = if ("page=" in request.data) {
            app.get(request.data + pageStr, headers = cookieHeader).document
        } else if ("/blog/" in request.data) {
            pageStr = ((page - 1) * 20).toString()
            app.get(request.data.replace(".html", "$pageStr.html"), headers = cookieHeader).document
        } else {
            app.get(request.data.replace(".html", ".html/$pageStr"), headers = cookieHeader).document
        }

        val home = document.select("div.main_content div.post_el_small").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(
            list = HomePageList(request.name, home, isHorizontalImages = true),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.post_text")?.text() ?: return null
        val href = fixUrl(this.selectFirst("a.js-pop")!!.attr("href"))
        var posterUrl = fixUrl(this.select("div.vid_container div.post_vid_thumb img").attr("src"))
        if (posterUrl.isBlank()) {
            posterUrl = fixUrl(this.select("div.vid_container div.post_vid_thumb img").attr("data-src"))
        }
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 0 until 15) {
            val searchParam = if (query == "latest") "NEW" else query
            val document = app.get(
                "$mainUrl/${searchParam.replace(" ", "-")}.html?page=${i * 30}",
                headers = cookieHeader
            ).document

            val results = document.select("div.main_content div.post_el_small").mapNotNull {
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
        val document = app.get(url, headers = cookieHeader).document
        val title = document.selectFirst("div.post_text")?.text()?.trim().toString()
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val recommendations = document.select("div.main_content div div.post_el_small").mapNotNull {
            it.toSearchResult()
        }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = cookieHeader).document
        val div = document.select(".post_text").first() ?: return false
        val a = div.select(".extlink")
        a.map {
            loadExtractor(it.attr("href"), null, subtitleCallback, callback)
        }
        return true
    }

    // Obfuscated URL handler (used by the site)
    private fun updateUrl(arg: MutableList<String>): MutableList<String> {
        arg[5] = (Integer.parseInt(arg[5]) - (generateNumber(arg[6]) + generateNumber(arg[7]))).toString()
        return arg
    }

    private fun generateNumber(arg: String): Int {
        val str = arg.replace(Regex("\\D"), "")
        var sum = 0
        for (ch in str) {
            sum += ch.toString().toInt()
        }
        return sum
    }
}
