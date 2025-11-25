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
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.google.gson.Gson
import com.kayo.helper.FindSceneResponse
import com.kayo.helper.FindScenesResponse
import com.kayo.helper.SceneItem
import com.kayo.helper.findSceneById
import com.kayo.helper.getAllScenes
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.TrailerData
import kotlinx.coroutines.joinAll
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.collections.emptyList
import kotlin.time.DurationUnit


class Stash : MainAPI() {

    override var mainUrl = "http://192.168.1.6:30198"
    override var name = "Stash"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)
    private val apiKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOiJrYXlvIiwic3ViIjoiQVBJS2V5IiwiaWF0IjoxNzY0MDcwNjcwfQ.LkVoGtPjLOLiPNcR44WVwI7V8k105VNIWikxFWilRPg"

    private val gson = Gson()
    val okHttp = okhttp3.OkHttpClient()


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
                in 720..1079  -> SearchQuality.WebRip
                in 480..719   -> SearchQuality.DVD
                in 360..479   -> SearchQuality.SD
                else          -> SearchQuality.SD
            }

            newMovieSearchResponse(
                scene.title ?: "Untitled",
                scene.id.toString(),
                TvType.NSFW
            ) {
                posterUrl = scene.paths?.screenshot+"&apikey="+apiKey
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
        val bodyJson = getAllScenes(page, query)
        val initResponse = stashGraphQL(bodyJson)
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
                in 720..1079  -> SearchQuality.WebRip
                in 480..719   -> SearchQuality.DVD
                in 360..479   -> SearchQuality.SD
                else          -> SearchQuality.SD
            }

            newMovieSearchResponse(
                scene.title ?: "Untitled",
                scene.id ?: "",
                TvType.NSFW
            ) {
                this.posterUrl = scene.paths?.screenshot+"&apikey="+apiKey
                this.quality = quality1
            }
        }

        val hasNext = (page * 40) < totalCount

        return newSearchResponseList(items, hasNext)
    }




    override suspend fun load(url: String): LoadResponse {
        val bodyJson = findSceneById(url.toInt())
        val initResponse = stashGraphQL(bodyJson)
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


        return newMovieLoadResponse(sceneFull?.title ?: "" , url, TvType.NSFW, url) {
            this.posterUrl = sceneFull?.paths?.screenshot+"&apikey="+apiKey
            this.plot = sceneFull?.details
            this.tags = sceneFull?.tags?.map { it.name.toString() }
            this.actors = actors
            this.duration = ((sceneFull?.files?.firstOrNull()?.duration ?: 0.0) / 60).toInt()
            this.year = sceneFull?.date?.substring(0, 4)?.toInt()
//            this.backgroundPosterUrl =  sceneFull?.paths?.screenshot+"&apikey="+apiKey
            if(preview != null){
                this.trailers =
                    listOf(TrailerData((sceneFull.paths.preview + "&apikey=" + apiKey), "", true)) as MutableList<TrailerData>
            }
            }

        }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val parsed = AppUtils.parseJson<Map<String, String>>(
            document.select("span.vidsnfo").attr("data-vnfo")
        )

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = parsed.toString(),
                type = ExtractorLinkType.VIDEO
            ) {
                this.referer = ""
                this.quality = Qualities.Unknown.value
            }
        )
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


