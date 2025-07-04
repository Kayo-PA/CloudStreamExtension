package com.ArKanZi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.StreamTape

@CloudstreamPlugin
class YandexPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Yandex())
//        registerExtractorAPI(StreamTape())
    }
}