package com.ArKanZi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Yandex : MainAPI() {
    override var mainUrl = "https://yandex.com/video"
    override var name = "Yandex"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.Others)

    override val mainPage = mainPageOf(
        "$mainUrl/search?text=latest+chinese+shorts+drama" to "Latest Drama",
        "$mainUrl/search?text=popular+chinese+shorts+drama" to "Popular",
        "$mainUrl/search?text=random+chinese+shorts+drama" to "Random Drama",
        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val targetUrl = "${request.data}&p=$page"


        val document = app.get(targetUrl).document
        val home =
            document.select("li.SearchBlock")
                .mapNotNull {
                    it.toSearchResult()
                }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("a.VideoSnippet-Title")?.attr("title") ?: return null
        val href = fixUrl(this.selectFirst("a.VideoSnippet-Title")!!.attr("href"))
        val posterUrl = this.select("video.VideoThumb3-Video").attr("poster")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..5) {
            val searchParam = if (query == "latest") "" else query
            val document = app.get("$mainUrl/search?text=${searchParam.replace(" ","+")}&p=$i").document
            val results =
                document.select("li.SearchBlock")
                    .mapNotNull {
                        it.toSearchResult()
                    }
            searchResponse.addAll(results)
            if (results.isEmpty()) break
        }
        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h2.iatUaVbAyPbOfNZ0-DetailsTitle")?.text()?.trim().toString()
        val poster =
            fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content").toString())
        val recommendations =
            document.select("article.box").mapNotNull {
                it.toSearchResult()
            }

        return newMovieLoadResponse(title, url, TvType.AsianDrama, url) {
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

        val iframe = app.get(data).document.select("div.epsdlist a").attr("href")

        if (iframe.startsWith(mainUrl)) {
            val video = app.get(iframe, referer = data).document.select("video source").attr("src")
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    video,
                    INFER_TYPE
                ) {
                    this.referer = "$mainUrl/"
                }
            )
        } else {
            loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
        }

        return true
    }
}