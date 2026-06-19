package com.kayo.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.*

class FxPrnHdExtractor(
    override val name: String = "Fxpornhd",
    override val mainUrl: String = "https://fxpornhd.com",
    override val requiresReferer: Boolean = false
) : ExtractorApi() {

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        coroutineScope {

            val document = app.get(url).document

            val iframeUrls = document.select("iframe[src]")
                .map { normalize(it.attr("src")) }
                .filter { it.isNotBlank() }
                .distinct()

            val trackingUrl = document.select("a#tracking-url.button")
                .attr("href")
                .takeIf { it.isNotBlank() }
                ?.let(::normalize)

            val jobs = mutableListOf<Deferred<Unit>>()

            iframeUrls.forEach { iframe ->

                // Try Cloudstream extractors on iframe URL
                jobs.add(
                    async(Dispatchers.IO) {
                        runCatching {
                            loadExtractor(
                                iframe,
                                referer ?: url,
                                subtitleCallback,
                                callback
                            )
                        }
                        Unit
                    }
                )

                // Check iframe page for direct video sources
                jobs.add(
                    async(Dispatchers.IO) {
                        runCatching {

                            val iframeDoc = app.get(
                                iframe,
                                referer = url
                            ).document

                            iframeDoc.select("video[src], source[src]")
                                .forEach { element ->

                                    val src = element.attr("src")
                                    if (src.isBlank()) return@forEach

                                    callback(
                                        newExtractorLink(
                                            source = name,
                                            name = "$name Direct",
                                            url = normalize(src),
                                            type = if (
                                                src.contains(
                                                    ".m3u8",
                                                    ignoreCase = true
                                                )
                                            ) {
                                                ExtractorLinkType.M3U8
                                            } else {
                                                ExtractorLinkType.VIDEO
                                            }
                                        ) {
                                            this.referer = iframe
                                        }
                                    )
                                }
                        }
                        Unit
                    }
                )
            }

            // Tracking button contains extractor URLs only
            trackingUrl?.let { tracking ->

                jobs.add(
                    async(Dispatchers.IO) {
                        runCatching {
                            loadExtractor(
                                tracking,
                                referer ?: url,
                                subtitleCallback,
                                callback
                            )
                        }
                        Unit
                    }
                )
            }

            jobs.awaitAll()
        }
    }

    private fun normalize(url: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            else -> url
        }
    }
}