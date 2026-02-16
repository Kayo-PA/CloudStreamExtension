package com.kayo

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Pornhd4kPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Pornhd4k())
//        registerExtractorAPI(StreamTape())
    }
}