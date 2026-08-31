package com.bilingual.dictionary.data.network

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OnlineTranslationService {

    private const val TAG = "OnlineTranslation"
    private const val TIMEOUT_MS = 6000

    /**
     * Translates text using lightweight online API (MyMemory / Free Open Gateway).
     * @param query Text to translate
     * @param sourceLang e.g. "en", "ms", "zh"
     * @param targetLang e.g. "zh", "en", "ms"
     */
    fun translate(query: String, sourceLang: String, targetLang: String): String? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null

        val langPair = "${sourceLang}|${targetLang}"
        val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
        val urlString = "https://api.mymemory.translated.net/get?q=$encodedQuery&langpair=$langPair"

        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "BilingualDictionaryApp/1.0 (Android)")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()

                parseMyMemoryResponse(sb.toString(), trimmed)
            } else {
                Log.w(TAG, "Server returned response code: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Translation request failed: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseMyMemoryResponse(jsonStr: String, originalQuery: String): String? {
        return try {
            val root = JSONObject(jsonStr)
            val responseStatus = root.optInt("responseStatus", 200)
            if (responseStatus != 200) {
                Log.w(TAG, "MyMemory responseStatus not 200: $responseStatus")
                return null
            }

            val responseData = root.optJSONObject("responseData")
            val translatedText = responseData?.optString("translatedText")?.trim()

            if (translatedText.isNullOrEmpty() || isInvalidTranslation(translatedText, originalQuery)) {
                return null
            }

            val cleanTranslations = mutableListOf<String>()
            cleanTranslations.add(translatedText)

            val matches = root.optJSONArray("matches")
            if (matches != null) {
                for (i in 0 until minOf(matches.length(), 4)) {
                    val matchObj = matches.getJSONObject(i)
                    val trans = matchObj.optString("translation").trim()
                    if (!trans.isNullOrEmpty()
                        && !cleanTranslations.contains(trans)
                        && !isInvalidTranslation(trans, originalQuery)
                    ) {
                        cleanTranslations.add(trans)
                    }
                }
            }

            cleanTranslations.joinToString("\n• ")
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse error", e)
            null
        }
    }

    private fun isInvalidTranslation(text: String, originalQuery: String): Boolean {
        val upper = text.uppercase()
        return upper.contains("MYMEMORY WARNING") ||
                upper.contains("INVALID SOURCE LANGUAGE") ||
                upper.contains("INVALID TARGET LANGUAGE") ||
                upper.contains("NO QUERY SPECIFIED") ||
                upper.contains("IS AN INVALID") ||
                upper.contains("PLEASE SPECIFY A VALID") ||
                upper.contains("QUERY LENGTH LIMIT EXCEEDED") ||
                upper.contains("YOU HAVE USED ALL YOUR FREE CREDITS") ||
                (text.equals(originalQuery, ignoreCase = true) && text.length > 3)
    }
}
