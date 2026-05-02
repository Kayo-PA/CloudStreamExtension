package com.kayo.extractor

import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.app
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import com.google.gson.Gson
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

var kaken: String? = null
var pd: String? = null
var ps: String? = null

data class ApiResponse(
    val sources: List<Source>?
)

data class Source(
    val file: String?
)

open class Pornkx(
    override val name: String = "Pornkx",
    override val mainUrl: String = "https://hls.pornkx.com",
    override val requiresReferer: Boolean = false
) : ExtractorApi() {
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val resp = app.get(url).document
        val scripts = resp.select("script:not([src])")
        val aaScript = scripts.firstOrNull { script ->
            val js = script.data()
            js.contains("ﾟωﾟ") && js.contains("ﾟДﾟ")
        }
        val aaEncoded = aaScript!!.data()

        val decoded = AAEncodeDecoder.decode(aaEncoded)
        val unpacked = JsUnpacker(decoded).unpack() ?: decoded
        applyDecodedValues(unpacked)
        val apiUrl = "https://hls.pornkx.com/api/?p="
        val body = kaken?.toRequestBody("text/plain; charset=utf-8".toMediaType())
        val document = app.post(url = apiUrl+ps, headers = mapOf("Content-Type" to "text/plain"),requestBody = body).body.string()
        val gson = Gson()
        val response = gson.fromJson(decryptCiphertext(document,pd!!), ApiResponse::class.java)
        val sourceFile = response.sources
            ?.firstOrNull()
            ?.file ?: ""
        callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    sourceFile,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                })
    }
    object AAEncodeDecoder {

        // Inverse AAEncode digit table
        private val table = linkedMapOf(
            "(c^_^o)" to "0",
            "(ﾟΘﾟ)" to "1",
            "((o^_^o) - (ﾟΘﾟ))" to "2",
            "(o^_^o)" to "3",
            "(ﾟｰﾟ)" to "4",
            "((ﾟｰﾟ) + (ﾟΘﾟ))" to "5",
            "((o^_^o) +(o^_^o))" to "6",
            "((ﾟｰﾟ) + (o^_^o))" to "7",
            "((ﾟｰﾟ) + (ﾟｰﾟ))" to "8",
            "((ﾟｰﾟ) + (ﾟｰﾟ) + (ﾟΘﾟ))" to "9",
            "(ﾟДﾟ) .ﾟωﾟﾉ" to "A",
            "(ﾟДﾟ) .ﾟΘﾟﾉ" to "B",
            "(ﾟДﾟ) ['c']" to "C",
            "(ﾟДﾟ) .ﾟｰﾟﾉ" to "D",
            "(ﾟДﾟ) .ﾟДﾟﾉ" to "E",
            "(ﾟДﾟ) [ﾟΘﾟ]" to "F",
        )

        // Regex that matches any AAEncode digit token
        private val tokenRegex = Regex(
            table.keys.joinToString("|") { Regex.escape(it) }
        )

        // Regex to extract each encoded character block
        private val charBlockRegex =
            Regex("""\(ﾟДﾟ\)\[ﾟεﾟ\]\+[\s\S]*?(?=\(ﾟДﾟ\)\[ﾟεﾟ\]\+|\(ﾟДﾟ\)\[ﾟoﾟ\]|$)""")

        fun decode(input: String): String {
            val blocks = charBlockRegex.findAll(input).toList()
            if (blocks.isEmpty()) return ""

            val result = StringBuilder()

            for (blockMatch in blocks) {
                val block = blockMatch.value
                val digits = StringBuilder()

                tokenRegex.findAll(block).forEach { m ->
                    digits.append(table[m.value])
                }

                val digitStr = digits.toString()

                val codePoint =
                    if (digitStr.length <= 3) {
                        // ASCII (octal)
                        digitStr.toInt(8)
                    } else {
                        // Unicode (hex)
                        digitStr.toInt(16)
                    }

                result.append(codePoint.toChar())
            }

            return result.toString()
        }
    }
    fun applyDecodedValues(decodedJs: String) {
        val regex =
            Regex("""(?:window\.)?([a-zA-Z_][a-zA-Z0-9_]*)\s*=\s*(?:"([^"]*)"|([^;]+));""")

        for (match in regex.findAll(decodedJs)) {
            val key = match.groupValues[1]
            val raw = match.groupValues[2].ifEmpty {
                match.groupValues[3].trim()
            }

            when (key) {
                "kaken" -> kaken = raw
                "pd" -> pd = raw
                "ps" -> ps = raw
            }
        }
    }
    fun decryptCiphertext(base64Input: String, pd: String): String {
        val decoded = Base64.getDecoder().decode(base64Input)

        // 1. Extract salt (first 16 bytes)
        val salt = decoded.copyOfRange(0, 16)

        // 2. Extract ciphertext
        val ciphertext = decoded.copyOfRange(16, decoded.size)

        // 3. PBKDF2 derive 48 bytes
        val keySpec = PBEKeySpec(
            pd.toCharArray(),
            salt,
            10_000,
            48 * 8 // bits
        )

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(keySpec).encoded

        // 4. Split key + IV
        val aesKey = derived.copyOfRange(0, 32)
        val iv = derived.copyOfRange(32, 48)

        // 5. AES-CBC decrypt
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            IvParameterSpec(iv)
        )

        val plainBytes = cipher.doFinal(ciphertext)
        return String(plainBytes, Charsets.UTF_8)
    }
}