package com.megix

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class YesPornPlease : MainAPI() {
    override var mainUrl              = "https://yespornpleasexxx.com"
    override var name                 = "YesPornPlease"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        mainUrl to "Home",
        "${mainUrl}/xnxx/small-tits/" to "Small Tits",
        "${mainUrl}/xnxx/teen/" to "Teen",
        "${mainUrl}/xnxx/threesome/" to "Threesome",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/page/${page}/").document
        val home = document.select("div.post-preview-styling").mapNotNull { it.toSearchResult() }

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
        val title = this.selectFirst("a")!!.attr("title")
        val href = this.selectFirst("a")!!.attr("href")
        var posterUrl = this.selectFirst("a > img")!!.attr("data-src")
        if(posterUrl.isEmpty()) {
            posterUrl = this.selectFirst("a > img")!!.attr("src")
        }
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val searchParam = if (query == "latest") "" else query
            val document = app.get("${mainUrl}/page/${i}/?s=${searchParam}").document

            val results = document.select("div.post-preview-styling").mapNotNull { it.toSearchResult() }

            searchResponse.addAll(results)

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("meta[property=og:title]")!!.attr("content")
        val posterUrl = document.selectFirst("meta[property=og:image]")!!.attr("content")
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
        ): Boolean {
        val document = app.get(data).document
        val iframes = document.select("iframe")
        if(iframes.size >= 3) {
            val thirdIframe = iframes[2]
            val link = thirdIframe.attr("data-litespeed-src")
            val doc = app.get(link).document
            val source = doc.selectFirst("video > source")?.attr("src") ?: return false
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    source,
                    INFER_TYPE
                )
                {
                    referer = mainUrl
                }
            )
        }

        return true
    }
}
