package com.bilingual.dictionary.ocr

import com.bilingual.dictionary.data.model.DictionaryEntry
import com.bilingual.dictionary.data.model.SearchMode
import com.bilingual.dictionary.data.repository.DictionaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class OcrTranslator(private val repository: DictionaryRepository) {

    // Fast in-memory cache for OCR lookups
    private val translationCache = ConcurrentHashMap<String, Pair<String, DictionaryEntry?>>()

    suspend fun translateText(rawText: String): Pair<String, DictionaryEntry?> = withContext(Dispatchers.IO) {
        val cleanText = rawText.trim()
            .replace(Regex("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$"), "")
            .trim()

        if (cleanText.isEmpty()) return@withContext Pair("", null)

        val key = cleanText.lowercase()
        translationCache[key]?.let { return@withContext it }

        val entries = repository.lookup(cleanText, SearchMode.AUTO_DETECT)
        if (entries.isNotEmpty()) {
            val bestEntry = entries[0]
            val briefDef = extractBriefDefinition(bestEntry.definition)
            val result = Pair(briefDef, bestEntry)
            translationCache[key] = result
            return@withContext result
        }

        // If not found in full string, try first word if multi-word
        if (cleanText.contains(" ")) {
            val firstWord = cleanText.split(" ")[0]
            val subEntries = repository.lookup(firstWord, SearchMode.AUTO_DETECT)
            if (subEntries.isNotEmpty()) {
                val bestSub = subEntries[0]
                val briefDef = "${firstWord}: ${extractBriefDefinition(bestSub.definition)}"
                val result = Pair(briefDef, bestSub)
                translationCache[key] = result
                return@withContext result
            }
        }

        val emptyResult = Pair(cleanText, null)
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
