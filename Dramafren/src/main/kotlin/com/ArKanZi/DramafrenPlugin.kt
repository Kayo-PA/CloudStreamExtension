package com.ArKanZi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.StreamTape

@CloudstreamPlugin
class DramafrenPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Dramafren())
//        registerExtractorAPI(StreamTape())
    }
}