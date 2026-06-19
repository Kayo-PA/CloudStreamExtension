package com.kayo.helper

import com.lagradost.cloudstream3.app
import java.security.MessageDigest

suspend fun getVideoUrl(
    uuid: String,
    id: String,
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
        "https://www9.pornhd3x.tv/ajax/get_sources/$uuid/$md5Hex?count=1&mobile=true",
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

fun generateId(): String {
    val chars =
        "abcdefghijklmnopqrstuvwxyz123456789"

    return (1..6)
        .map { chars.random() }
        .joinToString("")
}