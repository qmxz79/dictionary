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
    }

    // Fast in-memory cache for OCR lookups
    private val translationCache = ConcurrentHashMap<String, Pair<String, DictionaryEntry?>>()

    suspend fun translateText(rawText: String): Pair<String, DictionaryEntry?> = withContext(Dispatchers.IO) {
        val cleanText = rawText.trim()
            .replace(Regex("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$"), "")
            .trim()

        if (cleanText.isEmpty()) return@withContext Pair("", null)

        val key = cleanText.lowercase()
        translationCache[key]?.let { return@withContext it }

        try {
            // Try exact lookup (offline only — no online fallback for OCR)
            val entries = repository.lookup(cleanText, SearchMode.AUTO_DETECT, offlineOnly = true)
            if (entries.isNotEmpty()) {
                val bestEntry = entries[0]
                val briefDef = extractBriefDefinition(bestEntry.definition)
                val result = Pair(briefDef, bestEntry)
                translationCache[key] = result
                return@withContext result
            }

            // If multi-word, try each word individually
            if (cleanText.contains(" ")) {
                val words = cleanText.split(" ").filter { it.length >= 2 }
                for (word in words) {
                    val subEntries = repository.lookup(word, SearchMode.AUTO_DETECT, offlineOnly = true)
                    if (subEntries.isNotEmpty()) {
                        val bestSub = subEntries[0]
                        val briefDef = "${word}: ${extractBriefDefinition(bestSub.definition)}"
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
        val firstLine = fullDef.lines().firstOrNull { it.trim().isNotEmpty() } ?: fullDef
        val clean = firstLine
            .replace(Regex("^[0-9]+[.\\s、]"), "")
            .replace(Regex("^(n|v|adj|adv|prep|conj|pron|art|num|int|phr)[.]\\s*"), "")
            .trim()

        return if (clean.length > 22) {
            clean.take(20) + "..."
        } else {
            clean
        }
    }

    fun clearCache() {
        translationCache.clear()
    }
}
