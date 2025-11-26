package com.kayo

import android.util.Log
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.VPNStatus
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.google.gson.Gson
import com.kayo.dummy.allList
import com.kayo.dummy.that792
import com.kayo.dummy.that793
import com.kayo.helper.FindSceneResponse
import com.kayo.helper.FindScenesResponse
import com.kayo.helper.findSceneById
import com.kayo.helper.getAllScenes
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.collections.emptyList


class Stash : MainAPI() {

    override var mainUrl = "http://192.168.1.6:30198"
    override var name = "Stash"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)
    private val apiKey =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOiJrYXlvIiwic3ViIjoiQVBJS2V5IiwiaWF0IjoxNzY0MDcwNjcwfQ.LkVoGtPjLOLiPNcR44WVwI7V8k105VNIWikxFWilRPg"

    private val gson = Gson()
    val okHttp = OkHttpClient()


    override val mainPage = mainPageOf(
        "latest" to "Latest",
        "popular" to "Popular"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val jsonBody = getAllScenes(page)
        val response = stashGraphQL(jsonBody)
        val parsed = gson.fromJson(response, FindScenesResponse::class.java)
        val scenes = parsed.data?.findScenes?.scenes ?: emptyList()
        val totalCount = parsed.data?.findScenes?.count ?: 0

        // Convert to CloudStream SearchResponse
        val items = scenes.map { scene ->
            val file = scene.files?.maxByOrNull { it.height ?: 0 }
            val quality1 = when (file?.height ?: 0) {
                in 2160..5000 -> SearchQuality.FourK
                in 1440..2159 -> SearchQuality.HQ
                in 1080..1439 -> SearchQuality.HD
                in 720..1079 -> SearchQuality.WebRip
                in 480..719 -> SearchQuality.DVD
                in 360..479 -> SearchQuality.SD
                else -> SearchQuality.SD
            }

            newMovieSearchResponse(
                scene.title ?: "Untitled",
                scene.id.toString(),
                TvType.NSFW
            ) {
                posterUrl = scene.paths?.screenshot + "&apikey=" + apiKey
                quality = quality1
            }
        }
        val hasNext = (page * 40) < totalCount
        return newHomePageResponse(
            HomePageList(request.name, items),
            hasNext = hasNext
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        var initResponse: String
        if (query == "Dummy") {
            initResponse = allList()
        } else {
            val jsonBody = getAllScenes(page, query)
            initResponse = stashGraphQL(jsonBody)
        }
//        val bodyJson = getAllScenes(page, query)
//        val initResponse = stashGraphQL(bodyJson)
        val parsed = gson.fromJson(initResponse, FindScenesResponse::class.java)
        val result = parsed.data?.findScenes ?: return null

        val scenes = result.scenes ?: emptyList()
        val totalCount = result.count ?: 0

        // Convert scenes → CloudStream SearchResponse
        val items = scenes.map { scene ->
            val file = scene.files?.maxByOrNull { it.height ?: 0 }
            val quality1 = when (file?.height ?: 0) {
                in 2160..5000 -> SearchQuality.FourK
                in 1440..2159 -> SearchQuality.HQ
                in 1080..1439 -> SearchQuality.HD
                in 720..1079 -> SearchQuality.WebRip
                in 480..719 -> SearchQuality.DVD
                in 360..479 -> SearchQuality.SD
                else -> SearchQuality.SD
            }

            newMovieSearchResponse(
                scene.title ?: "Untitled",
                scene.id ?: "",
                TvType.NSFW
            ) {
                this.posterUrl = scene.paths?.screenshot + "&apikey=" + apiKey
                this.quality = quality1
            }
        }

        val hasNext = (page * 40) < totalCount

        return newSearchResponseList(items, hasNext)
    }


    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        var initResponse: String
        if (id == "792") {
            initResponse = that792()
        } else if ((id == "793")) {
            initResponse = that793()
        } else {
            val jsonBody = findSceneById(id.toInt())
            initResponse = stashGraphQL(jsonBody)
        }
//        val bodyJson = findSceneById(id.toInt())
//        val initResponse = stashGraphQL(bodyJson)
        val parsed = gson.fromJson(initResponse, FindSceneResponse::class.java)
        val sceneFull = parsed.data?.findScene
        val preview = sceneFull?.paths?.preview?.takeIf { it.isNotBlank() }
        val actors = sceneFull?.performers
            ?.map { performer ->
                ActorData(
                    Actor(
                        performer.name ?: "Unknown",
                        (performer.image_path + "&apikey=" + apiKey)   // or your own URL builder
                    )
                )
            } ?: emptyList()
        return newMovieLoadResponse(sceneFull?.title ?: "", url, TvType.NSFW, url) {
            this.posterUrl = sceneFull?.paths?.screenshot + "&apikey=" + apiKey
            this.plot = sceneFull?.details
            this.tags = sceneFull?.tags?.map { it.name.toString() }
            this.actors = actors
            this.duration = ((sceneFull?.files?.firstOrNull()?.duration ?: 0.0) / 60).toInt()
            this.year = sceneFull?.date?.substring(0, 4)?.toInt()
//            this.backgroundPosterUrl =  sceneFull?.paths?.screenshot+"&apikey="+apiKey


            if (preview != null) {
                addTrailer(
                    sceneFull.paths.preview + "?apikey=" + apiKey, addRaw = true, headers = mapOf(
                        "ApiKey" to apiKey,
                        "User-Agent" to "Mozilla/5.0",
                        "Accept" to "*/*"
                    )
                )
            }
        }

    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("loadlink123","is running")
        val id = data.substringAfterLast("/")
        var initResponse: String
        if (id == "792") {
            initResponse = that792()
        } else if ((id == "793")) {
            initResponse = that793()
        } else {
            val jsonBody = findSceneById(id.toInt())
            initResponse = stashGraphQL(jsonBody)
        }
//        val bodyJson = findSceneById(id.toInt())
//        val initResponse = stashGraphQL(bodyJson)
        val parsed = gson.fromJson(initResponse, FindSceneResponse::class.java)
        val sceneFull = parsed.data?.findScene ?: return false
        val captionUrl = sceneFull.paths?.caption + "?lang=en&type=vtt"
        if (captionUrl.isNotBlank()) {
            subtitleCallback.invoke(
                newSubtitleFile(
                    "English",
                    captionUrl
                )
            )
        }

        val streams = sceneFull.sceneStreams ?: emptyList()



        for (stream in streams) {
            val streamUrl = stream.url ?: continue
            callback.invoke(
                newExtractorLink(
                    source = "Stash",
                    name = stream.label ?: "Stream",
                    url = streamUrl,
                    type = ExtractorLinkType.VIDEO
                )
            )
        }

        val externalUrls = sceneFull.urls ?: emptyList()
        Log.d("external links",externalUrls.toString())

        for (ext in externalUrls) {
            if (ext.isBlank()) continue

            // CloudStream extractor handler
            loadExtractor(
                ext,
                referer = mainUrl,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        }

        return true
    }

    fun stashGraphQL(bodyJson: String): String {
        val req = Request.Builder()
            .url("$mainUrl/graphql")
            .addHeader("ApiKey", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        val resp = okHttp.newCall(req).execute().body.string()
        return resp
    }

}


