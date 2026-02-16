package com.kayo

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import kotlin.time.Duration
import kotlin.time.DurationUnit

class Fxprnhd : MainAPI() {
    override var mainUrl = "https://fxpornhd.com"
    override var name = "Fxprnhd"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)
    private val actorImgUrl = "https://cdn.dt18.com/images/names/big/"

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
        val trailer = "https:" + this.selectFirst("video.wpst-trailer source")?.attr("src")
        val href = fixUrl(this.selectFirst("a")!!.attr("href")) + "," + trailer
        var posterUrl = this.select("div.post-thumbnail").attr("data-thumbs")
        if (posterUrl.isEmpty()) {
            posterUrl = this.select("video.wpst-trailer").attr("poster")
        }
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = SearchQuality.HD
        }

    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val searchParam = if (query == "latest") "" else query
        val document = app.get("$mainUrl/page/$page/?s=$searchParam").document
        val results = document.select("div.videos-list > article").mapNotNull { it.toSearchResult() }
        val lastPageUrl = document.select("div.pagination ul li").last()?.selectFirst("a")?.attr("href")
        val totalPages = Regex("""page/(\d+)/""").find(lastPageUrl ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val hasNext = page < totalPages
        return newSearchResponseList(list = results, hasNext = hasNext)
    }

    override suspend fun load(url: String): LoadResponse {
        var newurl: String
        var trailerUrl: String
        if (url.contains(",")) {
            newurl = url.substringBeforeLast(",")
            trailerUrl = url.substringAfterLast(",")
        } else {
            newurl = url
            trailerUrl = "https:null"
        }
        val document = app.get(newurl).document

        val title = document.selectFirst("div.title-views > h1")?.text()?.trim().toString()
        val poster =
            fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content").toString())
        val tags = document.select("div.tags-list > i").map { it.text() }
        val description = document.select("div#rmjs-1 p:nth-child(1) > br").text().trim()
        val actorNames = document.select("div.tags-list a[href*=/actor/]")
            .mapNotNull { it.attr("title").takeIf { name -> name.isNotBlank() } }
        val actors = actorNames.map { name ->
            ActorData(
                Actor(
                    name,
                    "$actorImgUrl${name.replace(" ", "-").lowercase()}.jpg"
                )
            )
        }


        val duration = Duration.parse(
            document.select("div.video-player meta[itemprop=duration]").attr("content")
        )
        val recommendations =
            document.select("div.videos-list > article").mapNotNull {
                it.toSearchResult()
            }

        return newMovieLoadResponse(title, newurl, TvType.NSFW, newurl) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.actors = actors
            this.recommendations = recommendations
            this.duration = duration.toInt(DurationUnit.MINUTES)
            this.year = 2025
            if (trailerUrl != "https:null") {
                this.trailers =
                    listOf(TrailerData(trailerUrl, "", true)) as MutableList<TrailerData>
            }
        }

    }

    //    override suspend fun loadLinks(
//        data: String,
//        isCasting: Boolean,
//        subtitleCallback: (SubtitleFile) -> Unit,
//        callback: (ExtractorLink) -> Unit
//    ): Boolean {
//
//        val iframe = app.get(data).document.select("div.responsive-player iframe").attr("src")
//
//        if (iframe.startsWith(mainUrl)) {
//            val video = app.get(iframe, referer = data).document.select("video source").attr("src")
//            callback.invoke(
//                newExtractorLink(
//                    this.name,
//                    this.name,
//                    video,
//                    ExtractorLinkType.VIDEO
//                ) {
//                    this.referer = "$mainUrl/"
//                }
//            )
//        } else {
//            loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
//        }
//
//        return true
//    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val page = app.get(data).document
        val referer = data
        var foundAny = false

        // ---------- 1) Collect all iframes ----------
        val iframes = page.select("iframe")
            .mapNotNull { it.attr("src")?.takeIf { s -> s.isNotBlank() } }
            .map { fixUrl(it) }
            .distinct()

        // ---------- 2) Try extractors on each iframe FIRST ----------
        for (iframeUrl in iframes) {
            try {
                loadExtractor(
                    iframeUrl,
                    referer,
                    subtitleCallback
                ) { link ->
                    foundAny = true
                    callback(link)
                }
            } catch (_: Throwable) {
            }
        }

        // ---------- 3) Load each iframe page and try direct <video>/<source> ----------
        for (iframeUrl in iframes) {
            try {
                val iframeDoc = app.get(iframeUrl, referer = referer).document

                // <video src="...">
                iframeDoc.select("video[src]").forEach { v ->
                    val src = v.attr("src")
                    if (src.isNotBlank()) {
                        foundAny = true
                        callback(
                            newExtractorLink(
                                name,
                                name,
                                fixUrl(src),
                                ExtractorLinkType.VIDEO
                            ) {
                                this.referer = iframeUrl
                            }
                        )
                    }
                }

                // <video><source src="..."></source></video>
                iframeDoc.select("video source[src]").forEach { s ->
                    val src = s.attr("src")
                    if (src.isNotBlank()) {
                        val type = if (src.contains(".m3u8", true))
                            ExtractorLinkType.M3U8
                        else
                            ExtractorLinkType.VIDEO

                        foundAny = true
                        callback(
                            newExtractorLink(
                                name,
                                name,
                                fixUrl(src),
                                type
                            ) {
                                this.referer = iframeUrl
                            }
                        )
                    }
                }

                // ---------- 4) JWPlayer / script scan for visible HLS ----------
                // This works ONLY if the URL is visible in JS (soft JWPlayer)
                val scriptsText = iframeDoc.select("script").joinToString("\n") { it.data() }

                // Common patterns: file:"...m3u8", sources:[{file:"..."}]
                val hlsRegex = Regex(
                    """(?i)(file|src)\s*[:=]\s*["']([^"'\\]+\.m3u8[^"'\\]*)["']"""
                )

                hlsRegex.findAll(scriptsText).forEach { m ->
                    val hlsUrl = m.groupValues[2]
                    if (hlsUrl.isNotBlank()) {
                        foundAny = true
                        callback(
                            newExtractorLink(
                                name,
                                "HLS",
                                fixUrl(hlsUrl)
                            ) {
                                this.referer = iframeUrl
                            }
                        )
                    }
                }

            } catch (_: Throwable) {
            }
        }

        // ---------- 5) Final fallback: try extractors on the page itself ----------
        if (!foundAny) {
            try {
                loadExtractor(
                    data,
                    referer,
                    subtitleCallback
                ) { link ->
                    foundAny = true
                    callback(link)
                }
            } catch (_: Throwable) {
            }
        }

        return foundAny
    }

}