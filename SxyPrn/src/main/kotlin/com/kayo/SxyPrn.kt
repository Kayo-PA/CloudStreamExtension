package com.kayo

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class SxyPrn : MainAPI() {
    override var mainUrl = "https://sxyprn.com"
    override var name = "Sxyprn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)
    private val cfInterceptor = CloudflareKiller()

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

    override suspend fun search(query: String): List<SearchResponse> {

        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 0 until 15) {
            val searchParam = if (query == "latest") "NEW" else query
            val headers =
                mapOf("User-Agent" to "Mozilla/5.0 (Android 13; Mobile; rv:139.0) Gecko/139.0 Firefox/139.0")
            val doc = app.get(
                url = "$mainUrl/${searchParam.replace(" ", "-")}.html?page=${i * 30}",
                headers = headers,
                interceptor = cfInterceptor
            ).document
            Log.e("sxyprnLog", doc.toString())
            val results = doc.select("a.js-pop").mapNotNull {
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
        val document = app.get(url, interceptor = cfInterceptor, timeout = 100L).document
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

        url = tmp.joinToString("/")

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = url
            ) {
                this.referer = ""
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }
}


