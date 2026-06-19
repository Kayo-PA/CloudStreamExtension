package com.kayo.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.kayo.helper.generateId
import com.kayo.helper.getVideoUrl

class PornHd3xExtractor(
    override val name: String = "PornHD",
    override val mainUrl: String = "https://www9.pornhd3x.tv",
    override val requiresReferer: Boolean = false
) : ExtractorApi() {

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val document = app.get(url).document

        val uuid = document
            .selectFirst("input#uuid")
            ?.attr("value")
            ?: return

        val generatedId = generateId()

        val videoUrl = getVideoUrl(
            uuid = uuid,
            id = generatedId
        ) ?: return

        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = videoUrl
            ) {
                val cookie =
                    "826avrbi6m49vd7shxkn985m${uuid}k06twz87wwxtp3dqiicks2df=$generatedId"

                this.referer = url

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
    }
}