package com.kayo

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.delay
import org.jsoup.nodes.Element

class Fxprnhd : MainAPI() {
    override var mainUrl = "https://fxpornhd.com"
    override var name = "Fxprnhd"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "latest" to "Latest Video",
        "$mainUrl/c/bangbros" to "Bang Bros",
        "$mainUrl/c/brazzers" to "Brazzers",
        "$mainUrl/c/realitykings" to "Reality Kings",
        "$mainUrl/c/blacked" to "Blacked",
        "$mainUrl/c/pervmom" to "Pervmom",

        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val targetUrl = if (request.data == "latest") {
            "$mainUrl/page/$page/?s="
        } else {
            "${request.data}/page/$page"
        }

        val document = app.get(targetUrl).document
        val home =
            document.select("article")
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
        val title = this.selectFirst("span.title")?.text() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        var posterUrl = this.select("div.post-thumbnail img").attr("src")
        if (posterUrl.isEmpty()) {
            posterUrl = this.select("video.wpst-trailer").attr("poster")
        }
        Log.d("Fxprnhd", "toSearchResult: PosterUrl: $posterUrl")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = SearchQuality.HD
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        val searchParam = if (query == "latest") "" else query

        val firstDoc = app.get("$mainUrl/page/1/?s=$searchParam").document

        val lastPageUrl = firstDoc.select("div.pagination ul li").last()?.attr("href")
        val totalPages = Regex("""page/(\d+)/""").find(lastPageUrl ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: 1
        Log.d("Fxprnhd", "search: Total Pages: $totalPages")

        for (i in 1..totalPages) {
            val document = app.get("$mainUrl/page/$i/?s=$searchParam").document

            val results = document.select("div.videos-list > article")
                .mapNotNull { it.toSearchResult() }

            if (results.isEmpty()) break

            searchResponse.addAll(results)
        }

        return searchResponse.distinctBy { it.url }
    }


    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("div.title-views > h1")?.text()?.trim().toString()
        val poster =
            fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content").toString())
        val tags = document.select("div.tags-list > i").map { it.text() }
        val description = document.select("div#rmjs-1 p:nth-child(1) > br").text().trim()
        val actors =
            document.select("div#rmjs-1 p:nth-child(1) a:nth-child(2) > strong").map { it.text() }

        val recommendations =
            document.select("div.videos-list > article").mapNotNull {
                it.toSearchResult()
            }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            addActors(actors)
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val iframe = app.get(data).document.select("div.responsive-player iframe").attr("src")

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