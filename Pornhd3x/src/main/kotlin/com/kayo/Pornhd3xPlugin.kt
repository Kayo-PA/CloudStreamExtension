package com.kayo

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Pornhd3xPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Pornhd3x())
//        registerExtractorAPI(StreamTape())
    }
}