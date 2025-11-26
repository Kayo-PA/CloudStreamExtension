package com.kayo

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.HttpUrl.Companion.toHttpUrl

class LuluStream : BigWarp(
    name = "Lulustream"
) {
    override val name: String = "Lulustream"
    override val mainUrl: String = "https://lulustream.com"
}

open class BigWarp(
    override val name: String = "Big Warp",
    override val mainUrl: String = "https://bigwarp.io",
    override val requiresReferer: Boolean = false
) : ExtractorApi() {
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val resp = app.get(url).document
        val script =
            resp.select("script").find { it.data().contains("jwplayer(\"vplayer\").setup(") }
                ?.data()?.replace("file:", "\"file\":")
                ?.replace("label:", "\"label\":") ?: return
        val src = Regex("(?<=sources: ).*(?=,)").find(script)?.value ?: return
        val parsedSources = parseJson<List<Source>>(src)
        parsedSources.map {
            callback(
                newExtractorLink(
                    this.name,
                    this.name,
                    it.src,
                    type = INFER_TYPE
                ) {
                    this.headers = mapOf("Host" to it.src.toHttpUrl().host)
                }
            )
        }
    }

    data class Source(
        @JsonProperty("file") val src: String,
        @JsonProperty("label") val label: String?,
    )
}