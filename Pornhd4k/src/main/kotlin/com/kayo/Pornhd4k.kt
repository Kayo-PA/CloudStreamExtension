package com.kayo

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Element
import java.io.IOException
import java.security.MessageDigest

class Pornhd4k : MainAPI() {
    override var mainUrl = "https://pornhd4k.net"
    override var name = "Pornhd4k"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)
    private val cfInterceptor = CloudflareKiller()

    override val mainPage = mainPageOf(
        "latest" to "Latest Videos",
        "$mainUrl/studio/bang-bros" to "Bang Bros",
        "$mainUrl/studio/brazzers" to "Brazzers",
        "$mainUrl/studio/realitykings" to "Reality Kings",
        "$mainUrl/search/pervmom" to "Pervmom",
        "$mainUrl/studio/nubiles-porn" to "Nubiles Porn",
        "$mainUrl/studio/nubilefilms" to "Nubile Films",
        "$mainUrl/studio/teamskeet" to "Team Skeet",
        "$mainUrl/studio/mylf" to "Mylf",
        "$mainUrl/search/Vixen" to "Vixen",
        "$mainUrl/search/netvideogirls" to "Net Video Girls",
        "$mainUrl/search/digitalplayground" to "DigitalPlayground",
        "$mainUrl/studio/fakehub" to "Fakehub",
        "$mainUrl/studio/naughtyamerica" to "Naughty America"

    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val targetUrl = if (request.data == "latest") {
            "$mainUrl/premium-porn-hd/page-$page"

        } else {
            "${request.data}/page-$page"
        }

        val document = app.get(targetUrl).document
        val home =
            document.select("div.ml-item a")
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

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.attr("title")
        val imgTag = this.selectFirst("div.thumb__img img")!!
        val href = fixUrl(mainUrl + this.attr("href"))

        val posterUrl = mainUrl+imgTag.attr("data-original")


        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..5) {
            val searchUrl =
                if ("p=" in query) "$mainUrl/pornstar/${query.replace(" ","-").replace("p=","")}"
                else if (query == "latest") "$mainUrl/premium-porn-hd/page-$i"
                else if (" " in query) "$mainUrl/search/${query.replace(" ", "%20")}"
                else "$mainUrl/search/${query.replace(" ", "%20")}/page-$i"
            val document = app.get(searchUrl, interceptor = cfInterceptor).document
            val results =
                document.select("div.ml-item a")
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
        val info = document.selectFirst("div.mvi-content")!!
        val title = info.selectFirst("div.mvic-desc > h3")!!.text()
        val poster = document.selectFirst("meta[property=\"og:image\"]")?.attr("content")
        val tags = document.select("div#mv-keywords a").map { it!!.text() }
        val description = info.selectFirst("div.desc")!!.text()
        val actors =
            info.select("div.mvic-info div.mvici-left p:contains(Actor:) a").map { it!!.text() }

        val recommendations =
            document.select("div.ml-item a").mapNotNull {
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
        val document = app.get(data).document
        val uuid = document.selectFirst("input#uuid")?.attr("value") ?: return false

        val characters = "abcdefghijklmnopqrstuvwxyz123456789"
        val generatedId = (1..6).map { characters.random() }.joinToString("")
        val videoUrl = geturl(uuid, generatedId) ?: return false

        callback.invoke(
            newExtractorLink(
                name,
                name,
                videoUrl,
                type = ExtractorLinkType.M3U8
            ) {
                val cookie = "826avrbi6m49vd7shxkn985m${uuid}k06twz87wwxtp3dqiicks2df=$generatedId"
                this.referer = data
                this.headers = mapOf(
                    "Accept" to "*/*",
                    "Cookie" to cookie,
                    "User-Agent" to USER_AGENT,
                    "accept-encoding" to "gzip, deflate, br, zstd",
                    "accept-language" to "en-US,en;q=0.9",
                    "cache-control" to "no-cache",
                    "dnt" to "1",
                    "origin" to mainUrl,
                    "pragma" to "no-cache",
                    "priority" to "u=1, i"
                )
            }
        )

        return true
    }

    fun geturl(uuid: String, id: String): String? {
        val input = (uuid + id + "98126avrbi6m49vd7shxkn985")
        val md = MessageDigest.getInstance("MD5")
        val md5Bytes = md.digest(input.toByteArray())
        val md5Hex = md5Bytes.joinToString("") { "%02x".format(it) }
        val rurl = "https://www9.pornhd3x.tv/ajax/get_sources/$uuid/$md5Hex?count=1&mobile=true"
        val cookie = "826avrbi6m49vd7shxkn985m${uuid}k06twz87wwxtp3dqiicks2df=$id"
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(rurl)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Cookie", cookie)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code $response")
            }
            val responseData = response.body.string()
            val regex = """(?<="file":")[^"]+""".toRegex()
            val matchResult = regex.find(responseData)
            val ur = matchResult?.value
            return ur
        }
    }

}