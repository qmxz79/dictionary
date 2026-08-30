package com.bilingual.dictionary.data.model

enum class SearchMode {
    AUTO_DETECT, // Latin letters -> search both EN and MS, then translate to ZH
    ZH_TO_EN,    // Chinese -> English
    ZH_TO_MS     // Chinese -> Malay
}

data class DictionaryEntry(
    val id: Long = 0,
    val word: String,
    val displayWord: String = word,
    val lang: String, // "en", "ms", "zh", "online"
    val phonetic: String? = null,
    val pos: String? = null, // part of speech
    val definition: String,
    val example: String? = null,
    val stemNote: String? = null, // Malay root word annotation
    var isFavorite: Boolean = false,
    val isOnline: Boolean = false
)

data class SuggestionItem(
    val word: String,
    val lang: String,
    val definition: String
)

data class HistoryItem(
    val id: Long,
    val query: String,
    val lang: String,
    val timestamp: Long
)

data class FavoriteItem(
    val id: Long,
    val word: String,
    val lang: String,
    val phonetic: String?,
    val pos: String?,
    val definition: String,
    val timestamp: Long
)
