package com.coxju

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONException
import org.json.JSONObject

class Spankbang : MainAPI() {
    override var mainUrl = "https://spankbang.com"
    override var name = "Spankbang"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "${mainUrl}/trending_videos/" to "New Videos",
        "${mainUrl}/7r/channel/adult+time/" to "Adult Time ",
        "${mainUrl}/ce/channel/bratty+milf/" to "Bratty MILF",
        "${mainUrl}/cf/channel/bratty+sis/" to "Bratty Sis",
        "${mainUrl}/ho/channel/brazzers/" to "Brazzers",
        "${mainUrl}/49/channel/deeper/" to "Deeper",
        "${mainUrl}/9n/channel/evil+angel/" to "Evil Angel",
        "${mainUrl}/j2/channel/familyxxx/" to "Family XXX",
        "${mainUrl}/9h/channel/hardx/" to "HardX",
        "${mainUrl}/j3/channel/hot+wife+xxx/" to "Hot Wife XXX",
        "${mainUrl}/k5/channel/japan+hdv/" to "Japan HDV",
        "${mainUrl}/6w/channel/javhd/" to "JavHD",
        "${mainUrl}/4w/channel/letsdoeit/" to "Letsdoeit",
        "${mainUrl}/d6/channel/my+family+pies/" to "My Family Pies",
        "${mainUrl}/6l/channel/mylf/" to "MYLF",
        "${mainUrl}/3x/channel/naughty+america/" to "Naughty America",
        "${mainUrl}/ch/channel/nf+busty/" to "NF Busty",
        "${mainUrl}/ci/channel/nubile+films/" to "Nubile Films",
        "${mainUrl}/cc/channel/nubiles+porn/" to "Nubiles Porn",
        "${mainUrl}/60/channel/puretaboo/" to "PureTaboo",
        "${mainUrl}/hp/channel/realitykings/" to "RealityKings",
        "${mainUrl}/6c/channel/teamskeet/" to "TeamSkeet",
        "${mainUrl}/47/channel/vixen/" to "Vixen",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page).document
        var videos = document.select("div.video-item")
        if (videos.isEmpty()) videos = document.select("div.mb-6").select(".js-video-item")
        val home = videos.mapNotNull { it.toSearchResult() }

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
        var a = this.select("a[x-data=\"videoItem\"]")
        if (a.isEmpty()) a = this.select("a.thumb")

        val img = a.select("picture > img")
        val title = fixTitle(img.attr("alt")).trim()
        val posterUrl = fixUrlNull(img.attr("data-src"))
        val href = fixUrl(a.attr("href"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val searchParam = if (query == "latest") {
                "${mainUrl}/new_videos/"
            } else {
                "${mainUrl}/s/$query/"
            }
            val document = app.get(searchParam).document
            val results = document.select("div.video-item")
                .mapNotNull { it.toSearchResult() }

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

        val title =
            document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
        val poster = fixUrlNull(document.selectFirst("meta[property='og:image']")?.attr("content"))
        val description =
            document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()


        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val script = document.select("script").find { it.data().contains("stream_data") }?.data()
            ?: return false
        val streamData = script.substringAfter("var stream_data = ").substringBefore(";")
        val obj = JSONObject(streamData)
        val keys = mutableListOf<String>()
        obj.keys().forEach { keys.add(it) }
        keys.map { key ->
            try {
                val link = obj.getJSONArray(key).getString(0)
                val type =
                    if (key.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO

                val quality = if (type != ExtractorLinkType.M3U8) key.replace("p", "")
                    .toIntOrNull() else null

                val name = if (quality == null) this.name + " " + key else this.name
                Log.d("${this.name} - $key", link)
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = link,
                        type = type
                    )
                    {
                        this.referer = data
                        quality?.let { this.quality = it }
                    }
                )
            } catch (_: JSONException) {

            }
        }
        return true
    }
}