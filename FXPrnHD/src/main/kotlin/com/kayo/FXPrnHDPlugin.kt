package com.kayo

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.kayo.extractor.Pornkx
import com.lagradost.cloudstream3.extractors.JWPlayer
import com.lagradost.cloudstream3.extractors.StreamTape

@CloudstreamPlugin
class FXPrnHDPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Fxprnhd())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(Pornkx())

    }
}