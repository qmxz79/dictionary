package com.bilingual.dictionary.data.repository

import com.bilingual.dictionary.core.LanguageDetector
import com.bilingual.dictionary.core.MalayStemmer
import com.bilingual.dictionary.data.db.DictionaryDao
import com.bilingual.dictionary.data.model.DictionaryEntry
import com.bilingual.dictionary.data.model.FavoriteItem
import com.bilingual.dictionary.data.model.HistoryItem
import com.bilingual.dictionary.data.model.SearchMode
import com.bilingual.dictionary.data.model.SuggestionItem
import com.bilingual.dictionary.data.network.OnlineTranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DictionaryRepository(private val dao: DictionaryDao) {

    /**
     * Determines if text contains Chinese characters.
     */
    fun isChinese(text: String): Boolean {
        return LanguageDetector.isChinese(text)
    }

    /**
     * Main lookup routine.
     * @param offlineOnly If true, skip online translation fallback and history recording (for OCR use).
     */
    suspend fun lookup(query: String, mode: SearchMode, offlineOnly: Boolean = false): List<DictionaryEntry> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<DictionaryEntry>()
        val containsZh = isChinese(trimmed)

        if (containsZh || mode == SearchMode.ZH_TO_EN || mode == SearchMode.ZH_TO_MS) {
            // Chinese search
            val targetLang = when (mode) {
                SearchMode.ZH_TO_EN -> "en"
                SearchMode.ZH_TO_MS -> "ms"
                else -> null
            }
            val localList = dao.searchReverseChinese(trimmed, targetLang)
            results.addAll(localList)

            // Save history (skip for OCR)
            if (!offlineOnly) {
                dao.addHistory(trimmed, if (mode == SearchMode.ZH_TO_MS) "zh->ms" else "zh->en")
            }
        } else {
            // Latin search (Auto detect: search both EN and MS)
            val enMatches = dao.searchExact(trimmed, "en")
            val msMatches = dao.searchExact(trimmed, "ms")

            results.addAll(enMatches)
            results.addAll(msMatches)

            // If Malay is not found, attempt Morphological Stemming!
            if (msMatches.isEmpty()) {
                val stemCandidates = MalayStemmer.stem(trimmed)
                for (cand in stemCandidates) {
                    val rootMatches = dao.searchExact(cand.root, "ms")
                    if (rootMatches.isNotEmpty()) {
                        for (rm in rootMatches) {
                            results.add(
                                rm.copy(
                                    displayWord = trimmed,
                                    stemNote = cand.explanation
                                )
                            )
                        }
                        break // Found best root match
                    }
                }
            }

            // Save history (skip for OCR)
            if (!offlineOnly) {
                dao.addHistory(trimmed, "auto")
            }
        }

        // If offline search returned NO results, trigger Online Fallback (skip for OCR)
        if (results.isEmpty() && !offlineOnly) {
            val fallbackEntry = performOnlineFallback(trimmed, mode, containsZh)
            if (fallbackEntry != null) {
                results.add(fallbackEntry)
            }
        }

        return@withContext results
    }

    /**
     * Online fallback with local caching and smart language detection.
     */
    private fun performOnlineFallback(query: String, mode: SearchMode, containsZh: Boolean): DictionaryEntry? {
        if (containsZh) {
            val tgtLang = if (mode == SearchMode.ZH_TO_MS) "ms" else "en"
            return queryOnlineWithCache(query, "zh", tgtLang)
        }

        // Latin input: Smart detection between Malay (ms) and English (en)
        val primarySrcLang = LanguageDetector.detectLatinLanguage(query)
        val secondarySrcLang = if (primarySrcLang == "ms") "en" else "ms"

        // 1. Try detected primary language first
        val primaryResult = queryOnlineWithCache(query, primarySrcLang, "zh")
        if (primaryResult != null) {
            return primaryResult
        }

        // 2. If primary language failed, fallback to secondary Latin language
        return queryOnlineWithCache(query, secondarySrcLang, "zh")
    }

    private fun queryOnlineWithCache(query: String, srcLang: String, tgtLang: String): DictionaryEntry? {
        // 1. Check local cache first
        val cached = dao.getCachedOnlineResult(query, srcLang, tgtLang)
        if (!cached.isNullOrEmpty()) {
            return DictionaryEntry(
                word = query,
                displayWord = query,
                lang = "online",
                definition = cached,
                isOnline = true
            )
        }

        // 2. Query online
        val onlineResult = OnlineTranslationService.translate(query, srcLang, tgtLang)
        if (!onlineResult.isNullOrEmpty()) {
            dao.saveCachedOnlineResult(query, srcLang, tgtLang, onlineResult)
            return DictionaryEntry(
                word = query,
                displayWord = query,
                lang = "online",
                definition = onlineResult,
                isOnline = true
            )
        }
        return null
    }

    suspend fun getSuggestions(prefix: String): List<SuggestionItem> = withContext(Dispatchers.IO) {
        dao.searchSuggestions(prefix, 10)
    }

    suspend fun getFavorites(): List<FavoriteItem> = withContext(Dispatchers.IO) {
        dao.getAllFavorites()
    }

    suspend fun toggleFavorite(entry: DictionaryEntry): Boolean = withContext(Dispatchers.IO) {
        val isFav = dao.isFavorite(entry.word, entry.lang)
        if (isFav) {
            dao.removeFavorite(entry.word, entry.lang)
            false
        } else {
            dao.addFavorite(entry)
            true
        }
    }

    suspend fun removeFavorite(word: String, lang: String) = withContext(Dispatchers.IO) {
        dao.removeFavorite(word, lang)
    }

    suspend fun getHistory(): List<HistoryItem> = withContext(Dispatchers.IO) {
        dao.getHistory(50)
    }

    suspend fun deleteHistory(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteHistoryItem(id)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.clearAllHistory()
    }

    suspend fun getSpellCorrection(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2 || isChinese(trimmed)) return@withContext emptyList()
        val candidates = dao.getSpellCheckCandidates(trimmed, 200)
        com.bilingual.dictionary.core.SpellCorrector.findSuggestions(trimmed, candidates, maxResults = 3)
    }
}
