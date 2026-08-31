package com.bilingual.dictionary.ocr

import android.util.Log
import com.bilingual.dictionary.data.model.DictionaryEntry
import com.bilingual.dictionary.data.model.SearchMode
import com.bilingual.dictionary.data.repository.DictionaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class OcrTranslator(private val repository: DictionaryRepository) {

    companion object {
        private const val TAG = "OcrTranslator"
        private val STOPWORDS = setOf("and", "or", "in", "on", "at", "to", "for", "of", "a", "an", "the", "is", "it", "oe")
    }

    // Fast in-memory cache for OCR lookups
    private val translationCache = ConcurrentHashMap<String, Pair<String, DictionaryEntry?>>()

    suspend fun translateText(rawText: String, allowOnline: Boolean = true): Pair<String, DictionaryEntry?> = withContext(Dispatchers.IO) {
        val cleanText = rawText.trim()
            .replace(Regex("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$"), "")
            .trim()

        if (cleanText.length < 2) return@withContext Pair("", null)

        val key = "${cleanText.lowercase()}_$allowOnline"
        translationCache[key]?.let { return@withContext it }

        try {
            // 1. Try offline exact dictionary lookup first
            val offlineEntries = repository.lookup(cleanText, SearchMode.AUTO_DETECT, offlineOnly = true)
            if (offlineEntries.isNotEmpty()) {
                val bestEntry = offlineEntries[0]
                val briefDef = extractBriefDefinition(bestEntry.definition)
                val result = Pair(briefDef, bestEntry)
                translationCache[key] = result
                return@withContext result
            }

            // 2. If online translation is enabled (e.g. photo / gallery / full sentence), translate entire sentence
            if (allowOnline) {
                val onlineEntries = repository.lookup(cleanText, SearchMode.AUTO_DETECT, offlineOnly = false)
                if (onlineEntries.isNotEmpty()) {
                    val bestEntry = onlineEntries[0]
                    val briefDef = extractBriefDefinition(bestEntry.definition)
                    if (briefDef.isNotEmpty() && !briefDef.equals(cleanText, ignoreCase = true)) {
                        val result = Pair(briefDef, bestEntry)
                        translationCache[key] = result
                        return@withContext result
                    }
                }
            }

            // 3. If multi-word and offline fallback, look for meaningful content words (skip minor stopwords)
            if (cleanText.contains(" ")) {
                val words = cleanText.split(Regex("[\\s/]+"))
                    .map { it.trim().replace(Regex("[^a-zA-Z\\u4e00-\\u9fa5]"), "") }
                    .filter { it.length >= 3 && !STOPWORDS.contains(it.lowercase()) }

                for (word in words) {
                    val subEntries = repository.lookup(word, SearchMode.AUTO_DETECT, offlineOnly = true)
                    if (subEntries.isNotEmpty()) {
                        val bestSub = subEntries[0]
                        val briefDef = extractBriefDefinition(bestSub.definition)
                        val result = Pair(briefDef, bestSub)
                        translationCache[key] = result
                        return@withContext result
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "translateText error for '$cleanText': ${e.message}")
        }

        // No match found — return empty translation so it won't be displayed
        val emptyResult = Pair("", null)
        translationCache[key] = emptyResult
        emptyResult
    }

    private fun extractBriefDefinition(fullDef: String): String {
        val firstLine = fullDef.lines()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("•") } ?: fullDef.lines().firstOrNull { it.isNotEmpty() } ?: fullDef

        val clean = firstLine
            .replace(Regex("^[0-9]+[.\\s、]"), "")
            .replace(Regex("^(n|v|adj|adv|prep|conj|pron|art|num|int|phr|abbr)[.]\\s*"), "")
            .replace(Regex("^[•\\-\\s]+"), "")
            .trim()

        return if (clean.length > 26) {
            clean.take(24) + "..."
        } else {
            clean
        }
    }

    fun clearCache() {
        translationCache.clear()
    }
}
