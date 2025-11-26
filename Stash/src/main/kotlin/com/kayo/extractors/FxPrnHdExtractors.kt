package com.kayo.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class FxPrnHdExtractors(
    override val name: String = "FxPrnHd",
    override val mainUrl: String = "https://fxpornhd.com",
    override val requiresReferer: Boolean = false
) : ExtractorApi() {

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val doc = app.get(url).document

        // STEP 1: find iframe holding player
        val iframe = doc.select("div.responsive-player iframe").attr("src")

        if (iframe.isBlank()) return

        // STEP 2: If this iframe is fxpornhd itself
        if (iframe.startsWith(mainUrl)) {

            val videoDoc = app.get(iframe, referer = url).document
            val videoUrl = videoDoc.select("video source").attr("src")

            if (videoUrl.isBlank()) return

            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name (Direct)",
                    url = videoUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = mainUrl
                    this.quality = getQualityFromUrl(videoUrl)
                }
            )
        }
        else {
            // STEP 3: Let CloudStream extract from iframe provider (StreamTape, Dood, VidGuard, etc.)
            loadExtractor(iframe, referer ?: url, subtitleCallback, callback)
        }
    }

    private fun getQualityFromUrl(url: String): Int {
        return when {
            url.contains("2160") || url.contains("4k", ignoreCase = true) -> 2160
            url.contains("1440") -> 1440
            url.contains("1080") -> 1080
            url.contains("720") -> 720
            url.contains("480") -> 480
            else -> -1
        }
    }
}
