package com.kayo.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.security.MessageDigest

class PornHd4KExtractor(
    override val name: String = "PornHD4K",
    override val mainUrl: String = "https://pornhd4k.net",
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
                url = videoUrl,
                type = ExtractorLinkType.M3U8
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

    private suspend fun getVideoUrl(
        uuid: String,
        id: String
    ): String? {

        val input =
            uuid + id + "98126avrbi6m49vd7shxkn985"

        val md5Hex =
            MessageDigest
                .getInstance("MD5")
                .digest(input.toByteArray())
                .joinToString("") {
                    "%02x".format(it)
                }

        val cookie =
            "826avrbi6m49vd7shxkn985m${uuid}k06twz87wwxtp3dqiicks2df=$id"

        val response = app.get(
            "$mainUrl/ajax/get_sources/$uuid/$md5Hex?count=1&mobile=true",
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Cookie" to cookie,
                "Accept" to "application/json"
            )
        ).text

        return """(?<="file":")[^"]+"""
            .toRegex()
            .find(response)
            ?.value
    }

    private fun generateId(): String {
        val chars =
            "abcdefghijklmnopqrstuvwxyz123456789"

        return (1..6)
            .map { chars.random() }
            .joinToString("")
    }
}